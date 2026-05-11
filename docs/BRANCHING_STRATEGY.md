# Estrategia de Branching y Colaboración

Para el desarrollo del proyecto **ChessQuery**, el equipo implementó una estrategia de branching basada en **GitHub Flow** con adaptaciones para microservicios. Esta estrategia garantiza que la rama `main` siempre esté en un estado desplegable y saludable, mientras que las nuevas características se desarrollan de forma aislada.

## 1. Estructura de Ramas

- **`main`**: Rama principal del repositorio. Contiene el código de producción. Solo recibe código a través de *Pull Requests* (PRs) aprobados. Todas las pruebas (JaCoCo) deben pasar antes del merge.
- **`feat/[nombre-funcionalidad]`**: Ramas creadas para desarrollar nuevas características (ej. `feat/auth-supabase`, `feat/ms-tournament-pairing`).
- **`fix/[nombre-bug]`**: Ramas destinadas exclusivamente a corregir errores detectados en la integración (ej. `fix/ms-notifications-tests`).

## 2. Flujo de Trabajo (Workflow)

1. **Sincronización:** Cada desarrollador hace `git pull origin main` antes de iniciar una tarea.
2. **Creación de rama:** Se crea una rama a partir de `main` (`git checkout -b feat/...`).
3. **Desarrollo y Commits:** Se aplican convenciones de **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`).
4. **Pull Request:** Se empuja la rama al repositorio remoto y se abre un PR hacia `main`.
5. **Revisión y Merge:** Un miembro distinto al autor revisa el código. Si las pruebas CI pasan y se aprueba, se hace un *Squash and Merge*.

## 3. Gestión de Conflictos: Ejemplo Real

Durante el desarrollo colaborativo, es natural encontrar conflictos cuando dos desarrolladores modifican los mismos archivos. 

**Caso de Conflicto: Archivos de Configuración Docker**
- **Contexto:** El "Desarrollador A" estaba en la rama `feat/ms-notifications` y modificó el archivo `docker-compose.yml` para añadir las colas de RabbitMQ. Al mismo tiempo, el "Desarrollador B" en la rama `feat/api-gateway-routes` también modificó el `docker-compose.yml` para ajustar los puertos de exposición.
- **El Problema:** Al intentar hacer merge del segundo PR, GitHub detectó un conflicto en las líneas de configuración del Gateway y RabbitMQ.
- **Resolución:**
  1. El Desarrollador B actualizó su rama local con los últimos cambios de main: `git fetch origin` y `git rebase origin/main`.
  2. Git marcó el archivo `docker-compose.yml` con conflicto (`<<<<<<< HEAD`).
  3. En equipo (pair-programming), los desarrolladores abrieron el archivo y determinaron que **ambos bloques eran necesarios** (las colas y los puertos).
  4. Editaron el archivo manualmente para mantener las definiciones de RabbitMQ y las del API Gateway.
  5. Se ejecutó `make demo-up` localmente para validar que ambos servicios levantaran correctamente tras la unión de código.
  6. Se completó el rebase (`git add .` -> `git rebase --continue`) y se actualizó el PR (`git push -f`).

Este protocolo nos ha permitido mantener un entorno colaborativo ágil sin romper la rama principal.
