# Informe de Auditoría de Seguridad — ChessQuery

| | |
|---|---|
| **Proyecto** | ChessQuery — plataforma de ajedrez competitivo (DSY1106, DuocUC) |
| **Repositorio** | `agusnoopy3000/ChessQuery_FS3` — rama `main` (commit base `97c50ab`) |
| **Fecha** | 28 de mayo de 2026 |
| **Tipo de revisión** | Auditoría **estática** (lectura de código + configuración) y análisis de dependencias |
| **Estado del despliegue** | AWS ECS **todavía NO desplegado** — se auditaron los manifiestos antes de usarse |
| **Autorización** | Realizada con permiso del dueño del repositorio, sobre su propio proyecto |
| **Marco de referencia** | OWASP Top 10 (2021) + OWASP Agentic (ASI04/ASI09) |

---

## Cómo leer este informe (para no especialistas)

Este documento está pensado para que lo entienda **cualquier integrante del equipo, un
docente o una persona experta en seguridad**. Cada hallazgo se explica en cuatro partes:

- **Qué es** — el problema, en palabras simples.
- **Por qué importa** — qué podría pasar si no se corrige.
- **Cómo se explotaría** — el "ataque" paso a paso, resumido.
- **Cómo se corrige** — la solución concreta (con código cuando aplica).

### Mini-glosario

| Término | Significado en una línea |
|---|---|
| **Autenticación** | Verificar *quién sos* (ej: validar tu usuario/contraseña o tu token). |
| **Autorización** | Verificar *qué tenés permitido hacer* (ej: ¿podés borrar este torneo?). |
| **JWT** | "Carné digital" firmado que el navegador envía en cada pedido para probar quién sos. |
| **API Gateway** | La puerta de entrada única: recibe todos los pedidos y valida el JWT. |
| **BFF** (*Backend For Frontend*) | Servicio intermedio que adapta los datos para cada frontend (jugador / organizador). |
| **Microservicio (MS)** | Servicio backend chico y especializado (usuarios, torneos, partidas, etc.). |
| **IDOR** | Acceder a un recurso ajeno solo cambiando un identificador (ej: `/torneo/5` → `/torneo/6`). |
| **Escalada de privilegios** | Conseguir más permisos de los que te corresponden (ej: pasar de jugador a admin). |
| **Header** | Dato extra que viaja con cada pedido HTTP (ej: `X-User-Role: ORGANIZER`). |
| **Secreto** | Clave que debe permanecer oculta (ej: la clave para firmar/validar tokens). |
| **Severidad / CVSS** | Qué tan grave es (de *info* a *crítica*); CVSS es un puntaje estándar de 0 a 10. |

### Cómo está construida la aplicación (mapa rápido)

```
Navegador (React)
      │  Authorization: Bearer <JWT de Supabase>
      ▼
API Gateway (Spring Cloud Gateway)  ←── valida el JWT y agrega headers X-User-Id / X-User-Role
      │
      ├── /api/player/**     → BFF-Player    → microservicios
      ├── /api/organizer/**  → BFF-Organizer → microservicios
      └── /api/admin/**      → BFF-Admin (fuera de alcance)
                                   │
            ms-users · ms-tournament · ms-game · ms-notifications · ms-analytics
                                   │
              PostgreSQL (una BD por servicio) · RabbitMQ · Supabase (Auth/Storage)
```

**Dato clave para entender los hallazgos:** los microservicios **confían** en los headers
`X-User-Id` y `X-User-Role` que les llega. Es decir, el control de "quién sos" y "qué podés
hacer" depende de que el Gateway y los BFFs los completen **correctamente**. La mayoría de
los problemas graves aparecen justamente ahí.

---

## 1. Resumen ejecutivo

ChessQuery tiene una base sólida y aplica **bien** varios controles importantes: valida los
tokens JWT de forma robusta (rechaza tokens sin firma, vencidos o mal firmados), restringe
CORS por lista blanca, tiene rate limiting con Redis, no expone endpoints sensibles de
diagnóstico, usa contenedores sin privilegios de root, guarda los secretos en AWS Secrets
Manager y **no** tiene secretos reales subidos al repositorio ni inyección SQL.

El problema central es de **autorización** (qué puede hacer cada usuario), no de
autenticación (quién es). Hay dos fallas que conviene cerrar **antes de exponer la
aplicación a internet**:

1. **El BFF de organizadores no verifica el rol.** Solo comprueba que haya un usuario
   logueado. Como luego le dice a los microservicios "este es un ORGANIZER" de forma fija,
   **cualquier jugador común puede crear o borrar torneos, generar rondas y hasta cambiar
   resultados de partidas** llamando directamente a la API.
2. **El rol se puede auto-asignar al registrarse.** El tipo de cuenta (jugador / organizador
   / admin) viaja como un dato que controla el propio cliente. Nada del lado del servidor
   impide registrarse directamente como `ORGANIZER` o `ADMIN`.

A esto se suma un riesgo de configuración para producción: los **secretos por defecto** (la
clave para validar tokens y la del webhook) son valores **públicos y conocidos**. Si la
aplicación se despliega sin reemplazarlos, falsificar un token de administrador es trivial.
En AWS estos secretos ya vienen de Secrets Manager (correcto); falta una validación que
impida arrancar con los valores por defecto.

El resto son mejoras de mantenimiento (subir Spring Boot, agregar escaneo de imágenes,
endurecer ECS). La mayoría son **soluciones rápidas** de pocas horas.

**Veredicto:** buena base, pero **no apta para producción** hasta cerrar los hallazgos P0
(autorización por rol y por propiedad) y los secretos por defecto.

> **Nota honesta sobre el alcance:** esta auditoría es **estática** (revisión de código y
> configuración) más `npm audit`. **No** se ejecutó un pentest dinámico contra la aplicación
> corriendo. Por eso los hallazgos se basan en lectura de código; el **Apéndice B** incluye
> los comandos exactos para confirmarlos contra la stack levantada.

---

## 2. Tabla de hallazgos (ordenados por severidad)

| # | Hallazgo | Tipo (OWASP) | Severidad | CVSS aprox. |
|---|----------|--------------|-----------|-------------|
| H-01 | El BFF-Organizer no valida el rol; cualquier usuario logueado actúa como organizador | A01 | **Alta** | 8.1 |
| H-02 | El rol se puede auto-asignar al registrarse (ORGANIZER/ADMIN) | A01 / A04 | **Alta** | 8.1 |
| H-03 | Secretos por defecto conocidos (clave JWT y clave de webhook) | A02 / A07 | **Alta** (en prod) | 8.1 |
| H-04 | Acciones de torneo sin verificar propiedad (IDOR entre organizadores) | A01 | **Media** | 6.5 |
| H-05 | Los microservicios no se autentican entre sí (confían en headers) | A05 | **Media** | 5.8 |
| H-06 | Spring Boot 3.2.4 / Spring Cloud 2023.0.1 fuera de soporte + CVEs | A06 | **Media** | 5.0 |
| H-07 | CI/CD sin escaneo de imágenes (Trivy) ni ECR scan-on-push | A06 | **Media** | 5.0 |
| H-08 | Validación del secreto del webhook no es de tiempo constante | A07 | **Baja** | 3.7 |
| H-09 | El emisor (issuer) del JWT no se valida | A07 | **Baja** | 3.5 |
| H-10 | Consola de RabbitMQ (15672) expuesta en todas las interfaces + credenciales dev | A05 | **Baja** | 4.0 |
| H-11 | Dependencia `ws` con advisory moderado (GHSA-58qx-3vcg-4xpx) | A06 | **Baja** | 3.7 |
| H-12 | GitHub Actions sin fijar por SHA + runner self-hosted en PRs | A08 / ASI04 | **Baja** | 3.5 |
| H-13 | Endurecimiento ECS pendiente (readonlyRootFilesystem, circuit breaker, rol mínimo) | A05 | **Baja** | 3.5 |
| H-14 | Archivos `.env` de frontend en disco con la anon key (pública) | A05 | **Info** | — |

---

## 3. Detalle de cada hallazgo

### H-01 · El BFF-Organizer no valida el rol · **ALTA** · A01 (Broken Access Control)

**Archivos:**
- `bff-organizer/src/common/auth.guard.ts:11-18` — el guard solo exige `X-User-Id`.
- `bff-organizer/src/organizer/organizer.service.ts:53,124,…` — envía siempre
  `{ 'X-User-Role': 'ORGANIZER' }` a `ms-tournament`.
- `ms-tournament/.../controller/TournamentController.java:32` — confía en ese `X-User-Role`.

**Qué es.** El BFF de organizadores debería dejar pasar solo a usuarios con rol
`ORGANIZER`. Pero su control de acceso (`AuthGuard`) únicamente comprueba que el pedido
traiga un `X-User-Id` (es decir, que haya *alguien* logueado). No mira el rol. Peor aún:
cuando reenvía el pedido a `ms-tournament`, le pega de forma **fija** la etiqueta
`X-User-Role: ORGANIZER`. Así, el chequeo de rol que sí existe en `ms-tournament` queda
anulado: cree que todos los que llegan por el BFF son organizadores.

**Por qué importa.** Un jugador común (o cualquier cuenta) puede, llamando directamente a
`/api/organizer/...`, **crear y eliminar torneos, cambiar su estado, generar rondas,
aprobar o rechazar inscripciones y modificar resultados de partidas**. El panel de
organizador del frontend valida el rol en el navegador (`organizer-panel/src/App.tsx:38`),
pero eso es solo cosmético: se saltea llamando la API a mano.

**Cómo se explotaría.**
```
# Con el token de un jugador común (no organizador):
curl -X DELETE https://<host>/api/organizer/tournaments/5 \
     -H "Authorization: Bearer <jwt_de_jugador>"
# El BFF no chequea rol → reenvía con X-User-Role: ORGANIZER → el torneo se borra.
```

**Cómo se corrige.** Hacer que el `AuthGuard` del BFF-Organizer exija el rol, y dejar de
"hardcodear" el rol hacia abajo (propagar el real):
```typescript
// bff-organizer/src/common/auth.guard.ts
canActivate(context: ExecutionContext): boolean {
  const req = context.switchToHttp().getRequest<Request>();
  const userId = req.headers['x-user-id'];
  const role = req.headers['x-user-role'];
  if (!userId || typeof userId !== 'string') throw new UnauthorizedException('Falta X-User-Id');
  if (role !== 'ORGANIZER' && role !== 'ADMIN') throw new ForbiddenException('Requiere rol organizador');
  return true;
}
```
Y en `organizer.service.ts`, reenviar el rol que vino (`getUserRole(req)`) en vez del literal
`'ORGANIZER'`.

---

### H-02 · El rol se puede auto-asignar al registrarse · **ALTA** · A01 / A04

**Archivos:**
- `frontend/apps/chess-portal/src/pages/Register.tsx:296,343` — el cliente elige `role` y lo
  manda en los metadatos del registro.
- `supabase/migrations/00001_create_user_profiles.sql:43-53` — el trigger copia
  `raw_user_meta_data->>'role'` (controlado por el cliente) a `user_profiles.role`.
- `api-gateway/.../filter/SupabaseJwtAuthFilter.java` (método `extractRole`) — el Gateway
  arma el `X-User-Role` leyendo `user_metadata.role` del token.

**Qué es.** Cuando alguien se registra, el **tipo de cuenta** (PLAYER / ORGANIZER / ADMIN)
viaja como un dato dentro de los metadatos del usuario, y esos metadatos los define el
propio cliente. No hay ninguna validación del lado del servidor que diga "un usuario nuevo
no puede auto-asignarse ORGANIZER o ADMIN". La interfaz solo ofrece PLAYER y ORGANIZER, pero
la API de registro de Supabase acepta cualquier valor.

**Por qué importa.** Cualquiera puede crear directamente una cuenta con rol `ADMIN` o
`ORGANIZER` sin aprobación. Combinado con H-01, el atacante obtiene control sobre torneos y
(vía el panel admin, fuera de alcance pero existente) potencialmente sobre toda la
plataforma.

**Cómo se explotaría.**
```js
// Llamando la API pública de signup de Supabase directamente:
supabase.auth.signUp({
  email: 'atacante@x.com', password: '…',
  options: { data: { role: 'ADMIN' } }   // ← el servidor lo acepta tal cual
});
```

**Cómo se corrige.**
- El rol **nunca** debe venir del cliente. El registro debe crear siempre `PLAYER`.
- Las promociones a `ORGANIZER`/`ADMIN` deben hacerse desde el backend con la *service key*
  (la migración `00004` ya reconoce que el cambio de rol debe pasar por el backend).
- En el trigger `handle_new_user`, **ignorar** `raw_user_meta_data->>'role'` y fijar `'PLAYER'`.
- Si se requiere un alta de organizador, que pase por un flujo de aprobación.

---

### H-03 · Secretos por defecto conocidos · **ALTA (en producción)** · A02 / A07

**Archivos:**
- `api-gateway/src/main/resources/application.yml` → `supabase.jwt-secret` por defecto =
  `super-secret-jwt-token-with-at-least-32-characters-long` y `webhook-secret` =
  `dev-webhook-secret`.
- `infrastructure/docker-compose.yml:484-485` — mismos valores por defecto.

**Qué es.** La clave que el Gateway usa para **validar los tokens** (y la del webhook) tienen
valores por defecto que son **públicos y conocidos**: el primero es literalmente el secreto
de ejemplo que Supabase publica en su documentación para entornos locales.

**Por qué importa.** Si la aplicación se despliega sin reemplazar estos valores, **cualquiera
puede fabricar un token válido** con el rol que quiera (incluido ADMIN), porque conoce la
clave con la que se firman/validan. Es la llave maestra de toda la autenticación.

**Cómo se explotaría.**
```python
import jwt   # la clave es pública y conocida
token = jwt.encode(
    {"sub": "<uuid>", "email": "x@x", "user_metadata": {"role": "ADMIN"}},
    "super-secret-jwt-token-with-at-least-32-characters-long", algorithm="HS256")
# Authorization: Bearer <token>  → el Gateway lo da por válido
```
> Detalle técnico: el Gateway valida tokens ES256 (asimétricos, vía JWKS) y, si no encuentra
> la clave pública, **cae a HS256** con este secreto compartido. Por eso conocer el secreto
> HS256 alcanza para falsificar, aunque Supabase use ES256.

**Cómo se corrige.**
- En AWS ya se inyecta desde Secrets Manager (`JWT_SECRET_ARN`) — correcto. Usar el secreto
  **real** del proyecto Supabase Cloud y rotarlo.
- Agregar una validación de arranque que **rechace** los valores por defecto conocidos:
```java
@PostConstruct void assertNotDefaultSecret() {
    if (jwtSecret.startsWith("super-secret-jwt-token")) {
        throw new IllegalStateException("SUPABASE_JWT_SECRET por defecto: configurá el secreto real");
    }
}
```

---

### H-04 · Acciones de torneo sin verificar propiedad (IDOR) · **MEDIA** · A01

**Archivos:** `ms-tournament/.../service/TournamentService.java` — `transitionStatus` (112),
`generateRound` (300), `approveRegistration` (247), `rejectRegistration` (279),
`recordResult` (368).

**Qué es.** Varias acciones validan el *rol* (que seas organizador) pero **no la propiedad**
(que seas el dueño *de ese* torneo). `recordResult` (cambiar el resultado de una partida) ni
siquiera valida rol. La eliminación (`deleteTournament`) **sí** valida propiedad — bien.

**Por qué importa.** Un organizador puede manipular torneos de **otros** organizadores
(cambiar estados, generar rondas, alterar resultados). Combinado con H-01, lo puede hacer
cualquier usuario autenticado.

**Cómo se corrige.** Propagar `X-User-Id` a `ms-tournament` en esas acciones y validar
propiedad antes de modificar, igual que ya hace `deleteTournament`:
```java
if (!isAdmin && !tournament.getOrganizerId().equals(requesterId))
    throw new ApiException(403, "FORBIDDEN", "No sos el organizador dueño de este torneo");
```

---

### H-05 · Los microservicios no se autentican entre sí · **MEDIA** · A05

**Archivos:** ninguno de los MS tiene `SecurityConfig`; confían en `X-User-Id`/`X-User-Role`.

**Qué es.** Toda la confianza está puesta en el Gateway/BFFs. No hay autenticación entre
servicios. Quien pueda hablarle directo a un microservicio (sin pasar por el Gateway) puede
mandar el `X-User-Id` que quiera y suplantar a cualquiera.

**Por qué importa.** Hoy en local el riesgo está contenido: en `docker-compose.yml` los
puertos de los MS están en `127.0.0.1` (no se exponen a la red). **En AWS ECS la única
barrera serán los Security Groups.** Si quedan abiertos, el modelo se cae.

**Cómo se corrige.**
1. **ECS:** Security Group por servicio que **solo** acepte tráfico del SG del Gateway/BFF.
2. **Defensa en profundidad:** firmar la confianza Gateway→MS con un header secreto
   compartido (desde Secrets Manager) validado por un filtro en cada MS, o mTLS vía service mesh.

---

### H-06 · Framework fuera de soporte + CVEs · **MEDIA** · A06
Los 6 `pom.xml` usan **Spring Boot 3.2.4** y **Spring Cloud 2023.0.1**, fuera de soporte OSS
y con CVEs acumulados del ciclo 2024. **Solución:** subir a una línea soportada (3.3.x/3.4.x +
Spring Cloud 2024.x), correr la suite (530 tests) y agregar `dependency-check` al CI.

### H-07 · CI/CD sin escaneo de imágenes · **MEDIA** · A06
`.github/workflows/build-and-push.yml` construye y publica imágenes sin escanearlas.
**Solución:** agregar un paso Trivy que falle ante vulnerabilidades `HIGH,CRITICAL` y activar
*scan-on-push* en ECR.

### H-08 · Comparación del secreto de webhook no es de tiempo constante · **BAJA** · A07
`api-gateway/.../webhook/SupabaseWebhookController.java:62` usa `.equals()`. El webhook **sí**
rechaza si el secreto falta o no coincide (bien, no es *fail-open*), pero `.equals()` permite
en teoría un *timing attack*. **Solución:** `MessageDigest.isEqual(...)`.

### H-09 · Issuer del JWT no validado · **BAJA** · A07
El validador no llama a `requireIssuer(...)`. Cualquier token firmado con la misma clave es
aceptado sin importar el emisor. **Solución:** `Jwts.parser().requireIssuer(<issuer-esperado>)`.

### H-10 · Consola de RabbitMQ expuesta · **BAJA** · A05
`infrastructure/docker-compose.yml:518` → `"15672:15672"` (todas las interfaces) con
credenciales `chessquery/chessquery_dev`. **Solución:** bindear a `127.0.0.1:15672`, credenciales
fuertes vía `.env`, no exponer la consola en ECS.

### H-11 · Dependencia `ws` con advisory · **BAJA** · A06
`npm audit` (frontend) reporta `ws` (GHSA-58qx-3vcg-4xpx, severidad moderada). **Solución:**
`npm audit fix`.

### H-12 · Supply chain de CI/CD · **BAJA** · A08 / ASI04
Las GitHub Actions se referencian por tag móvil (`@v4`, `@v6`) en un runner **self-hosted**, y
el CI corre en `pull_request`. Un tag comprometido (o un PR malicioso) podría ejecutar código
en el runner. El repo privado limita el riesgo de PRs externos. **Solución:** fijar las actions
por SHA, mantener `permissions:` mínimos (ya están) y endurecer el runner (efímero, sin
secretos persistentes). *(ASI09: revisar manualmente las sugerencias de IA antes de mergear.)*

### H-13 · Endurecimiento de ECS pendiente · **BAJA** · A05
`infrastructure/aws/task-definitions/*.template.json`: falta `"readonlyRootFilesystem": true`,
conviene verificar el `deploymentCircuitBreaker` en la *service definition*, y mantener el
*execution role* con privilegio mínimo (solo los ARNs de secretos puntuales). No hay
`taskRoleArn` (correcto: los contenedores no necesitan credenciales AWS en runtime). El
Dockerfile ya corre como usuario `chessquery` (no root) — bien.

### H-14 · `.env` de frontend en disco con la anon key · **INFO** · A05
`frontend/apps/*/.env` contienen la *anon key* de Supabase (que es **pública por diseño**,
segura para frontends y limitada por RLS) y URLs locales. Hoy **no** están trackeados en git
(verificado). **Recomendación:** mantenerlos fuera de git y documentar que la anon key no es
un secreto.

---

## 4. Priorización y esfuerzo estimado

| Prioridad | Hallazgos | Esfuerzo |
|-----------|-----------|----------|
| **P0 — antes de cualquier exposición pública** | H-01, H-02, H-03 | ~10–14 h |
| **P1 — antes del despliegue en AWS** | H-04, H-05, H-07, H-13 | ~16–22 h |
| **P2 — primeras semanas en producción** | H-06, H-08, H-09, H-10 | ~12–16 h |
| **P3 — higiene continua** | H-11, H-12, H-14 | ~3–5 h |

### Soluciones rápidas (< 2 h cada una) ⚡
- **H-03** — rechazar el secreto JWT por defecto al arrancar *(~1 h)*
- **H-01** — exigir rol en el `AuthGuard` del BFF-Organizer *(~1.5 h)*
- **H-02** — fijar `role = 'PLAYER'` en el trigger de registro *(~1 h)*
- **H-08 / H-09** — comparación constant-time + validar issuer *(~1 h)*
- **H-11** — `npm audit fix` *(~15 min)*
- **H-10** — bindear RabbitMQ a loopback + credenciales fuertes *(~30 min)*

---

## 5. Plan de remediación en 3 sprints

**Sprint 1 — Autorización (cierra P0).** Exigir rol en el BFF-Organizer y propagar el rol
real (H-01). Quitar la auto-asignación de rol en el registro (H-02). Guard que rechace los
secretos por defecto (H-03). *Salida esperada:* tests que prueben que un jugador **no** puede
usar endpoints de organizador y que no se puede registrar como ADMIN.

**Sprint 2 — Defensa por capas y despliegue AWS.** Validar propiedad en las acciones de
torneo (H-04). Security Groups MS-only + confianza firmada Gateway→MS (H-05). Trivy en CI +
scan-on-push (H-07). Endurecer task definitions (H-13).

**Sprint 3 — Mantenimiento.** Subir Spring Boot/Spring Cloud (H-06). Comparación
constant-time e issuer (H-08, H-09). RabbitMQ y credenciales (H-10). `ws` (H-11). Fijar
actions por SHA (H-12). Ejecutar el **pentest dinámico** del Apéndice B sobre la stack ya
endurecida.

---

## 6. Estado de la cobertura OWASP

| OWASP 2021 | Estado | Evidencia |
|---|---|---|
| **A01 Broken Access Control** | ❌ Falla | H-01, H-02, H-04, H-05 |
| **A02 Cryptographic Failures** | ⚠️ Parcial | JWT robusto; secretos por defecto (H-03); TLS/ACM pendiente |
| **A03 Injection** | ✅ Bien | JPA + `nativeQuery` con parámetros nombrados; sin `eval`/`exec`; React escapa HTML (sin `dangerouslySetInnerHTML`) |
| **A04 Insecure Design** | ⚠️ Parcial | Rol confiado al cliente (H-02) |
| **A05 Security Misconfiguration** | ⚠️ Parcial | Actuator restringido ✅; confianza entre MS (H-05), RabbitMQ (H-10), ECS (H-13) |
| **A06 Vulnerable Components** | ⚠️ Parcial | Spring Boot EOL (H-06), sin scan de imágenes (H-07), `ws` (H-11) |
| **A07 Auth Failures** | ⚠️ Parcial | JWT bien validado ✅; secreto webhook (H-08), issuer (H-09) |
| **A08 Data/Software Integrity** | ⚠️ Parcial | Actions sin pin por SHA (H-12) |
| **A09 Logging & Monitoring** | ⚠️ Revisar | `awslogs` configurado ✅; faltan alertas y auditoría de accesos |
| **A10 SSRF** | ✅ Sin hallazgos | Las URLs de los servicios son configuración, no entrada del usuario |
| **ASI04 / ASI09 (dev tooling)** | ⚠️ | H-12 + revisión manual de sugerencias de IA |

### Controles correctos confirmados (lo que SÍ está bien)
- **Validación de JWT robusta:** rechaza tokens sin firma (`alg=none`), vencidos y mal
  firmados; soporta ES256 (JWKS) con fallback HS256.
- **CORS por lista blanca** (sin comodín `*`) en el Gateway.
- **Rate limiting activo** con Redis (100 req/min por IP) en el Gateway.
- **Actuator restringido** a `health`/`info` (en AWS, `show-details: never`) → `/actuator/env`
  responde 404.
- **Sin inyección SQL:** acceso vía JPA y consultas nativas con parámetros nombrados.
- **Sin secretos reales en el repo:** `.env` real ignorado por git; sin secretos en el historial.
- **Contenedores no-root** con build multi-stage.
- **Secretos vía AWS Secrets Manager** en las task definitions.
- **RLS** activado en `user_profiles` (lectura del propio perfil, admin y service role).
- **Borrado de torneo con verificación de propiedad** (`deleteTournament`).

---

## Apéndice A — Comandos usados (revisión estática)

```bash
# Mapa de endpoints y controles de autorización
grep -rn "PreAuthorize\|hasRole\|x-user-role" --include=*.java --include=*.ts   # rol solo en MS/BFF
grep -rn "Path=/api" api-gateway/src/main/resources/application.yml             # rutas expuestas

# Patrones peligrosos (resultado: ninguno)
grep -rn "dangerouslySetInnerHTML\|eval(\|Runtime.getRuntime\|ProcessBuilder\|child_process" \
  --include=*.java --include=*.ts --include=*.tsx | grep -v node_modules
grep -rn "nativeQuery=true" --include=*.java     # presentes, pero con parámetros nombrados

# Secretos
git ls-files | grep -iE '(^|/)\.env'                                            # sin .env real trackeado
grep -rn "super-secret-jwt-token\|dev-webhook-secret" infrastructure api-gateway # defaults (H-03)

# Dependencias
( cd frontend && npm audit --omit=dev )                                         # ws moderado (H-11)
grep -A4 '<parent>' */pom.xml | grep '<version>'                                # Spring Boot 3.2.4 (H-06)
```

## Apéndice B — Cómo confirmar los hallazgos contra la app corriendo (pentest dinámico, pendiente)

Levantar: `cd infrastructure && docker compose up -d --build`. Obtener un token de un
**jugador común** vía la API de Supabase.

```bash
GW=http://localhost:8080
PJUGADOR="Authorization: Bearer $TOKEN_JUGADOR"

# H-01: ¿un jugador puede actuar como organizador?  (esperado correcto: 403; hoy probablemente pasa)
curl -i -X POST $GW/api/organizer/tournaments -H "$PJUGADOR" \
     -H 'Content-Type: application/json' -d '{"name":"hack","format":"SWISS"}'
curl -i -X DELETE $GW/api/organizer/tournaments/1 -H "$PJUGADOR"

# H-02: ¿puedo registrarme como ADMIN?  (esperado correcto: el rol se ignora y queda PLAYER)
#   supabase.auth.signUp({ email, password, options: { data: { role: 'ADMIN' } } })
#   luego inspeccionar el JWT en jwt.io → ¿user_metadata.role == ADMIN?

# H-03: forjar un token con el secreto por defecto  (esperado correcto: 401)
python3 - <<'PY'
import jwt
print(jwt.encode({"sub":"00000000-0000-0000-0000-000000000001","email":"x@x",
      "user_metadata":{"role":"ADMIN"}},
      "super-secret-jwt-token-with-at-least-32-characters-long", algorithm="HS256"))
PY
# usar el token contra $GW/api/organizer/tournaments → si responde, forja confirmada

# Controles que deberían estar OK
curl -s -o /dev/null -w '%{http_code}\n' $GW/api/player/profile                 # sin token → 401
curl -s -o /dev/null -w '%{http_code}\n' $GW/actuator/env                       # → 404
curl -s -D - -o /dev/null -H 'Origin: https://evil.example' $GW/api/player/profile  # sin ACAO
```

---
*Informe de solo lectura. No se modificó ningún archivo del proyecto salvo este documento.
Requiere revisión humana antes de remediar y antes de mergear.*
