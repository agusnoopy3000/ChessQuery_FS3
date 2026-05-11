# Guía para la Defensa Oral (Cheat Sheet)

Este documento contiene los argumentos y justificaciones clave que exige la rúbrica (Ítems 5, 6 y 8) para la defensa oral del proyecto **ChessQuery**. Estúdienlo bien.

## 1. Patrones de Diseño Utilizados (Ítem 5 y 8)

### En el Frontend (React + NestJS BFF)
*   **Patrón Contenedor / Presentador:** Separamos la lógica de acceso a datos (Hooks, queries a la API) de la interfaz gráfica (UI Lib). **Justificación:** Mejora la mantenibilidad porque si cambia la forma de obtener datos (ej. migrar a Supabase), los componentes visuales de UI no se tocan, solo se adaptan los hooks (`useAuth`, etc.).
*   **Patrón Backend-For-Frontend (BFF):** Usamos aplicaciones en NestJS exclusivas por perfil (Jugador vs Organizador). **Justificación:** En vez de que el frontend llame a 10 microservicios, llama a un solo BFF. Esto agrupa los datos, reduce latencia y oculta la complejidad del backend a las aplicaciones cliente.

### En el Backend (Microservicios Java)
*   **Patrón Idempotent Receiver (Consumidor Idempotente):** Usado en la cola de RabbitMQ al registrar usuarios. **Justificación:** Evitó un problema real (*condición de carrera*) donde si el usuario hacía doble clic rápido, se creaban dos perfiles. Al hacerlo idempotente, el sistema verifica primero si el ID ya existe antes de procesar el evento, asegurando coherencia de la base de datos.
*   **Patrón Circuit Breaker (Resilience4j):** Usado al consultar ELO a servicios externos. **Justificación:** Si el servicio de ELO externo se cae, en vez de bloquear toda nuestra plataforma, el *Circuit Breaker* se abre y retorna un valor por defecto o de caché, garantizando el rendimiento y disponibilidad de la app.

## 2. Arquetipos y Arquitectura (Ítem 6)

*   **Arquitectura Orientada a Microservicios (Database-per-service):**
    *   *¿Qué es?* Cada servicio (Users, Tournament, Game, Notifications) tiene su propia base de datos (PostgreSQL/H2).
    *   *¿Por qué?* **Escalabilidad y Rendimiento:** Si el servicio de partidas (Game) tiene mucha carga durante un torneo, podemos instanciar más contenedores de ese servicio específico sin sobrecargar a los usuarios o notificaciones.
    *   *Coherencia:* Usamos un bus de eventos (RabbitMQ) para mantener eventual coherencia entre microservicios (Coreografía).
*   **Patrón API Gateway (Spring Cloud Gateway):**
    *   *Justificación:* Funciona como punto único de entrada. Nos garantizó coherencia al aplicar políticas de seguridad globales (paso de JWT) y enrutamiento centralizado sin que los frontends necesiten conocer las IPs internas.

## 3. Resolución de Problemas y Pruebas Unitarias (Ítem 8)

**¿Cómo estas prácticas resolvieron problemas?**
*   *Problema:* El acoplamiento. Si un servicio fallaba, todo moría.
*   *Solución:* Implementar microservicios con mensajería asíncrona (RabbitMQ) resolvió esto. Si `ms-notifications` se cae, el torneo se crea igual y el mensaje queda en cola hasta que vuelva a encenderse.
*   **Resultados de Pruebas Unitarias y Cobertura:**
    *   Implementamos **JaCoCo** en el backend.
    *   Pudimos detectar que `ms-notifications` tenía validaciones defectuosas (se estaba inyectando 2 veces la función de `saveLog`).
    *   Implementamos **Vitest + React Testing Library** en el frontend (portal de jugadores), demostrando que la separación lógica (Hooks) nos permitía testear los componentes puramente visuales garantizando que los CTAs (botones) y la UI no se rompan con nuevas versiones de React.
    *   *Cobertura:* Todos los servicios críticos, especialmente validaciones de negocio en Java y los componentes clave del Frontend, superan el estándar esperado de cobertura de pruebas y validación estricta de TypeScript.
