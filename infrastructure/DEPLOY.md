# ChessQuery — Despliegue

Plataforma de ajedrez competitivo chilena (DSY1106 DuocUC) desplegada como microservicios sobre Kubernetes. Este documento cubre el bootstrap local con k3s y el despliegue productivo en AWS (~USD 50/mes).

---

## 1. Requisitos previos

| Herramienta  | Versión mínima | Cómo se usa |
| ------------ | -------------- | ------------ |
| Docker       | 24             | `docker build` / `docker push` de las imágenes |
| AWS CLI      | 2.x            | Crear ECR, login, eventual EKS/RDS |
| kubectl      | 1.28           | Aplicar manifiestos al cluster |
| helm         | 3.13           | (Opcional) instalar ingress / cert-manager |
| k3s          | v1.28          | Cluster local para la demo |
| envsubst     | gettext        | Sustituir `${ECR_REGISTRY}` en YAMLs |
| Java 17      | Temurin        | Compilar microservicios Spring Boot |
| Node 20      | LTS            | Compilar BFFs NestJS + frontend |
| Python 3.11  | —              | ms-etl |

Variables de entorno requeridas para AWS:

```bash
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=us-east-1
export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
```

---

## 2. Despliegue local (demo)

```bash
# 1. Bootstrap del cluster k3s + Nginx Ingress + cert-manager
./infrastructure/scripts/setup-k3s.sh
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# 2. Construir imágenes localmente y cargarlas en k3s
#    (alternativa al ECR; útil cuando no hay conectividad AWS)
docker compose -f infrastructure/docker-compose.yml build
for svc in ms-users ms-tournament ms-game ms-analytics ms-notifications \
           api-gateway ms-etl bff-player bff-organizer bff-admin frontend; do
  docker tag chessquery_${svc}:latest local/chessquery/${svc}:latest
  docker save local/chessquery/${svc}:latest | sudo k3s ctr images import -
done

# 3. Aplicar manifiestos
./infrastructure/scripts/deploy.sh --env local

# 4. Abrir http://chessquery.local/
```

> **Nota:** el script `deploy.sh --env local` reemplaza `${ECR_REGISTRY}` por `local`. Para que el cluster encuentre las imágenes hay que importarlas con `k3s ctr images import` (paso 2).

---

## 3. Despliegue AWS (~USD 50/mes)

### 3.1 Topología y costos estimados

| Recurso             | Tipo                              | Costo aprox/mes (us-east-1) |
| ------------------- | --------------------------------- | --------------------------- |
| EC2 (k3s single-node) | t3.medium (2 vCPU / 4GB)        | ~30 USD                     |
| RDS PostgreSQL      | db.t4g.micro multi-AZ off         | ~13 USD                     |
| ECR                 | 10 GB almacenados                 | ~1 USD                      |
| Route53             | 1 hosted zone                     | 0.50 USD                    |
| Data transfer       | 10 GB salida                      | ~1 USD                      |
| **TOTAL**           |                                   | **~46 USD**                 |

> Para bajar el costo, usar Supabase Postgres en lugar de RDS (free tier) y un único PV en el nodo EC2 para RabbitMQ.

### 3.2 Pasos

```bash
# 1. Crear repositorios ECR (uno por servicio)
./infrastructure/scripts/setup-ecr.sh

# 2. Construir y pushear imágenes a ECR
./infrastructure/scripts/build-push-images.sh v1.0.0

# 3. Aprovisionar nodo EC2 (manualmente o con Terraform), instalar k3s ahí
ssh ec2-user@<EC2_IP>
curl -sfL https://get.k3s.io | sh -s - --disable=traefik
sudo cat /etc/rancher/k3s/k3s.yaml   # copiar a tu local y ajustar server: IP pública

# 4. Reemplazar placeholders del Secret antes del apply
kubectl -n chessquery create secret generic chessquery-secrets \
  --from-literal=DB_PASSWORD="$(aws ssm get-parameter --name /chessquery/db_password --with-decryption --query Parameter.Value --output text)" \
  --from-literal=RABBITMQ_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=SUPABASE_KEY="$(aws ssm get-parameter --name /chessquery/supabase_key --with-decryption --query Parameter.Value --output text)" \
  --from-literal=JWT_SECRET="$(openssl rand -hex 32)" \
  --dry-run=client -o yaml | kubectl apply -f -

# 5. Aplicar todos los manifiestos contra el cluster remoto
./infrastructure/scripts/deploy.sh --env aws
```

### 3.3 Configuración pendiente (placeholders)

Antes del primer `deploy.sh --env aws`, reemplaza en `infrastructure/k8s/configmaps/chessquery-config.yaml`:

- `DB_HOST` → host de tu RDS / Supabase
- `SUPABASE_URL` → URL completa de tu proyecto Supabase

Y en `infrastructure/k8s/secrets/chessquery-secrets.yaml` los `changeme` (mejor: no commitearlos y usar el `kubectl create secret` del paso 4 arriba).

Si los `application.yml` de los microservicios todavía usan valores hardcodeados de DB/Rabbit, parametrizarlos con `${DB_HOST}`, `${DB_PASSWORD}`, `${RABBITMQ_HOST}`, `${RABBITMQ_PASSWORD}`, `${SUPABASE_URL}`, `${SUPABASE_KEY}` y `${JWT_SECRET}`.

---

## 4. Verificación post-despliegue

```bash
# Estado de los pods
kubectl -n chessquery get pods

# Logs de un servicio
kubectl -n chessquery logs -f deployment/ms-users

# Ingress y URL pública
kubectl -n ingress-nginx get svc ingress-nginx-controller
curl -k https://chessquery.local/api/users/health

# Health endpoints internos
for d in ms-users ms-tournament ms-game ms-analytics ms-notifications api-gateway; do
  kubectl -n chessquery exec deployment/${d} -- wget -qO- http://localhost:80${d#ms-*}/actuator/health || true
done

# HPA en acción
kubectl -n chessquery get hpa
```

---

## 5. Rollback

```bash
# Volver al ReplicaSet anterior de un deployment
kubectl -n chessquery rollout undo deployment/ms-users
kubectl -n chessquery rollout status deployment/ms-users

# O fijar una revisión específica
kubectl -n chessquery rollout history deployment/ms-users
kubectl -n chessquery rollout undo deployment/ms-users --to-revision=3

# Rollback total al estado anterior de toda la app
for d in $(kubectl -n chessquery get deploy -o name); do
  kubectl -n chessquery rollout undo ${d}
done
```

---

## 6. Tests y cobertura

```bash
./infrastructure/scripts/run-tests.sh
```

El script ejecuta:
- `mvn test` en cada microservicio Java (genera `target/site/jacoco/jacoco.csv`)
- `pytest --cov=app` en `ms-etl`
- `npm test -- --coverage` en cada BFF
- `python3 parse_coverage.py` para resumen agregado

Falla con exit code 1 si algún servicio Java tiene cobertura de líneas < 70%.
