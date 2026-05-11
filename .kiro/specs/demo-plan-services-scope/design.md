# Demo Plan Services Scope Bugfix Design

## Overview

El documento PLAN_DEMO.md actual contiene referencias a servicios no operativos (MS-Analytics, BFF-Admin, admin-panel) que no estarán disponibles para la demo. Esto genera expectativas incorrectas sobre el alcance funcional y puede causar confusión durante la preparación de la presentación.

El bug se manifiesta cuando el plan documenta escenas, endpoints y configuraciones que dependen de servicios fuera del alcance operativo. La estrategia de corrección consiste en eliminar todas las referencias a servicios no operativos mientras se preserva la estructura útil del documento (monitoreo, cronograma, checklist) y se mantiene la documentación completa de los 9 servicios backend operativos y 2 frontends.

El alcance operativo real incluye:
- **Backend**: api-gateway, ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications, bff-player, bff-organizer
- **Frontend**: chess-portal (con vistas reducidas), organizer-panel (con vistas reducidas)
- **Infraestructura**: PostgreSQL, RabbitMQ, MinIO/S3, Redis, Prometheus, Grafana

**Alcance de vistas frontend:**
- **Portal Público (landing)**: Solo landing pública. Eliminar: Ranking top nacional, Torneos, Buscar jugadores
- **Chess-portal (Player Workspace)**: Portal (centro del jugador), Jugar (emparejamiento), Torneos (inscripción), Mi Perfil (dashboard). Eliminar: Ranking, Consulta de jugadores
- **Organizer-panel**: Inicio del organizador, Torneos (gestión completa y validación de inscripciones). Solo estas secciones

## Glossary

- **Bug_Condition (C)**: La condición que dispara el bug - cuando el plan documenta servicios, endpoints o escenas que dependen de MS-Analytics, BFF-Admin o admin-panel
- **Property (P)**: El comportamiento deseado - el plan debe documentar únicamente servicios operativos y sus flujos funcionales
- **Preservation**: Las secciones útiles del plan (Escenas A-D, F, monitoreo, cronograma, checklist) que deben mantenerse sin cambios
- **Servicio Operativo**: Servicio backend o frontend que estará disponible y funcional para la demo
- **Servicio No Operativo**: Servicio en desarrollo que no estará disponible (MS-Analytics, BFF-Admin, admin-panel)
- **Escena de Demo**: Flujo funcional end-to-end demostrable durante la presentación

## Bug Details

### Bug Condition

El bug se manifiesta cuando el documento PLAN_DEMO.md incluye referencias a servicios que no estarán operativos para la demo. Esto ocurre en múltiples secciones: escenas de demo, listados de endpoints, configuración de monitoreo, y flujos UI.

**Formal Specification:**
```
FUNCTION isBugCondition(section)
  INPUT: section of type DocumentSection
  OUTPUT: boolean
  
  RETURN (section.referencesService("ms-analytics") OR
          section.referencesService("bff-admin") OR
          section.referencesService("admin-panel"))
         AND section.documentType == "PLAN_DEMO"
         AND NOT section.isMarkedAsOutOfScope()
END FUNCTION
```

### Examples

- **Escena E completa**: Documenta `GET /admin/dashboard` (bff-admin → ms-analytics) y `GET /analytics/players/{id}/stats` (ms-analytics) como endpoints críticos, pero ambos servicios no estarán operativos
- **Sección "Endpoints faltantes"**: Lista "MS-Analytics — todos sus endpoints (ver brecha 1). Bloquea escena E" como prioridad 1, generando expectativa de trabajo bloqueante que no corresponde al alcance
- **Configuración Actuator (Paso 1)**: Incluye ms-analytics en la lista de servicios Java que recibirán configuración de monitoreo: "ms-auth, ms-users, ms-tournament, ms-game, ms-analytics, ms-notifications"
- **Flujo UI admin**: Referencia `/admin` y `/admin/etl` como rutas del admin-panel que no estará operativo
- **Escena B - Búsqueda y ranking**: Documenta flujos UI `/search` y `/rankings` que pueden no estar disponibles en los frontends según el alcance reducido de vistas
- **Edge case - MS-ETL**: Documenta endpoints de MS-ETL consumidos por BFF-Admin (`GET /admin/etl/status`, `POST /admin/etl/sync/:source`), pero el contexto correcto es que MS-ETL sirve datos a la plataforma para poblarla, no para administración
- **Vistas frontend fuera de alcance**: Referencias a secciones de Ranking top nacional, Torneos públicos, Buscar jugadores en portal público; Ranking y Consulta de jugadores en chess-portal player workspace

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Las Escenas A, B, C, D deben continuar documentadas completamente con sus endpoints, flujos UI, y riesgos identificados
- La Escena F (Notificaciones) debe continuar incluyendo verificaciones de RabbitMQ y notification_log
- La sección "Monitoreo de rendimiento" debe continuar con la configuración completa de Prometheus + Grafana
- El cronograma sugerido debe mantener su estructura temporal y recomendaciones de congelación de código
- El checklist pre-demo debe continuar incluyendo verificaciones de infraestructura (MinIO, RabbitMQ, PostgreSQL, seeds)
- La "Regla de oro" de congelación de código debe mantenerse sin cambios
- Las referencias a servicios de infraestructura (PostgreSQL, RabbitMQ, MinIO/S3, Redis) deben continuar presentes

**Scope:**
Todas las secciones que NO involucran servicios no operativos (ms-analytics, bff-admin, admin-panel) deben permanecer completamente inalteradas. Esto incluye:
- Documentación de endpoints de servicios operativos
- Configuración de infraestructura y monitoreo
- Cronogramas y procedimientos de preparación
- Verificaciones de salud del sistema

## Hypothesized Root Cause

Basado en la descripción del bug, las causas más probables son:

1. **Alcance Inicial Ambicioso**: El plan original fue creado con un alcance más amplio que incluía todos los servicios del sistema, sin considerar que algunos no estarían listos para la demo

2. **Falta de Sincronización con SPEC_BRECHAS.md**: El plan no refleja las brechas documentadas (especialmente Brecha 1 sobre MS-Analytics sin fuentes) que indican servicios no operativos

3. **Documentación de Aspiraciones vs Realidad**: El plan documenta el estado deseado del sistema completo en lugar del estado operativo real para la fecha de la demo

4. **Dependencias Implícitas No Validadas**: Las escenas de demo fueron diseñadas sin validar que todos los servicios dependientes estarían disponibles

## Correctness Properties

Property 1: Bug Condition - Eliminación de Referencias a Servicios No Operativos

_For any_ sección del documento PLAN_DEMO.md donde la condición de bug se cumple (isBugCondition returns true), el documento corregido SHALL eliminar completamente las referencias a servicios no operativos (ms-analytics, bff-admin, admin-panel) y sus endpoints asociados, reemplazándolas con documentación enfocada únicamente en los 9 servicios backend operativos y 2 frontends operativos.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**

Property 2: Preservation - Contenido de Servicios Operativos

_For any_ sección del documento PLAN_DEMO.md donde la condición de bug NO se cumple (isBugCondition returns false), el documento corregido SHALL producir exactamente el mismo contenido que el documento original, preservando toda la documentación de Escenas A-D, F, monitoreo, cronograma, checklist, y reglas de congelación de código.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

Asumiendo que nuestro análisis de causa raíz es correcto:

**File**: `PLAN_DEMO.md`

**Specific Changes**:

1. **Eliminar Escena E Completa**: Remover toda la sección "Escena E — Analytics y admin" incluyendo:
   - Tabla de endpoints de bff-admin y ms-analytics
   - Descripción del flujo UI `/admin`, `/admin/etl`
   - Nota bloqueante sobre recuperar fuentes de MS-Analytics
   - Referencias a SPEC_BRECHAS.md brecha 1

2. **Actualizar Sección "Endpoints faltantes / a crear"**: 
   - Eliminar punto 1 sobre MS-Analytics
   - Eliminar punto 2 sobre MS-Notifications (opcional, mantener si es útil)
   - Eliminar punto 4 sobre ruta `/api/analytics/**` en Gateway
   - Mantener punto 3 sobre MS-Users si aplica a servicios operativos
   - Agregar aclaración sobre el rol de MS-ETL: servir datos para poblar la plataforma, no para administración

3. **Actualizar Configuración de Actuator (Paso 1)**:
   - Cambiar lista de servicios de: "ms-auth, ms-users, ms-tournament, ms-game, ms-analytics, ms-notifications"
   - A: "ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications"
   - Actualizar conteo de "6 microservicios Java" a reflejar los servicios correctos

4. **Actualizar Configuración de Prometheus (Paso 2)**:
   - Cambiar referencia de "6 microservicios Java + api-gateway"
   - A: "6 microservicios Java operativos (ms-auth, ms-users, ms-tournament, ms-game, ms-etl, ms-notifications) + api-gateway"

5. **Actualizar Checklist Pre-Demo**:
   - Eliminar verificaciones relacionadas con admin-panel
   - Mantener verificaciones de chess-portal y organizer-panel
   - Mantener todas las verificaciones de infraestructura

6. **Agregar Nota de Alcance Operativo**: 
   - Al inicio del documento, después del objetivo, agregar sección clara que liste los servicios operativos vs no operativos
   - Explicar que MS-ETL está operativo para poblar datos, no para funcionalidades de administración

7. **Actualizar Referencias a Vistas Frontend**:
   - Documentar alcance reducido del Portal Público: solo landing, eliminar referencias a Ranking top nacional, Torneos públicos, Buscar jugadores
   - Documentar alcance reducido de Chess-portal (Player Workspace): Portal (centro), Jugar (emparejamiento), Torneos (inscripción), Mi Perfil. Eliminar referencias a Ranking y Consulta de jugadores
   - Documentar alcance reducido de Organizer-panel: solo Inicio y Torneos (gestión completa + validación de inscripciones)
   - Actualizar Escena B si referencia vistas de ranking/búsqueda que ya no existen en los frontends
   - Asegurar que las escenas de demo reflejen solo las vistas disponibles en cada frontend

## Testing Strategy

### Validation Approach

La estrategia de testing sigue un enfoque de dos fases: primero, identificar todas las referencias a servicios no operativos en el documento UNFIXED, luego verificar que el documento corregido las elimina completamente mientras preserva todo el contenido relacionado con servicios operativos.

### Exploratory Bug Condition Checking

**Goal**: Identificar todas las instancias del bug ANTES de implementar el fix. Confirmar o refutar el análisis de causa raíz. Si refutamos, necesitaremos re-hipotetizar.

**Test Plan**: Realizar búsqueda exhaustiva en PLAN_DEMO.md original de todas las referencias a "ms-analytics", "bff-admin", "admin-panel", "analytics", "/admin". Documentar cada ocurrencia con su contexto (sección, propósito, dependencias). Ejecutar en el documento UNFIXED para observar todas las instancias del bug.

**Test Cases**:
1. **Búsqueda de "ms-analytics"**: Identificar todas las menciones en escenas, endpoints, configuración (esperado: Escena E, endpoints faltantes, Actuator) - fallará en unfixed code mostrando múltiples referencias
2. **Búsqueda de "bff-admin"**: Identificar todas las menciones en endpoints y flujos (esperado: Escena E, tabla de endpoints) - fallará en unfixed code mostrando referencias
3. **Búsqueda de "admin-panel"**: Identificar referencias en flujos UI (esperado: Escena E, checklist) - fallará en unfixed code mostrando referencias
4. **Búsqueda de "/admin"**: Identificar rutas UI y endpoints (esperado: múltiples en Escena E) - fallará en unfixed code mostrando rutas no operativas
5. **Validación de Conteo de Servicios**: Verificar que listas de servicios Java incluyen ms-analytics incorrectamente - fallará en unfixed code mostrando conteo incorrecto
6. **Búsqueda de vistas frontend fuera de alcance**: Identificar referencias a "/search", "/rankings" en contexto de portal público o player workspace, "Ranking top nacional", "Buscar jugadores" - fallará en unfixed code mostrando vistas no disponibles
7. **Validación de Escena B**: Verificar si Escena B documenta flujos UI de ranking/búsqueda que no estarán en los frontends según alcance reducido - fallará si hay inconsistencia entre endpoints backend y vistas frontend disponibles

**Expected Counterexamples**:
- Escena E completa documenta servicios no operativos como críticos para la demo
- Sección de endpoints faltantes prioriza MS-Analytics como bloqueante
- Configuración de Actuator incluye ms-analytics en lista de servicios
- Escena B puede documentar flujos UI de ranking/búsqueda que no estarán disponibles en los frontends según alcance reducido
- Referencias a vistas de portal público (Ranking top nacional, Torneos, Buscar jugadores) que deben eliminarse
- Referencias a vistas de chess-portal player workspace (Ranking, Consulta de jugadores) que deben eliminarse
- Posibles causas: alcance inicial ambicioso, falta de sincronización con brechas documentadas, documentación de aspiraciones vs realidad, desalineación entre capacidades backend y vistas frontend disponibles

### Fix Checking

**Goal**: Verificar que para todas las secciones donde la condición de bug se cumple, el documento corregido elimina las referencias a servicios no operativos.

**Pseudocode:**
```
FOR ALL section WHERE isBugCondition(section) DO
  result := PLAN_DEMO_fixed.getSection(section.id)
  ASSERT NOT result.referencesService("ms-analytics")
  ASSERT NOT result.referencesService("bff-admin")
  ASSERT NOT result.referencesService("admin-panel")
  ASSERT result.onlyReferencesOperationalServices()
END FOR
```

### Preservation Checking

**Goal**: Verificar que para todas las secciones donde la condición de bug NO se cumple, el documento corregido produce exactamente el mismo contenido que el original.

**Pseudocode:**
```
FOR ALL section WHERE NOT isBugCondition(section) DO
  ASSERT PLAN_DEMO_original.getSection(section.id) = PLAN_DEMO_fixed.getSection(section.id)
END FOR
```

**Testing Approach**: La verificación manual es apropiada para este bugfix de documentación porque:
- El documento es relativamente pequeño (~200 líneas) y las secciones están claramente delimitadas
- La preservación requiere verificar contenido textual exacto, no comportamiento de código
- Las herramientas de diff (git diff, diff tools) proporcionan verificación visual clara de cambios vs preservación
- Property-based testing no agrega valor significativo para documentos de texto estructurado

**Test Plan**: Usar git diff para comparar PLAN_DEMO.md original vs corregido. Verificar visualmente que:
- Escenas A, B, C, D, F permanecen idénticas línea por línea
- Sección de monitoreo permanece idéntica excepto ajustes de conteo de servicios
- Cronograma y checklist permanecen idénticos excepto eliminación de referencias a admin-panel

**Test Cases**:
1. **Preservación de Escena A**: Verificar que toda la tabla de endpoints, flujo UI, y riesgos de registro/login permanecen sin cambios
2. **Preservación/Ajuste de Escena B**: Verificar que endpoints de búsqueda y ranking permanecen documentados en backend, pero flujos UI se ajustan para reflejar que estas vistas no están disponibles en portal público ni player workspace (solo endpoints backend para uso interno/futuro)
3. **Preservación de Escena C**: Verificar que flujo completo de torneo permanece sin cambios, alineado con vistas disponibles en organizer-panel
4. **Preservación de Escena D**: Verificar que endpoints de partida y PGN permanecen sin cambios, alineados con vista "Jugar" en player workspace
5. **Preservación de Escena F**: Verificar que verificaciones de notificaciones permanecen sin cambios
6. **Preservación de Monitoreo**: Verificar que pasos 1-5 permanecen sin cambios excepto listas de servicios actualizadas
7. **Preservación de Cronograma**: Verificar que tabla de semanas permanece sin cambios
8. **Preservación de Checklist**: Verificar que items de infraestructura permanecen sin cambios, con ajustes para reflejar frontends con alcance reducido
9. **Preservación de Regla de Oro**: Verificar que sección final permanece sin cambios

### Unit Tests

- Búsqueda de términos "ms-analytics", "bff-admin", "admin-panel" en documento corregido (debe retornar 0 resultados excepto en contexto de "servicios no operativos")
- Conteo de escenas en documento corregido (debe ser 5: A, B, C, D, F)
- Verificación de lista de servicios en Actuator (debe contener exactamente 6 servicios operativos)
- Verificación de estructura de secciones principales (debe mantener todas excepto Escena E)

### Property-Based Tests

No aplicable para este bugfix de documentación. La naturaleza del cambio (eliminación de secciones específicas y actualización de listas) no se beneficia de generación de casos aleatorios. La verificación manual con diff tools es más apropiada y eficiente.

### Integration Tests

- Lectura completa del documento corregido para verificar coherencia narrativa (las escenas restantes deben fluir lógicamente sin referencias a Escena E)
- Verificación de que todas las referencias cruzadas entre secciones siguen siendo válidas (ej: cronograma no debe referenciar trabajo de MS-Analytics)
- Validación de que el checklist pre-demo cubre todos los servicios operativos mencionados en las escenas
- Verificación de que la configuración de monitoreo (Prometheus scrape jobs) coincide con los servicios listados en las escenas
