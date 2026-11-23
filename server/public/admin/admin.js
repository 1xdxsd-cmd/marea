'use strict';

const $ = (sel, root = document) => root.querySelector(sel);
let state = null;

// ---------- Helpers ----------

async function api(method, url, body) {
  const res = await fetch(`/admin/api${url}`, {
    method,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-Marea-Admin': '1' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && url !== '/login') {
    showAuth();
    throw new Error(data.error || 'Inicia sesión');
  }
  if (!res.ok) throw new Error(data.error || `Error ${res.status}`);
  return data;
}

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

const fmt = (ts) =>
  ts ? new Date(ts).toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

let toastTimer;
function toast(msg, isError = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.toggle('err', isError);
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}

// Ejecuta una acción, muestra el error si falla y recarga el panel.
async function act(fn, okMsg) {
  try {
    const r = await fn();
    if (okMsg) toast(okMsg);
    await load();
    return r;
  } catch (e) {
    toast(e.message, true);
    return null;
  }
}

// ---------- Sesión ----------

function showAuth(setupNeeded = false) {
  $('#app').hidden = true;
  $('#auth').hidden = false;
  $('#login-form').hidden = setupNeeded;
  $('#setup-form').hidden = !setupNeeded;
  $('#auth-sub').textContent = setupNeeded ? 'Configuración inicial' : 'Administración de accesos';
}

function showApp(user) {
  $('#auth').hidden = true;
  $('#app').hidden = false;
  $('#who').textContent = `${user.name} · ${user.email}`;
}

async function boot() {
  try {
    const s = await api('GET', '/session');
    if (s.user) {
      showApp(s.user);
      await load();
    } else {
      showAuth(s.setupNeeded);
    }
  } catch (e) {
    showAuth(false);
    $('#auth-error').textContent = e.message;
  }
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

$('#login-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  $('#auth-error').textContent = '';
  try {
    const r = await api('POST', '/login', formData(ev.target));
    ev.target.reset();
    showApp(r.user);
    await load();
  } catch (e) {
    $('#auth-error').textContent = e.message;
  }
});

$('#setup-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  $('#auth-error').textContent = '';
  try {
    const r = await api('POST', '/setup', formData(ev.target));
    ev.target.reset();
    showApp(r.user);
    await load();
  } catch (e) {
    $('#auth-error').textContent = e.message;
  }
});

$('#logout').addEventListener('click', async () => {
  await api('POST', '/logout').catch(() => {});
  showAuth(false);
});

$('#refresh').addEventListener('click', () => act(() => Promise.resolve(), 'Actualizado'));

$('#new-family').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const data = formData(ev.target);
  const r = await act(() => api('POST', '/families', data), 'Plan creado');
  if (r) ev.target.reset();
});

$('#copy-code').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText($('#code-value').textContent);
    toast('Código copiado');
  } catch {
    toast('No se pudo copiar; selecciónalo a mano', true);
  }
});

// ---------- Render ----------

async function load() {
  state = await api('GET', '/state');
  render();
}

function planOptions(selected) {
  return state.plans.map((p) =>
    el('option', { value: p.id, selected: p.id === selected }, `${p.label} (${p.seats} cupo${p.seats > 1 ? 's' : ''})`)
  );
}

function render() {
  const sel = $('#plan-select');
  const current = sel.value || 'family';
  sel.replaceChildren(...planOptions(current));

  const box = $('#families');
  box.replaceChildren();
  if (!state.families.length) {
    box.append(el('p', { class: 'empty' }, 'Todavía no hay planes. Crea el primero arriba.'));
  }
  for (const f of state.families) box.append(renderFamily(f));

  const users = $('#users');
  users.replaceChildren();
  if (!state.users.length) {
    users.append(el('tr', {}, el('td', { colspan: 4, class: 'empty' }, 'Nadie se ha registrado todavía.')));
  }
  for (const u of state.users) {
    const s = u.subscription;
    users.append(
      el(
        'tr',
        {},
        el('td', {}, u.name, u.is_admin ? el('span', { class: 'badge muted gap-left' }, 'admin') : null),
        el('td', {}, u.email),
        el(
          'td',
          {},
          s
            ? el('span', { class: `badge ${s.active ? 'on' : 'off'}` }, `${s.planLabel} · ${s.family}`)
            : el('span', { class: 'badge muted' }, 'Sin suscripción')
        ),
        el('td', {}, fmt(u.last_seen))
      )
    );
  }
}

function renderFamily(f) {
  const active = !!f.active;
  const seats = [];
  for (let i = 0; i < f.seats; i++) {
    const cls = i < f.used.members ? 'used' : i < f.used.total ? 'pending' : '';
    seats.push(el('span', { class: `seat ${cls}` }));
  }

  const planSelect = el('select', {
    'aria-label': 'Cambiar plan',
    onchange: (ev) => act(() => api('PATCH', `/families/${f.id}`, { plan: ev.target.value }), 'Plan actualizado'),
  }, planOptions(f.plan));

  const toggle = el(
    'button',
    {
      class: `btn small ${active ? 'danger' : ''}`,
      onclick: () => {
        if (active && !confirm(`¿Desactivar "${f.name}"? Todos sus miembros pierden el acceso.`)) return;
        act(() => api('PATCH', `/families/${f.id}`, { active: !active }), active ? 'Plan desactivado' : 'Plan reactivado');
      },
    },
    active ? 'Desactivar' : 'Reactivar'
  );

  const members = f.members.filter((m) => !m.revoked);
  const revoked = f.members.filter((m) => m.revoked);

  const memberItems = members.map((m) =>
    el(
      'div',
      { class: 'item' },
      el(
        'div',
        { class: 'main' },
        el('b', {}, m.label, f.owner_user_id && m.user_id === f.owner_user_id ? el('span', { class: 'badge muted gap-left' }, 'dueño') : null),
        el('span', {}, [m.user_email || m.device_name || 'Sin cuenta (versión anterior de la app)', `visto ${fmt(m.last_seen)}`].join(' · '))
      ),
      el(
        'button',
        {
          class: 'btn small danger',
          onclick: () => {
            if (!confirm(`¿Quitar el acceso a ${m.label}? Se libera su cupo.`)) return;
            act(() => api('POST', `/members/${m.id}/revoke`), 'Acceso revocado');
          },
        },
        'Revocar'
      )
    )
  );

  const inviteItems = f.invites.map((i) =>
    el(
      'div',
      { class: 'item' },
      el('div', { class: 'main' }, el('b', {}, i.label), el('span', {}, `${i.code} · vence ${fmt(i.expires_at)}`)),
      el(
        'div',
        { class: 'family-actions' },
        el('button', { class: 'btn small', onclick: () => showCode(i.label, i.code) }, 'Ver código'),
        el(
          'button',
          {
            class: 'btn small danger',
            onclick: () => {
              if (!confirm(`¿Cancelar la invitación de ${i.label}?`)) return;
              act(() => api('DELETE', `/invites/${encodeURIComponent(i.code)}`), 'Invitación cancelada');
            },
          },
          'Cancelar'
        )
      )
    )
  );

  const emailItems = (f.emailInvites || []).map((e) =>
    el(
      'div',
      { class: 'item' },
      el('div', { class: 'main' }, el('b', {}, e.email), el('span', {}, `invitado por correo · ${fmt(e.created_at)}`)),
      el(
        'button',
        {
          class: 'btn small danger',
          onclick: () => {
            if (!confirm(`¿Cancelar la invitación a ${e.email}?`)) return;
            act(() => api('DELETE', `/email-invites/${e.id}`), 'Invitación cancelada');
          },
        },
        'Cancelar'
      )
    )
  );

  const full = f.used.total >= f.seats;
  const inviteForm = el(
    'form',
    {
      class: 'invite-form',
      onsubmit: async (ev) => {
        ev.preventDefault();
        const data = formData(ev.target);
        const r = await act(
          () => api('POST', `/families/${f.id}/invites`, { label: data.label, days: Number(data.days) || 7 }),
          null
        );
        if (r) showCode(data.label, r.code);
      },
    },
    el('input', { name: 'label', placeholder: 'Nombre de la persona', required: true, disabled: full || !active }),
    el('input', { name: 'days', type: 'number', min: 1, value: 7, title: 'Días de validez', 'aria-label': 'Días de validez', disabled: full || !active }),
    el('button', { class: 'btn primary small', type: 'submit', disabled: full || !active }, 'Generar código')
  );

  return el(
    'article',
    { class: `family ${active ? '' : 'off'}` },
    el(
      'div',
      { class: 'family-head' },
      el(
        'div',
        { class: 'family-title' },
        el('h3', {}, f.name, ' ', el('span', { class: `badge ${active ? 'on' : 'off'}` }, active ? 'Activo' : 'Desactivado')),
        el('span', { class: 'meta' }, `#${f.id} · ${f.planLabel} · ${f.used.members}/${f.seats} miembros${f.used.pending ? ` · ${f.used.pending} invitación(es) pendiente(s)` : ''}${f.owner ? ` · dueño: ${f.owner.email}` : ''}`),
        el('div', { class: 'seats', 'aria-hidden': 'true' }, seats)
      ),
      el('div', { class: 'family-actions' }, planSelect, toggle)
    ),
    el('div', { class: 'list' }, el('h4', {}, 'Miembros'), memberItems.length ? memberItems : el('p', { class: 'empty' }, 'Nadie todavía.')),
    inviteItems.length || emailItems.length
      ? el('div', { class: 'list' }, el('h4', {}, 'Invitaciones pendientes'), inviteItems, emailItems)
      : null,
    el(
      'div',
      { class: 'list' },
      el('h4', {}, 'Nueva invitación'),
      full ? el('p', { class: 'empty' }, 'Sin cupos libres. Revoca a alguien o cambia de plan.') : null,
      inviteForm
    ),
    revoked.length
      ? el(
          'details',
          {},
          el('summary', { class: 'muted' }, `Revocados (${revoked.length})`),
          el(
            'div',
            { class: 'list gap-top' },
            revoked.map((m) =>
              el('div', { class: 'item revoked' }, el('div', { class: 'main' }, el('b', {}, m.label), el('span', {}, m.user_email || m.device_name || '')))
            )
          )
        )
      : null
  );
}

function showCode(label, code) {
  $('#code-for').textContent = label;
  $('#code-value').textContent = code;
  $('#code-dialog').showModal();
}

boot();
