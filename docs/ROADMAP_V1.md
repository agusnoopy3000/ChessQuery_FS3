# ChessQuery — Roadmap a Producto Final (v1)

> **⚡ Resumen en 30 segundos:** en **~2 semanas** (Martin + Agustín) pasamos de "demo que funciona"
> a **producto v1**. Lo innegociable: **tests al 80% en el core con gate en CI (T1)**, **seguridad
> cerrada (T2)** y el **cierre de entrega — ensayo final, informe y presentación (T8)**. Lo que
> completa el alcance: **integrar ETL (T3)** y **QA funcional de paneles (T4)**. Lo operativo:
> **CloudWatch (T5)** y **CI/CD + correos + backups (T6)**. HTTPS (T7) queda **diferido** por el
> profesor. El detalle de cada tarea está en §4 y el plan día a día en §5.

## Índice

| Sección | Qué encontrás |
|---|---|
| [1. ¿Qué convierte una demo en "producto v1"?](#1-qué-convierte-una-demo-en-producto-v1) | Los 7 pilares y qué tarea cubre cada uno |
| [2. Escala de prioridades](#2-escala-de-prioridades-qué-significa-cada-nivel) | Qué significa P0–P3 + **tabla resumen de tareas** |
| [3. Mapa visual del producto](#3-mapa-visual-del-producto-dónde-cae-cada-tarea) | Diagrama de la arquitectura con cada tarea ubicada |
| [4. Detalle de cada tarea](#4-detalle-de-cada-tarea-qué-por-qué-cómo) | T1–T8: qué es, por qué importa, acciones y definición de hecho |
| [5. Plan visual de 2 semanas](#5-plan-visual-de-2-semanas-2-tracks-en-paralelo) | Calendario día a día en 2 tracks + hitos |
| [6. Backlog de ideas](#6-backlog-de-ideas-post-v1-no-comprometidas) | Ideas anotadas para después de v1 |
| [7. Riesgos y cosas a vigilar](#7-riesgos-y-cosas-a-vigilar) | Qué puede salir mal y cómo reaccionar |
| [8. Referencias](#8-referencias) | Docs de apoyo de cada tarea |

---

> **Cambio de enfoque:** ya **no** preparamos una _demo_, sino el **producto final v1**. Una demo
> alcanza con "que se vea funcionando una vez"; un producto tiene que ser **confiable, seguro,
> mantenible y operable**. Este documento traduce ese salto en tareas concretas, ordenadas por
> prioridad, con el **por qué** de cada una y un **plan visual** de 2 semanas.
>
> **Ventana:** ~**2 semanas** (≈ 10 días hábiles). **Equipo:** 2 (Martin + Agustín) ⇒ capacidad
> realista ≈ **16 días-dev** efectivos (descontando overhead, reuniones y refresco de credenciales
> Academy cada ~4 h).
>
> **HTTPS:** el profesor indicó que **no es prioridad** → se difiere (plan en `docs/PENDIENTE_HTTPS.md`).

> **📌 Actualización 2026-06-10 (para revisar en equipo):**
> - **T1** suma un **Paso 0**: verificar que la suite de tests corre **dentro de Docker** antes de arrancar.
> - **T4** explicita el caso: **un organizador no puede modificar torneos de otro** (UI + API directa).
> - **T6** explicita: probar el **servicio de correos en la réplica AWS de Martin** (desde su PC Arch).
> - Nueva **T8 (P0)**: ensayo final del producto (foco front) + **informe y presentación**
>   (enfocada en explicarle la app a un profesor que no la conoce).
> - Nueva sección **§6 Backlog de ideas** (menú de configuración de usuario, temas de tablero) — post-v1, sin compromiso.
> - "CI/CD pipeline" e "integrar ETL" ya estaban cubiertos (T6 y T3); no se duplican.

> **📌 Actualización 2026-06-11 (sesión de Martin — estado real tras el trabajo de Agustín):**
> El código está **mejor de lo que el roadmap asumía**. Resumen de avances y hallazgos:
> - **T2 casi cerrado:** **H-04 hecho** por Agustín (autorización por propiedad en TODAS las
>   escrituras de ms-tournament, commit `9864a8d`). **SG 8080 ya estaba cerrado** (solo ALB).
>   **Deps escaneadas:** 0 críticas, `axios` bumpeado (PR #29, mergeado). **OBS-02 cerrado:**
>   pass length hosted ya en 8 + `config.toml` local a 8 (PR #30, mergeado); **0 test users** que
>   limpiar en la réplica. *Falta de T2:* **H-06** (bump Spring/NestJS, arrastra 3 highs de los
>   BFFs) y **H-07** (Trivy, va con T6).
> - **Decisión pendiente (H-01/H-02):** el gateway saca el rol de `user_metadata` (editable) → un
>   usuario puede auto-asignarse **ADMIN**. Tras H-04 el daño se limita a lo propio, pero conviene
>   **endurecer ADMIN** (que solo salga de `app_metadata`). Cambio chico en el gateway → coordinar
>   con Agustín. ORGANIZER self-service queda como está (intencional para el demo).
> - **T3 — ✅ HECHO en la réplica de Martin.** `ms-etl` desplegado como 2º service ECS
>   (`chessquery-etl`, opción B) y **verificado e2e**: `POST /etl/sync/lichess` → SUCCESS (1 registro).
>   Se encontró y arregló un bug (PR #32): `chesscom` faltaba en `valid_sources` del router.
>   ⚠️ **Trampa operativa que queda:** Academy bloquea Service Discovery → el deploy hornea la **IP
>   privada** del stack; **cada apagado/encendido del stack obliga a re-correr `deploy-etl-service.sh`**.
>   El ETL es un **2º consumidor de créditos** → apagar aparte (`--service chessquery-etl --desired-count 0`).
> - **T6 — bloqueos descubiertos:** (1) el **runner self-hosted está offline** → los checks de
>   cualquier PR quedan en `queued` (no fallan, no corren); desbloquearlo habilita el gate de T1.
>   (2) **Correos NO se envían:** la task-def de `ms-notifications` tiene `MAIL_FROM=""` → `setFrom("")`
>   → JavaMail `Could not parse mail` → cae a log. **Fix pendiente:** desplegar con
>   `MAIL_FROM=martindevalvarez@gmail.com` (Gmail exige From = cuenta autenticada). Fix durable de
>   código: `from: ${MAIL_FROM:${SMTP_USERNAME:no-reply@chessquery.cl}}` en `application-aws.yml`.

> **🌙 Cierre de jornada 2026-06-11:** se completó **T3** (ETL + fix #32) y **T5** (CloudWatch: logs+
> retención, Container Insights, 5 alarmas, dashboard `ChessQuery-Replica`, doc `CLOUDWATCH_REPLICA.md`).
> **T2** quedó casi cerrado (PRs #29, #30 mergeados). Infra AWS **apagada** al cerrar. Próximo:
> el fix de correos (T6) en un próximo encendido, y lo de Agustín (H-06, ADMIN, gate de T1).

---

## 1. ¿Qué convierte una demo en "producto v1"?

No es una sola cosa; son **7 pilares**. Un producto v1 razonable necesita estar "verde" en los
primeros cuatro, y al menos iniciado en los demás:

| Pilar | Pregunta que responde | Tarea(s) en este roadmap |
|---|---|---|
| 🧪 **Calidad** | "¿cómo sé que no rompí nada al cambiar código?" | T1 (tests 80% + gate) |
| 🔒 **Seguridad** | "¿puede un tercero entrar/abusar?" | T2 (hardening + auditoría) |
| 🧩 **Funcionalidad completa** | "¿está TODO el producto, no un recorte?" | T3 (ETL), T4 (QA paneles) |
| 👁️ **Observabilidad** | "¿me entero cuándo y por qué falla?" | T5 (CloudWatch) |
| ⚙️ **Operación / CI-CD** | "¿desplegar es repetible y sin manos?" | T6 (runner, SMTP, backups) |
| 🔐 **Acceso seguro (HTTPS)** | "¿el tráfico va cifrado?" | T7 (diferido por el profesor) |
| 🎤 **Entrega** | "¿lo podés mostrar y explicar a quien no lo conoce?" | T8 (ensayo final + informe + presentación) |

> **La idea clave:** una demo prueba que el software *puede* funcionar; un producto prueba que
> *sigue* funcionando, que está *protegido*, y que cuando se rompe *te enterás y lo arreglás*.

---

## 2. Escala de prioridades (qué significa cada nivel)

No todas las tareas pesan igual. Usamos 4 niveles. El criterio es **"¿qué pasa si NO lo hago
para la entrega?"**:

| Nivel | Significado | Criterio de decisión | Si falta… |
|---|---|---|---|
| **P0 — Crítico** | El producto **no es v1 sin esto** | Calidad/seguridad básicas; un profe lo exige sí o sí | No se puede entregar como "producto" |
| **P1 — Importante** | Completa el **alcance** prometido | Funcionalidad que faltaba o QA del flujo real | Queda incompleto / con recortes visibles |
| **P2 — Deseable** | Lo que hace al producto **operable** | Mejora operación y mantenimiento, no el alcance | Funciona, pero "a ciegas" / manual |
| **P3 — Diferido** | Vale, pero **fuera de esta ventana** | Bloqueado por algo externo o despriorizado | No afecta la entrega de v1 |

> **Cómo se ordenó:** primero lo que **bloquea** la etiqueta "producto" (P0), después lo que
> **completa el alcance** (P1), después lo que **mejora la operación** (P2), y al final lo
> **diferido** (P3). Dentro de cada nivel, lo más barato/que desbloquea a otros va primero.

### Resumen ejecutivo — las 8 tareas de un vistazo

Ordenadas por prioridad (no por número). Lo marcado 🆕 se integró el **10-jun**.
Estados: ⬜ pendiente · 🟨 en curso · ✅ hecho · ⏸️ diferido — *ir actualizando esta columna en cada reunión.*

| # | Tarea | Incluye | Prioridad | Días-dev | Estado |
|---|---|---|---|---|---|
| 🧪 **T1** | Pruebas unitarias → **80% + gate en CI** | 🆕 Paso 0: la suite debe correr **en Docker** antes de arrancar | **P0** | 6–8 | 🟨 línea base verde en Docker; core casi 80% (ms-users 78%); gate bloqueado por runner (T6) |
| 🔒 **T2** | **Seguridad** + hardening | Cerrar SG 8080, deps, secrets, H-01/H-02 | **P0** | 3 | 🟨 casi cerrado (H-04 ✅, SG ✅, deps PR#29 ✅, OBS-02 PR#30 ✅); falta H-06/H-07 + decisión ADMIN |
| 🎤 **T8** | 🆕 **Ensayo final + informe + presentación** | Recorrido completo del front pre-entrega; presentación pensada para quien no conoce la app | **P0** | 1.5–2 | ⬜ |
| 🧩 **T3** | **Integrar `ms-etl`** en AWS | Deploy (límite 10 contenedores) + migraciones + tests + e2e | **P1** | 3 | ✅ **HECHO (11-jun):** desplegado y verificado e2e en la réplica de Martin (Lichess SUCCESS) + fix de chesscom (PR #32) |
| 🧩 **T4** | **QA funcional** paneles organizador | 🆕 Caso explícito: un organizador **no** modifica torneos de otro (UI + API) | **P1** | 2 | 🟨 caso de aislamiento blindado por H-04; falta el QA manual (UI + API) |
| 👁️ **T5** | **CloudWatch** | Logs + métricas + alarmas + dashboard | **P2** | 2 | 🟢 **montado (11-jun):** logs+retención, Container Insights, 5 alarmas, dashboard `ChessQuery-Replica`, doc `CLOUDWATCH_REPLICA.md`; falta opcional SNS+mail |
| ⚙️ **T6** | **CI/CD real** + operación | Runner self-hosted · 🆕 probar correos en la réplica AWS de Martin · backups RDS | **P2** | 2 | 🟨 runner **offline** (checks en cola); **correos diagnosticados:** `MAIL_FROM=""` → no envía, fix pendiente; backups RDS pendiente |
| 🔐 **T7** | HTTPS | Plan en `PENDIENTE_HTTPS.md`; bloqueado por dominio | **P3** | 1–4 | ⏸️ |

> P0–P2 suman ~20–22 días-dev sobre ~16 disponibles ⇒ **hay que paralelizar y recortar alcance**
> del 80% (ver §7 Riesgos). El plan de §5 reparte el trabajo en 2 tracks para que entre; si algo
> se atrasa, lo primero que se recorta es T6, después T5 — T8 no se recorta (es parte de la nota).

---

## 3. Mapa visual del producto (dónde cae cada tarea)

```
                          ☁️  AWS (us-east-1) — ChessQuery v1
   ┌──────────────────────────────────────────────────────────────────────────┐
   │                                                                            │
   │   👁️ T5 CloudWatch (logs + métricas + alarmas) ── observa TODO lo de abajo │
   │   ─────────────────────────────────────────────────────────────────────── │
   │                                                                            │
   │   🌐 internet                                                              │
   │      │  🔒 T2: cerrar 8080 público (hoy se saltea el ALB)                  │
   │      ▼                                                                      │
   │   [ALB] ──► [ ECS task · 10 contenedores ]                                 │
   │              ├─ api-gateway (🧪 T1: tests JWT/CORS/webhook)                │
   │              ├─ ms-users / ms-tournament / ms-game (🧪 T1 core)            │
   │              ├─ ms-analytics / ms-notifications (🧪 T1)                    │
   │              ├─ bff-player / bff-organizer (🧪 T1 + 🧩 T4 QA paneles)      │
   │              └─ rabbitmq + redis                                           │
   │                    │                                                        │
   │                    └─ 🧩 T3: + ms-etl  (NO entra: límite 10 → externalizar │
   │                                          RabbitMQ/Redis para liberar slot) │
   │                                                                            │
   │   [RDS Postgres] ◄── 🔒 T2 (acceso solo VPC+IP)   ⚙️ T6: backups          │
   └──────────────────────────────────────────────────────────────────────────┘
        ⚙️ T6 CI/CD: push → ci.yml → build → deploy   (hoy a mano local)
        📧 T6 correos: probar SMTP e2e en la réplica AWS de Martin
        🔐 T7 HTTPS: diferido
        🎤 T8 entrega: ensayo final del front + informe + presentación
```

---

## 4. Detalle de cada tarea (qué, por qué, cómo)

### 🧪 T1 · Pruebas unitarias al 80% de cobertura  ·  P0  ·  6–8 días-dev

**Qué es:** llevar la cobertura de tests a ≥80% y **bloquear** en CI que un cambio la baje.

**Por qué importa:**
- Sin tests, cada cambio es una apuesta; en una demo da igual, en un producto que sigue
  evolucionando los tests son la **red de seguridad**.
- El **80%** es el estándar de facto: alto para dar confianza, sin el costo desproporcionado del 100%.

**Estado base (verificado):**
- JaCoCo **ya configurado** en los 6 módulos Java; todos tienen carpeta de tests.
- Toolchain (Java 17 + Maven) corre OK. Falta: subir cobertura + gate.
- Base previa: `PLAN_TESTS_JWT_FILTER.md`, `PLAN_TESTS_ORGANIZER_TOURNAMENTS.md`, `PRUEBAS.md`.

**🐳 Paso 0 — antes de arrancar (nuevo):** verificar que la suite completa corre **dentro de los
contenedores Docker**, no solo en el host (`mvn test` por módulo dentro del contenedor / build de
compose). El gate de CI va a correr en ese entorno: si algo solo pasa en el host, hay que
arreglarlo **antes** de empezar a escribir tests nuevos.

**Orden sugerido (de más a menos crítico):**
1. **api-gateway** — `SupabaseJwtAuthFilter` (JWKS + fallback HMAC), CORS, ruteo, webhook controller.
2. **ms-users** — provisión por webhook, perfil, ELO/Lichess, constraint `lichess_username`.
3. **ms-tournament** — torneos, generación de rondas, consumer `game.finished`.
4. **ms-game** — partidas en vivo, control de tiempo, idempotencia del resultado.
5. **ms-analytics / ms-notifications** — estadísticas, campana/correos.
6. **BFFs (player/organizer)** — armado de respuestas (Node → vitest/jest).
7. **Frontend** — flows críticos (login, registro, panel organizador) con vitest.

**Cómo forzar el 80%:**
- Java: regla JaCoCo `<minimum>0.80</minimum>` en cada `pom` (falla el build si baja).
- Node/Front: `coverageThreshold: 80` en vitest/jest.
- **Gate en `ci.yml`:** un PR no mergea si la cobertura cae por debajo del umbral.

**Definición de hecho:** backend core (gateway, ms-users, ms-tournament, ms-game) ≥80% con gate
activo en CI; analytics/notifications/BFF/front en best-effort documentado.

> ⚠️ **Realismo:** 80% **parejo en TODO** + el resto de tareas en 2 semanas es muy ambicioso.
> Recomendado: 80% **obligatorio en backend core**, best-effort en el resto. **Acordar el número
> con el profesor** para no comprometer una meta irreal.

---

### 🔒 T2 · Pruebas de seguridad + hardening  ·  P0  ·  3 días-dev

**Qué es:** auditar y cerrar agujeros conocidos antes de etiquetar v1. Base: `SECURITY_AUDIT_REPORT.md`,
`SECURITY_PLAN.md`.

**Por qué importa:**
- "Producto" implica que terceros podrían usarlo/atacarlo.
- Un agujero **conocido** sin cerrar es deuda inaceptable en v1 (y es lo primero que mira un evaluador).

**Acciones concretas:**
1. **Cerrar el SG del gateway** — *verificado en vivo hoy:* `chessquery-ecs-sg`
   (`sg-0b894230fdd04ecf4`) expone 8080 a `0.0.0.0/0` **además** del ALB ⇒ se puede saltar el ALB.
   Cerrarlo (1 comando, seguro porque la app ya va por el ALB):
   ```bash
   aws ec2 revoke-security-group-ingress --group-id sg-0b894230fdd04ecf4 \
     --protocol tcp --port 8080 --cidr 0.0.0.0/0
   ```
2. **Revisitar H-01 / H-02** — antes "decisión de producto"; ahora que ES producto, re-evaluar
   si corresponde mitigarlos.
3. **`Minimum password length = 8`** en Supabase (Auth → Email) — hallazgo OBS-02.
4. **Escaneo de dependencias** — `npm audit` (BFF/front) + OWASP Dependency-Check / plugin de
   versiones (Java); subir lo crítico.
5. **Secret scanning** — gitleaks; confirmar que `.deploy-outputs.env` y `.env.production` siguen
   gitignored.
6. **Limpiar usuarios de prueba** (`smoke+*`, `webhooktest+*`) en Supabase Auth.
7. (Opcional) `/security-review` sobre el diff antes de cada merge grande.

**Definición de hecho:** SG cerrado, hallazgos del informe resueltos o justificados por escrito,
escaneo de deps sin críticos abiertos.

---

### 🧩 T3 · Integrar `ms-etl` en la arquitectura AWS  ·  P1  ·  3 días-dev

**Qué es:** sumar el microservicio `ms-etl` (quedó fuera de la demo) al despliegue, **con todo lo
que conlleva**: migraciones, healthcheck, tests propios (cuenta para el 80% de T1) y verificación e2e.

**Por qué importa:**
- v1 debería ser el producto **completo**, no un recorte: `ms-etl` es parte del diseño
  (procesa eventos hacia `etl_db` para analítica).
- Dejarlo afuera es entregar un producto "con un módulo desconectado".

**El problema:** AWS Academy limita **10 contenedores por task** y la task ya tiene 10. `ms-etl`
no entra tal cual.

**Opciones (de más a menos recomendada):**
- **A (recomendada): externalizar RabbitMQ a Amazon MQ** (o Redis a ElastiCache) para **liberar
  un slot** y meter `ms-etl` en la task principal. Bonus: endpoint estable de mensajería.
  ⚠️ **Verificar día 2** que Amazon MQ/ElastiCache estén habilitados en Academy.
- **B: segunda task ECS sólo para `ms-etl`.** Sin Service Discovery (Academy lo bloquea), no
  alcanza al RabbitMQ que vive en `localhost` de la otra task ⇒ frágil; sólo viable junto con A.
- **C: descartar ETL de v1** y documentarlo como limitación de Academy (último recurso).

**Definición de hecho:** `ms-etl` consumiendo de RabbitMQ y escribiendo en `etl_db`, con sus
migraciones Flyway y healthcheck, verificado e2e.

---

### 🧩 T4 · QA funcional de los paneles de organizador  ·  P1  ·  2 días-dev (continuo)

**Qué es:** probar a mano (y por API) los flujos reales del producto, con foco en el organizador.

**Por qué importa:**
- Los tests unitarios (T1) prueban piezas aisladas; el QA funcional prueba que el **flujo
  completo** funciona para un usuario real.
- Es lo que el profesor va a "tocar".

**Qué probar:**
- Crear torneo → generar rondas/emparejamientos → **partidas en vivo** (tablero espectador) →
  resultado vuelve solo al pairing (`game.finished`).
- Invitación a partida (push in-app + link, sin alta indebida en Supabase).
- Registro → webhook → perfil en `ms-users`; recuperación de contraseña; accesibilidad de login.
- 🔑 **Aislamiento entre organizadores (caso explícito):** un organizador **no puede ver, modificar
  ni eliminar torneos creados por otro organizador**. Probarlo **por UI y por API directa**
  (forzar el ID de un torneo ajeno en el request → debe dar 403/404, no éxito silencioso).
  Base: `PLAN_TESTS_ORGANIZER_TOURNAMENTS.md`. Si falla, es bug de seguridad ⇒ escala a T2.

**Definición de hecho:** checklist de `PRUEBAS.md` recorrido en el entorno AWS; bugs registrados
y los críticos resueltos.

---

### 👁️ T5 · CloudWatch  ·  P2  ·  2 días-dev

**Qué es:** observabilidad — logs, métricas, alarmas y un dashboard del sistema.

**Por qué importa:**
- En producto, cuando algo falla **tenés que enterarte y poder diagnosticar sin entrar a la task**.
- Hoy, si un contenedor se cae, nadie se entera hasta que un usuario reclama.

**Acciones:**
- **Logs centralizados:** confirmar `awslogs` por contenedor (log group por servicio) + retención (7–14 días).
- **Métricas:** CPU/Mem de task y servicio ECS; conexiones/almacenamiento de RDS.
- **Alarmas:** task caída (`runningCount < 1`), CPU/Mem alta, RDS storage bajo, 5xx en el ALB.
- **Dashboard** único (sirve además para mostrarle el estado al profesor).
- (Opcional) **Container Insights** en el cluster.

**Definición de hecho:** dashboard con estado del sistema + al menos las 4 alarmas clave activas.

---

### ⚙️ T6 · CI/CD real + SMTP + backups + limpieza  ·  P2  ·  2 días-dev

**Qué es:** que desplegar sea repetible y automático, y cerrar cabos de operación.

**Por qué importa:**
- Hoy el build/deploy se hace **a mano desde local**; un producto necesita un pipeline repetible
  (cualquiera del equipo despliega igual).
- Los datos de producto tienen que estar respaldados.

**Acciones:**
- **Configurar bien el pipeline CI/CD:** registrar el runner self-hosted
  (`docs/SELF_HOSTED_RUNNER.md`) y dejar la cadena `ci.yml` → `build-and-push.yml` → `deploy.yml`
  corriendo sola de punta a punta, con el gate de cobertura de T1 integrado.
- **📧 Probar el servicio de correos en la réplica AWS de Martin** (desde su PC Arch, cuenta
  `876204681432`): confirmar que el secreto `chessquery/smtp-password` tiene la App Password de
  Gmail **real** (si quedó placeholder, los correos de bienvenida/invitación fallan en silencio) y
  verificar envío e2e: registro → correo de bienvenida recibido; invitación → correo recibido.
- **Backups RDS:** confirmar retención de backups automáticos (producto, no demo).
- Limpieza de ramas/usuarios de prueba; consolidar docs.

**Definición de hecho:** un push a `main` despliega solo; correos e2e verificados en la réplica;
backups RDS confirmados.

---

### 🔐 T7 · HTTPS  ·  P3 (diferido)
Diferido por indicación del profesor. Plan completo en `docs/PENDIENTE_HTTPS.md`. Bloqueante real:
un **dominio propio**. Se retoma solo si cambia la prioridad.

---

### 🎤 T8 · Cierre de entrega: ensayo final + informe + presentación  ·  P0  ·  1.5–2 días-dev

**Qué es:** los entregables de la presentación y un **ensayo general** del producto antes de mostrarlo.

**Por qué importa:**
- El día de la presentación no hay margen: si algo del front falla en vivo, el trabajo de las
  2 semanas no se ve.
- El informe y la presentación **son parte de la nota**, no un extra.

**Acciones:**
- **🔎 Revisión final del producto (foco en frontend):** recorrido completo de la app en AWS
  **como lo haría el profesor** — registro, login, crear torneo, rondas, partida en vivo,
  paneles, correos, notificaciones. En el navegador, datos limpios, sin consola abierta.
  Hacerlo 1–2 días **antes** de presentar para tener margen de arreglar lo que aparezca.
- **📄 Informe:** documento de entrega (arquitectura, decisiones, seguridad, tests/cobertura,
  despliegue AWS, limitaciones de Academy). Reusar los docs existentes, no escribir de cero.
- **🎯 Presentación:** armarla con un enfoque claro — **explicar la aplicación a una persona
  que no la conoce** (el profesor no sabe nada de la app). Eso significa:
  - Empezar por **qué problema resuelve** y quién la usa, no por la arquitectura.
  - Demo guiada con un **guion de usuario** (organizador crea torneo → jugadores juegan →
    resultados), sin jerga interna ni nombres de microservicios hasta la parte técnica.
  - Recién después: arquitectura, AWS, seguridad, tests.
  - Ensayarla al menos una vez completa, con tiempo cronometrado.

**Definición de hecho:** ensayo final recorrido sin bugs bloqueantes, informe entregable y
presentación ensayada de punta a punta.

---

## 5. Plan visual de 2 semanas (2 tracks en paralelo)

> Track A = más backend (tests, lógica). Track B = más infra/QA. Ajustar según fortalezas.

```
        SEMANA 1                                  SEMANA 2
 Día │ Track A (backend)      │ Track B (infra/QA)   ║ Día │ Track A            │ Track B
 ────┼────────────────────────┼──────────────────────╫─────┼────────────────────┼──────────────────
  1  │ T2 cerrar SG + pass8    │ T5 logs + métricas    ║  6  │ T1 analytics/notif │ T4 QA roles/permisos
  2  │ T1 gateway (JWT/CORS)   │ T3 verificar MQ + dis.║  7  │ T1 BFF + front     │ T2 deps + sec-review
  3  │ T1 ms-users             │ T3 externalizar Rabbit║  8  │ T1 80% core + gate │ T6 runner + SMTP e2e
  4  │ T1 ms-tournament        │ T3 meter ms-etl e2e   ║  9  │ Deploy v1.0.0 AWS  │ T8 ensayo final front
  5  │ T1 ms-game              │ T5 alarmas+dashboard  ║ 10  │ T8 informe+present.│ Buffer/backups/cierre
```

**Hitos:**
- **Fin semana 1:** seguridad base cerrada, ETL integrado, observabilidad básica, core con tests avanzados.
- **Fin semana 2:** 80% en core con gate, v1.0.0 desplegado y verificado e2e en AWS, ensayo final
  hecho, informe y presentación listos.

**Innegociable para v1:** T1 (core 80% + gate), T2 y T8 (informe/presentación son parte de la nota).
**Muy deseable:** T3 y T5. **Si sobra:** T6. **Fuera:** T7.

---

## 6. Backlog de ideas (post-v1, no comprometidas)

Ideas que surgieron y vale la pena anotar, pero que **no entran en la ventana de 2 semanas**
(la capacidad ya está al límite). Se revisan después de entregar v1:

| Idea | Descripción | Notas |
|---|---|---|
| **Menú de configuración de usuario** | Pantalla de ajustes del usuario (preferencias de cuenta, notificaciones, etc.) | Definir alcance antes de estimar; toca frontend + posiblemente `ms-users` |
| **Temas de tablero** | Panel donde el usuario elige entre varios temas visuales para los tableros | Mayormente frontend (CSS/assets); la preferencia podría persistirse en el perfil |

> Si alguna se quiere adelantar, hay que **sacar algo de P2 a cambio** — no se suma alcance gratis.

---

## 7. Riesgos y cosas a vigilar
- **80% parejo es ambicioso** con todo lo demás → acordar el alcance del % con el profesor (recomendado: core obligatorio, resto best-effort).
- **ETL depende de servicios que Academy puede bloquear** (Amazon MQ/ElastiCache) → **verificar día 2**; tener el plan C listo.
- **Credenciales Academy caducan cada ~4 h** → fricción constante; ya contemplado en la capacidad.
- **Deploy final v1** debe re-probar el flujo en vivo de torneo (migraciones Flyway nuevas).
- **Capacidad ajustada** (~16 días-dev vs ~20–22 de trabajo) → si algo se atrasa, recortar primero
  alcance de T6, luego de T5. **T8 no se recorta** (informe/presentación son parte de la nota).
- **No dejar el ensayo final (T8) para el último día** → hacerlo con 1–2 días de margen para poder
  arreglar lo que aparezca.

## 8. Referencias
- `docs/DESPLIEGUE_REPLICA_AWS.md` — operación de la réplica de Martin (§1.1 Security Groups).
- `docs/SECURITY_AUDIT_REPORT.md`, `SECURITY_PLAN.md` — base de T2.
- `docs/PLAN_TESTS_JWT_FILTER.md`, `PLAN_TESTS_ORGANIZER_TOURNAMENTS.md`, `PRUEBAS.md` — base de T1.
- `docs/SELF_HOSTED_RUNNER.md` — T6. · `docs/PENDIENTE_HTTPS.md` — T7 diferido.
