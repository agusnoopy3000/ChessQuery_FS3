# ChessQuery — Setup local en Windows

Guía para levantar el stack completo de ChessQuery en una máquina Windows 10/11.

## Prerequisitos

Instala lo siguiente (una vez por máquina):

| Herramienta | Versión | Instalación |
|---|---|---|
| **Docker Desktop** | última | https://www.docker.com/products/docker-desktop — durante la instalación deja marcado "Use WSL 2" |
| **WSL 2** | — | Docker Desktop lo instala/configura. Si te pide reiniciar, hazlo |
| **Supabase CLI** | 1.150+ | `scoop install supabase` o `npm install -g supabase` |
| **Node.js** | 20 LTS+ | https://nodejs.org (instalar la "LTS") |
| **Git** | — | https://git-scm.com/download/win |
| **PowerShell** | 5.1+ (preinstalado) o **PowerShell 7** (`winget install Microsoft.PowerShell`) | — |

> No necesitas instalar Java ni Maven — todo eso corre dentro de los contenedores Docker.

## Setup en 1 comando

```powershell
# Desde la raíz del repo (donde está este README)
git clone <repo-url>
cd ChessQuery_FS3
pwsh -ExecutionPolicy Bypass -File scripts/setup.ps1
```

El script:

1. Verifica que tienes Docker, Supabase CLI, Node
2. Crea `infrastructure/.env` si no existe (copia de `.env.example`)
3. Levanta Supabase local (`supabase start`)
4. Buildea y levanta los 8 microservicios + 5 bases de datos + RabbitMQ + Redis
5. Instala dependencias del frontend (`npm install`)
6. Imprime las URLs a abrir

La primera vez tarda **5–10 minutos** (descarga imágenes Docker + Supabase + build Maven). Las siguientes, **~60 segundos**.

## Arrancar los frontends

Abre **dos terminales separadas** (PowerShell o Git Bash):

```powershell
# Terminal 1 — Portal del jugador
cd frontend
npm run dev:portal      # http://localhost:5173

# Terminal 2 — Panel del organizador
cd frontend
npm run dev:organizer   # http://localhost:5174
```

## URLs útiles

| Servicio | URL | Notas |
|---|---|---|
| Portal jugador | http://localhost:5173 | Login → jugar → torneos |
| Panel organizador | http://localhost:5174 | Crear torneos, validar inscripciones |
| Supabase Studio | http://localhost:54323 | Ver BD, usuarios auth, storage |
| Inbucket (mailpit) | http://localhost:54324 | Lee los emails de magic link |
| API Gateway | http://localhost:8080 | Endpoint que el frontend consume |
| RabbitMQ UI | http://localhost:15672 | user/pass: `chessquery` / `chessquery_dev` |

## Comandos del día a día

```powershell
# Ver estado de contenedores
docker compose -f infrastructure/docker-compose.yml ps

# Ver logs en vivo (Ctrl+C para salir)
docker compose -f infrastructure/docker-compose.yml logs -f ms-game

# Detener todo (conserva datos en volúmenes)
docker compose -f infrastructure/docker-compose.yml down
supabase stop

# Re-levantar (rápido)
docker compose -f infrastructure/docker-compose.yml up -d --wait
supabase start

# Borrar TODO incluyendo volúmenes (cuidado: se pierden BD y partidas)
docker compose -f infrastructure/docker-compose.yml down -v
supabase stop --no-backup
```

## Troubleshooting

### "Docker Desktop no está corriendo"
Abre Docker Desktop desde el menú Inicio. Espera que el ícono diga "Engine running". Luego vuelve a correr el setup.

### Login devuelve 504 después de `docker compose down`
Conocido — la red de Supabase queda desincronizada. Fix:
```powershell
docker restart supabase_kong_ChessQuery_FS3
```

### `ms-etl` aparece "unhealthy" pero todo lo demás funciona
Bug cosmético conocido: el healthcheck del contenedor usa `curl` que no está instalado en la imagen `python:3.11-slim`. El servicio funciona igual. Ignora.

### Puerto 5432 / 5433 / etc. ya en uso
Tienes otro Postgres corriendo. Detén tu servicio local de Postgres o ajusta los puertos en `infrastructure/docker-compose.yml`.

### "WSL 2 installation is incomplete"
Ejecuta en PowerShell con admin:
```powershell
wsl --install
wsl --set-default-version 2
```
Reinicia. Reabre Docker Desktop.

### El build de Maven se queda colgado
Suele ser por descarga lenta. Espera. Si tras 10 min sigue parado:
```powershell
docker compose -f infrastructure/docker-compose.yml build --no-cache ms-game
```

### Cómo reseteo todo y empiezo de cero
```powershell
docker compose -f infrastructure/docker-compose.yml down -v
supabase stop --no-backup
Remove-Item infrastructure/.env -Force
pwsh -ExecutionPolicy Bypass -File scripts/setup.ps1
```

## Cuentas de prueba (pobladas tras el primer login/signup)

| Rol | Email | Password | Notas |
|---|---|---|---|
| Organizador | `carla@demo.cl` | (la que registre quien lo cree) | Acceso a panel organizador (5174) |
| Jugador | `bruno@demo.cl` | — | Crea/juega live games |
| Jugador | `ana@demo.cl` | — | Otro jugador |

Para crearlos, regístrate desde la UI con esos emails. Los **passwords** los eliges al registrar.

## Estructura del repo (referencia rápida)

```
ChessQuery_FS3/
├── infrastructure/        # docker-compose, Nginx, Makefile, .env
├── ms-auth/               # legacy, no se usa (Supabase reemplaza)
├── ms-users/              # 8081 — players, profiles
├── ms-tournament/         # 8082 — torneos, inscripciones, pairings
├── ms-game/               # 8083 — live games, ELO, PGN
├── ms-etl/                # 8086 — ratings sync (Python)
├── ms-notifications/      # 8085 — push in-app + email
├── api-gateway/           # 8080 — Spring Cloud Gateway, JWT Supabase
├── bff-player/            # 3001 — BFF para chess-portal
├── bff-organizer/         # 3002 — BFF para organizer-panel
├── frontend/
│   ├── apps/chess-portal/      # 5173 — SPA jugador
│   └── apps/organizer-panel/   # 5174 — SPA organizador
└── supabase/              # config CLI, migraciones, templates email
```
