'use strict';
const path = require('path');
const crypto = require('crypto');
const express = require('express');
const readline = require('readline');
const d = require('./db');
const { run, tokenize } = require('./cli');

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', 1); // detrás de nginx/Caddy
app.use(express.json({ limit: '8kb' }));

// ---------- Utilidades ----------

// Límite simple de intentos por IP y por grupo de rutas.
function rateLimit(name, max, windowMs) {
  const hits = new Map();
  setInterval(() => {
    const t = Date.now();
    for (const [ip, list] of hits) {
      const fresh = list.filter((x) => t - x < windowMs);
      if (fresh.length) hits.set(ip, fresh);
      else hits.delete(ip);
    }
  }, 5 * 60 * 1000).unref();
  return (req, res, next) => {
    const t = Date.now();
    const list = (hits.get(req.ip) || []).filter((x) => t - x < windowMs);
    if (list.length >= max) {
      return res.status(429).json({ error: 'Demasiados intentos. Prueba en unos minutos.' });
    }
    list.push(t);
    hits.set(req.ip, list);
    next();
  };
}
const limitRedeem = rateLimit('redeem', 10, 15 * 60 * 1000);
const limitAuth = rateLimit('auth', 20, 15 * 60 * 1000);

// Envuelve un handler y convierte errores con .status en respuestas JSON.
const handle = (fn) => async (req, res) => {
  try {
    await fn(req, res);
  } catch (e) {
    if (!e.status) console.error(e);
    res.status(e.status || 500).json({ error: e.status ? e.message : 'Error interno' });
  }
};

const bearer = (req) => {
  const header = req.get('authorization') || '';
  return header.startsWith('Bearer ') ? header.slice(7) : '';
};

const meResponse = (user) => ({
  ok: true,
  user: d.publicUser(user),
  subscription: d.subscriptionFor(user.id),
});

app.get('/healthz', (_req, res) => res.json({ ok: true }));

// ---------- API de la app ----------

app.post(
  '/v1/auth/register',
  limitAuth,
  handle(async (req, res) => {
    const { email, password, name, deviceId, deviceName, legacyToken } = req.body || {};
    const user = await d.registerUser({ email, password, name });
    d.claimLegacyMember(user.id, legacyToken);
    const token = d.createSession(user.id, 'app', { deviceId, deviceName });
    res.json({ ...meResponse(user), token });
  })
);

app.post(
  '/v1/auth/login',
  limitAuth,
  handle(async (req, res) => {
    const { email, password, deviceId, deviceName, legacyToken } = req.body || {};
    const user = await d.authenticate(email, password);
    d.claimLegacyMember(user.id, legacyToken);
    const token = d.createSession(user.id, 'app', { deviceId, deviceName });
    res.json({ ...meResponse(user), token });
  })
);

app.post(
  '/v1/auth/logout',
  handle(async (req, res) => {
    const token = bearer(req);
    if (token) d.deleteSession(token);
    res.json({ ok: true });
  })
);

app.get(
  '/v1/me',
  handle(async (req, res) => {
    const token = bearer(req);
    const user = d.sessionUser(token, 'app');
    if (user) return res.json(meResponse(user));

    // Versiones anteriores de la app (entraban solo con código).
    const member = d.verifyToken(token);
    if (member) {
      return res.json({
        ok: true,
        plan: member.plan,
        planLabel: d.PLANS[member.plan]?.label ?? member.plan,
        family: member.family,
        label: member.label,
      });
    }
    res.status(401).json({ error: 'Tu sesión se cerró. Vuelve a iniciar sesión.' });
  })
);

app.post(
  '/v1/subscription/redeem',
  limitRedeem,
  handle(async (req, res) => {
    const user = d.sessionUser(bearer(req), 'app');
    if (!user) return res.status(401).json({ error: 'Tu sesión se cerró. Vuelve a iniciar sesión.' });
    const { code, deviceId, deviceName } = req.body || {};
    const subscription = d.redeemForUser(user.id, code, { deviceId, deviceName });
    res.json({ ok: true, subscription });
  })
);

// Canje sin cuenta: lo usan las versiones anteriores de la app.
app.post(
  '/v1/redeem',
  limitRedeem,
  handle(async (req, res) => {
    const { code, deviceId, deviceName } = req.body || {};
    const r = d.redeem({ code, deviceId, deviceName });
    res.json({
      ok: true,
      token: r.token,
      plan: r.plan,
      planLabel: d.PLANS[r.plan].label,
      family: r.family,
      label: r.label,
    });
  })
);

// ---------- Web de administración ----------

const ADMIN_COOKIE = 'marea_admin';
const ADMIN_TTL_MS = 7 * 24 * 60 * 60 * 1000;

// Código de un solo uso para crear el primer administrador desde la web.
let setupCode = null;
function ensureSetupCode() {
  if (d.adminCount() > 0) {
    setupCode = null;
    return null;
  }
  if (!setupCode) setupCode = crypto.randomInt(100000, 1000000).toString();
  return setupCode;
}

function readCookie(req, name) {
  const raw = req.get('cookie') || '';
  for (const part of raw.split(';')) {
    const [k, ...v] = part.trim().split('=');
    if (k === name) return decodeURIComponent(v.join('='));
  }
  return '';
}

function setAdminCookie(req, res, token, maxAgeMs) {
  const attrs = [
    `${ADMIN_COOKIE}=${encodeURIComponent(token)}`,
    'Path=/admin',
    'HttpOnly',
    'SameSite=Strict',
    `Max-Age=${Math.floor(maxAgeMs / 1000)}`,
  ];
  if (req.secure) attrs.push('Secure');
  res.setHeader('Set-Cookie', attrs.join('; '));
}

const admin = express.Router();

// Las peticiones que cambian algo deben venir de la propia web (protección CSRF).
admin.use((req, res, next) => {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'GET' && req.get('x-marea-admin') !== '1') {
    return res.status(403).json({ error: 'Petición no permitida' });
  }
  next();
});

function requireAdmin(req, res, next) {
  const user = d.sessionUser(readCookie(req, ADMIN_COOKIE), 'admin');
  if (!user || !user.is_admin) return res.status(401).json({ error: 'Inicia sesión' });
  req.user = user;
  next();
}

admin.get(
  '/session',
  handle(async (req, res) => {
    const user = d.sessionUser(readCookie(req, ADMIN_COOKIE), 'admin');
    res.json({
      user: user && user.is_admin ? d.publicUser(user) : null,
      setupNeeded: !!ensureSetupCode(),
    });
  })
);

admin.post(
  '/login',
  limitAuth,
  handle(async (req, res) => {
    const { email, password } = req.body || {};
    const user = await d.authenticate(email, password);
    if (!user.is_admin) throw d.httpError(403, 'Esta cuenta no es de administrador');
    const token = d.createSession(user.id, 'admin', { ttlMs: ADMIN_TTL_MS, deviceName: req.get('user-agent') });
    setAdminCookie(req, res, token, ADMIN_TTL_MS);
    res.json({ ok: true, user: d.publicUser(user) });
  })
);

admin.post(
  '/setup',
  limitAuth,
  handle(async (req, res) => {
    const expected = ensureSetupCode();
    if (!expected) throw d.httpError(409, 'Ya hay un administrador. Inicia sesión.');
    const { code, email, name, password } = req.body || {};
    const given = Buffer.from(String(code || '').trim());
    const want = Buffer.from(expected);
    if (given.length !== want.length || !crypto.timingSafeEqual(given, want)) {
      throw d.httpError(403, 'Código de configuración incorrecto (míralo en la consola del servidor)');
    }
    // Si la cuenta ya existe (creada en la app) se usa esa, con su contraseña.
    let user;
    try {
      user = await d.registerUser({ email, password, name: name || email });
    } catch (e) {
      if (e.status !== 409) throw e;
      user = await d.authenticate(email, password);
    }
    d.setAdmin(user.email, true);
    setupCode = null;
    const token = d.createSession(user.id, 'admin', { ttlMs: ADMIN_TTL_MS, deviceName: req.get('user-agent') });
    setAdminCookie(req, res, token, ADMIN_TTL_MS);
    console.log(`Administrador creado: ${user.email}`);
    res.json({ ok: true, user: d.publicUser({ ...user, is_admin: 1 }) });
  })
);

admin.post(
  '/logout',
  handle(async (req, res) => {
    const token = readCookie(req, ADMIN_COOKIE);
    if (token) d.deleteSession(token);
    setAdminCookie(req, res, '', 0);
    res.json({ ok: true });
  })
);

admin.get(
  '/state',
  requireAdmin,
  handle(async (_req, res) => {
    res.json({
      plans: Object.entries(d.PLANS).map(([id, p]) => ({ id, ...p })),
      families: d.listAll(),
      users: d.listUsers(),
    });
  })
);

admin.post(
  '/families',
  requireAdmin,
  handle(async (req, res) => {
    const { name, plan } = req.body || {};
    const id = d.createFamily(name, plan);
    res.json({ ok: true, id });
  })
);

admin.patch(
  '/families/:id',
  requireAdmin,
  handle(async (req, res) => {
    const id = Number(req.params.id);
    const { plan, active } = req.body || {};
    if (plan !== undefined) d.setPlan(id, plan);
    if (active !== undefined) d.setFamilyActive(id, !!active);
    res.json({ ok: true });
  })
);

admin.post(
  '/families/:id/invites',
  requireAdmin,
  handle(async (req, res) => {
    const { label, days } = req.body || {};
    const code = d.createInvite(Number(req.params.id), label, days ?? 7);
    res.json({ ok: true, code });
  })
);

admin.delete(
  '/invites/:code',
  requireAdmin,
  handle(async (req, res) => {
    if (!d.deleteInvite(req.params.code)) throw d.httpError(404, 'Invitación no encontrada');
    res.json({ ok: true });
  })
);

admin.post(
  '/members/:id/revoke',
  requireAdmin,
  handle(async (req, res) => {
    if (!d.revokeMember(Number(req.params.id))) throw d.httpError(404, 'Miembro no encontrado');
    res.json({ ok: true });
  })
);

app.use('/admin/api', admin);
app.use(
  '/admin',
  express.static(path.join(__dirname, 'public', 'admin'), {
    setHeaders: (res) => {
      res.setHeader('X-Frame-Options', 'DENY');
      res.setHeader(
        'Content-Security-Policy',
        "default-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; frame-ancestors 'none'"
      );
    },
  })
);
app.get('/', (_req, res) => res.redirect('/admin/'));

// ---------- Arranque ----------

// Pterodactyl define SERVER_PORT y necesita escuchar en 0.0.0.0.
const port = Number(process.env.PORT || process.env.SERVER_PORT || 1753);
const host = process.env.HOST || (process.env.SERVER_PORT ? '0.0.0.0' : '127.0.0.1');
app.listen(port, host, () => {
  console.log(`Servidor de acceso de Marea en http://${host}:${port}`);
  console.log(`Web de administración: /admin`);
  const code = ensureSetupCode();
  if (code) {
    console.log(`Todavía no hay administrador. Abre /admin y usa este código de configuración: ${code}`);
  }
  console.log('Escribe "help" en esta consola para ver los comandos de administración.');
});

// La consola sigue funcionando: invite 1 "Mamá", list, revoke 2...
const rl = readline.createInterface({ input: process.stdin });
rl.on('line', (line) => {
  const parts = tokenize(line.trim());
  if (!parts.length) return;
  if (parts[0] === 'admin:setup') {
    const code = ensureSetupCode();
    console.log(code ? `Código de configuración: ${code}` : 'Ya hay un administrador.');
    return;
  }
  try {
    run(parts[0], parts.slice(1), console.log);
  } catch (e) {
    console.log('Error:', e.message);
  }
});
