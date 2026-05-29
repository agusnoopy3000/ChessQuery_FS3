# ChessQuery — Despliegue AWS Academy (ECS Fargate + GitHub Actions)

> **Contexto.** Cuenta AWS Academy con ~USD 50 de crédito. Tope estricto:
> hay que aprovechar free-tier, Fargate Spot y servicios compartidos. El
> requisito académico es **ECS o EKS** con **CI/CD por GitHub Actions**.
>
> EKS añade ~USD 72/mes solo por el control plane → fuera de presupuesto.
> **Decisión: ECS Fargate (Spot cuando se pueda).** Sigue siendo DevOps
> real (task definitions, service rollouts, ALB) pero cabe en 50 USD.

---

## 1. Decisión arquitectónica — por qué Fargate (y no EC2 ni EKS)

| Opción | Costo aprox./mes | Devops real | Veredicto |
| --- | --- | --- | --- |
| **EKS** (control plane + 1 node t3.small) | ≈ USD 75 + node | ✅ | ❌ no entra en USD 50 |
| **EC2 + docker-compose** | ≈ USD 30 (t3.medium) | ⚠️ (SSH-driven) | OK barato pero el examinador puede leerlo como "no es orquestador" |
| **ECS Fargate Spot** (Recomendado) | ≈ USD 25-35 | ✅ task defs + ALB | ✅ cumple "ECS o EKS" sin gastar de más |
| **Lightsail Containers** | ≈ USD 10-40 | ⚠️ (UI-driven) | abstrae demasiado, no muestra DevOps |

### Optimizaciones aplicadas al presupuesto

1. **Fargate Spot** para tasks no críticas (~70% descuento). El api-gateway
   queda en Fargate normal (más estable para el ALB).
2. **Una sola RDS PostgreSQL** `db.t3.micro` Single-AZ con varias bases
   lógicas (`users_db`, `game_db`, `tournament_db`, etc.). Free tier los
   primeros 12 meses; ojo: AWS Academy a veces NO da free tier — usar
   instancia `db.t3.micro` igualmente (~USD 13/mes).
3. **RabbitMQ como un task más en Fargate**, no Amazon MQ (Amazon MQ
   mínimo ~USD 20/mes). Persistencia en EFS opcional o aceptar pérdida
   de mensajes en reinicios (mensajería es idempotente en ChessQuery
   gracias a `processed_event`).
4. **Sin NAT Gateway** (caro: ~USD 32/mes). Tasks Fargate corren en
   **subnets públicas** con IP pública asignada, restringidas por SG.
5. **ALB único** (~USD 17/mes) frente a api-gateway. Frontends servidos
   por **S3 + CloudFront** (centavos).
6. **Reducir scope para demo:** sólo desplegamos `api-gateway`, `ms-users`,
   `ms-game`, `ms-tournament`, `ms-notifications`, `rabbitmq`. Los
   restantes (`ms-analytics`, `ms-etl`, BFFs) se pueden levantar solo
   bajo demanda durante la evaluación o quedar fuera del cluster cloud.

### Cuadro de costos final (us-east-1)

| Recurso | Spec | USD/mes |
| --- | --- | --- |
| ECS Fargate (6 tasks × 0.25 vCPU / 0.5 GB, mix Spot/normal) | promedio 4h/día encendido | ~10 |
| ECS Fargate (1 task RabbitMQ 0.5 vCPU / 1 GB) | always-on | ~6 |
| Application Load Balancer | always-on | 17 |
| RDS PostgreSQL `db.t3.micro` Single-AZ 20 GB gp3 | always-on | 13 |
| ECR | 5 GB | ~0.50 |
| S3 + CloudFront | <1 GB egress | <1 |
| Secrets Manager | 5 secrets | 2 |
| **TOTAL** |  | **~49 USD** |

> Bajadas extra disponibles si aprieta el budget:
> - Apagar el cluster fuera de horario de demo (script `aws ecs update-service --desired-count 0`) → -50% Fargate.
> - Cambiar ALB por un Network Load Balancer si solo expones api-gateway sin TLS termination (más barato pero pierde routing por path).

---

## 2. Topología

```
                       ┌──────────────────────────────┐
                       │   CloudFront (frontends)     │
                       │   chess-portal + organizer   │
                       └──────────┬───────────────────┘
                                  │ ALL /  →
                                  │
                       ┌──────────▼───────────────────┐
                       │   ALB (api.chessquery.cl)    │
                       │   Listener :443              │
                       └──────────┬───────────────────┘
                                  │ /api/* → target group
              ┌───────────────────┴─────────────────┐
              │                                     │
        ┌─────▼─────┐ ┌──────────┐ ┌──────────┐ ┌──▼───────┐
        │api-gateway│ │ ms-users │ │ ms-game  │ │ rabbitmq │
        │  Fargate  │ │  Fargate │ │ Fargate  │ │ Fargate  │
        └─────┬─────┘ └─────┬────┘ └────┬─────┘ └────┬─────┘
              │             │           │            │
              └─────────────┴───────────┴────────────┘
                            ▼
                  ┌──────────────────────┐
                  │  RDS Postgres        │
                  │  (multi-DB lógicas)  │
                  └──────────────────────┘

  Supabase Auth/Storage (externo, cloud free tier) ←── api-gateway, ms-game
```

### VPC y subnets
- `chessquery-vpc` 10.0.0.0/16
- 2 subnets públicas en us-east-1a / us-east-1b (10.0.1.0/24, 10.0.2.0/24)
- IGW + route table pública
- **Sin NAT.** Tasks Fargate corren en públicas con `assignPublicIp: ENABLED`.
- Security Groups:
  - `sg-alb` → ingress 80/443 desde 0.0.0.0/0.
  - `sg-services` → ingress desde `sg-alb` en el puerto del container; egress all.
  - `sg-rds` → ingress 5432 desde `sg-services` solamente.

---

## 3. Inventario de recursos AWS a crear

```bash
# Aprovisionar (una sola vez por cuenta Academy)
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=us-east-1

# 1. ECR — ya hay script
./infrastructure/scripts/setup-ecr.sh

# 2. VPC + subnets + SG (manual desde consola; AWS Academy a veces bloquea CloudFormation)
#    o usar: ./infrastructure/aws/scripts/bootstrap-network.sh (creado abajo)

# 3. RDS (consola → Postgres 15, db.t3.micro, multi-DB se crea por migración)
#    Apuntar al secret de Secrets Manager para password.

# 4. ECS cluster
aws ecs create-cluster --cluster-name chessquery --region us-east-1 \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=1

# 5. ALB + target groups (uno por servicio que expone HTTP)
#    Consola → Application Load Balancer + listener :80 + rules por path.

# 6. Task definitions y services (idempotente vía GitHub Actions)
```

### IAM
- **TaskExecutionRole**: `arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy` para pull desde ECR y push de logs a CloudWatch.
- **TaskRole** (por servicio si necesita acceso a recursos AWS — opcional inicial).
- **GitHubActionsRole**: federado vía OIDC (`token.actions.githubusercontent.com`) con permisos ECR push + `ecs:UpdateService`, `ecs:RegisterTaskDefinition`.

---

## 4. Pipeline CI/CD GitHub Actions

Tres workflows en `.github/workflows/`:

1. **`ci.yml`** — corre en cada push y PR. Compila + tests Java/TS, no toca AWS.
2. **`build-and-push.yml`** — al merge a `main`. Por servicio:
   build Docker → push a ECR con tag `sha-<7chars>` y `latest`.
3. **`deploy.yml`** — manual (workflow_dispatch) o tras `build-and-push`.
   Actualiza task definitions y dispara rollout en ECS.

### Autenticación: OIDC en lugar de access keys

```yaml
permissions:
  id-token: write    # asumir rol vía OIDC
  contents: read

- uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/GitHubActionsChessQuery
    aws-region: us-east-1
```

> En AWS Academy: si no podés crear el OIDC provider (a veces está
> restringido), fallback a Access Keys del usuario IAM `vocstartsoft`
> guardadas como `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` en Repo
> Secrets. Es menos seguro pero compatible con Academy.

### Estrategia de tags

| Tag | Cuándo | Para qué |
| --- | --- | --- |
| `sha-abc1234` | Cada build | Trazabilidad commit↔imagen, rollback |
| `latest` | Cada build en main | Pull de demos |
| `v1.0.0` | Manual al publicar release | Demo final, snapshots |

### Matrix de servicios

Cada microservicio se buildea en paralelo con `strategy.matrix`. Si un
build falla los demás siguen (`fail-fast: false`) — útil cuando 1 MS
está roto pero querés ver si los otros pasan.

---

## 5. Esqueleto de archivos en este repo

```
.github/workflows/
├── ci.yml               # build + tests, cada push
├── build-and-push.yml   # docker build + push ECR al merge a main
└── deploy.yml           # ecs deploy (manual o tras build-and-push)

infrastructure/aws/
├── DEPLOY_ECS.md        # este documento
├── task-definitions/
│   ├── api-gateway.template.json
│   ├── ms-users.template.json
│   ├── ms-game.template.json
│   ├── ms-tournament.template.json
│   ├── ms-notifications.template.json
│   └── rabbitmq.template.json
└── scripts/
    └── bootstrap-network.sh   # VPC + subnets + SG (opcional)
```

Los templates usan placeholders `${ECR_REGISTRY}`, `${IMAGE_TAG}`,
`${DB_HOST}`, `${RABBITMQ_HOST}` que el workflow reemplaza con `envsubst`
antes de `aws ecs register-task-definition`.

---

## 6. Plan de ejecución día por día (sugerido)

| Día | Tarea | Resultado |
| --- | --- | --- |
| **D-1** | Crear VPC/subnets/SG en consola AWS Academy | red lista |
| **D-1** | Crear cluster ECS `chessquery` + ALB vacío | infra base |
| **D-1** | Crear RDS `db.t3.micro` + secret en Secrets Manager | BD lista |
| **D-2** | Correr `setup-ecr.sh` para crear 6-7 repos | ECR pobl. |
| **D-2** | Cargar repo en GitHub, configurar Secrets (`AWS_ACCOUNT_ID`, `AWS_REGION`, opcional `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` o `GH_OIDC_ROLE_ARN`) | secrets listos |
| **D-2** | Trigger manual `build-and-push.yml` | imágenes en ECR |
| **D-3** | `deploy.yml` por primera vez (crea services) | tasks corriendo |
| **D-3** | Apuntar target groups del ALB a los services | tráfico HTTP OK |
| **D-3** | Build de frontends y subir a S3 + invalidar CloudFront | UI accesible |
| **D-4** | Probar flujo E2E desde Internet | demo lista |

---

## 7. Riesgos conocidos y mitigación

| Riesgo | Mitigación |
| --- | --- |
| AWS Academy expira la sesión cada 4h | Script para arrancar/parar el cluster antes de cada sesión |
| RabbitMQ en Fargate sin EFS pierde colas al reiniciar | Aceptable: consumidores ya son idempotentes |
| 0.5 GB RAM puede no aguantar JVM Spring Boot | Si OOMKilled, subir a 1 GB (duplica costo del task) |
| Sin NAT, los tasks necesitan IP pública | SG restringe ingress; egress queda abierto pero el container no expone nada extra |
| Free tier RDS no disponible en Academy | Aceptar ~USD 13/mes |
| El examinador pide HTTPS termination | Habilitar ACM cert gratuito + listener 443 en el ALB (sin costo extra) |

---

## 8. Checklist antes del primer deploy real

- [ ] AWS Academy session activa con créditos > USD 30
- [ ] AWS CLI configurado (`aws sts get-caller-identity` responde)
- [ ] Secrets en GitHub:
      - `AWS_ACCOUNT_ID`
      - `AWS_REGION` (us-east-1)
      - `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` **o** `GH_OIDC_ROLE_ARN`
      - `DB_PASSWORD`, `JWT_SECRET`, `SUPABASE_SERVICE_KEY`, `SUPABASE_WEBHOOK_SECRET`
- [ ] VPC + subnets + SG creados
- [ ] Cluster ECS `chessquery` creado
- [ ] ECR repos creados (`setup-ecr.sh`)
- [ ] RDS endpoint disponible y migraciones aplicadas (Flyway en cada MS lo hace al arrancar)
- [ ] ALB + target groups creados, listener apuntando a los target groups
