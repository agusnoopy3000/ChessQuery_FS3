# Implementation Plan

## Objetivo
Corregir PLAN_DEMO.md para reflejar únicamente los servicios operativos (9 backend + 2 frontend), eliminando referencias a MS-Analytics, BFF-Admin y admin-panel que no estarán disponibles para la demo.

---

## Tareas de Implementación

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Referencias a Servicios No Operativos
  - **CRITICAL**: Este test DEBE FALLAR en el código sin corregir - el fallo confirma que el bug existe
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: Este test codifica el comportamiento esperado - validará el fix cuando pase después de la implementación
  - **GOAL**: Identificar todas las instancias del bug en PLAN_DEMO.md original
  - **Scoped PBT Approach**: Búsqueda exhaustiva de términos específicos que indican servicios no operativos
  - Test implementation details:
    - Buscar todas las ocurrencias de "ms-analytics" en PLAN_DEMO.md (esperado: Escena E, endpoints faltantes, Actuator)
    - Buscar todas las ocurrencias de "bff-admin" en PLAN_DEMO.md (esperado: Escena E, tabla de endpoints)
    - Buscar todas las ocurrencias de "admin-panel" en PLAN_DEMO.md (esperado: Escena E, checklist)
    - Buscar todas las ocurrencias de "/admin" en contexto de rutas UI (esperado: múltiples en Escena E)
    - Verificar que listas de servicios Java incluyen ms-analytics incorrectamente (esperado: Actuator paso 1, Prometheus paso 2)
    - Buscar referencias a vistas frontend fuera de alcance: "Ranking top nacional", "Buscar jugadores", "/search", "/rankings" en contexto de portal público o player workspace
    - Verificar si Escena B documenta flujos UI de ranking/búsqueda que no estarán disponibles en frontends según alcance reducido
  - The test assertions should match the Expected Behavior Properties from design
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (esto es correcto - prueba que el bug existe)
  - Document counterexamples found:
    - Escena E completa documenta servicios no operativos como críticos
    - Sección de endpoints faltantes prioriza MS-Analytics como bloqueante
    - Configuración de Actuator incluye ms-analytics en lista de servicios
    - Posibles referencias a vistas frontend no disponibles en Escena B
    - Referencias a vistas de portal público que deben eliminarse
    - Referencias a vistas de chess-portal player workspace que deben eliminarse
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Contenido de Servicios Operativos
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for sections that do NOT reference non-operational services
  - Write property-based tests capturing observed behavior patterns from Preservation Requirements:
    - Verificar que Escena A (Registro y login) permanece completamente inalterada
    - Verificar que Escena C (Torneo end-to-end) permanece completamente inalterada
    - Verificar que Escena D (Partida y PGN) permanece completamente inalterada
    - Verificar que Escena F (Notificaciones) permanece completamente inalterada
    - Verificar que sección "Monitoreo de rendimiento" mantiene su estructura (pasos 1-5)
    - Verificar que cronograma sugerido mantiene su tabla de semanas
    - Verificar que checklist pre-demo mantiene items de infraestructura
    - Verificar que "Regla de oro" permanece sin cambios
  - Property-based testing generates many test cases for stronger guarantees
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (esto confirma el comportamiento base a preservar)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [ ] 3. Fix for servicios no operativos en PLAN_DEMO.md

  - [ ] 3.1 Eliminar Escena E completa
    - Remover toda la sección "Escena E — Analytics y admin"
    - Eliminar tabla de endpoints de bff-admin y ms-analytics
    - Eliminar descripción del flujo UI `/admin`, `/admin/etl`
    - Eliminar nota bloqueante sobre recuperar fuentes de MS-Analytics
    - Eliminar referencias a SPEC_BRECHAS.md brecha 1 en contexto de Escena E
    - _Bug_Condition: isBugCondition(section) where section.referencesService("ms-analytics") OR section.referencesService("bff-admin") OR section.referencesService("admin-panel")_
    - _Expected_Behavior: El documento debe incluir únicamente escenas que dependen de servicios operativos (Escenas A, B, C, D, F)_
    - _Preservation: Las Escenas A, B, C, D, F deben continuar documentadas completamente_
    - _Requirements: 1.1, 1.2, 1.4, 2.1, 2.2, 2.4_

  - [ ] 3.2 Actualizar sección "Endpoints faltantes / a crear"
    - Eliminar punto 1 sobre MS-Analytics ("todos sus endpoints. Bloquea escena E")
    - Eliminar punto 4 sobre ruta `/api/analytics/**` en Gateway
    - Mantener punto 3 sobre MS-Users si aplica a servicios operativos
    - Agregar aclaración sobre el rol de MS-ETL: "MS-ETL está operativo para servir datos a la plataforma y evitar que se vea vacía, no para funcionalidades de administración"
    - Actualizar punto 2 sobre MS-Notifications si es necesario (marcar como opcional)
    - _Bug_Condition: section.documentType == "PLAN_DEMO" AND section.referencesService("ms-analytics") in endpoints list_
    - _Expected_Behavior: El plan debe enfocarse únicamente en endpoints necesarios para los servicios operativos_
    - _Preservation: Mantener referencias a endpoints de servicios operativos_
    - _Requirements: 1.3, 2.3, 2.6_

  - [ ] 3.3 Actualizar configuración de Actuator (Paso 1)
    - Cambiar lista de servicios de: "ms-auth, ms-users, ms-tournament, ms-game, ms-analytics, ms-notifications"
    - A: "ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications"
    - Verificar que el conteo de "6 microservicios Java" es correcto
    - _Bug_Condition: section.referencesService("ms-analytics") in monitoring configuration_
    - _Expected_Behavior: El sistema debe listar únicamente los 6 microservicios Java operativos excluyendo ms-analytics_
    - _Preservation: Mantener la estructura completa de configuración de Actuator_
    - _Requirements: 1.5, 2.5, 3.1_

  - [ ] 3.4 Actualizar configuración de Prometheus (Paso 2)
    - Cambiar referencia de "6 microservicios Java + api-gateway"
    - A: "6 microservicios Java operativos (ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications) + api-gateway"
    - Agregar nota sobre el conteo correcto de servicios a monitorear
    - _Bug_Condition: section.referencesService("ms-analytics") in Prometheus scrape configuration_
    - _Expected_Behavior: Prometheus debe scrapear únicamente los servicios operativos_
    - _Preservation: Mantener la estructura completa de configuración de Prometheus + Grafana_
    - _Requirements: 1.5, 2.5, 3.1_

  - [ ] 3.5 Actualizar checklist pre-demo
    - Eliminar verificaciones relacionadas con admin-panel
    - Mantener verificaciones de chess-portal y organizer-panel
    - Mantener todas las verificaciones de infraestructura (MinIO, RabbitMQ, PostgreSQL, seeds)
    - Actualizar verificación de frontends para reflejar alcance reducido de vistas
    - _Bug_Condition: section.referencesService("admin-panel") in checklist items_
    - _Expected_Behavior: El checklist debe referenciar únicamente chess-portal y organizer-panel como frontends operativos_
    - _Preservation: Mantener todas las verificaciones de infraestructura_
    - _Requirements: 1.4, 2.4, 3.3_

  - [ ] 3.6 Agregar nota de alcance operativo al inicio del documento
    - Después del objetivo, agregar sección clara que liste los servicios operativos vs no operativos
    - **Servicios Operativos Backend (9)**: api-gateway, ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications, bff-player, bff-organizer
    - **Servicios Operativos Frontend (2)**: chess-portal (con alcance reducido), organizer-panel (con alcance reducido)
    - **Infraestructura**: PostgreSQL, RabbitMQ, MinIO/S3, Redis, Prometheus, Grafana
    - **Servicios NO Operativos (fuera de alcance)**: ms-analytics, bff-admin, admin-panel
    - Explicar que MS-ETL está operativo para poblar datos, no para funcionalidades de administración
    - _Expected_Behavior: El documento debe tener una sección clara de alcance al inicio_
    - _Preservation: No afecta contenido existente, solo agrega claridad_
    - _Requirements: 2.1, 2.2, 2.4, 2.6_

  - [ ] 3.7 Actualizar referencias a vistas frontend (alcance reducido)
    - **Portal Público (landing)**: Documentar que solo incluye landing pública
    - Eliminar referencias a: "Ranking top nacional", "Torneos públicos", "Buscar jugadores"
    - **Chess-portal (Player Workspace)**: Documentar vistas disponibles: Portal (centro del jugador), Jugar (emparejamiento), Torneos (inscripción), Mi Perfil (dashboard)
    - Eliminar referencias a: "Ranking", "Consulta de jugadores"
    - **Organizer-panel**: Documentar vistas disponibles: Inicio del organizador, Torneos (gestión completa y validación de inscripciones)
    - **Actualizar Escena B**: Ajustar flujo UI para reflejar que endpoints de búsqueda y ranking existen en backend pero las vistas frontend no están disponibles (endpoints para uso interno/futuro)
    - Agregar nota: "Los endpoints de búsqueda y ranking están operativos en el backend para uso interno y desarrollo futuro, pero las vistas de usuario final no están incluidas en el alcance de la demo"
    - _Bug_Condition: section.referencesUI("/search") OR section.referencesUI("/rankings") in context of public portal or player workspace_
    - _Expected_Behavior: El plan debe referenciar únicamente vistas frontend disponibles en cada aplicación_
    - _Preservation: Mantener documentación de endpoints backend operativos_
    - _Requirements: 1.4, 2.4, 3.4_

  - [ ] 3.8 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Sin Referencias a Servicios No Operativos
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirma que el bug está corregido)
    - Verify:
      - No hay referencias a "ms-analytics" excepto en contexto de "servicios no operativos"
      - No hay referencias a "bff-admin" excepto en contexto de "servicios no operativos"
      - No hay referencias a "admin-panel" excepto en contexto de "servicios no operativos"
      - Listas de servicios Java contienen exactamente 6 servicios operativos
      - No hay referencias a vistas frontend fuera de alcance
      - Escena B documenta endpoints backend pero aclara que vistas no están disponibles
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ] 3.9 Verify preservation tests still pass
    - **Property 2: Preservation** - Contenido de Servicios Operativos Intacto
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirma que no hay regresiones)
    - Verify using git diff or diff tools:
      - Escenas A, C, D, F permanecen idénticas línea por línea
      - Sección de monitoreo mantiene estructura (solo ajustes de listas de servicios)
      - Cronograma mantiene tabla de semanas sin cambios
      - Checklist mantiene items de infraestructura (solo ajustes de frontends)
      - "Regla de oro" permanece sin cambios
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Verificar que el documento PLAN_DEMO.md corregido:
    - No contiene referencias a servicios no operativos excepto en sección de alcance
    - Mantiene todas las escenas operativas (A, B, C, D, F) completamente documentadas
    - Tiene una sección clara de alcance operativo al inicio
    - Lista correctamente los 6 microservicios Java operativos en configuración de monitoreo
    - Documenta correctamente el alcance reducido de vistas frontend
    - Aclara el rol de MS-ETL (poblar datos, no administración)
  - Realizar lectura completa del documento para verificar coherencia narrativa
  - Verificar que todas las referencias cruzadas entre secciones siguen siendo válidas
  - Validar que el checklist pre-demo cubre todos los servicios operativos mencionados
  - Confirmar que la configuración de monitoreo coincide con los servicios listados
  - Si surgen preguntas o inconsistencias, consultar al usuario antes de finalizar
