# Plan de Branching — ChessQuery

**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza

---

## 1. Estrategia adoptada: Trunk-Based Development con feature branches

El equipo adoptó una variante simplificada de **GitFlow** que privilegia
ramas cortas y merges frecuentes a `main`, dado el tamaño del equipo (3
personas) y la duración del proyecto académico.

```
main  ──●────────●─────────●─────────●─────────●─────●─── (protegida)
         \      /│        / \       / \       / \   /
          \    / │       /   \     /   \     /   \ /
           feat/ms-auth   feat/ms-game  feat/admin-panel
                  feat/ms-users  feat/ms-tournament
                          feat/ajefech-integration
```

## 2. Convenciones de nombres

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat/ms-<servicio>` | Nuevo microservicio o feature de un MS | `feat/ms-tournament` |
| `feat/<frontend-app>` | Nueva app o feature de frontend | `feat/admin-panel` |
| `feat/<integración>` | Integración cross-componente | `feat/ajefech-integration` |
| `fix/<scope>` | Corrección de bug | `fix(ms-game): pgn url` |
| `docs/<tema>` | Cambios sólo de documentación | `docs/minuta-20032026` |
| `infraestructura-setup` | Setup inicial Docker | (rama legacy) |

Commits siguen **Conventional Commits**: `feat(ms-game): ...`, `fix(api-gateway): ...`,
`docs: ...`, `refactor: ...`.

## 3. Reglas

1. **`main` está protegida.** Sólo se actualiza vía Pull Request aprobado.
2. **Una rama por feature.** Vida útil ideal: < 1 semana.
3. **Rebase opcional** antes de mergear si la rama está lejos de `main`.
4. **CI debe pasar** antes de merge (compilación + tests).
5. **Sin `--no-verify`** ni bypass de hooks.
6. **Sin `Co-Authored-By`** en commits (decisión del equipo: los avatares
   tapan el grafo de GitHub).

## 4. PRs mergeados a `main`

| PR | Rama | Contenido | Estado |
|---|---|---|---|
| #1 | `minuta/semana-1` | Minuta inicial del equipo | merged |
| #3 | `docs/minuta-20032026` | Minuta seguimiento | merged |
| #5 | `feat/ms-auth` | JWT, BCrypt, refresh tokens | merged |
| #6 | `feat/ms-users` | Player, federación FIDE, fuzzy search | merged |
| #7 | `feat/ms-tournament` | Factory + Strategy de pairings | merged |
| #8 | `feat/ms-game` | ELO calc, PGN storage, openings ECO | merged |
| #9 | `feat/ms-analytics` | Stats agregadas + consumers RabbitMQ | merged |
| #10 | `feat/ms-notifications` | Push in-app + email transaccional | merged |
| #11 | `feat/ms-etl` | Sync FIDE/AJEFECH/Lichess (Python) | merged |
| #12 | `feat/chess-portal-pages` | Páginas del portal del jugador | merged |
| #13 | `feat/organizer-panel` | Panel del organizador | merged |
| #14 | `feat/admin-panel` | Panel administrador (ETL) | merged |
| #15 | `codex/fix-register-role-visuals` | Fix visual registro de rol | merged |
| #16 | `feat/player-registration-flow` | Flujo registro jugador completo | merged |

Total commits en `main`: **133** (al cierre de Parcial N°2),
distribuidos entre los 3 integrantes.

Commits notables post-merge (fixes y refinamiento):

- `4f1b100` — provision idempotente + preflight demo (resuelve race condition)
- `50732f9` — setup Windows + fix invite encoding (URL double-encoding)
- `c0e2669` — gateway connection pool resiliente a restarts
- `75bc89d` — fix lentitud live game + session pollution cross-tab

## 5. Ejemplos de gestión de conflictos

### Caso 1 — Merge `feat/admin-panel` (#14) vs `feat/organizer-panel` (#13)

Ambas ramas modificaron `frontend/apps/*/api.ts` agregando endpoints
distintos. Al mergear #14 después de #13, Git marcó conflicto en imports.

**Resolución:** rebase de `feat/admin-panel` sobre `main` actualizado y
unificación manual de los imports. Commit de merge:
`67a806b — merge: resolver conflictos con main tras #13/#14`.

### Caso 2 — `feat/ajefech-integration` vs `feat/chess-portal-pages`

`ms-users` cambió `Player.java` agregando `enrichmentSource` mientras la
rama del portal modificaba `PlayerDto`. Conflicto en el mapper.

**Resolución:** merge manual privilegiando ambos campos (no excluyentes),
seguido de regeneración de tests. Commit:
`f2d9ef5 — merge feat/ajefech-scraper into integration branch`.

## 6. Beneficios observados

- **Paralelismo real:** los 3 integrantes trabajaron simultáneamente en
  ramas distintas sin pisarse.
- **Reversibilidad:** un PR malo se revierte sin afectar trabajo en curso
  de los demás.
- **Trazabilidad:** cada feature queda asociada a un PR con descripción.
- **Code review forzado:** ningún cambio entra a `main` sin revisión.

## 7. Trabajo futuro

- Habilitar **GitHub Actions** con CI obligatorio (compilación + JaCoCo).
- Política de **squash merge** automática para mantener historial lineal.
- Rama `develop` como integración previa a release (sólo si se libera
  semánticamente).
