# CI/CD de ChessQuery

Pipeline pensado para la infra económica en AWS Academy (ECS Fargate + RDS + S3),
con la restricción de que **las credenciales del lab rotan cada ~4 h**.

## Resumen en una línea

- **CI (automática)** → corre en cada push/PR a `main`. Tests + cobertura ≥90% + escaneo de seguridad. **No necesita AWS**, siempre funciona.
- **CD (manual)** → se dispara a mano tras refrescar las credenciales de AWS. Build+push de imágenes, deploy del stack ECS, sync de frontends a S3 y (opcional) el ETL con autoscaling.

```
 push/PR a main
      │
      ▼
 ┌─────────────────────────────────────────────┐
 │ CI — ci.yml  (automático, sin AWS)           │
 │  • Tests Java ×6  (gate JaCoCo 90%)          │
 │  • Tests frontend ×2                         │
 │  • Tests BFF ×3  (player/organizer/admin)    │
 │  • Trivy fs scan  → falla en HIGH/CRITICAL   │  ← H-07
 └─────────────────────────────────────────────┘
      │  (cuando querés desplegar, con creds frescas)
      ▼
 ┌─────────────────────────────────────────────┐
 │ build-and-push.yml  (manual)                 │
 │  • Build 9 imágenes (incluye ms-etl)         │
 │  • Cache de capas (type=gha)                 │
 │  • Trivy image scan → gate HIGH/CRITICAL     │  ← H-07
 │  • Push a ECR (+ scan-on-push de AWS)        │
 └─────────────────────────────────────────────┘
      ▼
 ┌─────────────────────────────────────────────┐
 │ deploy.yml  (manual, toggles)                │
 │  • Stack ECS (task única, 10 contenedores)   │
 │  • Frontends → S3 (build Vite + sync)        │
 │  • ms-etl + Application Auto Scaling          │
 │  • Smoke test del readiness vía ALB          │
 └─────────────────────────────────────────────┘
```

## ¿Por qué el CD es manual?

Las credenciales de AWS Academy se vencen cada ~4 h y entran en modo `voc-cancel-cred`.
Si el deploy se disparara en cada push, fallaría en rojo en cada rotación. Por eso el
build/deploy es `workflow_dispatch`: refrescás los secrets y lo corrés con un clic.
La **CI sí es automática** porque no toca AWS — tu compañero hace push y obtiene
feedback de tests y seguridad al instante.

## Runners: self-hosted o GitHub-hosted

Todos los jobs usan `runs-on: ${{ vars.RUNNER_LABEL || 'self-hosted' }}`.

- **Por defecto** usan el runner **self-hosted** (esta cuenta tiene los hosted
  deshabilitados por billing).
- Para usar **runners GitHub-hosted**, definí la variable de repositorio
  `RUNNER_LABEL = ubuntu-latest`
  en *Settings → Secrets and variables → Actions → **Variables***.

## Optimizaciones aplicadas (carga más liviana)

- **CI sólo cuando importa**: `paths-ignore` salta docs, `*.drawio` y `tools/**`.
- **Cache de dependencias**: Maven (`~/.m2`) y npm en CI; cache de capas Docker
  (`type=gha`) en el build → rebuilds incrementales casi instantáneos.
- **Trivy con `ignore-unfixed`**: sólo frena por vulnerabilidades **con fix**
  disponible (evita ruido por CVEs sin parche).
- **Deploy por partes**: toggles `deploy_stack` / `deploy_frontend` / `deploy_etl`
  para publicar sólo lo que cambió.
- **`.dockerignore`** ya excluye `node_modules`, `target`, `dist`, `.git`, etc.,
  manteniendo el contexto y las imágenes livianas.

## Secrets y variables necesarios para el CD

> La CI **no** necesita nada de esto. Sólo el CD (build/deploy).

### Variables (Settings → Actions → Variables)
| Variable | Para qué | Ejemplo |
|----------|----------|---------|
| `RUNNER_LABEL` | elegir runner (opcional) | `ubuntu-latest` |

### Secrets (Settings → Actions → Secrets)
**AWS (rotan cada ~4 h — refrescar antes de cada deploy):**
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `AWS_REGION`, `AWS_ACCOUNT_ID`

**Stack ECS (ARNs de Secrets Manager y config):**
`TASK_EXECUTION_ROLE_ARN`, `DB_HOST`, `SUPABASE_URL`, `DB_PASSWORD_ARN`,
`RABBITMQ_PASSWORD_ARN`, `SUPABASE_SERVICE_KEY_ARN`, `JWT_SECRET_ARN`,
`SUPABASE_WEBHOOK_SECRET_ARN`, `GATEWAY_CORS_ALLOWED_ORIGINS`,
`SMTP_USERNAME`, `SMTP_PASSWORD_ARN`, `MAIL_FROM`

**Frontends → S3:**
`VITE_SUPABASE_ANON_KEY`, `VITE_API_URL`, `VITE_PORTAL_URL`, `VITE_ORGANIZER_URL`,
`S3_BUCKET_PORTAL`, `S3_BUCKET_ORGANIZER`

**ETL (opcional) y smoke test:**
`VPC_ID`, `ALB_DNS`

> Alternativa a las access keys: definir `GH_OIDC_ROLE_ARN` para autenticarse por
> OIDC (si la cuenta lo permite; Academy a veces lo bloquea).

## Pasos para tu compañero (deploy desde cero)

1. Cargar los secrets/variables de arriba una vez (los ARNs salen de
   `infrastructure/aws/.deploy-outputs.env` tras correr `setup-aws.sh`).
2. **Refrescar las 5 credenciales de AWS** (rotan cada ~4 h).
3. Actions → **Build & push images to ECR** → Run workflow (anota el tag `sha-XXXXXXX`).
4. Actions → **Deploy to AWS** → Run workflow con ese `image_tag` y los toggles deseados.
5. Verificar: el smoke test del workflow, o `tools/lazychess smoke`.

> Para operar desde local (prender/apagar/deploy sin GitHub) está `tools/lazychess` — ver su manual en [`tools/LAZYCHESS.md`](../tools/LAZYCHESS.md).
