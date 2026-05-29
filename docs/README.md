# Índice de documentación — ChessQuery FS3

Mapa de toda la documentación del proyecto. Los docs operativos clave también están enlazados
desde el [README raíz](../README.md).

## Arquitectura y diseño
| Doc | Contenido |
|---|---|
| [CONTEXT.md](./CONTEXT.md) | Contexto compartido: ERD, contratos REST, eventos RabbitMQ, formato JWT y convenciones de naming. |
| [IMPLEMENTACION.md](./IMPLEMENTACION.md) | Documento de implementación: arquitectura general capa por capa. |
| [ANALISIS_PATRONES.md](./ANALISIS_PATRONES.md) | 8 patrones de diseño + 6 arquitectónicos + arquetipo Maven (rúbrica 1, 2, 5, 6). |

## Setup y operación
| Doc | Contenido |
|---|---|
| [SUPABASE_SETUP.md](./SUPABASE_SETUP.md) | Setup del Supabase local (genérico Mac/Linux). Para Arch ver `RUN_ARCH.md` en la raíz. |
| [PLAN_SUPABASE_CLOUD.md](./PLAN_SUPABASE_CLOUD.md) | Plan paso a paso para migrar de Supabase local a Supabase Cloud (managed) de cara al deploy. |
| [README-WINDOWS.md](./README-WINDOWS.md) | Setup en Windows con `setup.ps1`. |
| [SELF_HOSTED_RUNNER.md](./SELF_HOSTED_RUNNER.md) | Configurar el runner self-hosted de GitHub Actions para el CI. |

## Pruebas
| Doc | Contenido |
|---|---|
| [PRUEBAS.md](./PRUEBAS.md) | Estrategia y detalle de pruebas unitarias e integración, cobertura JaCoCo y pasos futuros. |

> Para los **comandos** de ejecución de tests ver [`TESTING.md`](../TESTING.md) en la raíz.

## Seguridad y cumplimiento
| Doc | Contenido |
|---|---|
| [SECURITY_PLAN.md](./SECURITY_PLAN.md) | Auditoría de seguridad + plan priorizado (Supabase, API Gateway, microservicios). |
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
| [ORAL_DEFENSE_CHEAT_SHEET.md](./ORAL_DEFENSE_CHEAT_SHEET.md) | Argumentos clave para la defensa oral (rúbrica 5, 6, 8). |
| [minutas/](./minutas/) | Minutas de reuniones (PDF/PNG). |

## Investigación / troubleshooting
| Doc | Contenido |
|---|---|
| [MAGIC_LINK_LAN.md](./MAGIC_LINK_LAN.md) | Investigación + workaround del magic link de Supabase Auth sobre IP de LAN. |

---
_Despliegue AWS: ver [`infrastructure/aws/`](../infrastructure/aws/) (`DEPLOY_ECS.md`, `RUNBOOK_ECS.md`)._
