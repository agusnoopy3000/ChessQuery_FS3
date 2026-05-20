# Plan de Despliegue AWS + CI/CD con GitHub Actions y Docker Hub

> **Proyecto:** ChessQuery FS3 — Plataforma de microservicios para ajedrez competitivo.
> **Objetivo:** Llevar el stack completo (10 microservicios + gateway + BFFs + 2 frontends + RabbitMQ + Redis) desde Docker Compose local a un entorno AWS productivo con despliegue automatizado en cada `push` a `main`.
> **Audiencia:** Equipo del ramo DSY1106 (DuocUC).
> **Fecha:** 2026-05-20.

---

## 1. Resumen ejecutivo

Vamos a montar ChessQuery en AWS usando la **Opción A** del [`PLAN_MIGRACION_CLOUD.md`](./PLAN_MIGRACION_CLOUD.md) (una EC2 con Docker Compose), pero la **profesionalizamos** con:

- VPC propia con subnets públicas y privadas (defensa en profundidad).
- Application Load Balancer (ALB) con TLS terminando ahí (en vez de Caddy/certbot dentro de la EC2).
- Frontends estáticos en S3 + CloudFront (separados del backend).
- Imágenes Docker publicadas en **Docker Hub** desde GitHub Actions.
- Deploy continuo a la EC2 vía SSH al hacer merge a `main`.

```
GitHub (push main)
   │
   ├─ Job test ──── mvn test / npm test (matrix por servicio)
   │
   ├─ Job build  ── docker buildx → push a Docker Hub (martindev/chessquery-*)
   │
   └─ Job deploy ── ssh ec2-user@<EIP> "docker compose pull && docker compose up -d"
                    aws s3 sync frontend/dist/ s3://chessquery-frontends/
                    aws cloudfront create-invalidation
```

> **Pre-requisito ya cubierto en el repo:** Supabase Cloud (auth + storage + DB lógica de identidad). Esta sección NO migra Supabase, asume que el eje 1 del `PLAN_MIGRACION_CLOUD.md` ya está hecho.

---

## 2. Inventario del stack a desplegar

Sacado del `infrastructure/docker-compose.yml`. Son **20 contenedores** en local; en AWS quedan **18** (excluyo `rabbitmq_setup` que es one-shot y `nginx` que reemplaza el ALB).

| Categoría | Servicios | Cantidad | Notas |
|---|---|---|---|
| Microservicios Java (Spring Boot 3.2) | `ms-users`, `ms-tournament`, `ms-game`, `ms-analytics`, `ms-notifications` | 5 | Cada uno con su DB propia. |
| Microservicio Python (FastAPI) | `ms-etl` | 1 | Consume eventos de RabbitMQ + DB propia. |
| BFFs (NestJS) | `bff-player`, `bff-organizer`, `bff-admin` | 3 | Agregadores por persona. |
| Gateway | `api-gateway` (Spring Cloud Gateway) | 1 | Único punto de entrada al backend. Valida JWT de Supabase. |
| Bases de datos | `user_db`, `tournament_db`, `game_db`, `analytics_db`, `notif_db`, `etl_db` | 6 | PostgreSQL 16-alpine. Un DB por servicio (decisión de arquitectura, ver `CONTEXT.md`). |
| Mensajería | `rabbitmq` (3.13-management) | 1 | Exchange `ChessEvents`. |
| Caché | `redis` (7-alpine) | 1 | Rate limiting + token cache. |
| Frontends estáticos | `chess-portal`, `organizer-panel` (Vite/React) | 2 | NO van a la EC2 → van a S3 + CloudFront. |

Total RAM estimada del backend en EC2: ~6.5 GB pico (cada Java ~600 MB, ETL ~300 MB, BFFs ~250 MB, infra ~1 GB).

→ **Instancia recomendada:** `t3.large` (2 vCPU, 8 GB RAM) para producción demo. `t3.medium` (4 GB) NO alcanza con el JVM default de los 6 Java.

---

## 3. Arquitectura AWS objetivo

### 3.1 Diagrama de red (alto nivel)

```
                                  Internet
                                      │
                                      │ HTTPS 443
                                      ▼
                  ┌───────────────────────────────────────┐
                  │       Route 53 (chessquery.cl)        │
                  │                                       │
                  │  app.chessquery.cl ─► CloudFront      │
                  │  api.chessquery.cl ─► ALB             │
                  └───────────────────────────────────────┘
                          │                   │
                          ▼                   ▼
                ┌──────────────────┐   ┌──────────────────┐
                │    CloudFront    │   │  ACM Certificate │
                │   (frontends)    │   │   (*.chessquery) │
                └──────────────────┘   └──────────────────┘
                          │                   │
                          ▼                   ▼
                ┌──────────────────┐   ┌──────────────────┐
                │  S3 bucket       │   │   ALB (público)  │
                │  chessquery-fe   │   │   :443 → :8080   │
                └──────────────────┘   └──────────────────┘
                                              │
══════════════════════════════════════════════│════════════════════════
   VPC chessquery-vpc (10.0.0.0/16)           │
                                              ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  Public Subnet A (10.0.1.0/24)  AZ: us-east-1a                  │
   │  ┌──────────────────────────────────────────────────────────┐   │
   │  │  Internet Gateway (IGW)                                  │   │
   │  │  NAT Gateway (egress de las privadas)                    │   │
   │  │  ALB ENI                                                 │   │
   │  └──────────────────────────────────────────────────────────┘   │
   └─────────────────────────────────────────────────────────────────┘
                                              │
                                              │ HTTP :8080 (api-gateway)
                                              ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  Private Subnet A (10.0.10.0/24)  AZ: us-east-1a                │
   │  ┌──────────────────────────────────────────────────────────┐   │
   │  │  EC2 chessquery-app  (t3.large)                          │   │
   │  │  ┌─────────────────────────────────────────────────────┐ │   │
   │  │  │  Docker Compose                                     │ │   │
   │  │  │   • api-gateway:8080  (único expuesto al ALB)       │ │   │
   │  │  │   • bff-player:3001                                 │ │   │
   │  │  │   • bff-organizer:3002                              │ │   │
   │  │  │   • bff-admin:3003                                  │ │   │
   │  │  │   • ms-users:8081 ms-tournament:8082                │ │   │
   │  │  │   • ms-game:8083 ms-analytics:8084                  │ │   │
   │  │  │   • ms-notifications:8085 ms-etl:8086               │ │   │
   │  │  │   • rabbitmq:5672 redis:6379                        │ │   │
   │  │  │   • 6× postgres (volúmenes EBS)                     │ │   │
   │  │  └─────────────────────────────────────────────────────┘ │   │
   │  └──────────────────────────────────────────────────────────┘   │
   └─────────────────────────────────────────────────────────────────┘
                                              │
                                              │ HTTPS egress vía NAT
                                              ▼
                                  ┌──────────────────────┐
                                  │ Supabase Cloud       │
                                  │ (auth + storage)     │
                                  │ <ref>.supabase.co    │
                                  └──────────────────────┘
                                  ┌──────────────────────┐
                                  │ Docker Hub           │
                                  │ (pull de imágenes)   │
                                  └──────────────────────┘
```

### 3.2 Recursos AWS detallados

| Recurso | Tipo | Detalle | CIDR / Config |
|---|---|---|---|
| VPC | `chessquery-vpc` | Aislamiento de red | `10.0.0.0/16` |
| Public Subnet A | `chessquery-public-a` | Hosts NAT, ALB | `10.0.1.0/24` AZ `us-east-1a` |
| Public Subnet B | `chessquery-public-b` | Segunda AZ para ALB | `10.0.2.0/24` AZ `us-east-1b` |
| Private Subnet A | `chessquery-private-a` | EC2 backend | `10.0.10.0/24` AZ `us-east-1a` |
| Internet Gateway | `chessquery-igw` | Inbound a públicas | — |
| NAT Gateway | `chessquery-nat` | Egress de privadas (apt-get, pull Docker Hub, Supabase) | En public-a, Elastic IP |
| Route Table pública | `chessquery-rt-public` | `0.0.0.0/0 → igw` | Asociada a public-a/b |
| Route Table privada | `chessquery-rt-private` | `0.0.0.0/0 → nat` | Asociada a private-a |
| EC2 | `chessquery-app` | Ubuntu 22.04 LTS, `t3.large`, EBS gp3 50 GB | Private-a |
| ALB | `chessquery-alb` | Internet-facing, listener 443 (TLS) → target group EC2:8080 | Public-a + Public-b |
| ACM cert | `*.chessquery.cl` | Para ALB y CloudFront (este último en `us-east-1`) | DNS validation |
| Route 53 hosted zone | `chessquery.cl` | Records `A alias` para `app` y `api` | — |
| S3 bucket | `chessquery-frontends` | Hosting de los dos frontends Vite | Static website OFF, OAC con CloudFront |
| CloudFront distribution | `chessquery-cdn` | OAC al S3, alias `app.chessquery.cl` | Default root `/chess-portal/index.html` |

### 3.3 Security Groups (firewall por instancia)

| SG | Inbound | Outbound | Aplicado a |
|---|---|---|---|
| `sg-alb` | `443/tcp` ← `0.0.0.0/0` | `8080/tcp` → `sg-ec2` | ALB |
| `sg-ec2` | `8080/tcp` ← `sg-alb`<br>`22/tcp` ← IP del bastion / GitHub Actions (ver §3.4) | `443/tcp` → `0.0.0.0/0` (NAT)<br>`5432-5672/tcp` interno irrelevante (mismo host) | EC2 |
| `sg-ssh-deploy` | (vacío, sólo se usa como referencia desde sg-ec2) | — | — |

> **Por qué SG en lugar de NACL:** NACLs son stateless y operan a nivel subnet. Para esta topología basta con SGs (stateful y más simples de auditar). NACLs default permisivas.

### 3.4 SSH para deploy desde GitHub Actions

Tres opciones, de menos a más segura:

| Opción | Cómo | Pro | Contra |
|---|---|---|---|
| **A.** SG abre `22/tcp` al rango de IPs públicas de GitHub Actions | Descargar `https://api.github.com/meta` → campo `actions` → agregarlo al SG (lista grande, ~3000 CIDRs) | Simple. Sin infra extra. | Rango cambia → mantener actualizado con un cron. |
| **B.** Bastion host + SSH tunnel | EC2 `t3.nano` en public-a, GH Actions hace `ssh -J bastion ec2-user@private-ec2` | Audit log centralizado. | +$5/mes. Más piezas. |
| **C.** AWS SSM Session Manager (recomendado largo plazo) | Sin SSH abierto. GH Actions usa `aws ssm start-session` + IAM | Sin puerto 22 expuesto, AuditLog en CloudTrail. | Requiere instalar SSM agent y rol IAM en la EC2. Algo más de tooling. |

**Decisión para este parcial:** Opción **A** por simplicidad. Documentar la dirección hacia C como mejora futura.

---

## 4. Docker Hub

### 4.1 Estructura de repos

Un repo Docker Hub **por servicio** (10 en total). Naming: `<dockerhub-user>/chessquery-<servicio>`.

```
martinmdev/chessquery-ms-users
martinmdev/chessquery-ms-tournament
martinmdev/chessquery-ms-game
martinmdev/chessquery-ms-analytics
martinmdev/chessquery-ms-notifications
martinmdev/chessquery-ms-etl
martinmdev/chessquery-bff-player
martinmdev/chessquery-bff-organizer
martinmdev/chessquery-bff-admin
martinmdev/chessquery-api-gateway
```

### 4.2 Tagging strategy

Cada imagen se publica con **tres tags** en cada push a `main`:

- `latest` → siempre apunta al último build de main (usado por la EC2).
- `sha-<7-chars>` → trazabilidad inmutable al commit.
- `v<MAJOR>.<MINOR>.<PATCH>` → si el commit lleva un tag de git (release manual).

### 4.3 Crear los repos (manual, una sola vez)

1. Login en `hub.docker.com` con la cuenta del equipo.
2. Por cada servicio: `New Repository` → privado (FREE tier permite 1 repo privado; si necesitamos los 10 privados pasamos a Pro $5/mes, o los dejamos públicos — el código ya está en GitHub público).
3. **Access Token** para CI: `Account Settings → Security → New Access Token` con scope `Read, Write, Delete`. Guardar el token; va a ir como secret en GitHub Actions.

---

## 5. CI/CD con GitHub Actions

### 5.1 Estructura de workflows

Vamos a tener **tres archivos** en `.github/workflows/`:

| Archivo | Trigger | Qué hace |
|---|---|---|
| `ci.yml` | `pull_request` a main | Tests unitarios + lint + build sin push. Bloquea merge si falla. |
| `deploy-backend.yml` | `push` a main que toca `ms-*`, `bff-*`, `api-gateway`, `infrastructure/` | Build + push a Docker Hub + SSH deploy a EC2. |
| `deploy-frontend.yml` | `push` a main que toca `frontend/` | Build Vite + `s3 sync` + invalidar CloudFront. |

### 5.2 Secrets necesarios en GitHub

`Settings → Secrets and variables → Actions`:

| Secret | Uso |
|---|---|
| `DOCKERHUB_USERNAME` | Login para `docker login`. |
| `DOCKERHUB_TOKEN` | Access token con scope write. |
| `AWS_ACCESS_KEY_ID` | IAM user con permisos `s3:PutObject` y `cloudfront:CreateInvalidation` (sólo eso). |
| `AWS_SECRET_ACCESS_KEY` | Idem. |
| `EC2_HOST` | IP pública del ALB o EIP de la EC2 (ver §6). |
| `EC2_USER` | `ubuntu`. |
| `EC2_SSH_KEY` | Llave privada PEM completa de la EC2 (entrar como text, no como file). |
| `SUPABASE_URL` | URL pública del proyecto Supabase Cloud. |
| `SUPABASE_JWT_SECRET` | Para inyectar en `.env` de prod en la EC2. |
| `SUPABASE_SERVICE_KEY` | Idem. |
| `DB_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD` | Reemplazan los `_dev` de docker-compose. |

> **Anti-patrón a evitar:** copiar el `.env.example` con valores de prod al repo. Los secretos viven en GitHub Actions y se renderizan a un `.env` temporal **en la EC2** durante el deploy.

### 5.3 `ci.yml` (tests en PR)

```yaml
# .github/workflows/ci.yml
name: CI tests
on:
  pull_request:
    branches: [main]

jobs:
  test-java:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - ms-users
          - ms-tournament
          - ms-game
          - ms-analytics
          - ms-notifications
          - api-gateway
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: maven
      - name: Run tests
        working-directory: ${{ matrix.service }}
        run: mvn -B test

  test-python:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - working-directory: ms-etl
        run: |
          pip install -r requirements.txt
          pytest

  test-node:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - bff-player
          - bff-organizer
          - bff-admin
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: ${{ matrix.service }}/package-lock.json
      - working-directory: ${{ matrix.service }}
        run: |
          npm ci
          npm test
```

### 5.4 `deploy-backend.yml` (push + SSH deploy)

```yaml
# .github/workflows/deploy-backend.yml
name: Deploy backend
on:
  push:
    branches: [main]
    paths:
      - 'ms-*/**'
      - 'bff-*/**'
      - 'api-gateway/**'
      - 'infrastructure/**'
      - '.github/workflows/deploy-backend.yml'

env:
  DOCKERHUB_USER: ${{ secrets.DOCKERHUB_USERNAME }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - service: ms-users
          - service: ms-tournament
          - service: ms-game
          - service: ms-analytics
          - service: ms-notifications
          - service: ms-etl
          - service: bff-player
          - service: bff-organizer
          - service: bff-admin
          - service: api-gateway
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build & push ${{ matrix.service }}
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.service }}
          push: true
          tags: |
            ${{ env.DOCKERHUB_USER }}/chessquery-${{ matrix.service }}:latest
            ${{ env.DOCKERHUB_USER }}/chessquery-${{ matrix.service }}:sha-${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy-ec2:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Render .env de prod
        run: |
          cat > .env.prod <<EOF
          DB_USER=chessquery
          DB_PASSWORD=${{ secrets.DB_PASSWORD }}
          REDIS_PASSWORD=${{ secrets.REDIS_PASSWORD }}
          RABBITMQ_USER=chessquery
          RABBITMQ_PASSWORD=${{ secrets.RABBITMQ_PASSWORD }}
          SUPABASE_URL=${{ secrets.SUPABASE_URL }}
          SUPABASE_PUBLIC_URL=${{ secrets.SUPABASE_URL }}
          SUPABASE_JWT_SECRET=${{ secrets.SUPABASE_JWT_SECRET }}
          SUPABASE_SERVICE_KEY=${{ secrets.SUPABASE_SERVICE_KEY }}
          SUPABASE_WEBHOOK_SECRET=${{ secrets.SUPABASE_WEBHOOK_SECRET }}
          CORS_ALLOWED_ORIGINS=https://app.chessquery.cl
          DOCKER_REGISTRY=${{ secrets.DOCKERHUB_USERNAME }}
          IMAGE_TAG=sha-${{ github.sha }}
          EOF

      - name: Copiar compose + env a EC2
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          source: "infrastructure/docker-compose.prod.yml,.env.prod"
          target: "/home/ubuntu/chessquery/"

      - name: Pull + restart en EC2
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /home/ubuntu/chessquery
            mv .env.prod infrastructure/.env
            cd infrastructure
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d --remove-orphans
            docker image prune -f
```

### 5.5 `deploy-frontend.yml`

```yaml
# .github/workflows/deploy-frontend.yml
name: Deploy frontend
on:
  push:
    branches: [main]
    paths:
      - 'frontend/**'
      - '.github/workflows/deploy-frontend.yml'

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        app: [chess-portal, organizer-panel]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Install deps
        working-directory: frontend
        run: npm ci

      - name: Build ${{ matrix.app }}
        working-directory: frontend/apps/${{ matrix.app }}
        env:
          VITE_SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          VITE_SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
          VITE_API_URL: https://api.chessquery.cl
        run: npm run build

      - name: Configurar AWS
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: us-east-1

      - name: Sync a S3
        run: |
          aws s3 sync frontend/apps/${{ matrix.app }}/dist/ \
            s3://chessquery-frontends/${{ matrix.app }}/ \
            --delete

      - name: Invalidar CloudFront
        run: |
          aws cloudfront create-invalidation \
            --distribution-id ${{ secrets.CLOUDFRONT_DIST_ID }} \
            --paths "/${{ matrix.app }}/*"
```

---

## 6. Cambios necesarios en el repo

### 6.1 Nuevo `docker-compose.prod.yml`

El compose actual hace `build: { context: ... }`. En prod queremos **pull** de Docker Hub, no build. Creamos un override:

```yaml
# infrastructure/docker-compose.prod.yml
# Override que reemplaza los `build:` por `image:` apuntando a Docker Hub.
# Se usa así:
#   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

services:
  ms-users:
    image: ${DOCKER_REGISTRY}/chessquery-ms-users:${IMAGE_TAG:-latest}
    build: !reset null
  ms-tournament:
    image: ${DOCKER_REGISTRY}/chessquery-ms-tournament:${IMAGE_TAG:-latest}
    build: !reset null
  ms-game:
    image: ${DOCKER_REGISTRY}/chessquery-ms-game:${IMAGE_TAG:-latest}
    build: !reset null
  ms-analytics:
    image: ${DOCKER_REGISTRY}/chessquery-ms-analytics:${IMAGE_TAG:-latest}
    build: !reset null
  ms-notifications:
    image: ${DOCKER_REGISTRY}/chessquery-ms-notifications:${IMAGE_TAG:-latest}
    build: !reset null
  ms-etl:
    image: ${DOCKER_REGISTRY}/chessquery-ms-etl:${IMAGE_TAG:-latest}
    build: !reset null
  bff-player:
    image: ${DOCKER_REGISTRY}/chessquery-bff-player:${IMAGE_TAG:-latest}
    build: !reset null
  bff-organizer:
    image: ${DOCKER_REGISTRY}/chessquery-bff-organizer:${IMAGE_TAG:-latest}
    build: !reset null
  bff-admin:
    image: ${DOCKER_REGISTRY}/chessquery-bff-admin:${IMAGE_TAG:-latest}
    build: !reset null
  api-gateway:
    image: ${DOCKER_REGISTRY}/chessquery-api-gateway:${IMAGE_TAG:-latest}
    build: !reset null
    ports:
      # En prod sólo el gateway escucha hacia el ALB.
      - "0.0.0.0:8080:8080"
```

### 6.2 Bugs P0 a resolver antes del primer deploy

Sacados de `PLAN_MIGRACION_CLOUD.md` §3 — **no son opcionales**:

- [ ] **X-User-Id propagation** (`api-gateway/.../SupabaseJwtAuthFilter.java:221`). Sin esto el flujo de torneos rompe con `400`.
- [ ] **Quitar `host.docker.internal`** del compose y reemplazar por la URL pública de Supabase Cloud.
- [ ] **Limpiar `.env.example`** de variables `MINIO_*` y `S3_*` ya muertas.
- [ ] **Verificar healthchecks** de los 6 servicios Java (algunos tenían `start_period` muy corto que en t3.large todavía puede fallar — subir a `120s`).

---

## 7. Provisionamiento de AWS paso a paso

Esto se puede hacer con consola web o con Terraform/CDK. Para el parcial, **consola web** es suficiente y didáctico. Lo dejo en orden para no encontrarte con dependencias rotas.

### Paso 1 — Crear la VPC y subnets

1. VPC → `Create VPC` → **VPC and more** → asistente.
2. Name `chessquery`, CIDR `10.0.0.0/16`, **2 AZs** (`us-east-1a`, `us-east-1b`), **2 public** + **1 private**, **1 NAT gateway** (in `us-east-1a`).

### Paso 2 — Crear ACM cert

1. ACM (en `us-east-1` — obligatorio para CloudFront) → request cert.
2. Domain `*.chessquery.cl` y `chessquery.cl`.
3. Validación **DNS**. Agregar el CNAME que pide a Route 53.

### Paso 3 — Crear el ALB

1. EC2 → Load Balancers → `Create ALB`.
2. Internet-facing, VPC `chessquery`, ambas subnets públicas.
3. Listener `HTTPS:443` → cert `*.chessquery.cl` → target group `chessquery-tg` (target type `instance`, protocol `HTTP:8080`, healthcheck `/actuator/health`).
4. Security group: `sg-alb` (creado nuevo) → inbound `443 0.0.0.0/0`.

### Paso 4 — Crear la EC2

1. EC2 → Launch instance.
2. Ubuntu 22.04 LTS, `t3.large`, key pair nueva `chessquery-deploy` (descargar `.pem`, guardarla → va a `EC2_SSH_KEY`).
3. VPC `chessquery`, subnet `chessquery-private-a`, **no asignar IP pública** (está en privada).
4. EBS root 50 GB gp3.
5. SG `sg-ec2`: inbound `8080/tcp` desde `sg-alb`, inbound `22/tcp` desde IPs de GitHub Actions (ver §3.4 Opción A).
6. User data:
   ```bash
   #!/bin/bash
   apt-get update
   apt-get install -y ca-certificates curl gnupg
   install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu jammy stable" > /etc/apt/sources.list.d/docker.list
   apt-get update
   apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   usermod -aG docker ubuntu
   mkdir -p /home/ubuntu/chessquery/infrastructure
   chown -R ubuntu:ubuntu /home/ubuntu/chessquery
   ```

### Paso 5 — Registrar la EC2 en el target group

EC2 → Target Groups → `chessquery-tg` → Register targets → seleccionar la EC2 → puerto `8080`.

### Paso 6 — Route 53 records

1. Hosted zone `chessquery.cl`.
2. Record `api.chessquery.cl` → A alias → ALB.
3. Record `app.chessquery.cl` → A alias → CloudFront (después del paso 7).

### Paso 7 — S3 + CloudFront para frontends

1. S3 → bucket `chessquery-frontends`, region `us-east-1`, **bloquear todo el acceso público** (CloudFront va por OAC, no por public-read).
2. Subir dos carpetas: `chess-portal/` y `organizer-panel/`.
3. CloudFront → Create distribution.
4. Origin = bucket S3, OAC nuevo.
5. Default behavior → redirect HTTP a HTTPS, allowed methods `GET, HEAD`.
6. Alternate domain `app.chessquery.cl`, cert ACM, default root `/chess-portal/index.html`.
7. Behaviors adicionales:
   - `/organizer/*` → `organizer-panel/index.html` (con función de viewer request que reescriba para SPAs).

### Paso 8 — IAM user para GitHub Actions

1. IAM → Users → `gh-actions-chessquery`, sin consola.
2. Policy inline:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": ["s3:PutObject","s3:DeleteObject","s3:ListBucket"],
         "Resource": ["arn:aws:s3:::chessquery-frontends","arn:aws:s3:::chessquery-frontends/*"] },
       { "Effect": "Allow", "Action": "cloudfront:CreateInvalidation",
         "Resource": "arn:aws:cloudfront::<ACCOUNT_ID>:distribution/<DIST_ID>" }
     ]
   }
   ```
3. Crear access key → guardar en GitHub Secrets.

> **Política mínima**: nada de `AdministratorAccess`. Si rotás secretos o cambia la pipeline, ampliás permisos puntuales.

### Paso 9 — Primer deploy manual de smoke

```bash
# Desde tu máquina, con la EC2 ya provisionada:
scp -i chessquery-deploy.pem -o ProxyJump=<bastion> \
    infrastructure/docker-compose.yml \
    infrastructure/docker-compose.prod.yml \
    ubuntu@10.0.10.X:/home/ubuntu/chessquery/infrastructure/

ssh -i chessquery-deploy.pem -J <bastion> ubuntu@10.0.10.X
cd /home/ubuntu/chessquery/infrastructure
# pegar .env con valores de prod
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose ps        # todos healthy en ~2 min
curl http://localhost:8080/actuator/health   # debería responder UP
```

Después de validar este smoke, el resto de los deploys los hace GitHub Actions automáticamente.

---

## 8. Operación día-a-día

### 8.1 Ver logs en prod

```bash
ssh ubuntu@<EIP>
cd /home/ubuntu/chessquery/infrastructure
docker compose logs -f ms-users           # un servicio
docker compose logs --tail=100 api-gateway
```

Mejora futura: enviar logs a CloudWatch Logs con `awslogs` driver de Docker.

### 8.2 Rollback rápido

GitHub Actions guarda el tag `sha-<commit>` por cada deploy. Para rollback:

```bash
ssh ubuntu@<EIP>
cd /home/ubuntu/chessquery/infrastructure
# Editar .env, cambiar IMAGE_TAG=sha-<commit-anterior>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 8.3 Backups de las DBs

Las 6 Postgres viven en volúmenes Docker locales de la EC2. **Esto NO es HA**, es la limitación conocida de la Opción A.

Mitigación mínima — cron diario en la EC2:

```bash
# /etc/cron.daily/chessquery-pgdump
#!/bin/bash
DATE=$(date +%Y%m%d)
cd /home/ubuntu/chessquery/infrastructure
for db in user_db tournament_db game_db analytics_db notif_db etl_db; do
  docker compose exec -T ${db%_db}_db pg_dump -U chessquery $db | gzip > /tmp/$db-$DATE.sql.gz
  aws s3 cp /tmp/$db-$DATE.sql.gz s3://chessquery-backups/$db/
  rm /tmp/$db-$DATE.sql.gz
done
```

Bucket `chessquery-backups` con lifecycle policy `glacier después de 30 días, delete después de 90`.

---

## 9. Costos estimados (USD/mes)

| Recurso | Estimación |
|---|---|
| EC2 t3.large on-demand (730 h) | $60 |
| EBS gp3 50 GB | $4 |
| NAT Gateway (730 h + ~50 GB egress) | $35 |
| ALB | $18 + ~$3 LCU = $21 |
| Elastic IP (sólo si la EC2 estuviera en pública) | $0 (en privada va por NAT) |
| Route 53 hosted zone | $0.50 |
| S3 frontends (<5 GB) | $0.15 |
| CloudFront (free tier primer año) | $0 |
| Data transfer out (10 GB/mes) | $1 |
| **Total estimado** | **~$120/mes** |

> **Optimizaciones posibles si presupuesto es estricto:**
> - NAT Gateway → VPC Endpoints para S3/ECR + EC2 en subnet pública con SG estricto. Ahorra ~$35/mes.
> - Reserved Instance 1 año de t3.large → ahorra ~40 % de los $60.
> - Apagar la EC2 fuera de horario de demo si es sólo académico (cron `stop-instances`) → ~50 % menos.

---

## 10. Checklist final pre-defensa

**Infraestructura**

- [ ] VPC y subnets creadas, NAT funcionando (test: `curl ifconfig.me` desde la EC2 devuelve la EIP del NAT).
- [ ] ACM cert emitido y validado.
- [ ] ALB health 200 en todos los targets.
- [ ] Route 53 resolviendo `api.chessquery.cl` y `app.chessquery.cl`.
- [ ] EC2 con Docker + Compose instalados.

**Docker Hub**

- [ ] 10 repos creados.
- [ ] Access token guardado en GitHub Secrets.
- [ ] Primer push manual de cada imagen exitoso (`docker push martinmdev/chessquery-ms-users:latest`).

**GitHub Actions**

- [ ] 3 workflows en `.github/workflows/`.
- [ ] Todos los secretos cargados (12 secrets, ver §5.2).
- [ ] PR de prueba que dispara `ci.yml` → verde.
- [ ] Merge a `main` dispara `deploy-backend.yml` → la EC2 corre el nuevo `sha-`.

**Aplicación**

- [ ] Bugs P0 de §6.2 cerrados.
- [ ] `https://api.chessquery.cl/actuator/health` → `UP`.
- [ ] `https://app.chessquery.cl` carga chess-portal.
- [ ] Magic link de Supabase Cloud llega y redirige al dominio prod.
- [ ] Flujo E2E (registrar player → crear torneo → jugar partida → ver analytics) funciona en prod.

**Operación**

- [ ] Cron de backups corriendo.
- [ ] Rollback probado al menos una vez.
- [ ] Documentado quién tiene la `.pem` de la EC2.

---

## 11. Próximos pasos (post-parcial, no bloqueantes)

1. **Migrar SSH → SSM Session Manager** (cerrar puerto 22 al mundo).
2. **Reemplazar Postgres por servicio en EC2 → RDS** con databases lógicas (HA + backup automático).
3. **ECS Fargate** para los microservicios (auto-scaling + sin SSH).
4. **Terraform** para todo lo que hicimos por consola (IaC reproducible).
5. **CloudWatch Logs + alarmas** (CPU >80 %, ALB 5xx >1 %).
6. **Multi-AZ** para la EC2 (Auto Scaling Group con 1 instancia mínima, sube a 2 si la AZ-A cae).

---

## Apéndice — Comandos útiles

```bash
# Build local de todas las imágenes (antes de tener CI):
cd /home/kenny/ChessQuery_FS3
for svc in ms-users ms-tournament ms-game ms-analytics ms-notifications ms-etl bff-player bff-organizer bff-admin api-gateway; do
  docker build -t martinmdev/chessquery-$svc:latest ./$svc
  docker push martinmdev/chessquery-$svc:latest
done

# Verificar que la EC2 puede hacer pull (después de docker login):
docker pull martinmdev/chessquery-api-gateway:latest

# Forzar redeploy sin commit:
gh workflow run deploy-backend.yml --ref main

# Ver últimas 5 runs de deploy:
gh run list --workflow=deploy-backend.yml --limit 5
```
