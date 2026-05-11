# Plan de seguridad — ChessQuery

> Auditoría del estado actual + plan priorizado para endurecer Supabase, el API Gateway y los microservicios.
> Fecha: 2026-05-09 · Branch: `main` · Último commit: `085e0bf`.

---

## 1. Estado actual (lo que ya existe)

### 1.1 Autenticación
- **JWT de Supabase Auth** — validación local en el gateway (`SupabaseJwtAuthFilter`). Soporta ES256 vía JWKS (asimétrico, recomendado por Supabase v2) con fallback a HS256.
- **JWKS cache** con refresh en background (4 min) — evita bloquear el thread reactor del request.
- **Auto-provisión** de `Player` cuando llega un JWT válido pero el usuario no existe en `user_db` (cubre webhooks caídos).
- **Resolución de identidad** UUID Supabase → `player.id` numérico cacheada con Caffeine (TTL 5 min, max 10k).
- **Headers propagados** downstream: `X-User-Id`, `X-Supabase-User-Id`, `X-User-Email`, `X-User-Role`.

### 1.2 Autorización
- **Role checks** en `ms-tournament`: `ORGANIZER`/`ADMIN` requeridos para crear torneo, transicionar estado, generar rondas, aprobar/rechazar inscripciones.
- **PUBLIC_PATHS** en el gateway: `/auth/*`, `/actuator`, `/webhooks/`. Todo lo demás requiere JWT válido.

### 1.3 Rate limiting
- **Redis-rate-limiter** en el gateway: 100 rpm con burst de 120 por IP, replenishRate 100/min, requestedTokens 1.

### 1.4 Webhooks
- `POST /webhooks/supabase/user-registered` protegido con header `X-Supabase-Webhook-Secret` validado por `equals` contra `${SUPABASE_WEBHOOK_SECRET}`.

### 1.5 Supabase
- **RLS habilitado** en:
  - `public.user_profiles` (lectura propia + service role full).
  - `storage.objects` para el bucket `chessquery-pgn` (upload por authenticated, full por service role).
- **Buckets privados** + signed URLs para PGN (TTL 1 h).

### 1.6 Resiliencia (no es seguridad pura, pero relevante)
- Connection pool con eviction agresivo en gateway hacia ms-users (`evictInBackground 5s`).
- Retry transparente en errores transitorios (`ConnectException`, `PrematureClose`, `SocketException`).
- Circuit breaker (Resilience4j) hacia ms-users desde ms-tournament.

---

## 2. Vectores de ataque + brechas detectadas

### 🔴 Críticas (rompen el modelo de seguridad)

| # | Brecha | Impacto |
|---|---|---|
| **C1** | **Microservicios expuestos directamente en el host**: `docker-compose.yml` mapea `8081-8086` y `5432-5438` a `0.0.0.0`. Cualquiera en la LAN puede saltarse el gateway y golpear `http://host:8081/users/...` con headers fabricados. | Bypass total de auth: un atacante manda `X-User-Id: 1` y `X-User-Role: ADMIN` y los MS confían. |
| **C2** | **MS confían en headers `X-User-*` sin validación**. No hay shared secret ni mTLS entre gateway y MS. | Si C1 está abierto, el atacante actúa como cualquier usuario/rol. |
| **C3** | **Defaults de contraseñas en `.env.example`** y posiblemente en `.env` real: `chessquery_dev`, `dev-webhook-secret`. Si alguien hace deploy con esos defaults a un host accesible, compromiso inmediato. | Acceso a Postgres, RabbitMQ, Redis, falsificación de webhooks. |
| **C4** | **`SUPABASE_SERVICE_KEY` en `application.yml`/`docker-compose.yml`** del lado del backend. Si se filtra (commit accidental, log dump) → bypass total de RLS, capacidad admin sobre Supabase Auth. | Lectura/escritura ilimitada en Supabase. |

### 🟡 Importantes (degradan la postura sin romperla del todo)

| # | Brecha | Impacto |
|---|---|---|
| **I1** | **Rate limit solo por IP**, no por user.id. Un usuario logueado puede hacer flood desde la misma IP. | DoS de aplicación; puntuaciones falsas en partidas. |
| **I2** | **CORS permisivo**: `localhost:*` + `127.0.0.1:*` con `credentials: true`. En prod debe ser allowlist exacta. | CSRF si se sube a prod sin endurecer. |
| **I3** | **`webhook-secret` validado con `equals`** en lugar de `MessageDigest.isEqual` o equivalente constant-time. | Timing attack teórico; bajo riesgo real porque el secret es largo. |
| **I4** | **No hay headers de seguridad** en respuestas (HSTS, CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy). | Vulnerable a clickjacking, MIME-sniffing, mixed-content. |
| **I5** | **Errors revelan códigos internos** (`PLAYER_NOT_FOUND`, `INVALID_TRANSITION`). OK para dev, problemático en prod si revelan estructura. | Reconnaissance facilitado. |
| **I6** | **No hay auditoría** de intentos de login fallidos, JWT expirados, role-checks denegados. | Imposible detectar brute-force o reconocimiento. |
| **I7** | **Refresh tokens de Supabase no rotan** explícitamente — se delega al cliente Supabase. | Robo de refresh token = acceso prolongado. |
| **I8** | **Imágenes Docker corren como root** (`eclipse-temurin:17-jdk-alpine` por default). | Escalada lateral si un MS es comprometido. |
| **I9** | **Presigned URL de PGN dura 1 h**. Si se comparte por error, ventana grande. | Filtración de partidas privadas. |
| **I10** | **No hay 2FA / MFA** disponible en el flujo de login (Supabase soporta TOTP). | Riesgo de phishing/credential stuffing. |

### 🟢 Bajas / pulido

| # | Brecha | Impacto |
|---|---|---|
| **B1** | Sin tests de seguridad automatizados (OWASP ZAP, Snyk, dependabot). | Vulnerabilidades en deps no detectadas. |
| **B2** | Sin política de rotación de secrets. | Secrets eternos = riesgo creciente. |
| **B3** | Sin política de retención/borrado de datos PII. | Cumplimiento Ley 21.719 (Chile). |
| **B4** | Logs no estructurados ni centralizados (no Loki/ELK). | Imposible correlacionar incidentes. |

---

## 3. Plan priorizado

### P0 — bloquear los caminos críticos (1-2 días)

**P0.1 — Cerrar puertos directos de MS y DBs en `docker-compose.yml`**
- Quitar el mapping `:8081-8086:8081-8086` de los servicios `ms-*`. Solo el gateway expone `:8080`.
- Quitar `:5432-5438:5432` de las DBs. Solo Studio/migraciones acceden via `docker compose exec` o un perfil `tools`.
- Mantener acceso interno por DNS docker (`http://ms-users:8081`) entre containers.

**P0.2 — Shared secret entre Gateway y MS**
- Agregar header `X-Internal-Auth: ${INTERNAL_SHARED_SECRET}` que el gateway añade en cada request al downstream.
- Cada MS valida ese header con un `OncePerRequestFilter`. Si falta o es incorrecto, devuelve 401 sin procesar.
- El secret se carga via env, **nunca** committeado. Mínimo 32 chars random.

**P0.3 — Defaults inseguros fuera del repo**
- `.env.example` con valores OBVIAMENTE inválidos (`CHANGE_ME`, `REQUIRED`).
- `application.yml` sin defaults para `supabase.jwt-secret`, `supabase.webhook-secret`, `supabase.service-key` — que falle al boot si faltan.
- Pre-commit hook con `gitleaks` o regex que bloquee commits con strings de secret patterns.

**P0.4 — Verificación de webhook con `MessageDigest.isEqual`**
- Reemplazar `webhookSecret.equals(expected)` por comparación de bytes constant-time.

### P1 — endurecer perímetro (3-5 días)

**P1.1 — RLS exhaustivo en Supabase**
- Auditar todas las tablas en `public` schema con `SELECT relname, relrowsecurity FROM pg_class JOIN pg_namespace ON ...`.
- Habilitar RLS por default en cualquier tabla nueva. Política base: deny-all si no hay policy específica.
- `REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;` + GRANTs explícitos solo donde haga falta.

**P1.2 — Custom claims en JWT (rol y player.id en el JWT)**
- Crear un Auth Hook (PostgreSQL function) que añada `role` y `player_id` al `app_metadata`.
- Gateway lee `app_metadata.role` (ya hay fallback) — más confiable que `user_metadata.role` que es editable por el usuario.
- Esto evita el roundtrip a `ms-users` para resolver player.id en cada request.

**P1.3 — Rate limit por usuario, no solo IP**
- Reemplazar `KeyResolver` del gateway de `ipKeyResolver` a uno híbrido: si hay JWT válido, usar `sub`; si no, IP.
- Agregar buckets más estrictos para endpoints sensibles (`/auth/login`, `/auth/register`, `/api/admin/*`).

**P1.4 — Headers de seguridad en respuestas**
- Spring Cloud Gateway: agregar filtro global que setee:
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Content-Security-Policy: default-src 'self'; ...` (ajustar para Vite + Supabase Realtime).

**P1.5 — CORS configurable**
- `gateway.cors.allowed-origins=${CORS_ORIGINS:http://localhost:5173,http://localhost:5174}` en yml.
- En prod: lista exacta de dominios productivos.

**P1.6 — Auditoría de eventos de auth**
- Filtro en gateway que loggee a `auth_audit` (tabla en notif_db o similar): JWT inválido, JWT expirado, role denegado, webhook secret inválido, rate-limit hit. Estructura: `{ts, event, ip, sub?, path, status}`.

### P2 — defensa en profundidad (1-2 semanas)

**P2.1 — TLS interno + HTTPS forzado**
- Nginx/Caddy en producción con cert real (Let's Encrypt) y `force_ssl_redirect`.
- mTLS opcional entre gateway y MS si la red interna no es confiable.

**P2.2 — Rotación de secrets**
- Plan trimestral: `JWT_SECRET`, `WEBHOOK_SECRET`, passwords de DB/Rabbit/Redis, `INTERNAL_SHARED_SECRET`.
- Documentar el procedimiento de rotación sin downtime (dual-secret window).

**P2.3 — Imágenes Docker non-root**
- Dockerfile: crear user `appuser`, `chown` del jar, `USER appuser` antes de `ENTRYPOINT`.
- Validar con `docker scout` o `trivy`.

**P2.4 — Dependency scanning automatizado**
- GitHub Dependabot para `pom.xml` (Maven) y `package.json` (Node).
- Run de `mvn dependency-check:check` en CI.

**P2.5 — 2FA opcional**
- Habilitar TOTP en Supabase Auth (built-in).
- Marcar cuentas ADMIN/ORGANIZER como required-MFA.

**P2.6 — Headers internos de origen**
- Gateway añade `X-Forwarded-For` real, `X-Real-IP` (configurar Nginx para que sean confiables).
- Logs de MS pueden tracear IP origen para auditoría.

### P3 — cumplimiento + observabilidad (continuo)

**P3.1 — Ley 21.719** (especificada en `SPEC_LEY21719.md`)
- Borrado por solicitud (right to delete): script que purga Player + games + registrations + notifs.
- Exportación de datos personales (right to portability).
- Privacy policy + términos visibles en Register.tsx (ya parcial).

**P3.2 — Retención de PGN** y otros datos
- Política: borrar partidas casuales > 1 año, mantener torneos oficiales indefinido.

**P3.3 — SIEM / observabilidad**
- Loki + Grafana para logs centralizados.
- Alertas: `> N` JWT fallidos por IP en 5 min, `> M` 401/403 en endpoints sensibles, fallas de circuit breaker.

**P3.4 — Pen-testing** anual + bug bounty para usuarios autorizados.

---

## 4. Quick wins recomendados para HOY (≤ 2 h)

Si solo se puede hacer una sentada corta antes de demo/entrega:

1. **Cerrar puertos** de MS y DBs en `docker-compose.yml` (P0.1).
2. **Verificación constant-time** de webhook secret (P0.4).
3. **Headers de seguridad** vía global filter en gateway (P1.4).
4. **Quitar el default `dev-webhook-secret`** de `application.yml` (P0.3 parcial).
5. **Limitar `/auth/register` a 5 requests/min/IP** (P1.3 parcial).

Esos cinco bloquean casi toda la superficie crítica con cambios localizados.

---

## 5. Modelo de amenaza resumido

| Atacante | Capacidad asumida | Mitigaciones actuales | Mitigaciones recomendadas |
|---|---|---|---|
| **Externo no autenticado** | HTTP al gateway | JWT auth, rate-limit IP, CORS | + WAF/Cloudflare, headers de seguridad |
| **Usuario autenticado abusivo** | JWT válido como PLAYER | Role checks en `ms-tournament` | + role checks en TODOS los MS, rate-limit por user.id |
| **Atacante en LAN** (red privada/dev) | Acceso al host:8081-8086 | NINGUNA (puertos abiertos) | **P0.1 + P0.2 (urgente)** |
| **Insider con acceso al repo** | Lectura del código | `.env` en `.gitignore` | + gitleaks, rotación, secret manager |
| **Compromiso de un MS** | RCE en uno solo | Aislamiento de DBs por servicio | + non-root, network policies, mTLS |

---

## 6. Pruebas de seguridad realizadas

Durante la fase de desarrollo y preparación para la demo, se ejecutaron las siguientes pruebas focalizadas:

- **SQL injection:** Probamos endpoints de búsqueda difusa (fuzzy search) con payloads maliciosos como `'; DROP TABLE--`. Esto fue mitigado exitosamente gracias al uso nativo de *prepared statements* de JPA/Hibernate en el `PlayerRepository`.
- **Header injection X-User-Id:** Intentamos acceder directamente a `ms-users:8081` desde el host local, inyectando el header fabricado `X-User-Role: ADMIN`. Confirmamos que la brecha **C1** existe (los microservicios expuestos confían en los headers sin validar origen).
- **Race condition en signup:** Se enviaron 5 peticiones concurrentes (`POST /users/provision`). Antes esto resultaba en 4 errores 500 y 1 éxito. Ahora, con la implementación del patrón **Idempotent Receiver** (documentado en `ANALISIS_PATRONES.md §2.8`, commit `4f1b100`), la plataforma maneja correctamente las peticiones concurrentes, resultando en 5 respuestas 200 consistentes para el mismo ID.
- **Double URL encoding en invite:** Confirmamos que al enviar un correo doblemente codificado como `bruno%2540demo.cl` hacia `ms-users`, el sistema fallaba devolviendo un 404. Este comportamiento fue corregido en el commit `50732f9`.
- **Pentesting y Auditorías Automáticas:** Declaramos explícitamente que **NO** se realizaron pruebas formales de pentesting utilizando herramientas como OWASP ZAP o Burp Suite, dado que dicho nivel de análisis excede el alcance académico de la entrega actual.

---

## 7. KPIs de seguridad sugeridos

- 0 secrets en commits (gitleaks gate en CI).
- 100 % MS con role checks de origen (X-Internal-Auth).
- 100 % tablas Supabase `public` con RLS habilitado.
- < 1 % requests con JWT inválido (sino: ataque de credential stuffing).
- p95 de tiempo de detección de incidentes ≤ 24 h (con SIEM).
