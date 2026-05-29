# Guía de Pruebas — ChessQuery

Este documento explica **qué pruebas existen**, **cómo correrlas**, y **qué cobertura se espera** por cada servicio. Pensado para que cualquier miembro del equipo (o el profesor) pueda validar el proyecto tras un `git clone` limpio.

> 📖 Para el **detalle de diseño** de las pruebas (qué cubre cada clase, el patrón
> `@SpringBootTest` + H2, exclusiones de cobertura y pasos futuros) ver
> [`docs/PRUEBAS.md`](./docs/PRUEBAS.md). Este documento es el operativo (cómo correrlas).

---

## 0. Pre-requisitos (una sola vez)

> **Importante:** las pruebas **unitarias y de integración NO necesitan Docker ni Supabase.**
> Los tests de integración usan **H2 en memoria** (perfil `test`), así que corren con el daemon
> de Docker apagado. Docker solo hace falta para la verificación **E2E manual** del §4.

Verificá lo que ya tenés:

```bash
# JDK 17 (obligatorio — los pom.xml exigen Java 17)
java -version    # debe mostrar 17.x

# Maven 3.9+ (necesario para correr los tests Java en tu host)
mvn -version

# Node 20+ y npm 10+ (para BFFs y frontend)
node -v
npm -v

# Docker + Docker Compose v2 — SOLO para la E2E manual del §4
docker --version
docker compose version
```

### Instalar lo que falte, por sistema operativo

> No hay wrapper `mvnw` en los módulos, así que **Maven debe estar instalado en tu host**.

**Arch Linux:**
```bash
sudo pacman -S maven jdk17-openjdk      # Maven usa el JDK por defecto del sistema
archlinux-java status                   # confirmá que el default sea java-17-openjdk
# si no lo es:  sudo archlinux-java set java-17-openjdk
```

**Ubuntu / Debian:**
```bash
sudo apt update && sudo apt install -y maven openjdk-17-jdk
```

**Fedora:**
```bash
sudo dnf install -y maven java-17-openjdk-devel
```

**macOS (Homebrew):**
```bash
brew install maven openjdk@17
```

> 💡 **Maven debe usar el JDK 17.** Confirmá con `mvn -version` que la línea `Java version:`
> diga 17.x. Si tomara otra (JDK 21/25 en Fedora, o varios JDK conviviendo en Arch), forzalo
> exportando `JAVA_HOME` (dejalo en tu `~/.bashrc` para que persista):
> ```bash
> # Arch:    /usr/lib/jvm/java-17-openjdk
> # Ubuntu:  /usr/lib/jvm/java-17-openjdk-amd64
> # Fedora:  /usr/lib/jvm/java-17-openjdk
> export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
> export PATH=$JAVA_HOME/bin:$PATH
> ```

> ℹ️ **Node muy nuevo (22/24/26):** la suite anda igual, pero el CI usa Node 20. Si el frontend o
> los BFFs tiran errores raros de ESM/Vitest, alineá con `nvm install 20 && nvm use 20`.

### Setup inicial tras `git clone`

```bash
git clone <repo-url> chessquery && cd chessquery

# 1) Variables de entorno (NO commitear nunca el .env real)
cp infrastructure/.env.example infrastructure/.env
# Editar infrastructure/.env y completar SUPABASE_* con valores reales

# 2) Dependencias Node (todos los workspaces)
cd frontend && npm install && cd ..
cd bff-player && npm install && cd ..
cd bff-organizer && npm install && cd ..
```

Los microservicios Java descargan dependencias automáticamente la primera vez que corras `mvn`.

---

## 1. Panorama de pruebas

| Servicio | Framework | Tipos de prueba | Total tests | Cobertura objetivo |
|---|---|---|---:|---:|
| `api-gateway` | JUnit 5 + Mockito + WebTestClient | Unit + Filter slice | 44 | 80% |
| `ms-users` | JUnit 5 + Mockito + `@SpringBootTest` | Unit + Integration H2 | 110 | 85% |
| `ms-tournament` | JUnit 5 + Mockito + `@SpringBootTest` | Unit + Integration H2 | 94 | 85% |
| `ms-game` | JUnit 5 + Mockito + `@SpringBootTest` | Unit + Integration H2 | 91 | 85% |
| `ms-notifications` | JUnit 5 + Mockito + `@SpringBootTest` | Unit + Integration H2 | 65 | 85% |
| `ms-analytics` | JUnit 5 + Mockito + `@SpringBootTest` | Unit + Integration H2 | 14 | 75% |
| `bff-player` | Jest (NestJS) | Unit service + http | 47 | 96% líneas |
| `bff-organizer` | Jest (NestJS) | Unit service + http | 28 | 94% líneas |
| `chess-portal` (frontend) | Vitest + RTL + jsdom | Page specs | 37 | 76% líneas |
| `organizer-panel` (frontend) | Vitest + RTL + jsdom | Page specs | 38 | 75% líneas |
| **Total** | — | — | **568** | — |

**Patrones aplicados:**
- **Unit**: servicios + controllers con dependencias mockeadas (Mockito / Jest mocks / `vi.mock`).
- **Integration backend**: `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")` con H2 in-memory en modo PostgreSQL; `RabbitMQConfig` excluido con `@Profile("!test")`.
- **Frontend**: React Testing Library + mocks de `@tanstack/react-query`, `@chessquery/ui-lib` y `../api`.
- **Cobertura**: JaCoCo (Java) y v8 (Vite) con exclusiones de `config/**`, `dto/**`, `entity/**`, `exception/**`.

---

## 2. Comandos por servicio

### 2.1 Microservicios Java (Spring Boot)

Mismo patrón para los 6: `api-gateway`, `ms-users`, `ms-tournament`, `ms-game`, `ms-notifications`, `ms-analytics`.

```bash
cd ms-users          # o cualquiera de los 6

# Solo unit + integration (rápido)
mvn -B clean test

# Con reporte JaCoCo (HTML)
mvn -B clean verify
open target/site/jacoco/index.html       # macOS
xdg-open target/site/jacoco/index.html   # Linux

# Un solo test
mvn -B test -Dtest=PlayerIntegrationTest

# Un solo método
mvn -B test -Dtest=PlayerIntegrationTest#shouldCreatePlayer
```

> 💡 Usa **siempre** `clean` la primera vez. Reportes obsoletos en `target/surefire-reports/` pueden confundir conteos cuando renombras o eliminas tests `@Nested`.

#### Correr los 6 módulos en cadena

Desde la raíz del proyecto:

```bash
for mod in api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics; do
  echo "==================== $mod ===================="
  (cd $mod && mvn -B clean test) || { echo "FALLO en $mod"; break; }
done
```

#### Resultado esperado

Cada módulo debe terminar con `BUILD SUCCESS` y la línea:

```
[INFO] Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

---

### 2.2 BFFs (NestJS + Jest)

```bash
cd bff-player        # o bff-organizer

# Suite completa
npm test

# Modo watch (desarrollo)
npm run test:watch

# Con cobertura
npm test -- --coverage
```

Esperado: `Tests: 47 passed` (bff-player) y `Tests: 28 passed` (bff-organizer).

---

### 2.3 Frontends (Vite + Vitest + RTL)

```bash
cd frontend

# Instalar workspace una sola vez
npm install

# Suite completa de TODAS las apps
npm test --workspaces --if-present
```

O por app individual:

```bash
cd frontend/apps/chess-portal && npm test          # 37 tests, cobertura v8
cd frontend/apps/organizer-panel && npm test       # 38 tests, cobertura v8

# Watch mode
npx vitest

# Una página específica
npx vitest src/pages/Login.spec.tsx
```

Reporte HTML de cobertura:

```bash
cat coverage/index.html   # se abre en navegador
```

---

## 3. Suite completa de un tirón (smoke "todo verde")

Script recomendado antes de cada PR/push:

```bash
#!/usr/bin/env bash
set -e
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export PATH=$JAVA_HOME/bin:$PATH

# 1. Backend Java
for mod in api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics; do
  (cd $mod && mvn -B clean test)
done

# 2. BFFs
(cd bff-player && npm test)
(cd bff-organizer && npm test)

# 3. Frontend
(cd frontend/apps/chess-portal && npm test)
(cd frontend/apps/organizer-panel && npm test)

echo "✅ 568 tests pasaron"
```

Este script **ya está en el repo** como `scripts/test-all.sh`. Corré toda la suite con:

```bash
bash scripts/test-all.sh
```

> ⚠️ Asume que ya hiciste `npm install` en `frontend/`, `bff-player/` y `bff-organizer/` (ver §0).
> La **primera** corrida de Maven baja dependencias (~5-10 min); las siguientes, ~2 min.
> Última corrida verificada: **568 tests, 0 fallos, 0 errores**.

---

## 4. Verificación E2E manual con Docker Compose

Para validar **integración real** (RabbitMQ + Postgres + 6 MS + 2 BFFs + gateway):

```bash
cd infrastructure

# Levantar stack completa
docker compose up -d --build

# Esperar healthchecks (~60s)
docker compose ps

# Smoke checks
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8081/actuator/health   # ms-users
curl http://localhost:8082/actuator/health   # ms-tournament
curl http://localhost:8083/actuator/health   # ms-game
curl http://localhost:8085/actuator/health   # ms-notifications
curl http://localhost:3001/health            # bff-player
curl http://localhost:3002/health            # bff-organizer

# Logs de un servicio
docker compose logs -f ms-users

# Bajar todo
docker compose down
```

**Esperado:** todos devuelven `{"status":"UP"}`. Si `ms-users` muestra `"rabbit":{"status":"UP"}` y `"db":{"status":"UP"}` el wiring de RabbitMQ + Postgres está OK.

---

## 5. CI/CD — GitHub Actions

El workflow `.github/workflows/ci.yml` corre **automáticamente** en cada `push` / `pull_request`:

- **Job `java-tests`** — matriz 6 módulos × `mvn clean test` + sube artefactos `target/site/jacoco/`.
- **Job `frontend-tests`** — matriz 2 apps × `npm test` + sube `coverage/`.
- **Job `bff-tests`** — matriz 2 BFFs × `npm test`.

Verificar localmente antes de pushear:

```bash
gh workflow view ci.yml      # ver definición
gh run list --workflow=ci    # ver últimas corridas
```

---

## 6. Troubleshooting

| Síntoma | Causa | Fix |
|---|---|---|
| `mvn test` falla con `UnsupportedClassVersionError` | JDK ≠ 17 | `export JAVA_HOME=/ruta/jdk-17` |
| `Tests run: N, Errors: M` con errores fantasma | Reportes XML obsoletos | `mvn clean test` (no solo `test`) |
| `npm test` cuelga en Vitest | Cache de Vite corrupto | `rm -rf node_modules/.vite && npm test` |
| Compose `unhealthy: ms-users` | `infrastructure/.env` sin Supabase | Completar `SUPABASE_URL` y keys |
| `host.docker.internal` no resuelve | Linux antiguo | Ya está manejado: `extra_hosts` en compose |
| BFF connection refused en 3100/3200 | Puertos correctos son 3001/3002 | Ver tabla §4 |
| Frontend `npm test` ESM error | Node < 20 | `nvm install 20 && nvm use 20` |
| `Could not find or load main class` | Falta `mvn package` | Build previo: `mvn -DskipTests package` |

---

## 7. Cobertura — qué se mide y dónde se ve

### Java (JaCoCo)
Ubicación: `<modulo>/target/site/jacoco/index.html`
Exclusiones globales: `config/**`, `dto/**`, `entity/**`, `exception/**` (no aportan a métricas).

Umbrales sugeridos por módulo (no bloquean build aún):
- `ms-users` / `ms-tournament` / `ms-game` / `ms-notifications`: **≥85%** líneas
- `api-gateway`: **≥80%** líneas
- `ms-analytics`: **≥75%** líneas

### Frontend (v8 vía Vitest)
Ubicación: `frontend/apps/<app>/coverage/index.html`
Esperado:
- `chess-portal`: 76% líneas / 72% statements
- `organizer-panel`: 75% líneas (`OrganizerTournaments.tsx` cubierto al 84%)

---

## 8. Checklist antes de `git push`

- [ ] `for m in api-gateway ms-{users,tournament,game,notifications,analytics}; do (cd $m && mvn -B clean test); done` → 6× BUILD SUCCESS
- [ ] `(cd bff-player && npm test) && (cd bff-organizer && npm test)` → 75 tests OK
- [ ] `(cd frontend/apps/chess-portal && npm test) && (cd frontend/apps/organizer-panel && npm test)` → 75 tests OK
- [ ] `docker compose -f infrastructure/docker-compose.yml up -d --build` → 13 contenedores healthy
- [ ] `curl http://localhost:8080/actuator/health` devuelve UP
- [ ] `git status` no muestra `node_modules`, `target/`, `coverage/`, `.env`
