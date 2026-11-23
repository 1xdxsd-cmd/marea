'use strict';
const path = require('path');
const crypto = require('crypto');
const { promisify } = require('util');
const Database = require('better-sqlite3');

const scrypt = promisify(crypto.scrypt);

// Cupos por plan (miembros = personas que pueden entrar).
const PLANS = {
  solo: { label: 'Individual', seats: 1 },
  duo: { label: 'Dúo', seats: 2 },
  family: { label: 'Familiar', seats: 6 },
};

const dbPath = process.env.MAREA_DB || path.join(__dirname, 'marea.db');
const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
CREATE TABLE IF NOT EXISTS families (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  plan TEXT NOT NULL,
  active INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS members (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  device_id TEXT NOT NULL,
  device_name TEXT,
  token_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  last_seen INTEGER,
  revoked INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS invites (
  code TEXT PRIMARY KEY,
  family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at INTEGER
);
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  is_admin INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  kind TEXT NOT NULL,              -- 'app' (teléfono) o 'admin' (web)
  device_id TEXT,
  device_name TEXT,
  created_at INTEGER NOT NULL,
  last_seen INTEGER,
  expires_at INTEGER               -- NULL = no vence (sesiones de la app)
);
`);

// Migración: cada miembro (cupo) ahora puede estar ligado a una cuenta.
const memberCols = db.prepare('PRAGMA table_info(members)').all().map((c) => c.name);
if (!memberCols.includes('user_id')) {
  db.exec('ALTER TABLE members ADD COLUMN user_id INTEGER REFERENCES users(id) ON DELETE SET NULL');
}
db.exec('CREATE INDEX IF NOT EXISTS members_user ON members(user_id)');

// Migración: quien canjea el primer código de un plan Dúo/Familiar es su dueño y puede
// sumar a otras personas por correo.
const familyCols = db.prepare('PRAGMA table_info(families)').all().map((c) => c.name);
if (!familyCols.includes('owner_user_id')) {
  db.exec('ALTER TABLE families ADD COLUMN owner_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL');
}
// Un solo dispositivo reproduciendo a la vez por cuenta.
const userCols = db.prepare('PRAGMA table_info(users)').all().map((c) => c.name);
if (!userCols.includes('active_device')) {
  db.exec('ALTER TABLE users ADD COLUMN active_device TEXT');
  db.exec('ALTER TABLE users ADD COLUMN active_at INTEGER');
}
db.exec(`
CREATE TABLE IF NOT EXISTS email_invites (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  family_id INTEGER NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  email TEXT NOT NULL COLLATE NOCASE,
  invited_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at INTEGER NOT NULL,
  UNIQUE (family_id, email)
);
`);

const now = () => Date.now();
const sha256 = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');
const newToken = () => crypto.randomBytes(32).toString('base64url');
const httpError = (status, message) => Object.assign(new Error(message), { status });

// ---------- Contraseñas ----------

async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const key = await scrypt(password, salt, 64, { N: 16384, r: 8, p: 1 });
  return `scrypt$16384$8$1$${salt.toString('base64')}$${key.toString('base64')}`;
}

async function checkPassword(password, stored) {
  const [alg, N, r, p, salt, hash] = String(stored || '').split('$');
  if (alg !== 'scrypt') return false;
  const expected = Buffer.from(hash, 'base64');
  const key = await scrypt(password, Buffer.from(salt, 'base64'), expected.length, {
    N: Number(N),
    r: Number(r),
    p: Number(p),
  });
  return crypto.timingSafeEqual(key, expected);
}

// Hash fijo para comparar aunque el email no exista (evita revelar qué cuentas hay por el tiempo).
let dummyHash = null;
hashPassword('marea-dummy-password').then((h) => (dummyHash = h));

// ---------- Códigos de invitación ----------

// Alfabeto sin caracteres ambiguos (sin 0/O, 1/I/L).
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
function randomCode() {
  const pick = () => ALPHABET[crypto.randomInt(ALPHABET.length)];
  const block = () => Array.from({ length: 4 }, pick).join('');
  return `MAREA-${block()}-${block()}`;
}
function normalizeCode(raw) {
  return String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}
const codeKey = (code) => normalizeCode(code);
const prettyCode = (key) => key.replace(/^([A-Z]+)(.{4})(.{4})$/, '$1-$2-$3');

// ---------- Planes (familias) ----------

function seatsUsed(familyId) {
  const members = db
    .prepare('SELECT COUNT(*) c FROM members WHERE family_id = ? AND revoked = 0')
    .get(familyId).c;
  const pendingCodes = db
    .prepare(
      'SELECT COUNT(*) c FROM invites WHERE family_id = ? AND used_at IS NULL AND expires_at > ?'
    )
    .get(familyId, now()).c;
  const pendingEmails = db
    .prepare('SELECT COUNT(*) c FROM email_invites WHERE family_id = ?')
    .get(familyId).c;
  const pending = pendingCodes + pendingEmails;
  return { members, pending, pendingCodes, pendingEmails, total: members + pending };
}

function getFamily(familyId) {
  const fam = db.prepare('SELECT * FROM families WHERE id = ?').get(familyId);
  if (!fam) throw httpError(404, 'Plan no encontrado');
  return fam;
}

function createFamily(name, plan) {
  name = String(name || '').trim();
  if (!name) throw httpError(400, 'Falta el nombre del plan');
  if (!PLANS[plan]) throw httpError(400, `Plan inválido: ${plan}. Usa: ${Object.keys(PLANS).join(', ')}`);
  const info = db
    .prepare('INSERT INTO families (name, plan, created_at) VALUES (?, ?, ?)')
    .run(name.slice(0, 80), plan, now());
  return Number(info.lastInsertRowid);
}

function setPlan(familyId, plan) {
  if (!PLANS[plan]) throw httpError(400, `Plan inválido: ${plan}`);
  getFamily(familyId);
  db.prepare('UPDATE families SET plan = ? WHERE id = ?').run(plan, familyId);
}

function setFamilyActive(familyId, active) {
  getFamily(familyId);
  db.prepare('UPDATE families SET active = ? WHERE id = ?').run(active ? 1 : 0, familyId);
}

function createInvite(familyId, label, days = 7) {
  const fam = getFamily(familyId);
  label = String(label || '').trim();
  if (!label) throw httpError(400, 'Falta el nombre de la persona');
  days = Number(days);
  if (!Number.isFinite(days) || days <= 0) throw httpError(400, 'Días inválidos');
  const seats = PLANS[fam.plan].seats;
  const used = seatsUsed(familyId);
  if (used.total >= seats) {
    throw httpError(
      409,
      `Plan ${PLANS[fam.plan].label}: ${seats} cupo(s) y ya hay ${used.members} miembro(s) + ${used.pending} invitación(es) pendiente(s).`
    );
  }
  const code = randomCode();
  db.prepare(
    'INSERT INTO invites (code, family_id, label, created_at, expires_at) VALUES (?, ?, ?, ?, ?)'
  ).run(codeKey(code), familyId, label.slice(0, 80), now(), now() + days * 86400000);
  return code;
}

function deleteInvite(code) {
  return db.prepare('DELETE FROM invites WHERE code = ? AND used_at IS NULL').run(codeKey(code)).changes > 0;
}

function revokeMember(memberId) {
  return db.prepare('UPDATE members SET revoked = 1 WHERE id = ?').run(memberId).changes > 0;
}

function deleteEmailInvite(id) {
  return db.prepare('DELETE FROM email_invites WHERE id = ?').run(id).changes > 0;
}

function listAll() {
  const fams = db.prepare('SELECT * FROM families ORDER BY id').all();
  return fams.map((f) => ({
    ...f,
    owner: f.owner_user_id
      ? db.prepare('SELECT id, name, email FROM users WHERE id = ?').get(f.owner_user_id) || null
      : null,
    emailInvites: db
      .prepare('SELECT id, email, created_at FROM email_invites WHERE family_id = ? ORDER BY id')
      .all(f.id),
    planLabel: PLANS[f.plan]?.label ?? f.plan,
    seats: PLANS[f.plan]?.seats ?? 0,
    used: seatsUsed(f.id),
    members: db
      .prepare(
        `SELECT m.id, m.label, m.device_name, m.created_at, m.last_seen, m.revoked, m.user_id,
                u.email AS user_email, u.name AS user_name
         FROM members m LEFT JOIN users u ON u.id = m.user_id
         WHERE m.family_id = ? ORDER BY m.id`
      )
      .all(f.id),
    invites: db
      .prepare('SELECT * FROM invites WHERE family_id = ? AND used_at IS NULL AND expires_at > ? ORDER BY created_at')
      .all(f.id, now())
      .map((i) => ({ ...i, code: prettyCode(i.code) })),
  }));
}

function listUsers() {
  return db
    .prepare(
      `SELECT u.id, u.email, u.name, u.is_admin, u.created_at,
              (SELECT MAX(s.last_seen) FROM sessions s WHERE s.user_id = u.id) AS last_seen
       FROM users u ORDER BY u.id`
    )
    .all()
    .map((u) => ({ ...u, subscription: subscriptionFor(u.id) }));
}

// ---------- Cuentas y sesiones ----------

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function publicUser(u) {
  return { id: u.id, email: u.email, name: u.name, isAdmin: !!u.is_admin };
}

async function registerUser({ email, password, name }) {
  email = String(email || '').trim().toLowerCase();
  name = String(name || '').trim();
  password = String(password || '');
  if (!EMAIL_RE.test(email) || email.length > 120) throw httpError(400, 'Escribe un correo válido');
  if (!name) throw httpError(400, 'Escribe tu nombre');
  if (password.length < 8) throw httpError(400, 'La contraseña debe tener al menos 8 caracteres');
  if (db.prepare('SELECT 1 FROM users WHERE email = ?').get(email)) {
    throw httpError(409, 'Ya existe una cuenta con ese correo. Inicia sesión.');
  }
  const hash = await hashPassword(password);
  const info = db
    .prepare('INSERT INTO users (email, name, password_hash, created_at) VALUES (?, ?, ?, ?)')
    .run(email, name.slice(0, 80), hash, now());
  return db.prepare('SELECT * FROM users WHERE id = ?').get(info.lastInsertRowid);
}

async function authenticate(email, password) {
  email = String(email || '').trim().toLowerCase();
  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  const ok = await checkPassword(String(password || ''), user ? user.password_hash : dummyHash);
  if (!user || !ok) throw httpError(401, 'Correo o contraseña incorrectos');
  return user;
}

function createSession(userId, kind, { deviceId, deviceName, ttlMs } = {}) {
  const token = newToken();
  db.prepare(
    `INSERT INTO sessions (user_id, token_hash, kind, device_id, device_name, created_at, last_seen, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(
    userId,
    sha256(token),
    kind,
    deviceId ? String(deviceId).slice(0, 80) : null,
    deviceName ? String(deviceName).slice(0, 80) : null,
    now(),
    now(),
    ttlMs ? now() + ttlMs : null
  );
  return token;
}

function sessionUser(token, kind) {
  if (!token) return null;
  const row = db
    .prepare(
      `SELECT s.id AS session_id, s.expires_at, u.*
       FROM sessions s JOIN users u ON u.id = s.user_id
       WHERE s.token_hash = ? AND s.kind = ?`
    )
    .get(sha256(token), kind);
  if (!row) return null;
  if (row.expires_at && row.expires_at < now()) {
    db.prepare('DELETE FROM sessions WHERE id = ?').run(row.session_id);
    return null;
  }
  db.prepare('UPDATE sessions SET last_seen = ? WHERE id = ?').run(now(), row.session_id);
  return row;
}

function deleteSession(token) {
  db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(sha256(token));
}

// ---------- Suscripción de una cuenta ----------

function subscriptionFor(userId) {
  const row = db
    .prepare(
      `SELECT m.id AS member_id, m.label, f.id AS family_id, f.name AS family, f.plan, f.active
       FROM members m JOIN families f ON f.id = m.family_id
       WHERE m.user_id = ? AND m.revoked = 0
       ORDER BY f.active DESC, m.id DESC LIMIT 1`
    )
    .get(userId);
  if (!row) return null;
  return {
    active: !!row.active,
    plan: row.plan,
    planLabel: PLANS[row.plan]?.label ?? row.plan,
    family: row.family,
    label: row.label,
  };
}

// Detalle del plan para la sección Cuenta de la app: dueño, personas y correos pendientes.
function familyDetailsFor(userId) {
  const row = db
    .prepare(
      `SELECT f.* FROM members m JOIN families f ON f.id = m.family_id
       WHERE m.user_id = ? AND m.revoked = 0
       ORDER BY f.active DESC, m.id DESC LIMIT 1`
    )
    .get(userId);
  if (!row) return null;
  const owner = row.owner_user_id
    ? db.prepare('SELECT id, name, email FROM users WHERE id = ?').get(row.owner_user_id)
    : null;
  const isOwner = row.owner_user_id === userId;
  const members = db
    .prepare(
      `SELECT m.id, m.label, u.id AS user_id, u.name, u.email
       FROM members m LEFT JOIN users u ON u.id = m.user_id
       WHERE m.family_id = ? AND m.revoked = 0 ORDER BY m.id`
    )
    .all(row.id)
    .map((m) => ({
      id: m.id,
      name: m.name || m.label,
      email: isOwner ? m.email || null : null,
      isOwner: !!owner && m.user_id === owner.id,
      isYou: m.user_id === userId,
    }));
  const used = seatsUsed(row.id);
  return {
    seats: PLANS[row.plan]?.seats ?? 0,
    seatsFree: Math.max(0, (PLANS[row.plan]?.seats ?? 0) - used.total),
    isOwner,
    owner: owner ? { name: owner.name, email: isOwner ? owner.email : null } : null,
    members,
    pendingEmails: isOwner
      ? db.prepare('SELECT id, email FROM email_invites WHERE family_id = ? ORDER BY id').all(row.id)
      : [],
  };
}

function ownedFamily(userId) {
  const fam = db
    .prepare(
      `SELECT f.* FROM families f JOIN members m ON m.family_id = f.id
       WHERE f.owner_user_id = ? AND m.user_id = ? AND m.revoked = 0 LIMIT 1`
    )
    .get(userId, userId);
  if (!fam) throw httpError(403, 'Solo el dueño del plan puede administrar a las personas');
  if (!fam.active) throw httpError(403, 'El plan no está activo');
  return fam;
}

// El dueño suma a otra persona por correo. Si ya tiene cuenta entra al momento;
// si no, queda pendiente hasta que se registre con ese correo.
function addFamilyEmail(ownerId, rawEmail) {
  const email = String(rawEmail || '').trim().toLowerCase();
  if (!EMAIL_RE.test(email)) throw httpError(400, 'Escribe un correo válido');
  const tx = db.transaction(() => {
    const fam = ownedFamily(ownerId);
    const seats = PLANS[fam.plan].seats;
    if (seats <= 1) throw httpError(409, `El plan ${PLANS[fam.plan].label} es solo para una persona`);
    if (seatsUsed(fam.id).total >= seats) {
      throw httpError(409, `Tu plan ${PLANS[fam.plan].label} ya no tiene cupos libres (${seats} en total)`);
    }
    const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
    if (user) {
      if (user.id === ownerId) throw httpError(409, 'Ese es tu propio correo');
      const sub = subscriptionFor(user.id);
      if (sub && sub.active) throw httpError(409, 'Esa persona ya tiene un plan activo');
      db.prepare('UPDATE members SET revoked = 1 WHERE user_id = ? AND revoked = 0').run(user.id);
      db.prepare(
        `INSERT INTO members (family_id, label, device_id, device_name, token_hash, created_at, last_seen, user_id)
         VALUES (?, ?, 'cuenta', NULL, ?, ?, ?, ?)`
      ).run(fam.id, user.name, sha256(newToken()), now(), now(), user.id);
      return { added: true, name: user.name };
    }
    if (db.prepare('SELECT 1 FROM email_invites WHERE family_id = ? AND email = ?').get(fam.id, email)) {
      throw httpError(409, 'Ese correo ya está invitado');
    }
    db.prepare(
      'INSERT INTO email_invites (family_id, email, invited_by, created_at) VALUES (?, ?, ?, ?)'
    ).run(fam.id, email, ownerId, now());
    return { added: false };
  });
  return tx();
}

function removeFamilyMember(ownerId, memberId) {
  const fam = ownedFamily(ownerId);
  const m = db
    .prepare('SELECT * FROM members WHERE id = ? AND family_id = ? AND revoked = 0')
    .get(memberId, fam.id);
  if (!m) throw httpError(404, 'Persona no encontrada');
  if (m.user_id === ownerId) throw httpError(409, 'No puedes quitarte a ti mismo');
  db.prepare('UPDATE members SET revoked = 1 WHERE id = ?').run(m.id);
}

function removeFamilyEmail(ownerId, inviteId) {
  const fam = ownedFamily(ownerId);
  const ok = db.prepare('DELETE FROM email_invites WHERE id = ? AND family_id = ?').run(inviteId, fam.id).changes > 0;
  if (!ok) throw httpError(404, 'Invitación no encontrada');
}

// Al registrarse o iniciar sesión: si alguien le compartió su plan por correo, entra.
function claimEmailInvite(user) {
  const sub = subscriptionFor(user.id);
  if (sub && sub.active) return;
  const inv = db
    .prepare(
      `SELECT e.*, f.plan, f.active FROM email_invites e JOIN families f ON f.id = e.family_id
       WHERE e.email = ? ORDER BY e.id LIMIT 1`
    )
    .get(user.email);
  if (!inv || !inv.active) return;
  const tx = db.transaction(() => {
    db.prepare('UPDATE members SET revoked = 1 WHERE user_id = ? AND revoked = 0').run(user.id);
    db.prepare(
      `INSERT INTO members (family_id, label, device_id, device_name, token_hash, created_at, last_seen, user_id)
       VALUES (?, ?, 'cuenta', NULL, ?, ?, ?, ?)`
    ).run(inv.family_id, user.name, sha256(newToken()), now(), now(), user.id);
    db.prepare('DELETE FROM email_invites WHERE id = ?').run(inv.id);
  });
  tx();
}

// ---------- Un dispositivo reproduciendo a la vez ----------

const ACTIVE_TIMEOUT_MS = 2 * 60 * 1000;

// claim = este dispositivo empezó a reproducir: pasa a ser el activo.
// Sin claim (latido): sigue activo si nadie más tomó la cuenta desde entonces.
function playbackHeartbeat(userId, deviceId, claim) {
  deviceId = String(deviceId || '').slice(0, 80);
  if (!deviceId) throw httpError(400, 'Dispositivo inválido');
  const u = db.prepare('SELECT active_device, active_at FROM users WHERE id = ?').get(userId);
  const stale = !u.active_at || now() - u.active_at > ACTIVE_TIMEOUT_MS;
  if (claim || stale || u.active_device === deviceId) {
    db.prepare('UPDATE users SET active_device = ?, active_at = ? WHERE id = ?').run(deviceId, now(), userId);
    return { active: true };
  }
  return { active: false };
}

// Canjea un código de invitación para una cuenta ya iniciada.
function redeemForUser(userId, code, { deviceId, deviceName } = {}) {
  const key = codeKey(code);
  if (key.length < 8) throw httpError(400, 'Código inválido');

  const tx = db.transaction(() => {
    const current = subscriptionFor(userId);
    if (current && current.active) {
      throw httpError(409, `Ya tienes un plan activo (${current.planLabel} · ${current.family})`);
    }
    const inv = db.prepare('SELECT * FROM invites WHERE code = ?').get(key);
    if (!inv) throw httpError(404, 'Código no encontrado');
    if (inv.used_at) throw httpError(409, 'Este código ya fue usado');
    if (inv.expires_at < now()) throw httpError(410, 'Este código expiró');

    const fam = db.prepare('SELECT * FROM families WHERE id = ?').get(inv.family_id);
    if (!fam || !fam.active) throw httpError(403, 'El plan no está activo');

    const members = db
      .prepare('SELECT COUNT(*) c FROM members WHERE family_id = ? AND revoked = 0')
      .get(fam.id).c;
    if (members >= PLANS[fam.plan].seats) throw httpError(409, 'El plan no tiene cupos libres');

    // Si tenía un cupo en un plan desactivado, se libera al pasar al nuevo.
    db.prepare('UPDATE members SET revoked = 1 WHERE user_id = ? AND revoked = 0').run(userId);

    // token_hash es obligatorio en el esquema original; los cupos ligados a una cuenta
    // no usan token propio (la app entra con la sesión), así que se guarda uno aleatorio.
    db.prepare(
      `INSERT INTO members (family_id, label, device_id, device_name, token_hash, created_at, last_seen, user_id)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      fam.id,
      inv.label,
      String(deviceId || 'cuenta').slice(0, 80),
      deviceName ? String(deviceName).slice(0, 80) : null,
      sha256(newToken()),
      now(),
      now(),
      userId
    );
    db.prepare('UPDATE invites SET used_at = ? WHERE code = ?').run(now(), key);
    // El primero que canjea un código del plan queda como dueño.
    if (!fam.owner_user_id) {
      db.prepare('UPDATE families SET owner_user_id = ? WHERE id = ?').run(userId, fam.id);
    }
  });
  tx();
  return subscriptionFor(userId);
}

// Al actualizar la app: el token del código canjeado antes pasa a la cuenta (no se pide código de nuevo).
function claimLegacyMember(userId, legacyToken) {
  if (!legacyToken) return;
  const m = db
    .prepare('SELECT id FROM members WHERE token_hash = ? AND revoked = 0 AND user_id IS NULL')
    .get(sha256(legacyToken));
  if (!m) return;
  if (subscriptionFor(userId)?.active) return;
  db.prepare('UPDATE members SET user_id = ? WHERE id = ?').run(userId, m.id);
  db.prepare(
    'UPDATE families SET owner_user_id = ? WHERE owner_user_id IS NULL AND id = (SELECT family_id FROM members WHERE id = ?)'
  ).run(userId, m.id);
}

// ---------- Compatibilidad con versiones anteriores de la app (solo código) ----------

function redeem({ code, deviceId, deviceName }) {
  const key = codeKey(code);
  if (key.length < 8) throw httpError(400, 'Código inválido');
  if (!deviceId || String(deviceId).length < 8) throw httpError(400, 'Dispositivo inválido');

  const tx = db.transaction(() => {
    const inv = db.prepare('SELECT * FROM invites WHERE code = ?').get(key);
    if (!inv) throw httpError(404, 'Código no encontrado');
    if (inv.used_at) throw httpError(409, 'Este código ya fue usado');
    if (inv.expires_at < now()) throw httpError(410, 'Este código expiró');

    const fam = db.prepare('SELECT * FROM families WHERE id = ?').get(inv.family_id);
    if (!fam || !fam.active) throw httpError(403, 'El plan no está activo');

    const members = db
      .prepare('SELECT COUNT(*) c FROM members WHERE family_id = ? AND revoked = 0')
      .get(fam.id).c;
    if (members >= PLANS[fam.plan].seats) throw httpError(409, 'El plan no tiene cupos libres');

    const token = newToken();
    db.prepare(
      `INSERT INTO members (family_id, label, device_id, device_name, token_hash, created_at, last_seen)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run(fam.id, inv.label, String(deviceId), deviceName || null, sha256(token), now(), now());
    db.prepare('UPDATE invites SET used_at = ? WHERE code = ?').run(now(), key);

    return { token, plan: fam.plan, family: fam.name, label: inv.label };
  });
  return tx();
}

function verifyToken(token) {
  if (!token) return null;
  const row = db
    .prepare(
      `SELECT m.id, m.label, m.revoked, f.name AS family, f.plan, f.active
       FROM members m JOIN families f ON f.id = m.family_id
       WHERE m.token_hash = ?`
    )
    .get(sha256(token));
  if (!row || row.revoked || !row.active) return null;
  db.prepare('UPDATE members SET last_seen = ? WHERE id = ?').run(now(), row.id);
  return row;
}

// ---------- Administradores ----------

const adminCount = () => db.prepare('SELECT COUNT(*) c FROM users WHERE is_admin = 1').get().c;

function setAdmin(email, isAdmin) {
  return (
    db
      .prepare('UPDATE users SET is_admin = ? WHERE email = ?')
      .run(isAdmin ? 1 : 0, String(email || '').trim().toLowerCase()).changes > 0
  );
}

module.exports = {
  PLANS,
  db,
  httpError,
  prettyCode,
  createFamily,
  setPlan,
  setFamilyActive,
  createInvite,
  deleteInvite,
  revokeMember,
  listAll,
  listUsers,
  seatsUsed,
  publicUser,
  registerUser,
  authenticate,
  createSession,
  sessionUser,
  deleteSession,
  subscriptionFor,
  redeemForUser,
  claimLegacyMember,
  familyDetailsFor,
  addFamilyEmail,
  removeFamilyMember,
  removeFamilyEmail,
  claimEmailInvite,
  deleteEmailInvite,
  playbackHeartbeat,
  redeem,
  verifyToken,
  adminCount,
  setAdmin,
};
