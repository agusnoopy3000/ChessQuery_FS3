# DevOps — planificación

Carpeta para planificar y documentar el trabajo de DevOps de ChessQuery: CI/CD,
infraestructura AWS, observabilidad, operación y todo lo que haga el despliegue
**repetible, seguro y observable**.

> Espacio de **planificación**. Lo que ya está hecho/operativo vive en sus docs de
> referencia (ver abajo); acá se planifican los próximos pasos.

## Índice

| Doc | Tema | Estado |
|-----|------|--------|
| [PLAN_EP3_DEVOPS.md](PLAN_EP3_DEVOPS.md) | Plan de la **EP3 (ISY1101)**: mapeo de ChessQuery a la rúbrica, arquitectura, secretos, CloudWatch, autoscaling, presentación, gaps y roadmap | 🟨 en armado |
| [INFORME_EP3.md](INFORME_EP3.md) | **Esqueleto del informe** de entrega (secciones por indicador + marcadores de captura) | 🟨 por completar |
| `EP3_Instrucciones y Pauta Encargo_Estudiante.pdf` | Pauta oficial de la EP3 (fuente) | 📎 referencia |

## Contexto del producto

Estamos en la ventana a **producto v1** (ver `../ROADMAP_V1.md`). Lo de DevOps cae sobre
todo en **T5 (observabilidad)** y **T6 (CI/CD + operación)**.

### Estado rápido (al 19-jun-2026)
- **CI/CD:** workflows `ci.yml` (tests + gate cobertura 90% + gate Trivy H-07) y
  `build-and-push.yml` / `deploy.yml` (manuales, AWS). Corren sobre **runner self-hosted**.
- **Bloqueo actual:** el runner self-hosted está **offline** → los gates no corren.
  Plan: **pool de 2 runners** (Agustín + Martin) para redundancia. Martin pendiente de
  permiso **Admin** en el repo para registrar el suyo.
- **Observabilidad:** CloudWatch montado en la réplica (5 alarmas + dashboard).
- **Pendiente operación:** backups RDS, correos e2e (con Agustín).

## Docs de referencia (lo ya existente)
- `../CICD_PIPELINE.md` — pipeline actual (flujo, secrets, runners).
- `../SELF_HOSTED_RUNNER.md` — registrar/operar el runner.
- `../CLOUDWATCH_REPLICA.md` — observabilidad de la réplica.
- `../DESPLIEGUE_REPLICA_AWS.md` — operación de la réplica AWS de Martin.
- `../HARDENING_PENDIENTE.md` — hardening diferido (H-06, H-02).
