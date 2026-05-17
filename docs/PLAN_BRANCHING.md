# Plan de Branching — ChessQuery

**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza
**Evaluación:** Parcial N°2

---

## 1. Estrategia adoptada: GitHub Flow adaptado a microservicios

El equipo adoptó **GitHub Flow**: una rama `main` siempre desplegable y
protegida, y ramas de feature de vida corta que entran a `main`
exclusivamente vía Pull Request. Se descartó GitFlow (sin rama `develop`
ni `release/*`) por el tamaño del equipo (3 personas) y la duración del
proyecto académico: la simplicidad y los merges frecuentes pesan más que
un flujo de release semántico.

```
main  ──●────────●─────────●─────────●─────────●─────●─── (protegida)
         \      /│        / \       / \       / \   /
          \    / │       /   \     /   \     /   \ /
       feat/ms-auth  feat/ms-game  feat/admin-panel
              feat/ms-users  feat/ms-tournament
                      feat/ajefech-integration
```

## 2. Convenciones de nombres

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat/ms-<servicio>` | Nuevo microservicio o feature de un MS | `feat/ms-tournament` |
| `feat/<frontend-app>` | Nueva app o feature de frontend | `feat/admin-panel` |
| `feat/<integración>` | Integración cross-componente | `feat/ajefech-integration` |
| `fix/<scope>` | Corrección de bug | `fix/ms-notifications-tests` |
| `docs/<tema>` | Cambios sólo de documentación | `docs/minuta-20032026` |
| `infraestructura-setup` | Setup inicial Docker | (rama legacy) |

Commits siguen **Conventional Commits**: `feat(ms-game): ...`,
`fix(api-gateway): ...`, `docs: ...`, `refactor: ...`.

## 3. Flujo de trabajo

1. **Sincronización:** `git pull origin main` antes de iniciar una tarea.
2. **Creación de rama:** `git checkout -b feat/...` a partir de `main`.
3. **Desarrollo:** commits atómicos con Conventional Commits.
4. **Pull Request:** se empuja la rama y se abre PR hacia `main`.
5. **Revisión y merge:** un miembro distinto al autor revisa; si CI
   (compilación + JaCoCo) pasa y se aprueba, se hace *Squash and Merge*.

## 4. Reglas

1. **`main` está protegida.** Sólo se actualiza vía Pull Request.
2. **Una rama por feature.** Vida útil ideal: < 1 semana.
3. **Rebase opcional** antes de mergear si la rama está lejos de `main`.
4. **CI debe pasar** antes de merge (compilación + tests).
5. **Sin `--no-verify`** ni bypass de hooks.
6. **Sin `Co-Authored-By`** en commits (decisión del equipo: los avatares
   tapan el grafo de GitHub).

## 5. Historia de integración a `main`

A cierre de Parcial N°2: **156 commits** en `main`, **18 merges de
integración**, de los cuales **10 fueron Pull Requests formales**.

| Ref | Rama | Contenido | Tipo de merge |
|---|---|---|---|
| PR #1 | `minuta/semana-1` | Minuta inicial del equipo | Pull Request |
| PR #3 | `docs/minuta-20032026` | Minuta de seguimiento | Pull Request |
| — | `feat/ms-auth` | JWT, BCrypt, refresh tokens | Merge directo |
| — | `feat/ms-users` | Player, federación FIDE, fuzzy search | Merge directo |
| — | `feat/ms-tournament` | Factory + Strategy de pairings | Merge directo |
| — | `feat/ms-game` | ELO calc, PGN storage, openings ECO | Merge directo |
| PR #9 | `feat/ms-analytics` | Stats agregadas + consumers RabbitMQ | Pull Request |
| PR #10 | `feat/ms-notifications` | Push in-app + email transaccional | Pull Request |
| PR #11 | `feat/ms-etl` | Sync FIDE/AJEFECH/Lichess (Python) | Pull Request |
| PR #12 | `feat/chess-portal-pages` | Páginas del portal del jugador | Pull Request |
| PR #13 | `feat/organizer-panel` | Panel del organizador | Pull Request |
| PR #14 | `feat/admin-panel` | Panel administrador (ETL) | Pull Request |
| PR #15 | `codex/fix-register-role-visuals` | Fix visual registro de rol | Pull Request |
| PR #16 | `feat/player-registration-flow` | Flujo registro jugador completo | Pull Request |

> **Nota de transparencia:** los primeros 4 microservicios
> (`ms-auth`, `ms-users`, `ms-tournament`, `ms-game`) se integraron con
> *merge directo* de rama (`Merge feat/ms-X into main`) durante el arranque
> del proyecto, antes de formalizar la política de PR obligatorio que rige
> desde PR #9 en adelante. Se documenta tal cual ocurrió, sin reescribir
> historia.

Commits notables post-merge (fixes y refinamiento):

- `4f1b100` — provision idempotente + preflight demo (resuelve race condition)
- `50732f9` — setup Windows + fix invite encoding (URL double-encoding)
- `c0e2669` — gateway connection pool resiliente a restarts
- `75bc89d` — fix lentitud live game + session pollution cross-tab

## 6. Reparto real del trabajo por integrante

La calificación de la defensa es individual. Para sustentarla con
evidencia objetiva, este es el reparto **real** según `git shortlog -sn main`
y los directorios efectivamente tocados por cada autor (sin reescribir ni
maquillar la historia):

| Integrante | Identidad(es) git | Commits en `main` | Áreas trabajadas |
|---|---|---:|---|
| **Agustín Garrido** | `agusnoopy3000` | 148 | Stack completo: 7 MS, api-gateway, 3 BFF, frontend, arquetipo, infra, docs |
| **Martín Mora** | `MartinDev`, `Martin Mora Alvarez` | 6 | `bff-player`, `frontend`, `ms-auth`, `ms-users` |
| **Gabriel Espinoza** | — | 0 identificables | Aporte no reflejado en autoría git (pair-programming / revisión presencial) |

> Los 2 commits del autor `JavPenaR` (`jav.penar@profesor.duoc.cl`)
> corresponden a la plantilla/seguimiento del docente, no a un integrante.

**Lectura honesta para la defensa:** la autoría git está fuertemente
concentrada en un integrante. Donde un integrante no figura en `git log`,
su aporte debe defenderse oralmente de forma concreta (qué módulo conoce,
qué decisiones tomó, qué puede explicar del código) en lugar de atribuir
commits que no existen. La rúbrica (indicadores 3 y 7) evalúa la
*colaboración real*, no la simetría de la historia git.

## 7. Gestión de conflictos — ejemplos reales

### Caso 1 — `feat/admin-panel` (#14) vs `feat/organizer-panel` (#13)

Ambas ramas modificaron `frontend/apps/*/api.ts` agregando endpoints
distintos. Al mergear #14 después de #13, Git marcó conflicto en imports.
**Resolución:** rebase de `feat/admin-panel` sobre `main` actualizado y
unificación manual de imports. Commit de merge:
`67a806b — merge: resolver conflictos con main tras #13/#14`.

### Caso 2 — `feat/ajefech-integration` vs `feat/chess-portal-pages`

`ms-users` cambió `Player.java` agregando `enrichmentSource` mientras la
rama del portal modificaba `PlayerDto`. Conflicto en el mapper.
**Resolución:** merge manual privilegiando ambos campos (no excluyentes)
y regeneración de tests. Commit:
`f2d9ef5 — merge feat/ajefech-scraper into integration branch`.

### Caso 3 — `docker-compose.yml` (colas RabbitMQ vs puertos Gateway)

Una rama añadió colas de RabbitMQ a `docker-compose.yml` mientras otra
ajustaba puertos de exposición del gateway en el mismo archivo. Git marcó
conflicto en el bloque de servicios.
**Resolución:** `git fetch origin` + `git rebase origin/main`; se
determinó en pair-programming que **ambos bloques eran necesarios**; edición
manual conservando colas y puertos; `make demo-up` local para validar que
ambos servicios levantaran; `git rebase --continue` y `git push -f`.

## 8. Beneficios observados

- **Paralelismo:** ramas por feature permitieron trabajar sin pisarse.
- **Reversibilidad:** un PR malo se revierte sin afectar trabajo en curso.
- **Trazabilidad:** cada feature queda asociada a un PR con descripción.
- **Code review:** desde PR #9, ningún cambio entra a `main` sin revisión.

## 9. Trabajo futuro

- Habilitar **GitHub Actions** con CI obligatorio (compilación + JaCoCo).
- Política de **squash merge** automática para historial lineal.
- Rama `develop` como integración previa a release (sólo si se libera
  semánticamente).
