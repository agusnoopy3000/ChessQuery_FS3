# Correr ChessQuery FS3 en Arch Linux

Guía operativa **paso a paso** para levantar la demo completa en el equipo Acer Nitro AN515-55 con Arch Linux + Hyprland descrito en `~/hypr-ricing-plan/contexto-actual.md`.

> **Recursos disponibles en tu equipo (verificado 2026-05-27):**
>
> - CPU: Intel i7-10750H (12 cores) @ 5.00 GHz
> - RAM: 15.44 GiB (de sobra para el modo completo `make up`, ~5–6 GB en uso; ~12 GB libres ahora)
> - Disco: 743 GiB libres en `/dev/nvme1n1p3` (ext4) — sobra para imágenes Docker
> - GPU: RTX 3060 Mobile (no se usa)
> - OS: Arch Linux x86_64, kernel **7.0.9-arch2-1**, Hyprland **0.55.2**
> - Usuario `kenny` ya está en grupo `docker` ✓

---

## Uso diario — prender y apagar todo (lo que vas a hacer el 90% del tiempo)

Esta es la **rutina corta** una vez que ya hiciste el setup inicial (§ 1–§ 3). Si es tu primera vez, salteá esta sección y empezá desde el § 0.

### Prender todo (3 comandos + 2 terminales)

```bash
# 1. Arrancar el daemon de Docker (alias propio, NO usa systemctl enable)
docker-on

# 2. Arrancar Supabase Local (auth + storage + studio)
cd /home/kenny/ChessQuery_FS3
supabase start                # ~20 s si ya está cacheado

# 3. Arrancar el stack ChessQuery (microservicios + BDs + RabbitMQ + Redis)
cd infrastructure
make up                       # ~30 s si las imágenes ya están built

# 4. (En otra terminal) Frontend del jugador
cd /home/kenny/ChessQuery_FS3/frontend
npm run dev:portal            # http://localhost:5173

# 5. (En otra terminal) Frontend del organizador
cd /home/kenny/ChessQuery_FS3/frontend
npm run dev:organizer         # http://localhost:5174
```

**Verificar que todo arrancó OK:**

```bash
docker compose ps             # desde infrastructure/ — todos (healthy) o Up
docker-status                 # estado del daemon
supabase status               # URLs y keys de Supabase
```

### Apagar todo (orden inverso)

```bash
# 1. Cerrar los dos `npm run dev` con Ctrl+C en cada terminal
#    (o si quedaron en background:)
pkill -f vite

# 2. Detener stack ChessQuery (conserva los volumes con datos)
cd /home/kenny/ChessQuery_FS3/infrastructure
make down

# 3. Detener Supabase (conserva datos)
cd /home/kenny/ChessQuery_FS3
supabase stop

# 4. Apagar el daemon de Docker (libera RAM, alias propio)
docker-off
```

### Atajos visuales


| Acción               | Comando                            | Cuánto tarda |
| -------------------- | ---------------------------------- | ------------ |
| **Prender Docker**   | `docker-on`                        | <2 s         |
| **Apagar Docker**    | `docker-off`                       | <2 s         |
| **Estado Docker**    | `docker-status`                    | instantáneo  |
| **Logs del daemon**  | `docker-logs`                      | instantáneo  |
| **Prender stack**    | `make up` (en `infrastructure/`)   | 30 s         |
| **Apagar stack**     | `make down` (en `infrastructure/`) | 10 s         |
| **Prender Supabase** | `supabase start` (en raíz)         | 20 s         |
| **Apagar Supabase**  | `supabase stop` (en raíz)          | 5 s          |
| **Reset de datos**   | `make demo-reset`                  | 5 s          |


> **Importante (recordatorio):** este equipo usa Docker con **control manual** (sin `systemctl enable`). Cada sesión arrancás con `docker-on` y al terminar apagás con `docker-off` para no dejar el daemon corriendo de fondo. Si no, gasta CPU/RAM al pedo.

---

## 0. Resumen de lo que vas a levantar


| Capa                                      | Cuántos contenedores                                                            | Para qué                                                |
| ----------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------- |
| Supabase Local                            | ~10 (lo levanta `supabase start`)                                               | Auth, Storage, Realtime, Postgres meta, Studio, Mailpit |
| Bases de datos PostgreSQL                 | 6 (`user_db`, `tournament_db`, `game_db`, `analytics_db`, `notif_db`, `etl_db`) | Una por microservicio                                   |
| RabbitMQ                                  | 1 + 1 one-shot setup                                                            | Bus de eventos `ChessEvents`                            |
| Redis                                     | 1                                                                               | Rate-limit + cache                                      |
| Microservicios Java                       | 5 (`ms-users`, `ms-tournament`, `ms-game`, `ms-analytics`, `ms-notifications`)  | Backend de dominio                                      |
| Microservicio Python                      | 1 (`ms-etl`, fuera del alcance demo pero arranca)                               | ETL (apagable)                                          |
| BFFs NestJS                               | 3 (`bff-player`, `bff-organizer`, `bff-admin`)                                  | Backend para frontends                                  |
| API Gateway                               | 1 (Spring Cloud Gateway)                                                        | Punto de entrada                                        |
| Frontends Vite (locales, fuera de Docker) | 2 (`chess-portal`, `organizer-panel`)                                           | Las dos SPAs                                            |


Total: **~25 contenedores + 2 procesos node locales**. Ocupa ~5 GB de RAM en uso normal.

---

## 1. Verificación previa de dependencias

Tu equipo ya tiene casi todo. Esta sección **solo verifica** y te indica qué instalar si falta algo. La mayoría de los comandos se corren con tu usuario `kenny` (no `sudo`) salvo donde se indique.

### 1.1 Herramientas que ya tenés (verificado en tu equipo)

```bash
docker --version          # esperado: Docker 29.x+  (tenés 29.5.1) ✓
docker compose version    # esperado: v2.x+         (tenés 5.1.4 binario standalone) ✓
supabase --version        # esperado: ≥ 1.150       (tenés 2.98.2) ✓
node --version            # esperado: ≥ 20          (tenés 26.1.0) ✓
npm --version             # esperado: ≥ 10          (tenés 11.14.1) ✓
java -version             # opcional: 17.x          (tenés 17.0.19; build corre en Docker)
jq --version              # esperado: 1.7+          (tenés 1.8.1) ✓
```

**No necesitás:** Maven (`mvn`) ni cliente Postgres (`psql`). El build de los microservicios Java ocurre dentro de Docker, y `psql` solo sirve si querés inspeccionar las BDs a mano.

> Nota: hay una versión más nueva de Supabase CLI disponible (`2.101.0`). **No es bloqueante.** La CLI viene del **AUR** (paquete `supabase-bin`), no de los repos oficiales — por eso `pacman -S supabase-cli` falla. Para actualizar usá tu helper AUR (`yay`), SIN `sudo`:
>
> ```bash
> yay -S supabase-bin
> ```
>
> ⚠️ **Cuidado:** en la CLI 2.10x el paquete AUR `supabase-bin` quedó roto — instala
> `supabase` pero **no** el motor `supabase-go`, así que `supabase start` falla con
> `Could not find the supabase-go binary`. Si te pasa, usá la vía npm (`npm i -g supabase`)
> — ver §7.0.

> Si algo no responde, instalá con (repos oficiales + AUR por separado):
>
> ```bash
> sudo pacman -S docker docker-compose docker-buildx nodejs npm jdk17-openjdk
> yay -S supabase-bin     # AUR, sin sudo
> ```

### 1.2 Herramientas opcionales (NO instaladas en tu equipo, opcionales)

```bash
# Para inspeccionar bases de datos desde la terminal (no obligatorio)
sudo pacman -S postgresql      # te da el cliente psql

# curl + jq ya los tenés ✓
```

### 1.3 Verificar que tu usuario está en el grupo `docker` ✓ confirmado

Tu usuario `kenny` ya pertenece al grupo `docker` (`wheel input docker`). Comando de chequeo:

```bash
id -nG kenny | tr ' ' '\n' | grep -w docker
```

Si en otro equipo **no devuelve nada**:

```bash
sudo usermod -aG docker $USER
newgrp docker        # o cerrar sesión y volver a entrar
```

### 1.4 Arrancar el daemon de Docker (control manual — sin autoarranque)

En este equipo Docker se opera **manualmente**: lo prendés al empezar la sesión de trabajo y lo apagás al terminar. **No se usa `systemctl enable`** para evitar que consuma RAM/CPU cuando no estás usando contenedores. Para esto tenés aliases ya configurados en tu shell:

```bash
docker-on       # arranca el daemon (equivale a: sudo systemctl start docker)
docker-off      # detiene el daemon (equivale a: sudo systemctl stop docker)
docker-status   # ver estado actual
docker-logs     # ver journal del daemon (últimas 50 líneas)
```

Flujo típico al sentarse a trabajar:

```bash
docker-on
docker info | head -5    # verificar que responde sin error
```

> **NO ejecutes `sudo systemctl enable docker`** — rompería el patrón de control manual que usás también para Oracle XE (alias `oracle-on`/`oracle-off`).

### 1.5 Verificar que `host.docker.internal` funcione en Linux

Los containers necesitan resolver `host.docker.internal` para hablar con Supabase (que corre en tu host, no en la red de docker compose). En Linux moderno con Docker 20.10+ esto se hace agregando `--add-host=host.docker.internal:host-gateway`, pero **el compose de ChessQuery NO lo declara**. Vamos a parcharlo solo si hace falta.

**Test rápido** (más adelante, cuando el stack esté arriba):

```bash
docker exec chessquery_api_gateway sh -c "getent hosts host.docker.internal || echo MISSING"
```

Si imprime `MISSING`, ver §7.1 (Troubleshooting) para agregar `extra_hosts`.

### 1.6 Verificar puertos libres ✓ confirmado

Al momento de escribir esta guía (2026-05-27) **todos los puertos críticos están libres** en tu equipo. ChessQuery usa muchos puertos; si alguno se ocupa después, el compose falla con `bind: address already in use`. Comando de chequeo:

```bash
ss -tlnp 2>/dev/null | grep -E ':(3001|3002|3003|5173|5174|5433|5434|5435|5436|5437|5438|5672|6379|8080|8081|8082|8083|8084|8085|8086|15672|54321|54322|54323|54324|54325)' || echo "Todos los puertos críticos están libres"
```

Si ves algún puerto ocupado, decidí: ¿podés cerrar el proceso o vas a mapear ChessQuery a otro puerto? (Generalmente conviene cerrar el otro proceso.)

### 1.7 Verificar recursos en vivo

```bash
free -h
df -h /
```

Necesitás **al menos 4 GB libres de RAM y 10 GB libres de disco** antes de empezar. En tu equipo ahora mismo tenés ~11 GB libres y 800 GB de disco — más que suficiente.

---

## 2. Configurar Supabase y `.env` (primera vez)

### 2.1 Arrancar Supabase Local

Desde la raíz del repo:

```bash
cd /home/kenny/ChessQuery_FS3
supabase start
```

La **primera vez** descarga las imágenes (~2 GB) y tarda 2–4 minutos. Las siguientes son <30 s.

Al final imprime una tabla con:

- `Project URL` / `API URL` (típicamente `http://127.0.0.1:54321`)
- `DB URL` (postgres en `54322`)
- `Studio` (`http://127.0.0.1:54323`)
- `Mailpit` (`http://127.0.0.1:54324`) — emails de prueba (en versiones viejas se llamaba *Inbucket*)
- `Publishable` key (`sb_publishable_...`)
- `Secret` key (`sb_secret_...`)

> ⚠️ **Importante — formato de keys (CLI ≥ 2.x):** las versiones nuevas de la CLI (ej. la 2.98.2 que tenés) solo muestran las keys en formato **nuevo** (`Publishable` / `Secret`). Pero este proyecto usa el formato **legacy** (`anon key`, `service_role key`, `JWT secret`), que la CLI ya **no imprime**. La buena noticia: esas keys legacy son **valores fijos por defecto** del Supabase local (idénticos en cualquier máquina), así que están escritos tal cual en el `.env` del § 2.2. No tenés que buscarlas.

Aplica automáticamente las migraciones de `supabase/migrations/`:

- `00001` crea `user_profiles` + trigger `on_auth_user_created`
- `00002` crea el bucket `chessquery-pgn` (1 MB máx, privado)
- `00003` configura el webhook `auth.users → http://host.docker.internal:8080/webhooks/supabase/user-registered`
- `00004` policies admin

Para imprimir las keys cuando quieras:

```bash
supabase status
```

### 2.2 Crear `infrastructure/.env`

```bash
cd /home/kenny/ChessQuery_FS3/infrastructure
cp .env.example .env
```

Abrí `.env` con tu editor y dejá las variables de Supabase así. Estas son las **keys legacy por defecto del Supabase local** — son públicas y fijas (las mismas en toda máquina mientras no cambies el `JWT secret` del `config.toml`), por eso podés pegarlas directo sin buscar nada:

```bash
# En infrastructure/.env

# === Supabase (keys legacy por defecto del Supabase local) ===
SUPABASE_URL=http://host.docker.internal:54321
SUPABASE_JWT_SECRET=super-secret-jwt-token-with-at-least-32-characters-long
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0
SUPABASE_SERVICE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU
SUPABASE_WEBHOOK_SECRET=dev-webhook-secret
STORAGE_PROVIDER=supabase

# === Para el frontend (la usa Vite cuando build/dev) ===
VITE_SUPABASE_URL=http://localhost:54321
VITE_SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0

# === CORS (agregá tu IP LAN si vas a probar desde el celu) ===
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost
```

> **¿De dónde salen esas keys si `supabase status` no las muestra?** Son las keys legacy estándar del stack local de Supabase. Si algún día cambiás el `JWT secret` en `supabase/config.toml`, podés recuperar las reales con:
>
> ```bash
> docker exec supabase_storage_ChessQuery_FS3 sh -c 'env' | grep -E "^(ANON_KEY|SERVICE_KEY|AUTH_JWT_SECRET)="
> ```

> **Importante sobre `SUPABASE_URL`:** dentro de Docker, los containers no resuelven `127.0.0.1` o `localhost` del host. Deben usar `host.docker.internal`. Si te falla (§7.1), agregaremos `extra_hosts`.

### 2.3 Crear `.env` de los frontends

Cada SPA Vite lee su propio `.env`:

```bash
cd /home/kenny/ChessQuery_FS3/frontend/apps/chess-portal
cp .env.example .env

cd /home/kenny/ChessQuery_FS3/frontend/apps/organizer-panel
cp .env.example .env
```

Los `.env.example` de los frontends **ya traen la anon key correcta por defecto**, así que con el `cp` de arriba ya quedan listos — no tenés que editar nada. Verificá solo que `VITE_SUPABASE_URL=http://localhost:54321` (no `host.docker.internal`, porque el browser corre en tu host, no dentro de Docker).

---

## 3. Primer arranque (rebuild completo)

La primera vez tenés que **construir las imágenes Docker**. Tarda 3–6 minutos según tu red (Maven baja dependencias). Las siguientes veces es cuestión de segundos.

### 3.1 Levantar todo el stack ChessQuery

```bash
cd /home/kenny/ChessQuery_FS3/infrastructure
make up
```

Esto ejecuta `docker compose up -d --build --remove-orphans`. Verás logs de build de cada Dockerfile + arranque de cada contenedor en background.

### 3.2 Esperar a que todo esté `healthy`

```bash
docker compose ps
```

Esperá hasta ver `(healthy)` o `Up` en todos. Los Java tienen `start_period: 90s`, así que dales tiempo. Esperá ~1 min después del build.

Si alguno queda en `unhealthy` después de 3 min, mirá sus logs:

```bash
docker compose logs --tail=100 ms-users     # cambiá por el servicio que falla
```

### 3.3 Verificar que RabbitMQ creó las colas correctamente

El contenedor `rabbitmq_setup` es un job one-shot que crea exchanges y colas. Verificá que terminó OK:

```bash
docker compose logs rabbitmq_setup
```

Debe terminar con `[init-rabbitmq] Topología creada. Saliendo.` o similar. Si quedó algo a medias, regenerar las colas:

```bash
make clean-setup
```

Mirá la Management UI en `http://localhost:15672` (user: `chessquery`, pass: `chessquery_dev`). Debés ver el exchange `ChessEvents` y al menos 4 colas (`users.elo.queue`, `notifications.game.events`, `notifications.user.events`, `notifications.tournament.events`).

### 3.4 Instalar deps de frontend y arrancar las SPAs

Esto es **una vez** por máquina (las dependencias quedan cacheadas en `node_modules`).

```bash
cd /home/kenny/ChessQuery_FS3/frontend
npm install              # ~1-2 min la primera vez, instala todo el monorepo
```

Después abrí **dos terminales** (o usá un terminal multiplexer como `kitty` con splits):

```bash
# Terminal 1 — chess-portal (jugador)
cd /home/kenny/ChessQuery_FS3/frontend
npm run dev:portal       # quedará en http://localhost:5173

# Terminal 2 — organizer-panel (organizador)
cd /home/kenny/ChessQuery_FS3/frontend
npm run dev:organizer    # quedará en http://localhost:5174
```

Vite imprime "ready in XXX ms" y queda escuchando. **No cierres esas terminales** durante la demo.

### 3.5 Preflight check

Hay un script que valida todo el stack en un solo comando:

```bash
cd /home/kenny/ChessQuery_FS3
bash scripts/preflight.sh
```

Debe terminar con `✅ Todo OK. Demo lista.` o con algunos `WARN` no bloqueantes (como "Studio apagado", que es esperado si no lo activaste). Si imprime `FAIL`, no salgas a presentar — investigá primero.

### 3.6 Crear los 3 usuarios canónicos de la demo

Tres maneras, elegí una:

**(a) Desde Supabase Studio (más visual)**

1. Abrí `http://127.0.0.1:54323`
2. Authentication → Users → "Add user" → manual
3. Creá los tres:
  - `carla@demo.cl` / `demo1234` — auto-confirm — user_metadata: `{"role":"ORGANIZER","firstName":"Carla","lastName":"Soto"}`
  - `ana@demo.cl` / `demo1234` — `{"role":"PLAYER","firstName":"Ana","lastName":"Rojas"}`
  - `bruno@demo.cl` / `demo1234` — `{"role":"PLAYER","firstName":"Bruno","lastName":"Lagos"}`

**(b) Desde el frontend (Register form)**

1. `http://localhost:5174` → "Crear cuenta" → completar como ORGANIZER → `carla@demo.cl`
2. `http://localhost:5173` → "Crear cuenta" → completar como PLAYER → `ana@demo.cl`
3. Repetir con `bruno@demo.cl`

**(c) Por curl al endpoint signUp** (avanzado, dejá para automatización)

Tras crear los usuarios, verificá en `ms-users` que se provisionaron:

```bash
curl -s http://localhost:8081/users/by-email?email=carla@demo.cl | jq
curl -s http://localhost:8081/users/by-email?email=ana@demo.cl | jq
curl -s http://localhost:8081/users/by-email?email=bruno@demo.cl | jq
```

Cada uno debe devolver un JSON con `"id": <número>`. Si devuelve 404, el webhook + auto-provisioning fallaron — leer §7.1.

---

## 4. Probar la demo (smoke test)

Antes de salir a presentar, corré los 4 flujos. Usá los URLs:


| URL                                     | Para qué                                                 |
| --------------------------------------- | -------------------------------------------------------- |
| `http://localhost:5173`                 | chess-portal (login con `ana@demo.cl` / `demo1234`)      |
| `http://localhost:5174`                 | organizer-panel (login con `carla@demo.cl` / `demo1234`) |
| `http://localhost:15672`                | RabbitMQ Management — ver mensajes circulando            |
| `http://localhost:54323`                | Supabase Studio — inspeccionar tablas y storage          |
| `http://localhost:54324`                | Inbucket / Mailpit — ver emails de prueba                |
| `http://localhost:8080/actuator/health` | API Gateway healthcheck                                  |
| `http://localhost:8083/actuator/health` | ms-game healthcheck (válida la conexión a Storage)       |


Seguí los flujos F1–F4 documentados en `PRESENTACION.md` §9. Lo más vistoso es F3 (partida en vivo). Si una partida termina con resultado, abrí Supabase Studio → Storage → bucket `chessquery-pgn` y vas a ver el `.pgn` subido.

---

## 5. Operación: detener y volver a arrancar

### 5.1 Detener todo (preserva datos)

```bash
# Detener stack ChessQuery (los volumes con datos se conservan)
cd /home/kenny/ChessQuery_FS3/infrastructure
make down

# Detener Supabase
cd /home/kenny/ChessQuery_FS3
supabase stop
```

Las terminales con `npm run dev:portal` y `npm run dev:organizer` se cierran con `Ctrl+C` en cada una.

### 5.2 Volver a arrancar (próximas veces, ya con todo instalado)

> Este es el flujo **rápido** una vez que ya hiciste el primer arranque. Es el mismo de la sección **"Uso diario"** al inicio del documento — replicado acá para referencia rápida desde la sección de operación.

```bash
# 1. Arrancar el daemon Docker (alias propio — control manual)
docker-on                       # ~2 s

# 2. Supabase Local
cd /home/kenny/ChessQuery_FS3
supabase start                  # ~20 s

# 3. Stack ChessQuery
cd infrastructure
make up                         # ~30 s si las imágenes ya estaban built

# 4. Esperar healthchecks
docker compose ps               # esperar (healthy)

# 5. Frontends (dos terminales)
cd ../frontend
npm run dev:portal &            # terminal 1
npm run dev:organizer &         # terminal 2

# 6. Preflight (opcional pero recomendado antes de presentar)
cd ..
bash scripts/preflight.sh
```

### 5.3 Resetear datos sin destruir todo

Para repetir la demo desde cero conservando seeds (aperturas, clubes, países):

```bash
cd /home/kenny/ChessQuery_FS3/infrastructure
make demo-reset
```

Borra partidas, torneos, inscripciones, notificaciones y usuarios provisionados; conserva la estructura.

### 5.4 Borrar todo (cuando algo se rompió fuerte)

```bash
cd /home/kenny/ChessQuery_FS3/infrastructure
make reset                      # pide confirmación, borra volumes
cd ..
supabase stop --no-backup       # borra datos Supabase también
```

Tras esto, hay que **rehacer todo desde §2** (Supabase start + .env + make up + crear usuarios).

---

## 6. URLs útiles durante la demo


| URL                                     | Para qué               | Credenciales                    |
| --------------------------------------- | ---------------------- | ------------------------------- |
| `http://localhost:5173`                 | chess-portal (jugador) | `ana@demo.cl` / `demo1234`      |
| `http://localhost:5174`                 | organizer-panel        | `carla@demo.cl` / `demo1234`    |
| `http://localhost:8080`                 | API Gateway            | (público)                       |
| `http://localhost:8080/actuator/health` | Health del gateway     | (público)                       |
| `http://localhost:15672`                | RabbitMQ Mgmt          | `chessquery` / `chessquery_dev` |
| `http://localhost:54323`                | Supabase Studio        | (sin auth en local)             |
| `http://localhost:54324`                | Inbucket (Mailpit)     | (sin auth)                      |
| `http://localhost:54321/storage/v1/...` | Supabase Storage REST  | Bearer service_role_key         |


Pegale `http://localhost:5173/play/{id}` cuando crees una partida en vivo y querés que un segundo navegador (incógnito) se sume.

---

## 7. Troubleshooting

### 7.0 Supabase: `Could not find the supabase-go binary`

**Síntoma:** `supabase start` / `supabase status` fallan con
`Could not find the supabase-go binary`, aunque `supabase --version` sí responde.

**Causa:** desde la CLI 2.10x el comando se parte en dos: el launcher `supabase`
y el motor `supabase-go` (un binario aparte). El paquete **AUR `supabase-bin`
NO instala `supabase-go`**, así que su `/usr/bin/supabase` no arranca.

**Fix:** usar la instalación de **npm**, que sí trae el motor y queda primera en el PATH:

```bash
npm i -g supabase
hash -r                 # limpia la ruta cacheada por bash (o abrí una terminal nueva)
which supabase          # debe apuntar a ~/.local/share/npm-global/bin/supabase
supabase --version
```

Opcional, para sacar el binario roto del AUR y que no confunda el PATH:

```bash
sudo pacman -R supabase-bin
```

> ⚠️ Por esto, el `yay -S supabase-bin` que sugiere §1.1 puede dejarte un
> `supabase` que no levanta. Preferí la vía npm de arriba.

### 7.1 `host.docker.internal` no resuelve dentro de los containers

**Síntoma:** `ms-game` arranca pero su healthcheck queda en `unhealthy`. Logs mencionan `Connection refused` a `host.docker.internal:54321`. O el webhook `user.registered` no llega y el provisioning queda en manos del fallback del gateway.

**Verificación:**

```bash
docker exec chessquery_api_gateway sh -c "getent hosts host.docker.internal"
```

Si imprime nada o `MISSING`, agregá `extra_hosts` al compose:

```yaml
# En infrastructure/docker-compose.yml, agregar a ms-game, ms-notifications, api-gateway:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

Luego:

```bash
cd /home/kenny/ChessQuery_FS3/infrastructure
docker compose up -d ms-game ms-notifications api-gateway
```

Alternativa: cambiar `SUPABASE_URL` en `infrastructure/.env` por la IP del puente Docker (`172.17.0.1` típicamente) en lugar de `host.docker.internal`. Pero `extra_hosts` es la forma correcta y portable.

### 7.2 RabbitMQ no muestra colas

`docker compose logs rabbitmq_setup` quedó con error o no terminó.

```bash
make clean-setup
docker compose logs -f rabbitmq_setup     # mirar hasta "Topología creada"
```

### 7.3 Un microservicio Java queda `unhealthy`

Ver logs específicos:

```bash
docker compose logs --tail=200 ms-tournament    # o el que sea
```

Causas frecuentes:

- **Flyway falló** (migración con error) → el container reintenta sin parar. Solución: `make reset` + `make up` (perdés datos) o entrar al DB y arreglar a mano (`docker exec -it chessquery_tournament_db psql -U chessquery tournament_db`).
- **No puede conectarse a RabbitMQ** → revisar que `rabbitmq` esté `healthy`. Reintentar `docker compose restart ms-tournament`.
- **No puede conectarse a su DB** → revisar healthcheck del Postgres correspondiente.

### 7.4 Login en el frontend devuelve 504/502

Síntoma reportado en `docs/README-DEMO-M1.md`: el contenedor `kong` de Supabase se queda colgado tras varios restarts.

```bash
docker restart supabase_kong_ChessQuery_FS3
```

### 7.5 `npm install` falla en el frontend

Probablemente Node viejo o npm con cache corrupto. En tu equipo tenés Node 26 → debería andar. Si falla:

```bash
cd /home/kenny/ChessQuery_FS3/frontend
rm -rf node_modules apps/*/node_modules packages/*/node_modules
npm cache clean --force
npm install
```

### 7.6 Puerto ocupado en `make up`

```
Error response from daemon: driver failed programming external connectivity on endpoint
chessquery_xxx: failed to bind port 0.0.0.0:5433/tcp ...
```

Algo en tu host usa ese puerto. Identificalo:

```bash
sudo ss -tlnp | grep :5433
```

Cerrá ese proceso o pará el container que lo usa.

### 7.7 Vite no levanta o tira "EADDRINUSE 5173"

Algún `npm run dev:portal` previo quedó zombie:

```bash
pkill -f "vite.*chess-portal"
pkill -f "vite.*organizer-panel"
```

Después relanzá las terminales.

### 7.8 Webhook no dispara `user.registered`

Si creás un usuario en Studio y `player` no aparece en `user_db`, el flujo asíncrono falló — pero el fallback debería cubrirlo apenas el usuario haga login y haga su primer request al gateway.

Para forzar el provisioning manualmente desde tu equipo:

```bash
# Asumiendo que tenés el access_token tras login en el frontend, pegalo abajo:
TOKEN="<access_token aquí>"
curl -s -X POST http://localhost:8080/api/player/me/dashboard \
  -H "Authorization: Bearer $TOKEN" | jq
```

Esto fuerza al gateway a resolver el UUID → `player.id` y, si no existe, llama a `POST /users/provision`.

### 7.9 RAM al límite

`docker stats` te muestra el uso vivo. Si te quedás corto:

```bash
# Apagar lo no esencial de Supabase
docker stop supabase_analytics_ChessQuery_FS3 \
            supabase_studio_ChessQuery_FS3 \
            supabase_pg_meta_ChessQuery_FS3 \
            supabase_edge_runtime_ChessQuery_FS3 \
            supabase_vector_ChessQuery_FS3

# Apagar microservicios fuera de scope
docker compose stop ms-analytics ms-etl bff-admin analytics_db etl_db
```

Recuperás ~1.5 GB. **Tu equipo tiene 15 GB**, así que esto raramente hace falta.

### 7.10 Cómo ver los eventos circulando por RabbitMQ

```bash
# Lista de mensajes en cada cola (rate por segundo)
curl -s -u chessquery:chessquery_dev http://localhost:15672/api/queues | jq '.[] | {name, messages, message_stats}'
```

O Management UI → Queues → click en la cola → "Get messages" (consume y muestra los pendientes).

---

## 8. Comandos memorizables

```bash
# === Levantar ===
docker-on                                   # daemon (alias propio, sin systemctl enable)
supabase start                              # auth + storage + realtime
cd infrastructure && make up                # stack ChessQuery
cd ../frontend && npm run dev:portal &      # SPA jugador
cd ../frontend && npm run dev:organizer &   # SPA organizador
bash scripts/preflight.sh                   # check 5 min antes

# === Operar ===
docker-status                                # estado del daemon
docker compose ps                            # estado contenedores
docker compose logs -f ms-game               # logs de un servicio
docker stats                                 # uso CPU/RAM en vivo
make demo-reset                              # limpiar datos entre dry-runs

# === Apagar (orden correcto) ===
pkill -f vite                                # frontends
make down                                    # stack ChessQuery (desde infrastructure/)
supabase stop                                # supabase
docker-off                                   # daemon (libera RAM)

# === Reset total (cuidado) ===
make reset                                   # borra volumes ChessQuery
supabase stop --no-backup                    # borra datos supabase
```

---

## 9. Checklist final pre-demo (5 minutos antes)

- `docker-status` → `active (running)`
- `supabase status` imprime URLs sin error
- `docker compose ps` → todos `(healthy)` o `Up`
- `curl -s http://localhost:8080/actuator/health` → `{"status":"UP"}`
- `curl -s http://localhost:8083/actuator/health` → `{"status":"UP"}` (incluye health de Storage)
- `http://localhost:5173` carga el portal del jugador
- `http://localhost:5174` carga el panel del organizador
- Login con `carla@demo.cl` / `demo1234` funciona en organizer-panel
- Login con `ana@demo.cl` / `demo1234` funciona en chess-portal
- `http://localhost:15672` muestra exchange `ChessEvents` + colas
- `http://localhost:54324` (Mailpit) carga
- `bash scripts/preflight.sh` → `Todo OK` o solo warnings
- (Opcional) Warm-up: una partida live completa de prueba entre Ana y Bruno

> Si todo está verde, salí tranquilo. La demo está lista.

---

## 10. Apéndice: por qué la primera vez tarda más


| Acción            | Primera vez                          | Veces siguientes      |
| ----------------- | ------------------------------------ | --------------------- |
| `supabase start`  | 2–4 min (descarga 2 GB)              | 15–30 s               |
| `make up`         | 3–6 min (build de 9 imágenes Docker) | 20–40 s (usa cache)   |
| `npm install`     | 1–2 min (descarga 400+ paquetes)     | <5 s (verifica cache) |
| Healthchecks Java | 60–90 s por servicio                 | 30–60 s por servicio  |


El total **la primera vez** ronda los **8–12 minutos**. Las siguientes veces es **<2 minutos** end-to-end.

---

## 11. Mantener el sistema sano

- Cada cuanto entre demos: `make demo-reset` para empezar limpio.
- Si Docker acumula imágenes viejas: `docker image prune -f` (libera espacio sin tocar containers).
- Si tras semanas algo anda raro: `docker system prune --volumes -f` (¡cuidado! borra todo lo no en uso, incluidos volumes huérfanos).
- Las imágenes de Supabase se actualizan con `supabase stop && supabase start` periódicamente.

---

## 12. Correr Claude Code en este proyecto (MCP de Supabase local)

Para que Claude Code (el asistente de la terminal) pueda **leer tu Supabase local directamente** —listar tablas, ver filas, correr queries de lectura— hay que abrirlo **parado dentro de la carpeta del proyecto** y tener Supabase corriendo.

### 12.1 Abrir Claude Code en el proyecto

```bash
cd /home/kenny/ChessQuery_FS3
claude
```

> El MCP está registrado con **alcance local para esta carpeta**. Si abrís Claude Code desde `~` u otro directorio, el servidor `supabase-local` NO se carga. Siempre entrá desde la raíz del repo.

### 12.2 El MCP `supabase-local`

Ya está registrado (apunta a `http://127.0.0.1:54321/mcp`). Para verlo o re-crearlo:

```bash
claude mcp list                  # ver estado de todos los MCP
claude mcp get supabase-local    # ver detalle de este

# Si hay que volver a agregarlo (NO lleva sudo):
claude mcp add --transport http supabase-local http://127.0.0.1:54321/mcp
```

Requisitos para que conecte (debe figurar en verde al correr `/mcp` dentro de Claude Code):

1. Docker prendido (`docker-on`).
2. Supabase corriendo (`supabase start`) — el endpoint MCP vive dentro del stack de Supabase.

### 12.3 No confundir con el MCP de la nube

También vas a ver un `plugin:supabase:supabase → https://mcp.supabase.com/mcp` que pide autenticación. **Ese es el de Supabase Cloud** (necesitaría cuenta/token, p. ej. el de tu colega). Para tu trabajo local **ignoralo** y usá `supabase-local`.

### 12.4 Si `supabase-local` no conecta

El endpoint `/mcp` es una feature nueva de la CLI y en versiones viejas (2.98.2) puede fallar el health check. Solución:

```bash
yay -S supabase-bin              # actualizar la CLI (AUR, sin sudo)
supabase stop && supabase start  # reiniciar el stack
```

Volvé a entrar a Claude Code desde la carpeta y corré `/mcp`. Recordá: **el MCP es opcional** — la demo funciona perfecto sin él; es solo una comodidad para que el asistente consulte la BD.

---

## 13. Documentos asociados

- `PRESENTACION.md` — Guía narrativa del proyecto (lo que vas a contar al profesor).
- `CONTEXT.md` — ERD + contratos REST + eventos RabbitMQ.
- `PLAN_DEMO.md` — Guion oficial de la demo (4 escenas + endpoints).
- `docs/SUPABASE_SETUP.md` — Setup Supabase original (Mac/Linux).
- `docs/LIVE_GAME_DEMO.md` — Flujo detallado de partida en vivo.
- `docs/DEMO_FLOWS_VERIFICATION.md` — Checklist de validación E2E.

