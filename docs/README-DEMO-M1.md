# ChessQuery — Demo en MacBook Air M1 (8 GB)

Guía específica para correr la demo en una M1 con 8 GB.
**Para desarrollo en máquinas con 16+ GB, ignora este archivo y usa `make up`.**

## Por qué este flujo es distinto

El stack completo (todos los microservicios + Supabase + frontend + Docker Desktop overhead) consume **~6 GB** bajo demo activa. En una M1 de 8 GB, eso deja apenas 2 GB para macOS + Chrome + lo que tengas abierto → riesgo de swap y lag visible.

Este flujo:

- Apaga servicios fuera de scope (`ms-etl`, `bff-admin`)
- Reduce heap JVM de cada microservicio a 256 MB (default ~1 GB)
- Mantiene apagados containers no esenciales de Supabase (`analytics`, `edge_runtime`, `vector`) pero deja **Studio** disponible para mostrar usuarios autenticados y PGN en Storage
- Resultado: **~3 GB** en idle, **~4 GB** bajo demo activa → margen cómodo

## Antes del día de demo

### 1. La noche anterior — pre-build

```bash
cd infrastructure
make demo-prebuild
```

Buildea las 7 imágenes de scope demo con cache. Tarda 5–10 min la primera vez, **segundos** después. Hacerlo la noche antes te ahorra esa ventana de espera el día D.

### 2. Cierra todo lo que no necesites

- Chrome: cerrar tabs pesados (Gmail, YouTube)
- Slack, Spotify, Discord, OBS, Zoom (si no lo usas para grabar)
- Cualquier IDE abierto (VS Code, IntelliJ) — ¡incluyendo el que tiene este repo abierto! Edita con `vim` o cierra el indexer
- Time Machine pausada si está corriendo backup

## El día de la demo

### 1. Levantar Supabase

```bash
cd /ruta/al/repo
supabase start
```

Tras eso, **apagar los containers de Supabase que no se usan** (saca RAM sin perder Studio):

```bash
cd infrastructure
make demo-supabase-trim
```

Studio queda encendido para poder mostrar:
- `Authentication > Users`
- `Storage > chessquery-pgn`

### 2. Levantar stack ChessQuery (modo demo)

```bash
cd infrastructure
make demo-m1-up
```

Este target usa `docker-compose.demo.yml` que aplica:
- `JAVA_TOOL_OPTIONS=-Xms128m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`
- Mantiene `ms-analytics` y `analytics_db` activos para que el perfil del jugador siga mostrando métricas reales
- Solo deja fuera `ms-etl` y `bff-admin`
- Recorta automáticamente los contenedores pesados de Supabase que no entran en escena

Sale cuando todos los healthchecks pasen (~30–60s).

### 3. Levantar frontends

En dos terminales separadas:

```bash
# Terminal 1
cd frontend && npm run dev:portal       # http://localhost:5173

# Terminal 2
cd frontend && npm run dev:organizer    # http://localhost:5174
```

### 4. Preflight (5 min antes)

```bash
make preflight
# o
bash scripts/preflight.sh
```

Debe terminar con `Todo OK` o solo warnings menores. Si dice `FAIL`, no salgas a escenario sin investigar.

### 5. Warm-up (1 min antes)

Hacer **un live game completo de práctica** entre dos navegadores (modo incógnito en uno) ayuda a:

- Calentar la JVM (primer request siempre es lento)
- Verificar que el upload de PGN a Supabase Storage no tarde >2s
- Confirmar que la notif in-app llega en <8s

## Durante la demo

### Monitor de logs en una pestaña aparte

```bash
docker compose -f infrastructure/docker-compose.yml \
               -f infrastructure/docker-compose.demo.yml \
               logs -f ms-game ms-notifications api-gateway \
  | grep --line-buffered -E "ERROR|Exception|game\.|invitation"
```

Si algo se cae, lo ves antes de que el público lo note.

### Fixes rápidos en vivo

| Síntoma | Fix |
|---|---|
| Login devuelve 504/502 | `docker restart supabase_kong_ChessQuery_FS3` |
| Microservicio Java tarda mucho en responder | `docker compose restart ms-XXX` (10s) |
| Frontend muestra "Network error" | Verificar que el gateway en :8080 esté up |
| `ms-etl unhealthy` en `ps` | Ignorar — bug cosmético del healthcheck, no afecta |

## Después de la demo / entre dry-runs

### Reset de datos preservando seeds

```bash
make demo-reset
```

Borra: partidas, torneos, registros de notif, usuarios provisionados.
Conserva: aperturas ECO, clubes, países seed.

### Apagar todo

```bash
make demo-down              # detiene containers (preserva datos)
supabase stop               # detiene Supabase
```

### Apagar todo y empezar de cero mañana

```bash
make demo-down
supabase stop --no-backup
docker volume prune -f      # ¡cuidado! borra TODOS los volumes de Docker
```

## Cheatsheet de comandos memorizables

```bash
make demo-m1-up      # levantar demo en M1
make demo-up         # levantar solo el scope app si Supabase ya está recortado
make demo-supabase-trim  # apagar Supabase no esencial
make demo-down       # apagar
make preflight       # check 5min antes
make demo-reset      # limpiar datos entre dry-runs
docker stats         # ver memoria en vivo (Ctrl+C)
```

## Si pasa algo inesperado

1. Mantén la calma — 30 segundos de silencio es mejor que pánico
2. Cambia a otro flujo de la demo (si F1 falla, salta a F2 mientras se recupera)
3. Plan B (video grabado) en cualquier momento que sea necesario
4. Para fallos persistentes:
   ```bash
   make demo-down && make demo-up
   ```
   2 min de espera, pero recupera estado limpio.
