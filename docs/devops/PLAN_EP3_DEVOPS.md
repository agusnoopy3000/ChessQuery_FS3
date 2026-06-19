# Plan EP3 — Introducción a Herramientas DevOps (ISY1101)

> **Evaluación:** EP3 — Encargo con Presentación · **40%** de la asignatura.
> **Caso usado:** **ChessQuery** como entorno productivo en la nube.
> **Dupla:** Martin + compañera de la EP3 (NO Agustín; el equipo de ChessQuery es aparte).
> **Presentación:** individual.
> **Demo en vivo:** **100% sobre la cuenta AWS propia de Martin** (`876204681432`), que ya
> tiene el stack completo desplegado y verificado (`docs/DESPLIEGUE_REPLICA_AWS.md`).
> **GitHub:** se trabaja en un **repo que Martin controla** (fork/copia con admin), no en el
> de Agustín → así puede setear sus secrets, runners y correr el deploy contra SU AWS.
> **Fecha base de este plan:** 2026-06-19.
> **Decisión frontend (IE2):** se **justifica S3** como hosting del front (mínimo costo, no
> gasta slot de la task); el front es público vía S3 y habla al back por el ALB.

---

## 0. Idea central

La EP3 pide montar un entorno productivo en la nube con **orquestación de contenedores
(ECS/EKS)** + **CI/CD con GitHub Actions** + **observabilidad**. ChessQuery **ya tiene
todo esto montado** (ECS Fargate, ECR, ALB, RDS, CloudWatch, autoscaling, 3 workflows de
Actions). El trabajo de la EP3 no es construir de cero, sino: **(a)** ajustar 3–4 detalles
para cubrir la rúbrica al 100%, **(b)** dejar el runner del CI online para mostrar el deploy
automático, y **(c)** armar el informe + la presentación.

---

## 1. Qué pide la EP3 (rúbrica con pesos)

| IE | Indicador | Peso | ChessQuery lo cubre con… |
|----|-----------|------|--------------------------|
| **IE1** | Configuración del clúster AWS (ECS/EKS): VPC, subredes, SG, roles IAM, arquitectura justificada | **25%** | Cluster `chessquery-cluster` (Fargate), VPC default, SG ALB→task, execution/task role (LabRole) |
| **IE2** | Despliegue Frontend + Backend desde ECR: task def, env vars, ALB, URL pública, Front→Back | **25%** | 9 imágenes en ECR, task `chessquery-stack` (10 contenedores) tras ALB; frontends en S3 |
| **IE3** | Autoscaling: target tracking CPU/Mem, umbral justificado, evidencia | **10%** | `chessquery-etl` con Application Auto Scaling (1–2) en `deploy-etl-service.sh` |
| **IE4** | Pipeline CI/CD (build → push → deploy) automatizado, seguro, documentado | **15%** | `ci.yml` + `build-and-push.yml` + `deploy.yml` + `CICD_PIPELINE.md` |
| **IE5** | Gestión de Secrets y credenciales: seguros, sin exposición | **5%** | AWS Secrets Manager (ARNs) + GitHub Actions Secrets; `.env` gitignored + gitleaks |
| **IE6** | Análisis de logs, métricas y tiempos del pipeline | **10%** | CloudWatch (5 alarmas + dashboard) + tiempos de jobs en Actions |
| **IE7** | Validación funcional del clúster (Front → Back) | **10%** | e2e verificado: registro → webhook → ALB → ms-users → RDS |

> **Nota de cálculo:** la nota = **20% encargo (parejas)** + **80% presentación (individual)**.
> O sea: aunque el encargo esté impecable, **la presentación pesa 4×**. Hay que ensayarla.

---

## 2. Decisiones de arquitectura (esto se defiende en la presentación)

### 2.1 ¿ECS o EKS? → **ECS Fargate**
- **AWS Academy** limita fuerte (sin EKS cómodo, 10 contenedores/task, créditos acotados).
- **Fargate = serverless de contenedores:** no administramos nodos EC2 ni los pagamos
  ociosos → **mínimo costo** y menos superficie de operación.
- EKS (Kubernetes) sería sobre-ingeniería para el tamaño del proyecto y más caro (control
  plane + nodos). **ECS cubre escalado, alta disponibilidad y autorrecuperación** sin esa carga.

### 2.2 ¿Por qué GitHub Actions (y no Jenkins/GitLab CI)?
> **Esto lo pediste explícito — "herramientas de Actions, por qué se tomó ese camino".**
- **Nativo al repo:** el código ya vive en GitHub → cero infra extra de CI; el pipeline
  vive versionado junto al código (`.github/workflows/`).
- **Self-hosted runners gratis:** corremos el CI en nuestras máquinas → **sin costo de
  minutos** y con cache local (Maven/npm/Docker) → builds rápidos.
- **Marketplace:** acciones oficiales listas (`configure-aws-credentials`, `amazon-ecr-login`,
  `trivy-action`, `build-push-action`) → menos YAML propenso a errores.
- **Secrets/Variables integrados** + soporte **OIDC** para AWS (sin llaves de larga vida).
- vs **Jenkins:** habría que hostear y mantener un servidor. vs **GitLab CI:** el repo no
  está en GitLab. Actions es la opción de **menor fricción y costo** para este caso.

### 2.3 Mínimo costo (decisiones)
- **Fargate** (sin EC2 ocioso) + **task sizes mínimos** (CPU/mem justos).
- **Apagar cuando no se usa:** ECS `desired-count 0` + RDS `stop-db-instance` (rutina en
  `DESPLIEGUE_REPLICA_AWS.md §2`). Se prende solo para demo/CI.
- **Sin NAT Gateway** (caro, ~USD 32/mes): las tasks van en subred pública con
  `assignPublicIp=ENABLED` (necesario para bajar imágenes de ECR) **pero bloqueadas por SG**
  (ver 2.4). Alternativa más cara descartada: VPC endpoints/NAT.
- **Frontends en S3** (static website) en vez de un contenedor extra → **gratis/centavos** y
  no consume uno de los 10 slots de la task.
- **CloudWatch** con retención corta (14 días) para no acumular costo de logs.

### 2.4 ⚠️ "Sin acceso a ninguna parte de la VPC" (requisito tuyo, clave para IE1/IE5)
El **único ingreso público** es el **ALB (puerto 80)** y el **sitio S3**. Todo lo demás de
la VPC es inalcanzable desde internet, garantizado por **Security Groups**, no por suerte:

```
Internet ──80──► [ALB]  (SG_ALB: ingress 0.0.0.0/0:80)
                   │
                   └──► [ECS task]  (SG_TASK: ingress SOLO desde SG_ALB:8080)   ← no 0.0.0.0/0
                          │
                          └──► [RDS Postgres]  (SG_RDS: ingress SOLO desde SG_TASK:5432, Publicly Accessible = NO)
                          └──► RabbitMQ / Redis  (viven en localhost de la task, jamás expuestos)
```
- **SG del gateway: 8080 NO abierto a `0.0.0.0/0`** (se cerró en T2 — antes se podía saltar
  el ALB; hoy solo entra tráfico del ALB).
- **RDS:** `Publicly Accessible = NO`, SG solo acepta del SG de las tasks.
- **RabbitMQ/Redis:** contenedores internos de la task (localhost), nunca con puerto público.
- Resultado: **un atacante solo ve el ALB**; ni la app, ni la base, ni la mensajería son
  alcanzables directo. Este es el punto fuerte de seguridad para la defensa.

> Para la **demo** podés acotar aún más: SG del ALB ingress solo a **tu IP** (`x.x.x.x/32`)
> mientras presentás, y abrirlo a `0.0.0.0/0` solo si necesitás mostrarlo desde otra red.

---

## 3. Infraestructura propuesta (para el diagrama)

> **El diagrama debe mostrarlo TODO** (lo pediste). Base: `docs/ARQUITECTURA_AWS.drawio` y
> `docs/DIAGRAMA_CICD_OPERACION.drawio`. Acá la versión en texto para el informe:

```
        Desarrollador
            │ git push / PR
            ▼
   ┌──────────────── GitHub ────────────────┐
   │  Actions:                               │
   │   ci.yml (auto)  → tests+cobertura+Trivy│
   │   build-and-push.yml (manual) → ECR     │
   │   deploy.yml (manual) → ECS + S3        │
   │  Runners self-hosted (pool: Agus+Martin)│
   │  Secrets (AWS rotativas + ARNs)         │
   └───────────────┬─────────────────────────┘
                   │ creds AWS (OIDC / access keys)
                   ▼
   ☁️ AWS us-east-1  (cuenta 876204681432)
   ┌─────────────────────────────────────────────────────────┐
   │ 👁️ CloudWatch: logs + Container Insights + 5 alarmas +   │
   │     dashboard "ChessQuery-Replica"                        │
   │ ───────────────────────────────────────────────────────  │
   │  [ECR] 9 repos ──► imágenes :sha-XXXX / :latest          │
   │                                                           │
   │  Internet─80─►[ALB]─►[ECS Fargate · chessquery-stack]     │
   │                        ├ api-gateway (8080)               │
   │                        ├ ms-users / ms-tournament / ms-game│
   │                        ├ ms-analytics / ms-notifications  │
   │                        ├ bff-player / bff-organizer       │
   │                        └ rabbitmq + redis                 │
   │                      [ECS · chessquery-etl] ◄ Auto Scaling│
   │                        └ ms-etl (target tracking 1–2)     │
   │                                                           │
   │  [RDS Postgres] (privado, SG solo desde task)            │
   │  [S3] chess-portal + organizer-panel (static website)    │
   └─────────────────────────────────────────────────────────┘
            ▲ URL pública: ALB (API) + S3 (frontends)
```

**Roles IAM (IE1):** `LabRole` de Academy cumple execution role (pull de ECR, leer Secrets
Manager, escribir logs) y task role. Documentar qué permisos usa cada uno.

---

## 4. Secretos — qué se guarda y dónde (IE5)

> Pediste **"mostrar qué secretos se guardan en cada repositorio/lugar"**. Dos planos:

### 4.1 AWS Secrets Manager (runtime — los consume la task vía execution role)
| Secret | Contenido |
|--------|-----------|
| `chessquery/db-password` | password de RDS Postgres |
| `chessquery/jwt-secret` | JWT secret real de Supabase (valida tokens) |
| `chessquery/supabase-webhook-secret` | secreto del webhook de alta de usuarios |
| `chessquery/supabase-service-key` | service role key de Supabase |
| `chessquery/rabbitmq-password` | password de RabbitMQ |
| `chessquery/smtp-password` | App Password de Gmail (correos) |

→ La task def referencia el **ARN**, no el valor. El secreto **nunca** está en el repo ni en
la imagen; se inyecta como variable de entorno en runtime.

### 4.2 GitHub Actions Secrets/Variables (pipeline — los usa el CD)
- **AWS (rotan cada ~4 h):** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
  `AWS_SESSION_TOKEN`, `AWS_REGION`, `AWS_ACCOUNT_ID`.
- **ARNs + config del stack:** `TASK_EXECUTION_ROLE_ARN`, `DB_HOST`, `*_ARN`,
  `GATEWAY_CORS_ALLOWED_ORIGINS`, `MAIL_FROM`, etc. (lista completa en `CICD_PIPELINE.md §Secrets`).
- **Frontends:** `VITE_*`, `S3_BUCKET_*`.
- **Variable:** `RUNNER_LABEL` (elegir runner).

### 4.3 Higiene de secretos (lo que se dice en la defensa)
- `.env.production` / `.deploy-outputs.env` **gitignored** (verificable).
- **gitleaks** / secret scanning para confirmar que nada se filtró al historial.
- Las credenciales de Academy **rotan** → ventaja de seguridad (no hay llaves eternas).
- Nada de secretos en logs (los `*_ARN` se resuelven en runtime, no se imprimen).

---

## 5. Configuración de los repositorios (IE4) — "qué config se utiliza"

- **3 workflows** en `.github/workflows/`:
  - `ci.yml` — **automático** en push/PR. Tests Java×6 (gate JaCoCo 90%), front×2, BFF×3,
    **Trivy fs** (gate HIGH/CRITICAL). No toca AWS → siempre corre.
  - `build-and-push.yml` — **manual**. Build 9 imágenes + cache `type=gha` + **Trivy image
    scan** → push a ECR.
  - `deploy.yml` — **manual, con toggles** (`deploy_stack`/`deploy_frontend`/`deploy_etl`):
    stack ECS + sync S3 + ms-etl con autoscaling + smoke test del readiness.
- **¿Por qué el CD es manual?** Las creds Academy rotan cada ~4 h; en push automático
  fallaría en rojo en cada rotación. Se refrescan los secrets y se corre con un clic.
- **Runners:** `runs-on: ${{ vars.RUNNER_LABEL || 'self-hosted' }}` → **pool self-hosted**
  (Agustín + Martin) para redundancia; o `ubuntu-latest` si se setea la variable.
- **Optimizaciones (sirve para IE6):** `paths-ignore` (docs/drawio/tools), cache Maven/npm/
  Docker, `ignore-unfixed` en Trivy, deploy por partes, `.dockerignore`.
- **Commits explicativos** (lo exige el formato): seguimos `tipo(scope): mensaje` con el
  porqué del cambio.

---

## 6. CloudWatch — cómo se obtienen las métricas (IE6) — "lo pediste explícito"

- **Logs:** driver `awslogs` por contenedor → un **log group por servicio**
  (`/ecs/chessquery/stack`, `/ecs/chessquery/ms-etl`), retención 14 días.
- **Métricas de infra:** **Container Insights** activado en el cluster → CPU/Mem por task y
  service, automáticamente.
- **Alarmas (5):** `task-down` (`runningCount<1`), `cpu-high`, `mem-high`, `rds-storage-low`,
  `alb-5xx`.
- **Dashboard:** `ChessQuery-Replica` — una sola pantalla con el estado del sistema (es la
  **"página en la nube con la métrica"** que mostrás en vivo).
- **Métricas del pipeline (para IE6):** tiempos de cada job y tasa de fallos desde la pestaña
  **Actions** (y `gh run list`), + los logs de deploy. Comparar build con/sin cache.
- Detalle completo: `docs/CLOUDWATCH_REPLICA.md`.

---

## 7. Autoscaling (IE3)

- **Hoy:** `chessquery-etl` usa **Application Auto Scaling — target tracking** (min 1, max 2)
  en `scripts/deploy-etl-service.sh`.
- **Justificación del umbral:** definir y documentar (ej. **CPU 50%** → escala antes de
  saturar, baja cuando afloja; equilibrio entre respuesta y costo).
- **Evidencia (lo pide la rúbrica):** **simulación de carga** (ej. `hey`/`ab` o un script que
  golpee un endpoint) → mostrar en CloudWatch cómo sube CPU y ECS agrega una task, y cómo
  baja después. Capturas para el informe.
- **GAP a decidir:** ¿agregamos target tracking también al **stack principal**? Suma puntos
  de IE3 pero hay que cuidar el límite de 10 contenedores/costo. Ver §9.

---

## 8. Plan de presentación (10–15 min, individual)

> 80% de la nota. Estructura sugerida (cronometrar):

1. **(1 min) Contexto** — qué es ChessQuery (plataforma de torneos) y qué resuelve la EP3.
2. **(3 min) Arquitectura** — mostrar el diagrama (todo: GitHub→ECR→ECS→ALB→RDS/S3+CloudWatch).
   Roles IAM, redes, **el punto de "nada de la VPC es accesible salvo el ALB"**, balanceador.
3. **(3 min) GitHub con Actions** — abrir el repo, mostrar los 3 workflows, **disparar/mostrar
   un run** (CI verde tras un commit; o el build→push→deploy). Explicar por qué Actions.
4. **(3 min) Página en la nube con la métrica** — abrir el **dashboard CloudWatch
   `ChessQuery-Replica`** en vivo; mostrar logs y una alarma. Mostrar autoscaling (si hay carga).
5. **(2 min) Validación Front→Back** — abrir el sitio S3, hacer una acción real (registro/login)
   → mostrar que llega al backend (logs) y a RDS. URL pública del frontend.
6. **(2 min) Mejora continua** — métricas del pipeline (tiempos, cache, fallos), problemas
   encontrados (runner offline, creds que rotan, IP del ETL, gate Trivy vs Spring) y cómo se
   resolvieron.

**Tip:** tener todo **prendido y probado ~1 h antes** (créditos Academy rotan; el ETL hornea
la IP privada → re-correr `deploy-etl-service.sh` tras encender).

---

## 9. GAPS / cosas a preparar antes de presentar

| # | Gap | Acción | Bloqueado por |
|---|-----|--------|---------------|
| 1 | **Pipeline corriendo contra TU AWS** (IE4) | Trabajar en un **repo propio** (fork con admin): setear tus secrets (ARNs de tu `.deploy-outputs.env` + creds Academy) y correr build→push→deploy a tu cluster | Nada externo — lo controlás vos |
| 2 | **Runner** | En tu repo: usar **runners GitHub-hosted gratis** (`RUNNER_LABEL=ubuntu-latest`, 2000 min/mes) **o** registrar tu self-hosted (sos admin de tu fork) | — (ya no dependés del repo de Agustín) |
| 3 | **Autoscaling sin evidencia** (IE3) | Script de **simulación de carga** (`hey`/`ab`) + capturas CloudWatch del escalado de `ms-etl` | Stack encendido |
| 4 | **Umbral de autoscaling sin justificar** | Documentar el valor (ej. 50% CPU) y el porqué | — |
| 5 | **Informe** (se entrega ANTES de presentar) | Armarlo reusando estos docs (no escribir de cero) | — |
| 6 | **Capturas/evidencia** para el informe | Screenshots: cluster, task def, SG (mostrar que no hay `0.0.0.0/0` salvo ALB), ECR, Actions run, dashboard | Stack encendido |
| 7 | **README de los repos** (lo exige el formato) | README explicando funcionamiento + cómo usar; commits explicativos | — |
| 8 | **Gate Trivy puede bloquear el deploy** (CVEs Spring/H-06) | Verificar en el 1er run; si bloquea, `ignore-unfixed`→modo reporte temporal | Pipeline corriendo |

---

## 9.bis Roadmap resumido (pendiente, en orden simple)

**0. Esperando** → que **Agustín dé Admin** del repo a Martin. *(en curso, bloquea lo demás)*

Con Admin:
1. **Prender el runner** — registrar el runner (o usar hosted gratis). El CI deja de quedar en cola y corre de verdad.
2. **Cargar secrets** — ARNs + creds de **tu AWS** en el repo → el deploy apunta a tu cuenta.
3. **Correr el pipeline completo** — un commit dispara build → push a ECR → deploy a tu cluster. *(cierra IE4 + grueso de T6)*
4. **Probar el autoscaling** — meter carga a un endpoint y capturar en CloudWatch cómo `ms-etl` escala 1→2 y vuelve; justificar el umbral. *(IE3 — ver §7)*
5. **Analizar tiempos del pipeline** — duración de builds, efecto del cache, fallos → conclusiones. *(IE6)*
6. **Sacar capturas** — cluster, SGs (nada público salvo ALB), ECR, dashboard, run de Actions. *(evidencia)*
7. **Escribir el informe** — reusar docs, no de cero (esqueleto en `INFORME_EP3.md`). Se entrega **antes** de presentar.
8. **README + commits claros** — lo pide el formato.
9. **Ensayar la presentación** cronometrada (10–15 min). **80% de la nota.**

> **Pasos 1–3 también cierran el grueso del T6** del roadmap v1. Lo único que quedaría
> fuera de T6: **backups RDS** y **correos e2e** (esto último lo ve Martin con Agustín).

---

## 10. Cronograma

> Hoy: **2026-06-19** (viernes). Hoy también es cierre de jornada del producto v1.

- **Semana del 22-jun:** ya **se puede presentar** la EP3. Ventana para encender la réplica,
  dejar el runner online, sacar evidencia y ensayar.
- **Semana del 29-jun:** presentaciones de **solo Experiencia 3 + examen**.
- **El informe se entrega ANTES de la presentación** (por AVA, junto con los repos).

**Ruta crítica sugerida:** (1) Agustín te da Admin → runner online · (2) encender réplica,
sacar evidencia + simular carga para autoscaling · (3) armar informe con estos docs ·
(4) ensayar la presentación cronometrada.

---

## 11. Relación con el producto v1

Esta EP3 **reutiliza la misma infra** del producto v1 de ChessQuery (no es trabajo paralelo):
el cluster, el CI/CD (T6), CloudWatch (T5) y el hardening (T2) son exactamente los entregables
del roadmap. Ver `../ROADMAP_V1.md`. Lo que la EP3 agrega es el **enfoque DevOps explícito**
(orquestación + automatización) y la **evidencia/presentación** para la rúbrica.

> Referencias: `../CICD_PIPELINE.md`, `../SELF_HOSTED_RUNNER.md`, `../CLOUDWATCH_REPLICA.md`,
> `../DESPLIEGUE_REPLICA_AWS.md`, `../ARQUITECTURA_AWS.drawio`, `../DIAGRAMA_CICD_OPERACION.drawio`.
