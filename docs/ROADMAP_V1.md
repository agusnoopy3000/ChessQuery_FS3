# ChessQuery — Roadmap a Producto Final (v1)

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

---

## 1. ¿Qué convierte una demo en "producto v1"?

No es una sola cosa; son **6 pilares**. Un producto v1 razonable necesita estar "verde" en los
primeros cuatro, y al menos iniciado en los otros dos:

| Pilar | Pregunta que responde | Tarea(s) en este roadmap |
|---|---|---|
| 🧪 **Calidad** | "¿cómo sé que no rompí nada al cambiar código?" | T1 (tests 80% + gate) |
| 🔒 **Seguridad** | "¿puede un tercero entrar/abusar?" | T2 (hardening + auditoría) |
| 🧩 **Funcionalidad completa** | "¿está TODO el producto, no un recorte?" | T3 (ETL), T4 (QA paneles) |
| 👁️ **Observabilidad** | "¿me entero cuándo y por qué falla?" | T5 (CloudWatch) |
| ⚙️ **Operación / CI-CD** | "¿desplegar es repetible y sin manos?" | T6 (runner, SMTP, backups) |
| 🔐 **Acceso seguro (HTTPS)** | "¿el tráfico va cifrado?" | T7 (diferido por el profesor) |

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

### Resumen ejecutivo

| # | Tarea | Prioridad | Esfuerzo (días-dev) | Estado |
|---|---|---|---|---|
| T1 | Pruebas unitarias → **80% cobertura** + gate en CI | **P0** | 6–8 | ⬜ |
| T2 | **Pruebas de seguridad** + hardening | **P0** | 3 | ⬜ |
| T3 | **Integrar `ms-etl`** en AWS (límite 10 contenedores) | **P1** | 3 | ⬜ |
| T4 | **QA funcional de paneles de organizador** | **P1** | 2 | ⬜ |
| T5 | **CloudWatch** (logs + métricas + alarmas + dashboard) | **P2** | 2 | ⬜ |
| T6 | CI/CD real (runner) + SMTP real + backups + limpieza | **P2** | 2 | ⬜ |
| T7 | HTTPS | **P3 (diferido)** | 1–4 | ⏸️ |

> P0–P2 suman ~18–20 días-dev sobre ~16 disponibles ⇒ **hay que paralelizar y recortar alcance**
> del 80% (ver §"Riesgos"). El plan de §5 reparte el trabajo en 2 tracks para que entre.

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
        🔐 T7 HTTPS: diferido
```

---

## 4. Detalle de cada tarea (qué / por qué / cómo)

### 🧪 T1 · Pruebas unitarias al 80% de cobertura  ·  P0  ·  6–8 días-dev
**Qué es:** llevar la cobertura de tests a ≥80% y **bloquear** en CI que un cambio la baje.

**Por qué es necesario para un producto:** sin tests, cada cambio es una apuesta. En una demo no
importa (no se vuelve a tocar); en un producto que sigue evolucionando, los tests son la **red de
seguridad** que evita romper lo que ya funcionaba. El 80% es el estándar de facto: alto para dar
confianza, sin caer en el costo desproporcionado del 100%.

**Estado base (verificado hoy):** JaCoCo **ya está configurado** en los 6 módulos Java y todos
tienen carpeta de tests; el toolchain (Java 17 + Maven) corre OK. Falta subir cobertura y poner el
gate. Base previa: `PLAN_TESTS_JWT_FILTER.md`, `PLAN_TESTS_ORGANIZER_TOURNAMENTS.md`, `PRUEBAS.md`.

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

**Por qué es necesario:** "producto" implica que terceros podrían usarlo/atacarlo. Un agujero
conocido sin cerrar es deuda inaceptable en v1 (y es lo primero que mira un evaluador de seguridad).

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
**Qué es:** sumar el microservicio `ms-etl` (quedó fuera de la demo) al despliegue.

**Por qué es necesario:** v1 debería ser el producto **completo**, no un recorte. `ms-etl` es parte
del diseño (procesa eventos hacia `etl_db` para analítica). Dejarlo afuera es entregar un producto
"con un módulo desconectado".

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

**Por qué es necesario:** los tests unitarios (T1) prueban piezas aisladas; el QA funcional prueba
que el **flujo completo** funciona para un usuario real. Es lo que el profesor va a "tocar".

**Qué probar:**
- Crear torneo → generar rondas/emparejamientos → **partidas en vivo** (tablero espectador) →
  resultado vuelve solo al pairing (`game.finished`).
- Invitación a partida (push in-app + link, sin alta indebida en Supabase).
- Registro → webhook → perfil en `ms-users`; recuperación de contraseña; accesibilidad de login.
- **Distintos organizadores / roles:** que un organizador no vea/edite lo de otro (permisos).

**Definición de hecho:** checklist de `PRUEBAS.md` recorrido en el entorno AWS; bugs registrados
y los críticos resueltos.

---

### 👁️ T5 · CloudWatch  ·  P2  ·  2 días-dev
**Qué es:** observabilidad — logs, métricas, alarmas y un dashboard del sistema.

**Por qué es necesario:** en producto, **cuando algo falla tenés que enterarte y poder diagnosticar
sin entrar a la task**. Hoy, si un contenedor se cae, nadie se entera hasta que un usuario reclama.

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

**Por qué es necesario:** hoy el build/deploy se hace **a mano desde local**. Un producto necesita
un pipeline repetible (cualquiera del equipo despliega igual) y datos respaldados.

**Acciones:**
- **Registrar runner self-hosted** (`docs/SELF_HOSTED_RUNNER.md`) → `ci.yml` → `build-and-push.yml`
  → `deploy.yml` corren solos.
- **SMTP real:** confirmar que `chessquery/smtp-password` tiene la App Password de Gmail real
  (si quedó placeholder, los correos de bienvenida/invitación fallan); probar envío e2e.
- **Backups RDS:** confirmar retención de backups automáticos (producto, no demo).
- Limpieza de ramas/usuarios de prueba; consolidar docs.

**Definición de hecho:** un push a `main` despliega solo; correos e2e OK; backups RDS confirmados.

---

### 🔐 T7 · HTTPS  ·  P3 (diferido)
Diferido por indicación del profesor. Plan completo en `docs/PENDIENTE_HTTPS.md`. Bloqueante real:
un **dominio propio**. Se retoma solo si cambia la prioridad.

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
  4  │ T1 ms-tournament        │ T3 meter ms-etl e2e   ║  9  │ Deploy v1.0.0 AWS  │ Verificación e2e
  5  │ T1 ms-game              │ T5 alarmas+dashboard  ║ 10  │ Buffer/bugs/docs   │ Buffer/backups/cierre
```

**Hitos:**
- **Fin semana 1:** seguridad base cerrada, ETL integrado, observabilidad básica, core con tests avanzados.
- **Fin semana 2:** 80% en core con gate, v1.0.0 desplegado y verificado e2e en AWS.

**Innegociable para v1:** T1 (core 80% + gate) y T2. **Muy deseable:** T3 y T5. **Si sobra:** T6.
**Fuera:** T7.

---

## 6. Riesgos / cosas a vigilar
- **80% parejo es ambicioso** con todo lo demás → acordar el alcance del % con el profesor (recomendado: core obligatorio, resto best-effort).
- **ETL depende de servicios que Academy puede bloquear** (Amazon MQ/ElastiCache) → **verificar día 2**; tener el plan C listo.
- **Credenciales Academy caducan cada ~4 h** → fricción constante; ya contemplado en la capacidad.
- **Deploy final v1** debe re-probar el flujo en vivo de torneo (migraciones Flyway nuevas).
- **Capacidad ajustada** (~16 días-dev vs ~18–20 de trabajo) → si algo se atrasa, recortar primero alcance de T6, luego de T5.

## 7. Referencias
- `docs/DESPLIEGUE_REPLICA_AWS.md` — operación de la réplica de Martin (§1.1 Security Groups).
- `docs/SECURITY_AUDIT_REPORT.md`, `SECURITY_PLAN.md` — base de T2.
- `docs/PLAN_TESTS_JWT_FILTER.md`, `PLAN_TESTS_ORGANIZER_TOURNAMENTS.md`, `PRUEBAS.md` — base de T1.
- `docs/SELF_HOSTED_RUNNER.md` — T6. · `docs/PENDIENTE_HTTPS.md` — T7 diferido.
