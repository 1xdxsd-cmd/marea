# Servidor de acceso de Marea (cuentas + planes Individual / Dúo / Familiar)

Controla quién puede usar la app:

1. Cada persona **crea una cuenta** en la app (nombre, correo y contraseña) o inicia sesión.
2. Dentro de la app, en **Cuenta → Suscripción**, pone el **código de invitación** (`MAREA-XXXX-XXXX`)
   que le diste. Con eso su cuenta ocupa un cupo del plan y puede usar la app.
3. Tú administras todo desde la **web de administración** (`/admin`): crear planes, cambiar de
   plan, generar códigos, cancelar invitaciones, revocar miembros y ver las cuentas.

| Plan | Cupos |
|------|-------|
| `solo` (Individual) | 1 |
| `duo` (Dúo) | 2 |
| `family` (Familiar) | 6 |

Sin código activo la cuenta usa el **plan gratis** de la app (anuncios cada 3 canciones y hasta 6
saltos por hora). Con código es **Premium** (sin anuncios, saltos sin límite y ecualizador). Si
revocas a alguien o desactivas el plan, vuelve al plan gratis en su próxima verificación.

## Instalar / actualizar

```bash
cd server
npm install
node server.js        # en Pterodactyl usa SERVER_PORT y escucha en 0.0.0.0
```

Variables: `PORT` o `SERVER_PORT` (1753 por defecto), `HOST`, `MAREA_DB` (ruta del archivo SQLite, `marea.db` por defecto).

**Actualizar desde la versión anterior:** reemplaza `db.js`, `server.js`, `cli.js`, `package.json` y
copia la carpeta `public/`. La base de datos ahora se llama `marea.db`; si quieres conservar la de
la versión anterior, renómbrala a `marea.db` antes de arrancar (las tablas nuevas se crean solas).
Los códigos nuevos empiezan con `MAREA-`.

### HTTPS

La app bloquea tráfico sin cifrar, así que publica el servidor detrás de HTTPS (Caddy, nginx o el
proxy de tu panel). La web de administración también debe abrirse por HTTPS para que la contraseña
viaje cifrada.

## Web de administración

Abre `https://tu-servidor/admin`.

**Primera vez:** al arrancar sin ningún administrador, la consola muestra:

```
Todavía no hay administrador. Abre /admin y usa este código de configuración: 123456
```

En la web pon ese código, tu nombre, correo y contraseña. Esa cuenta queda como administrador
(y también sirve para entrar a la app). Si ya te registraste en la app, usa el mismo correo y
contraseña. Para ver el código otra vez escribe `admin:setup` en la consola.

Desde la web puedes:

- Crear un plan (nombre + Individual / Dúo / Familiar).
- Cambiar el plan, desactivarlo (bloquea a todos) o reactivarlo.
- Generar un código de invitación para una persona (7 días por defecto) y copiarlo.
- Cancelar invitaciones pendientes.
- Revocar a un miembro (libera su cupo).
- Ver las cuentas registradas y su suscripción.

## Consola (opcional)

Los comandos de antes siguen funcionando en la consola del panel o con `node cli.js`:

```
family:new "<nombre>" <solo|duo|family>   Crea un plan
family:plan <familiaId> <solo|duo|family> Cambia el plan
family:off <familiaId> / family:on <id>   Desactiva / reactiva
invite <familiaId> "<nombre>" [dias]      Genera un código
revoke <miembroId>                        Quita el acceso a un miembro
list                                      Planes, miembros e invitaciones
users                                     Cuentas registradas
admin:grant <email> / admin:remove <email> Da o quita acceso a la web
```

## Conectar la app

Compila el APK apuntando a tu servidor (`MAREA_ACCESS_URL`, ya configurado como secreto en GitHub
Actions):

```bash
./gradlew assembleRelease -PMAREA_ACCESS_URL=https://marea.tudominio.com
```

## API

App:

- `POST /v1/auth/register` `{ name, email, password, deviceId, deviceName, legacyToken? }` → `{ token, user, subscription }`
- `POST /v1/auth/login` `{ email, password, deviceId, deviceName, legacyToken? }` → `{ token, user, subscription }`
- `POST /v1/auth/logout` con `Authorization: Bearer <token>`
- `GET /v1/me` con `Authorization: Bearer <token>` → `{ ok, user, subscription }` o `401`
- `POST /v1/subscription/redeem` `{ code }` con `Authorization: Bearer <token>` → `{ ok, subscription }`
- `subscription` = `{ active, plan, planLabel, family, label }` o `null`.
- `legacyToken`: si el teléfono ya había canjeado un código con la versión anterior, ese cupo pasa
  a la cuenta al iniciar sesión (no hace falta un código nuevo).
- Se mantienen `POST /v1/redeem` y el `GET /v1/me` con token de miembro para las versiones
  anteriores de la app.
- Límites por IP: 20 intentos de inicio de sesión / registro y 10 canjes cada 15 min.

Web de administración (`/admin/api/*`, cookie `HttpOnly` + `SameSite=Strict`, solo cuentas admin):
`GET /session`, `POST /login`, `POST /setup`, `POST /logout`, `GET /state`, `POST /families`,
`PATCH /families/:id`, `POST /families/:id/invites`, `DELETE /invites/:code`,
`POST /members/:id/revoke`.

## Límites honestos

El servidor decide quién *entra* a la app, pero el audio lo obtiene la app directamente de
YouTube, no de tu servidor. Alguien con el APK y conocimientos técnicos podría recompilarlo
sin esta verificación. Por eso, **no publiques el APK** en grupos abiertos: compártelo solo con
tu familia. Sin conexión, la app deja usarse hasta 7 días desde la última verificación.
