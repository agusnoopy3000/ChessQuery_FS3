# Frontend en S3 + ALB para el gateway (guía de implementación)

> Pasos para (A) darle al `api-gateway` una **URL estable** con un ALB y (B) publicar
> los frontends React/Vite en **S3**. Pre-requisito: la task `chessquery-stack` ya
> corriendo en `chessquery-cluster` (ver `DESPLIEGUE_AWS_REALIZADO.md`).

```bash
export AWS_REGION=us-east-1 PROJECT=chessquery
export VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
export SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID Name=default-for-az,Values=true --query 'Subnets[].SubnetId' --output text)
```

---

## A. ALB delante del api-gateway (URL estable)

> Por qué: hoy la IP pública de la task **cambia en cada redeploy**. Un ALB da un DNS
> fijo (`chessquery-alb-xxxx.us-east-1.elb.amazonaws.com`) y health checks.

### A.1 Security groups
```bash
# SG del ALB: acepta 80 (y 443 si ponés cert) desde internet
SG_ALB=$(aws ec2 create-security-group --group-name $PROJECT-alb-sg \
  --description "ALB ChessQuery" --vpc-id $VPC_ID --query 'GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $SG_ALB --protocol tcp --port 80 --cidr 0.0.0.0/0

# SG de la task (chessquery-ecs-sg): que 8080 venga SOLO del ALB, no de internet
SG_ECS=$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-ecs-sg --query 'SecurityGroups[0].GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $SG_ECS --protocol tcp --port 8080 --source-group $SG_ALB
# (opcional) quitar la regla vieja 8080 0.0.0.0/0:
# aws ec2 revoke-security-group-ingress --group-id $SG_ECS --protocol tcp --port 8080 --cidr 0.0.0.0/0
```

### A.2 ALB + target group
```bash
ALB_ARN=$(aws elbv2 create-load-balancer --name $PROJECT-alb \
  --type application --scheme internet-facing \
  --subnets $SUBNETS --security-groups $SG_ALB \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)

# Target group tipo IP (Fargate awsvpc), puerto 8080, health check al readiness del gateway
TG_ARN=$(aws elbv2 create-target-group --name $PROJECT-gw-tg \
  --protocol HTTP --port 8080 --vpc-id $VPC_ID --target-type ip \
  --health-check-path /actuator/health/readiness --health-check-port 8080 \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

aws elbv2 create-listener --load-balancer-arn $ALB_ARN \
  --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_ARN
```

### A.3 Enganchar el ALB al service ECS
El service `chessquery-stack` se creó **sin** load balancer. ECS no deja agregar un LB
a un service existente → hay que **recrearlo** apuntando al target group:
```bash
CLUSTER=$PROJECT-cluster
# 1. Borrar el service actual (no borra la task-def ni las imágenes)
aws ecs update-service --cluster $CLUSTER --service chessquery-stack --desired-count 0
aws ecs delete-service --cluster $CLUSTER --service chessquery-stack --force

# 2. Recrear con --load-balancers (apunta al contenedor api-gateway:8080)
aws ecs create-service \
  --cluster $CLUSTER --service-name chessquery-stack \
  --task-definition chessquery-stack \
  --desired-count 1 --launch-type FARGATE \
  --health-check-grace-period-seconds 180 \
  --network-configuration "awsvpcConfiguration={subnets=[$(echo $SUBNETS|tr ' ' ',')],securityGroups=[$SG_ECS],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=$TG_ARN,containerName=api-gateway,containerPort=8080"
```

### A.4 Obtener el DNS estable
```bash
aws elbv2 describe-load-balancers --names $PROJECT-alb --query 'LoadBalancers[0].DNSName' --output text
# → chessquery-alb-xxxx.us-east-1.elb.amazonaws.com   (esta URL ya no cambia)
curl http://<ALB_DNS>/actuator/health/readiness
```

> HTTPS: para `https://` necesitás un certificado en ACM + listener 443. Para la demo,
> HTTP en el puerto 80 alcanza.

---

## B. Frontends en S3

> Los SPAs (`chess-portal`, `organizer-panel`) se compilan a estáticos y se sirven desde
> S3. Hablan con Supabase (auth) y con el gateway (vía el DNS del ALB).

### B.1 Variables de build
`frontend/apps/chess-portal/.env.production` (y equivalente en `organizer-panel`):
```
VITE_API_BASE_URL=http://<ALB_DNS>
VITE_SUPABASE_URL=https://pmtxxzscpactsgkijpul.supabase.co
VITE_SUPABASE_ANON_KEY=<anon key>
```
> Confirmá el nombre exacto de la var de la API en el código del front (ej. `VITE_API_URL`).

### B.2 Build
```bash
cd frontend
npm ci
npm --prefix apps/chess-portal run build      # genera apps/chess-portal/dist
npm --prefix apps/organizer-panel run build
```

### B.3 Bucket + static website hosting
```bash
export AWS_REGION=us-east-1
for app in chess-portal organizer-panel; do
  B=chessquery-$app
  aws s3 mb s3://$B --region $AWS_REGION
  # Desbloquear acceso público (Academy suele permitirlo)
  aws s3api put-public-access-block --bucket $B \
    --public-access-block-configuration BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false
  # Política de lectura pública
  aws s3api put-bucket-policy --bucket $B --policy "{
    \"Version\":\"2012-10-17\",
    \"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::$B/*\"}]
  }"
  # Hosting de sitio estático (SPA → fallback a index.html)
  aws s3 website s3://$B --index-document index.html --error-document index.html
done
```

### B.4 Subir y URL
```bash
aws s3 sync frontend/apps/chess-portal/dist/     s3://chessquery-chess-portal     --delete
aws s3 sync frontend/apps/organizer-panel/dist/  s3://chessquery-organizer-panel  --delete

# URL del sitio:
echo "http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com"
```

### B.5 CORS en el gateway
El gateway usa `GATEWAY_CORS_ALLOWED_ORIGINS` (hoy `*`). En producción, acotarlo a las
URLs de los buckets (env en la task-def, redeploy):
```
GATEWAY_CORS_ALLOWED_ORIGINS=http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com,http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com
```

---

## C. Orden recomendado
1. **ALB primero** (A) → obtené el DNS estable.
2. Poné ese DNS en `.env.production` del front (B.1) y buildeá.
3. Subí a S3 (B).
4. Acotá CORS del gateway al dominio S3 (B.5).

> Notas Academy: ELB y S3 website suelen estar permitidos. CloudFront (para HTTPS +
> dominio propio) puede estar restringido; si lo está, el bucket como static website +
> ALB en HTTP alcanza para la demo.
