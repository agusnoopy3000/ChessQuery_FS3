# Informe EP3 — Introducción a Herramientas DevOps (ISY1101)

> **Esqueleto del informe de entrega.** Se completa con texto + **capturas** y se entrega por
> AVA **antes** de la presentación. Cada sección apunta a un indicador de la rúbrica (IE).
> Reusar lo ya escrito en `PLAN_EP3_DEVOPS.md`, `../CICD_PIPELINE.md`, `../CLOUDWATCH_REPLICA.md`
> y `../DESPLIEGUE_REPLICA_AWS.md` — **no escribir de cero**.
>
> _Marcadores:_ `[CAPTURA: ...]` = poner screenshot · `[COMPLETAR: ...]` = redactar.

---

## 1. Portada
- Asignatura: Introducción a Herramientas DevOps (ISY1101) — EP3.
- Integrantes de la dupla: `[COMPLETAR: Martin + compañera]`.
- Proyecto: **ChessQuery** — plataforma de torneos de ajedrez.
- Fecha · repositorios entregados (URLs) · cuenta AWS usada.

## 2. Introducción y objetivo
- Qué se pide en la EP3 (orquestación + automatización en la nube).
- Qué es ChessQuery en una línea y por qué sirve como caso (front + back reales).
- `[COMPLETAR: 1 párrafo]`

## 3. Arquitectura de la solución  · (IE1)
- **Diagrama general** `[CAPTURA: diagrama de ARQUITECTURA_AWS.drawio / el de PLAN_EP3 §3]`.
- Descripción: ECS Fargate, ECR, ALB, RDS, S3, CloudWatch.
- **Justificación ECS Fargate vs EKS** (Academy, costo, sin administrar nodos) — ver PLAN §2.1.
- **Redes y seguridad — "sin acceso a la VPC":** SG ALB (público :80) → SG task (solo desde ALB)
  → SG RDS (solo desde task, no público). `[CAPTURA: reglas de cada Security Group]`.
- **Roles IAM:** execution role / task role (LabRole) y qué permisos usa cada uno.
- `[COMPLETAR: por qué esta arquitectura es la de mínimo costo]`

## 4. Despliegue de servicios (Frontend + Backend)  · (IE2)
- **Imágenes en ECR** (9 repos). `[CAPTURA: consola ECR con las imágenes y tags]`.
- **Task Definition** del stack: contenedores, puertos, variables de entorno. `[CAPTURA]`.
- **ALB** y URL pública de la API. `[CAPTURA: target group healthy + DNS del ALB]`.
- **Frontend en S3** (decisión de costo): URL pública del sitio. `[CAPTURA: sitio funcionando]`.
- **Comunicación Front → Back** vía ALB. `[COMPLETAR: explicar el flujo]`.

## 5. Autoscaling  · (IE3)
- Servicio con autoscaling: `chessquery-etl` (Application Auto Scaling, target tracking).
- **Umbral elegido y justificación** (ej. CPU 50%, min 1 / max 2). `[CAPTURA: config]`.
- **Evidencia de escalado bajo carga:** `[CAPTURA: CloudWatch mostrando CPU sube → +1 task → baja]`.
- `[COMPLETAR: descripción de la simulación de carga usada]` (ver PLAN §7).

## 6. Pipeline CI/CD con GitHub Actions  · (IE4)
- **Por qué GitHub Actions** (nativo, runners gratis, marketplace, OIDC) — ver PLAN §2.2.
- Los 3 workflows: `ci.yml` (auto), `build-and-push.yml`, `deploy.yml` (manuales). `[CAPTURA: lista de workflows]`.
- **Flujo build → push → deploy** y por qué el CD es manual (creds Academy rotan).
- **Deploy automatizado tras commit:** `[CAPTURA: run de Actions verde, de commit a deploy]`.
- Runners (self-hosted / hosted) y optimizaciones (cache, paths-ignore).

## 7. Gestión de secretos  · (IE5)
- **AWS Secrets Manager** (runtime): tabla de secretos (db, jwt, webhook, smtp...). `[CAPTURA: lista de secrets, sin valores]`.
- **GitHub Actions Secrets/Variables** (pipeline): AWS creds + ARNs + config.
- **Higiene:** `.env` gitignored, gitleaks, sin secretos en imagen ni logs. Ver PLAN §4.

## 8. Observabilidad — CloudWatch  · (IE6)
- **Logs** por contenedor (awslogs, log group por servicio, retención 14d). `[CAPTURA: logs]`.
- **Métricas** (Container Insights: CPU/Mem por task). `[CAPTURA]`.
- **5 alarmas + dashboard `ChessQuery-Replica`.** `[CAPTURA: dashboard]`.
- **Análisis del pipeline:** tiempos por job, efecto del cache, fallos. `[COMPLETAR: tabla de tiempos + conclusión]`.

## 9. Validación funcional (Front → Back)  · (IE7)
- Recorrido e2e: registro → webhook → ALB → ms-users → RDS. `[CAPTURA: usuario creado en RDS]`.
- Endpoints respondiendo, logs del flujo. `[CAPTURA]`.
- **Recuperación post-redeploy:** mostrar que tras un redeploy el servicio vuelve solo. `[CAPTURA]`.

## 10. Problemas encontrados y soluciones  · (defensa / mejora continua)
> Esto suma mucho en la defensa. Reales del proyecto:
- Runner self-hosted offline → checks en cola. Solución: `[COMPLETAR]`.
- Credenciales Academy rotan cada ~4 h → CD manual. Solución aplicada.
- ETL hornea la IP privada (Academy sin Service Discovery) → re-deploy tras encender.
- Gate Trivy vs CVEs de Spring (frameworks fuera de soporte) → `ignore-unfixed` / modo reporte.
- `[COMPLETAR: otros que aparezcan]`.

## 11. Conclusiones
- Qué se logró vs lo pedido. Aprendizajes. Mejoras futuras (HTTPS, bump de frameworks).
- `[COMPLETAR]`

## 12. Anexos
- URLs (repos, ALB, sitio S3, dashboard).
- Referencias a los docs del repo.
