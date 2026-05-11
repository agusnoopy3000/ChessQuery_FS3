# Estrategia de Testing (TESTING_STRATEGY) — ChessQuery

## 1. Filosofía de Testing
En ChessQuery adoptamos un enfoque de testing pragmático priorizando el backend y el core de negocio. El modelo asume las siguientes directrices:
- **Lógica de negocio compleja (Core):** Se cubre mediante **Tests Unitarios**. Esto incluye el cálculo del algoritmo ELO, la generación de emparejamientos en torneos (Suizo, Round Robin) y la validación de almacenamiento (Supabase).
- **Flujos REST y Persistencia:** Se cubren con **Tests de Integración** levantando un contexto Spring y bases de datos H2 en memoria. No se realiza un mockeo excesivo de las bases de datos para garantizar que las consultas JPA y restricciones estructurales funcionen fielmente.
- **Microservicios (Spring Boot):** Son la principal fuente de verdad y se espera de ellos una cobertura mínima del 60% (actualmente en progreso de alcanzar esta meta).
- **Gateways, BFFs (NestJS) y Frontend (React):** Quedan fuera del alcance inicial de pruebas automatizadas priorizándose la validación del backend por temas académicos y restricciones de tiempo.

## 2. Cobertura por Microservicio

| Microservicio | Archivo de Test | Qué cubre | Nivel |
|---|---|---|---|
| **ms-users** | `UserControllerIntegrationTest.java` | Endpoints REST, creación y búsqueda de perfiles | Integración |
| | `AgeCategoryTest.java` | Cálculo de categoría de edad a partir de la fecha de nacimiento | Unitario |
| | `PlayerSearchServiceTest.java` | Lógica de búsqueda difusa (fuzzy search) y filtros | Unitario |
| | `UserRegisteredConsumerTest.java` | Consumo de RabbitMQ, idempotencia al crear usuarios | Messaging |
| | `RatingUpdatedConsumerTest.java` | Actualización de rating FIDE | Messaging |
| **ms-tournament** | `SwissPairingStrategyTest.java` | Emparejamiento por sistema Suizo, cálculo de desempates | Unitario |
| **ms-game** | `EloCalculatorTest.java` | Fórmula estándar FIDE, variación de K según el historial | Unitario |
| | `LiveGameServiceTest.java` | Registro de jugadas y finalización de partidas | Unitario |
| | `SupabaseStorageServiceTest.java` | Interfaz y subida de PGNs a Supabase Storage | Unitario |
| **ms-notifications** | `NotificationServiceTest.java` | Guardado de log de notificaciones y envío SMTP | Unitario/Integración |

## 3. Métricas de Cobertura (JaCoCo)

Obtenidas con `mvn test jacoco:report` en mayo de 2026. Los resultados están generados bajo la carpeta `docs/coverage-reports/`.

| Microservicio | Tests Ejecutados | Fallos | Line Coverage | Branch Coverage | Instruction Cov. |
|---|---|---|---|---|---|
| **ms-users** | 48 | 0 | 35.88% | 26.24% | 39.41% |
| **ms-tournament** | 2 | 0 | 3.05% | 1.92% | 2.63% |
| **ms-game** | 13 | 0 | 37.04% | 22.05% | 38.60% |
| **ms-notifications** | 3 | **2 (Fallidos)** | 15.79% | 1.22% | 14.54% |

**Nota sobre fallos:** `ms-notifications` presenta 2 tests fallidos. Los reportes fueron generados forzando la bandera `-Dmaven.test.failure.ignore=true`. No se alteraron los tests defectuosos para conservar la integridad del diagnóstico inicial.

## 4. Gaps Reconocidos y Limitaciones

Se identifican honestamente los siguientes vacíos en la estrategia actual:
- **Baja cobertura global:** Ninguno de los microservicios alcanza el 60% requerido en la rúbrica (AGENTS.md). `ms-tournament` y `ms-notifications` están en estado crítico.
- **Tests defectuosos:** La suite en `ms-notifications` está rota (posiblemente por configuraciones del cliente de correo o la base de datos).
- **Componentes excluidos:** Ni `ms-auth` (legado), ni el API Gateway, ni el frontend en React cuentan con tests automatizados formales.
- **Dependencia de H2:** Los tests de integración corren sobre H2, omitiendo características propias de PostgreSQL (ej. funciones nativas complejas o ciertas integraciones de `pg_trgm`).

## 5. Plan Futuro Priorizado

1. **Reparar tests en `ms-notifications`**: Investigar por qué están fallando los 2 tests e inyectar el mock necesario o resolver la dependencia faltante.
2. **Aumentar cobertura en `ms-tournament` y `ms-game`:** Son componentes críticos (emparejamientos y ELO). Redactar casos de uso más exhaustivos hasta superar el 60% de cobertura.
3. **Mocks E2E con Testcontainers:** Reemplazar progresivamente H2 por contenedores Docker reales (PostgreSQL y RabbitMQ) en la suite de integración de `ms-users` y `ms-tournament`.

## 6. Cómo correr la suite localmente

Para ejecutar todos los tests y generar los reportes (ignorando posibles fallos en la compilación y avanzando módulo por módulo), utiliza:

```bash
export JAVA_HOME="/Users/agustingarridosnoopy/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home"

# Para un microservicio específico:
cd ms-<nombre>
mvn clean test jacoco:report
```

*Los reportes HTML quedarán disponibles en `target/site/jacoco/index.html`.*
