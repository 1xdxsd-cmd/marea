#!/usr/bin/env node
'use strict';
const d = require('./db');

const fmt = (ts) => (ts ? new Date(ts).toLocaleString('es') : '-');

const help = `
Marea - administración de acceso

  family:new "<nombre>" <solo|duo|family>   Crea un plan
  family:plan <familiaId> <solo|duo|family> Cambia el plan
  family:off <familiaId>                    Desactiva el plan (bloquea a todos)
  family:on <familiaId>                     Reactiva el plan
  invite <familiaId> "<nombre>" [dias]      Genera un código de invitación (7 días por defecto)
  revoke <miembroId>                        Quita el acceso a un miembro (libera su cupo)
  list                                      Muestra planes, miembros e invitaciones
  users                                     Muestra las cuentas registradas
  admin:grant <email>                       Da acceso a la web de administración
  admin:remove <email>                      Quita el acceso a la web de administración
  admin:setup                               Muestra el código para crear el primer admin

La web de administración está en /admin.

Planes: solo=1 cupo, duo=2 cupos, family=6 cupos.
`;

// Separa una línea respetando comillas: invite 1 "Tía Rosa" 3
function tokenize(line) {
  const out = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(line))) out.push(m[1] ?? m[2] ?? m[3]);
  return out;
}

function run(cmd, args = [], log = console.log) {
  switch (cmd) {
    case 'family:new': {
      const [name, plan] = args;
      if (!name || !plan) throw new Error('Uso: family:new "<nombre>" <solo|duo|family>');
      const id = d.createFamily(name, plan);
      log(`Plan creado. ID de familia: ${id}`);
      break;
    }
    case 'family:plan':
      d.setPlan(Number(args[0]), args[1]);
      log('Plan actualizado.');
      break;
    case 'family:off':
      d.setFamilyActive(Number(args[0]), false);
      log('Plan desactivado. Los miembros perderán acceso en su próxima verificación.');
      break;
    case 'family:on':
      d.setFamilyActive(Number(args[0]), true);
      log('Plan reactivado.');
      break;
    case 'invite': {
      const [fid, label, days] = args;
      if (!fid || !label) throw new Error('Uso: invite <familiaId> "<nombre>" [dias]');
      const code = d.createInvite(Number(fid), label, days ? Number(days) : 7);
      log(`Código para ${label}: ${code}`);
      break;
    }
    case 'revoke': {
      const ok = d.revokeMember(Number(args[0]));
      log(ok ? 'Acceso revocado. Cupo liberado.' : 'Miembro no encontrado.');
      break;
    }
    case 'list': {
      const all = d.listAll();
      if (!all.length) log('Sin planes todavía. Crea uno con family:new.');
      for (const f of all) {
        const used = d.seatsUsed(f.id);
        log(
          `\n[${f.id}] ${f.name} - ${f.planLabel} (${used.members}/${f.seats} miembros)` +
            `${f.active ? '' : '  ** DESACTIVADO **'}`
        );
        for (const m of f.members) {
          log(
            `   miembro #${m.id} ${m.label}  ${m.user_email || m.device_name || ''}  ` +
              `visto: ${fmt(m.last_seen)}${m.revoked ? '  (revocado)' : ''}`
          );
        }
        for (const i of f.invites) {
          log(`   invitación pendiente ${i.label}: ${i.code}  vence: ${fmt(i.expires_at)}`);
        }
      }
      break;
    }
    case 'users': {
      const users = d.listUsers();
      if (!users.length) log('Sin cuentas todavía.');
      for (const u of users) {
        const s = u.subscription;
        log(
          `#${u.id} ${u.email} (${u.name})${u.is_admin ? ' [admin]' : ''}  ` +
            (s ? `${s.planLabel} · ${s.family}${s.active ? '' : ' (desactivado)'}` : 'sin suscripción')
        );
      }
      break;
    }
    case 'admin:grant':
      log(d.setAdmin(args[0], true) ? 'Ahora es administrador.' : 'No existe una cuenta con ese correo.');
      break;
    case 'admin:remove':
      log(d.setAdmin(args[0], false) ? 'Ya no es administrador.' : 'No existe una cuenta con ese correo.');
      break;
    default:
      log(help);
  }
}

module.exports = { run, tokenize };

if (require.main === module) {
  const [, , cmd, ...args] = process.argv;
  try {
    run(cmd, args);
  } catch (e) {
    console.error('Error:', e.message);
    process.exitCode = 1;
  }
}
