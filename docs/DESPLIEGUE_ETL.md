# Despliegue de ms-etl en AWS — task ECS separada + Auto Scaling

> Cierra el **T3 del ROADMAP_V1** con la opción "task separada" (se mantiene
> RabbitMQ dentro del stack, sin Amazon MQ, como acordó el equipo). El ETL
> sincroniza ratings de **Lichess y Chess.com** (fuentes reales) contra ms-users.

## Por qué una task separada

AWS Academy limita **10 contenedores por task** y `chessquery-stack` ya los usa.
`ms-etl` corre entonces como **segundo service ECS** (`chessquery-etl`) en el
mismo cluster, con su propia task definition
(`infrastructure/aws/task-definitions/ms-etl.template.json`).

```
   [service chessquery-stack]  1 task · 10 contenedores
        gateway · ms-* · bffs · rabbitmq · redis
              ▲ 5672 / 6379 / 8081 (reglas self del SG, NO públicas)
              │
   [service chessquery-etl]    1–2 tasks (Auto Scaling) · ms-etl :8086
              │
              ▼ salida a internet: api.chess.com · lichess.org
   [RDS chessquery-pg]  ◄── etl_db
```

## La limitación importante (leer antes de operar)

Academy **bloquea Service Discovery/Cloud Map**, así que ms-etl no puede
descubrir por DNS al RabbitMQ/Redis/ms-users que viven dentro de la task del
stack. El script de deploy resuelve la **IP privada** de la task del stack y la
hornea en la task-def del ETL.

⚠️ **Si `chessquery-stack` se redespliega o se apaga/prende, su IP cambia → hay
que re-correr `bash scripts/deploy-etl-service.sh`** (tarda ~1 min y no toca el
stack). Síntoma de IP vieja: los runs del ETL fallan con timeouts a RabbitMQ y
`/etl/status` muestra circuit breakers abiertos.

## Despliegue (desde cero o actualización)

```bash
# 0) credenciales Academy frescas + stack corriendo
export IMAGE_TAG=v0.3.1            # mismo tag que el resto de imágenes

# 1) build + push de la imagen (build-push-ecr.sh ya incluye ms-etl)
bash scripts/build-push-ecr.sh

# 2) deploy del service + autoscaling (idempotente)
bash scripts/deploy-etl-service.sh

# con acceso para disparar syncs desde tu PC:
ETL_ADMIN_CIDR=$(curl -s https://checkip.amazonaws.com)/32 bash scripts/deploy-etl-service.sh
```

El script hace: ① resuelve la IP del stack · ② reglas self del SG para
5672/6379/8081 (tráfico interno, nada nuevo expuesto a internet) · ③ registra la
task-def · ④ crea/actualiza el service · ⑤ configura **Application Auto Scaling**.

## Auto Scaling configurado

| Parámetro | Valor | Por qué |
|---|---|---|
| Min / Max tasks | **1 / 2** | una task alcanza en reposo; la 2ª absorbe syncs pesados. Max 2 cuida los créditos Academy |
| Métrica | `ECSServiceAverageCPUUtilization` | los syncs (AJEFECH scrape, batches Lichess/Chess.com) son CPU-bound |
| Target | **70 %** | escala antes de saturar sin oscilar |
| Cooldown out / in | 120 s / 300 s | sale rápido, vuelve a 1 con calma (los syncs son ráfagas) |

Ver estado: `aws application-autoscaling describe-scalable-targets --service-namespace ecs`
y `describe-scaling-policies --service-namespace ecs`.

> Nota: los syncs se disparan por POST y son idempotentes por fuente; con 2
> réplicas cada disparo cae en una sola task (no se duplican corridas).

## Verificación e2e (Chess.com + Lichess)

```bash
# 1) IP pública de la task ETL (igual que el RUNBOOK §4.4 pero del service etl)
TASK=$(aws ecs list-tasks --cluster chessquery-cluster --service-name chessquery-etl --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster chessquery-cluster --tasks $TASK --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value | [0]" --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

# 2) salud y fuentes (requiere ETL_ADMIN_CIDR en el deploy)
curl http://$IP:8086/health                      # {"status":"UP","service":"ms-etl"}
curl http://$IP:8086/etl/status                  # fuentes + circuit breakers

# 3) disparar syncs reales
curl -X POST http://$IP:8086/etl/sync/lichess
curl -X POST http://$IP:8086/etl/sync/chesscom

# 4) confirmar que llegó a ms-users (vía la app: perfil del jugador,
#    o por SQL: SELECT lichess_username, chesscom_username, elo_chesscom_blitz
#    FROM player WHERE chesscom_username IS NOT NULL;)
```

Para que el sync de Chess.com tenga a quién sincronizar, el jugador debe tener
`chesscom_username` en su perfil (`PUT /users/{id}/profile`) — o acotar con la
env `CHESSCOM_USERNAMES` en la task-def para una demo.

## Costos / apagado

El ETL se apaga junto con la rutina normal de la demo:

```bash
aws ecs update-service --cluster chessquery-cluster --service chessquery-etl --desired-count 0
```

(al re-prender, `desired-count 1`; el autoscaling sigue registrado). El
autoscaling **nunca** baja de min=1 por sí solo — para no gastar, apagar a mano.

## Referencias
- `scripts/deploy-etl-service.sh` · `infrastructure/aws/task-definitions/ms-etl.template.json`
- `docs/ROADMAP_V1.md` §T3 · `docs/DESPLIEGUE_REPLICA_AWS.md` (operación general)
- `ms-etl/README.md` (endpoints y fuentes)
