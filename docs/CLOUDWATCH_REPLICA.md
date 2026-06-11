# ChessQuery — Observabilidad con CloudWatch (réplica de Martin)

> **Qué es este doc:** la configuración de **CloudWatch** (T5 del `ROADMAP_V1`) en la réplica AWS
> propia de Martin (cuenta `876204681432`, región `us-east-1`). Logs, métricas, alarmas y dashboard.
> Complementa `docs/DESPLIEGUE_REPLICA_AWS.md` (operación general) y `docs/DESPLIEGUE_ETL.md`.
> Configurado el **2026-06-11**.

## Resumen — qué quedó montado

| Pieza | Estado | Detalle |
|---|---|---|
| **Logs** | ✅ | Los contenedores ya logean vía `awslogs`. Retención puesta en **14 días**. |
| **Container Insights** | ✅ habilitado | Métricas por task (CPU/Mem/RunningTaskCount). *Populan tras el próximo redeploy de tasks.* |
| **Alarmas** | ✅ 5 creadas | Sin SNS aún (visibles en consola/dashboard). Ver §3 para sumar el mail. |
| **Dashboard** | ✅ `ChessQuery-Replica` | Stack, ETL, RDS, ALB + panel de alarmas. |

---

## 0. Accesos rápidos (consola, región `us-east-1`)

| Vista | Para qué | Link |
|---|---|---|
| **CloudWatch (home)** | Punto de entrada a todo | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1 |
| **Dashboard `ChessQuery-Replica`** | Estado general de un vistazo | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards/dashboard/ChessQuery-Replica |
| **Log groups** | Ver/buscar logs por servicio | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:log-groups |
| **Logs Insights** | Consultar logs con queries (SQL-like) | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:logs-insights |
| **Alarms** | Estado e historial de las alarmas | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#alarmsV2: |
| **Metrics** | Explorar todas las métricas | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#metricsV2 |
| **Container Insights** | Mapa de cluster/servicios/tasks | https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#container-insights:infrastructure |

> ⚠️ Estas vistas muestran datos **solo con la infra prendida**. Con todo apagado se ven vacías.

---

## 1. Logs

Dos log groups (los crea ECS con el `awslogs` driver de cada task-def):

| Log group | Qué contiene |
|---|---|
| `/ecs/chessquery/stack` | **Los 10 contenedores** de la task principal, separados por *stream prefix* (gateway, ms-*, bffs, rabbitmq, redis). |
| `/ecs/chessquery/ms-etl` | El `ms-etl` (service `chessquery-etl`). |

Retención fijada a **14 días** (antes era `None` = nunca expiran → crecían sin límite):

```bash
aws logs put-retention-policy --log-group-name /ecs/chessquery/stack  --retention-in-days 14
aws logs put-retention-policy --log-group-name /ecs/chessquery/ms-etl --retention-in-days 14
```

Ver logs en vivo:
```bash
aws logs tail /ecs/chessquery/stack  --since 10m --follow
aws logs tail /ecs/chessquery/ms-etl --since 10m --follow
```

> Nota: todo el stack comparte **un** log group. Es aceptable para v1 (los streams separan por
> contenedor); si se quiere un log group por servicio, hay que tocar cada `logConfiguration` en
> las task-defs.

---

## 2. Container Insights

Habilitado a nivel cluster:

```bash
aws ecs update-cluster --cluster chessquery-cluster --settings name=containerInsights,value=enabled
```

⚠️ **Las métricas de Insights (incluida `RunningTaskCount`) solo empiezan a reportar cuando las
tasks se reinician.** Como se habilitó con el stack ya corriendo, la alarma `task-down` queda en
`INSUFFICIENT_DATA` hasta el **próximo redeploy** del stack. No es un error.

---

## 3. Alarmas

5 alarmas, prefijo `chessquery-`. Recién creadas arrancan en `INSUFFICIENT_DATA` y pasan a `OK`
a medida que llegan las métricas (minutos).

| Alarma | Namespace · Métrica | Umbral | Cubre |
|---|---|---|---|
| `chessquery-stack-task-down` | `ECS/ContainerInsights` · `RunningTaskCount` | `< 1` (3×60s) | El stack se cayó (sin tasks) |
| `chessquery-stack-cpu-high` | `AWS/ECS` · `CPUUtilization` | `> 85%` (2×5m) | CPU saturada |
| `chessquery-stack-mem-high` | `AWS/ECS` · `MemoryUtilization` | `> 85%` (2×5m) | Memoria saturada |
| `chessquery-rds-storage-low` | `AWS/RDS` · `FreeStorageSpace` | `< 2 GB` (1×5m) | RDS por llenarse |
| `chessquery-alb-5xx` | `AWS/ApplicationELB` · `HTTPCode_Target_5XX_Count` | `> 5` (1×5m) | Errores 5xx de la app |

Ver estado:
```bash
aws cloudwatch describe-alarms --alarm-name-prefix chessquery- \
  --query 'MetricAlarms[].{Name:AlarmName,State:StateValue}' --output table
```

### (Opcional) Que las alarmas avisen por mail — SNS

Hoy las alarmas **no tienen acción** (solo cambian de estado, visibles en el dashboard). Para
recibir un correo cuando saltan:

```bash
# 1) crear el topic y suscribir tu mail (te llega un correo de confirmación: hay que aceptarlo)
TOPIC_ARN=$(aws sns create-topic --name chessquery-alarms --query TopicArn --output text)
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol email --notification-endpoint TU_MAIL@gmail.com

# 2) wirear cada alarma al topic (--alarm-actions) — ejemplo con una:
aws cloudwatch put-metric-alarm --alarm-name chessquery-stack-task-down \
  --alarm-actions "$TOPIC_ARN"  # (repetir los demás parámetros de la alarma)
```

---

## 4. Dashboard

`ChessQuery-Replica` — un solo panel con: CPU/Mem del **stack**, CPU/Mem del **ETL**, **RDS**
(CPU, conexiones, espacio libre), **ALB** (requests, 5xx, latencia) y el **estado de las 5 alarmas**.

- Consola: **CloudWatch → Dashboards → ChessQuery-Replica**
  `https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards/dashboard/ChessQuery-Replica`
- Recrear desde cero: `aws cloudwatch put-dashboard --dashboard-name ChessQuery-Replica --dashboard-body file://<json>`
  (el JSON usa el sufijo del ALB `app/chessquery-alb/1e7d7f657f26516c`; si se recrea el ALB, actualizarlo).

---

## 5. Qué más hay disponible en CloudWatch

Más allá de lo que ya dejamos armado, estas capacidades están disponibles sin configurar nada extra:

### 5.1 Logs Insights — consultar logs con queries
Permite buscar/filtrar/agrupar logs como si fuera SQL. En la consola (link en §0) elegí el log group
y pegá una query. Ejemplos útiles para ChessQuery:

```sql
-- Errores en cualquier contenedor del stack (últimos)
fields @timestamp, @logStream, @message
| filter @message like /ERROR|Exception/
| sort @timestamp desc | limit 50

-- Solo el gateway (por prefijo de stream)
fields @timestamp, @message
| filter @logStream like /api-gateway/
| sort @timestamp desc | limit 50

-- Webhooks de Supabase recibidos
fields @timestamp, @message
| filter @message like /user.registered|webhook/
| sort @timestamp desc | limit 30
```
Por CLI: `aws logs start-query ...` o el más simple `aws logs tail /ecs/chessquery/stack --filter-pattern ERROR`.

### 5.2 Métricas disponibles (namespaces)
Se pueden graficar/alarmar sin tocar nada:

| Namespace | Qué trae | Ejemplos |
|---|---|---|
| `AWS/ECS` | CPU/Mem por **service** | `CPUUtilization`, `MemoryUtilization` (stack y etl) |
| `ECS/ContainerInsights` | Por **task/contenedor** (tras redeploy) | `RunningTaskCount`, `CpuUtilized`, `MemoryUtilized`, `NetworkRxBytes` |
| `AWS/RDS` | Base de datos | `CPUUtilization`, `FreeStorageSpace`, `DatabaseConnections`, `FreeableMemory`, `Read/WriteLatency` |
| `AWS/ApplicationELB` | Tráfico del ALB | `RequestCount`, `HTTPCode_Target_2XX/4XX/5XX_Count`, `TargetResponseTime`, `HealthyHostCount` |

### 5.3 Otras vistas
- **Container Insights → mapa** del cluster (servicios/tasks con su salud y consumo) — link en §0.
- **Alarms → historial**: cada alarma guarda su línea de tiempo de cambios de estado.
- **Metrics → "Explore"**: armar gráficos ad-hoc de cualquier métrica de arriba (y agregarlos al dashboard).
- *(No habilitado)* **X-Ray / Service map** (traza distribuida) y **Synthetics** (canaries) — quedan como mejora post-v1.

---

## 6. Notas de operación

- **Datos en frío:** alarmas y métricas necesitan que el stack esté **prendido** para tener datos.
  Con todo apagado, el dashboard se ve vacío y las alarmas quedan `INSUFFICIENT_DATA` (normal).
- **Costo:** CloudWatch en este volumen es prácticamente gratis; lo que gasta créditos es el
  ECS/RDS prendido (ver rutina de apagado en `docs/DESPLIEGUE_REPLICA_AWS.md`).
- **Pendiente para "T5 completo":** sumar el SNS+mail (§3) y, si se quiere, regenerar la alarma
  `task-down` tras el próximo redeploy del stack (para que tenga datos de Insights).

## Referencias
- `docs/ROADMAP_V1.md` §T5 · `docs/DESPLIEGUE_REPLICA_AWS.md` · `docs/DESPLIEGUE_ETL.md`
