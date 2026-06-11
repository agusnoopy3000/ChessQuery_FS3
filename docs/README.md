# Índice de documentación — ChessQuery FS3

Mapa de toda la documentación del proyecto. Los docs operativos clave también están enlazados
desde el [README raíz](../README.md).

## Arquitectura y diseño
| Doc | Contenido |
|---|---|
| [CONTEXT.md](./CONTEXT.md) | Contexto compartido: ERD, contratos REST, eventos RabbitMQ, formato JWT y convenciones de naming. |
| [IMPLEMENTACION.md](./IMPLEMENTACION.md) | Documento de implementación: arquitectura general capa por capa. |
| [ANALISIS_PATRONES.md](./ANALISIS_PATRONES.md) | 8 patrones de diseño + 6 arquitectónicos + arquetipo Maven (rúbrica 1, 2, 5, 6). |
| [PLAN_LICHESS_Y_UX_EN_VIVO.md](./PLAN_LICHESS_Y_UX_EN_VIVO.md) | Diseño de la integración con la API de Lichess + mejoras de UX del tablero en vivo (inspiradas en en-croissant). |

## Setup y operación
| Doc | Contenido |
|---|---|
| [SUPABASE_SETUP.md](./SUPABASE_SETUP.md) | Setup del Supabase local (genérico Mac/Linux). Para Arch ver `RUN_ARCH.md` en la raíz. |
| [PLAN_SUPABASE_CLOUD.md](./PLAN_SUPABASE_CLOUD.md) | Plan paso a paso para migrar de Supabase local a Supabase Cloud (managed) de cara al deploy. |
| [README-WINDOWS.md](./README-WINDOWS.md) | Setup en Windows con `setup.ps1`. |
| [SELF_HOSTED_RUNNER.md](./SELF_HOSTED_RUNNER.md) | Configurar el runner self-hosted de GitHub Actions para el CI. |
| [OPERACION_DEMO.md](./OPERACION_DEMO.md) | Cheat-sheet de operación de la demo: encender/apagar el stack, usuarios y verificación. |
| [ENDPOINTS.md](./ENDPOINTS.md) | Referencia en español de todas las rutas: qué hace cada una, quién la llama, headers y renombres propuestos. |
| [ENDPOINTS_QA.md](./ENDPOINTS_QA.md) | Listado de endpoints de la plataforma para QA / pruebas manuales. |

## Despliegue y AWS
| Doc | Contenido |
|---|---|
| [INFRA_PARA_EL_EQUIPO.md](./INFRA_PARA_EL_EQUIPO.md) | Cómo está desplegado ChessQuery, explicado de forma simple para el equipo. |
| [DESPLIEGUE_REPLICA_AWS.md](./DESPLIEGUE_REPLICA_AWS.md) | Despliegue y operación de la réplica en AWS (cuenta propia): encender/apagar, costos. |
| [SETUP_AWS_COMPLETO.md](./SETUP_AWS_COMPLETO.md) | Setup completo de AWS desde cero para replicar toda la infra en otra cuenta Academy. |
| [SETUP_SUPABASE_REPLICA.md](./SETUP_SUPABASE_REPLICA.md) | Setup del Supabase propio (hosted) para una réplica independiente apuntando a tu ALB. |
| [DESPLIEGUE_AWS_REALIZADO.md](./DESPLIEGUE_AWS_REALIZADO.md) | Bitácora paso a paso de lo que se hizo en el despliegue AWS. |
| [DESPLIEGUE_ETL.md](./DESPLIEGUE_ETL.md) | ms-etl como task ECS separada (T3): deploy, Auto Scaling, verificación Lichess/Chess.com. |
| [CLOUDWATCH_REPLICA.md](./CLOUDWATCH_REPLICA.md) | Observabilidad (T5) en la réplica: logs, Container Insights, alarmas, dashboard y accesos a la consola. |
| [FRONTEND_S3_Y_ALB.md](./FRONTEND_S3_Y_ALB.md) | Guía de implementación del frontend en S3 + ALB para el gateway. |
| [PENDIENTE_HTTPS.md](./PENDIENTE_HTTPS.md) | Plan pendiente para habilitar HTTPS (CloudFront) en el frontend. |

> Detalle de infraestructura ECS en [`infrastructure/aws/`](../infrastructure/aws/) (`DEPLOY_ECS.md`, `RUNBOOK_ECS.md`).

## Pruebas
| Doc | Contenido |
|---|---|
| [PRUEBAS.md](./PRUEBAS.md) | Estrategia y detalle de pruebas unitarias e integración, cobertura JaCoCo y pasos futuros. |
| [PLAN_TESTS_ORGANIZER_TOURNAMENTS.md](./PLAN_TESTS_ORGANIZER_TOURNAMENTS.md) | Plan (Prioridad 1) para subir `organizer-panel` de 52% a ≥75% cubriendo `OrganizerTournaments.tsx`. |
| [PLAN_TESTS_JWT_FILTER.md](./PLAN_TESTS_JWT_FILTER.md) | Plan (Prioridad 2) para subir `api-gateway` a ≥80% cubriendo la maquinaria ES256/JWKS de `SupabaseJwtAuthFilter`. |
| [REVISION_CODIGO_FASES_0-2.md](./REVISION_CODIGO_FASES_0-2.md) | Informe de revisión de código (línea base de cobertura + hallazgos transversales y de los servicios core). |

> Para los **comandos** de ejecución de tests ver [`TESTING.md`](../TESTING.md) en la raíz.

## Seguridad y cumplimiento
| Doc | Contenido |
|---|---|
| [SECURITY_AUDIT_REPORT.md](./SECURITY_AUDIT_REPORT.md) | Informe de auditoría de seguridad (hallazgos H-01..H-14). |
| [SECURITY_PLAN.md](./SECURITY_PLAN.md) | Plan de hardening priorizado (Supabase, API Gateway, microservicios). |
| [specs/SPEC_LEY21719.md](./specs/SPEC_LEY21719.md) | Cumplimiento de la Ley 21.719 (protección de datos personales). |

## Especificaciones
| Doc | Contenido |
|---|---|
| [specs/HISTORIAS_USUARIO.md](./specs/HISTORIAS_USUARIO.md) | Historias de usuario. |
| [specs/SPEC_BRECHAS.md](./specs/SPEC_BRECHAS.md) | Spec de cierre de las 5 brechas "demo-ready" (histórico, 2026-04). |

## Entrega académica
| Doc | Contenido |
|---|---|
| [repositorios.txt](./repositorios.txt) | Enlaces a los repositorios (requisito de entrega). |
| [ROADMAP_V1.md](./ROADMAP_V1.md) | Plan a producto final v1: 8 tareas priorizadas (T1–T8), estado y plan de 2 semanas. |
| [ORAL_DEFENSE_CHEAT_SHEET.md](./ORAL_DEFENSE_CHEAT_SHEET.md) | Argumentos clave para la defensa oral (rúbrica 5, 6, 8). |
| [minutas/](./minutas/) | Minutas de reuniones (PDF/PNG). |

## Investigación / troubleshooting
| Doc | Contenido |
|---|---|
| [MAGIC_LINK_LAN.md](./MAGIC_LINK_LAN.md) | Investigación + workaround del magic link de Supabase Auth sobre IP de LAN. |
