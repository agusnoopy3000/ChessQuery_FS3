# Bugfix Requirements Document

## Introduction

El documento PLAN_DEMO.md actual incluye referencias a servicios y funcionalidades que no estarán operativos para la demo. Específicamente, documenta escenarios que dependen de MS-Analytics y BFF-Admin, servicios que aún están en desarrollo y no estarán disponibles para la presentación. Esto genera expectativas incorrectas sobre el alcance de la demo y puede causar confusión durante la preparación y ejecución de la presentación.

El alcance real de la demo se limita a 9 servicios backend (api-gateway, bff-player, bff-organizer, ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications) y 2 frontends (chess-portal, organizer-panel), junto con la infraestructura de soporte (PostgreSQL, RabbitMQ, MinIO/S3).

Este bugfix corrige la documentación para reflejar únicamente los servicios operativos, eliminando referencias a funcionalidades no disponibles mientras preserva la estructura y secciones útiles del plan (monitoreo, cronograma, checklist).

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN el plan documenta la "Escena E — Analytics y admin" THEN el sistema incluye endpoints de MS-Analytics que no estarán operativos (`GET /admin/dashboard`, `GET /analytics/players/{id}/stats`, `GET /analytics/platform/summary`)

1.2 WHEN el plan documenta endpoints de BFF-Admin THEN el sistema incluye servicios que no estarán disponibles para la demo (`GET /admin/dashboard`, `GET /admin/etl/status`, `POST /admin/etl/sync/:source`)

1.3 WHEN el plan lista "Endpoints faltantes / a crear" THEN el sistema incluye MS-Analytics como prioridad 1 bloqueante, generando expectativas incorrectas sobre el trabajo requerido

1.4 WHEN el plan documenta el flujo UI de admin THEN el sistema referencia admin-panel que no estará operativo para la demo

1.5 WHEN el plan documenta servicios en la sección de Actuator THEN el sistema incluye ms-analytics en la lista de servicios Java que recibirán configuración de monitoreo

### Expected Behavior (Correct)

2.1 WHEN el plan documenta escenas de demo THEN el sistema SHALL incluir únicamente escenas que dependen de servicios operativos (Escenas A, B, C, D, F) y SHALL omitir la Escena E completa

2.2 WHEN el plan documenta endpoints críticos THEN el sistema SHALL listar únicamente endpoints de servicios operativos: api-gateway, bff-player, bff-organizer, ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications

2.3 WHEN el plan lista "Endpoints faltantes / a crear" THEN el sistema SHALL enfocarse únicamente en endpoints necesarios para los servicios operativos y SHALL omitir referencias a MS-Analytics y BFF-Admin

2.4 WHEN el plan documenta flujos UI THEN el sistema SHALL referenciar únicamente chess-portal y organizer-panel como frontends operativos

2.5 WHEN el plan documenta configuración de Actuator THEN el sistema SHALL listar únicamente los 6 microservicios Java operativos (ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications) excluyendo ms-analytics

2.6 WHEN el plan documenta el rol de MS-ETL THEN el sistema SHALL aclarar que su función es servir datos a la plataforma para evitar que se vea vacía, no para funcionalidades de administración

### Unchanged Behavior (Regression Prevention)

3.1 WHEN el plan documenta la sección "Monitoreo de rendimiento" THEN el sistema SHALL CONTINUE TO incluir la configuración completa de Prometheus + Grafana con los ajustes necesarios para los servicios operativos

3.2 WHEN el plan documenta el cronograma sugerido THEN el sistema SHALL CONTINUE TO mantener la estructura temporal y las recomendaciones de congelación de código

3.3 WHEN el plan documenta el checklist pre-demo THEN el sistema SHALL CONTINUE TO incluir verificaciones de infraestructura (MinIO, RabbitMQ, PostgreSQL, seeds de datos)

3.4 WHEN el plan documenta las Escenas A, B, C, D THEN el sistema SHALL CONTINUE TO mantener la documentación completa de endpoints, flujos UI, y riesgos identificados

3.5 WHEN el plan documenta la Escena F (Notificaciones) THEN el sistema SHALL CONTINUE TO incluir las verificaciones de RabbitMQ y notification_log

3.6 WHEN el plan documenta la "Regla de oro" de congelación de código THEN el sistema SHALL CONTINUE TO mantener la recomendación de congelar main desde T-5 días

3.7 WHEN el plan documenta servicios de infraestructura (PostgreSQL, RabbitMQ, MinIO/S3, Redis) THEN el sistema SHALL CONTINUE TO referenciarlos como servicios operativos necesarios
