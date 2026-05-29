# Plan de pruebas — Prioridad 1: `OrganizerTournaments.tsx`

**Objetivo:** subir `organizer-panel` de **52.3% → ≥75% líneas** cubriendo la pantalla
`OrganizerTournaments.tsx`, hoy en **~35% líneas / ~18% ramas** (el archivo que hunde el
promedio del módulo).

**Esfuerzo estimado:** 1–2 sesiones. Es la mejor relación impacto/esfuerzo del proyecto:
una sola pantalla concentra casi toda la deuda de cobertura del panel.

---

## 1. Por qué está bajo hoy

El spec actual (`src/pages/OrganizerTournaments.spec.tsx`) solo cubre **3 casos**:

1. Lista vacía → `EmptyState`.
2. Query falla → `ErrorAlert`.
3. Click en "Crear torneo" → abre el modal.

Eso ejercita el `loading/error/empty` y poco más. **Todo el camino feliz** (renderizar
torneos, seleccionarlos, transicionar estados, borrar, filtrar) queda sin tocar — y es la
mayor parte de las 712 líneas del componente.

---

## 2. Qué falta cubrir (mapa del componente)

| Bloque del componente | Líneas aprox. | Cubierto hoy | Acción |
|---|---|:--:|---|
| `loading` / `error` / lista vacía | 355–378 | ✅ | — |
| **Render del listado** (tarjetas de torneo, KPIs, badges) | 411–500 | ❌ | Test nuevo |
| **Búsqueda** (`cq-search-input`, `visibleTournaments`) | 176–187, 381–390 | ❌ | Test nuevo |
| **Filtros por estado** (`filterTabs`, chips) | 189–195, 391–403 | ❌ | Test nuevo |
| **Selección de torneo** → card de detalle | 40–48, 502–534 | ❌ | Test nuevo |
| **Transiciones de estado** (DRAFT→OPEN→IN_PROGRESS→FINISHED) | 572–606 | ❌ | Test nuevo |
| **Borrado** (`handleDelete` + `window.confirm`) | 144–150, 451–492 | ❌ | Test nuevo |
| **Generar ronda** (`generateRound`) | 62–71, 547–569 | ❌ | Test nuevo |
| **Standings** y **tabla de emparejamientos** | 639–707 | ❌ | Test nuevo |

---

## 3. Casos de prueba a escribir

Reutilizá el **mismo andamiaje** del spec actual (mocks de `@chessquery/ui-lib`,
`CreateTournamentModal`, `RegistrationsPanel` y `../api`; helper `renderPage`). Solo hay que
sumar más métodos al mock de `organizerApi` y más casos.

### 3.1 Listado y KPIs
- **`renderiza las tarjetas de torneos cuando la lista trae datos`**
  `listTournaments` resuelve `{ content: [t1, t2] }` → aparecen los nombres de ambos torneos
  y los KPIs (`total`, `open`, `en curso`, `finalizados`) con los conteos correctos.
- **`selecciona automáticamente el primer torneo`** → la card de detalle muestra el nombre
  del primer torneo (efecto de `useEffect` en línea 34–38).

### 3.2 Búsqueda y filtros
- **`filtra la lista al escribir en el buscador`**: render con 2 torneos de nombres distintos,
  `fireEvent.change` en el input `aria-label="Buscar torneos"` → solo queda el que matchea.
- **`filtra por estado al clickear un chip`**: click en el chip "Borrador (n)" → la lista
  muestra solo torneos `DRAFT`. Verificar también el `EmptyState` "Sin torneos para este
  filtro" cuando ningún torneo matchea.

### 3.3 Selección y detalle
- **`al seleccionar otro torneo, muestra su detalle`**: click en la tarjeta del 2º torneo →
  la card de detalle pasa a mostrar su `name`, `format`, `registered/maxPlayers`, `rounds`.

### 3.4 Transiciones de estado (el corazón de la lógica)
Un test por transición; en cada uno se mockea `patchTournamentStatus` y se verifica que se
llama con el `status` correcto:
- **DRAFT** → botón "Abrir inscripciones" → `patchStatus({ status: 'OPEN' })`.
- **OPEN** → botón "Iniciar torneo" → `patchStatus({ status: 'IN_PROGRESS' })`.
- **IN_PROGRESS** → botón "Finalizar torneo" → `patchStatus({ status: 'FINISHED' })`.
- **`muestra ErrorAlert si patchStatus falla`** (rama 607–616).

### 3.5 Borrado
- **`borra un torneo tras confirmar`**: `vi.spyOn(window, 'confirm').mockReturnValue(true)`,
  click en el botón "Eliminar torneo" → `deleteTournament` se llama con el id correcto.
- **`no borra si el usuario cancela`**: `confirm` → `false` → `deleteTournament` NO se llama.
- **`muestra ErrorAlert cuando el borrado falla`** (rama 246–255).

### 3.6 Generar ronda y resultados
- **`genera la ronda al clickear el botón`** (con torneo `IN_PROGRESS`) → `generateRound`
  invocado. Verificar también que el botón está **deshabilitado** en estado `DRAFT`/`FINISHED`.
- **`muestra ErrorAlert si generateRound falla`** (rama 559–569).

### 3.7 Standings y emparejamientos
- **`muestra StandingsTable cuando hay clasificación`** vs **`EmptyState` cuando viene vacía**.
- **`muestra la tabla de pairings cuando la ronda trae datos`** vs los distintos `EmptyState`
  (ronda inexistente / sin pairings).

---

## 4. Patrón técnico (recordatorio)

```tsx
// Mock de window.confirm para los tests de borrado
const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

// Datos de prueba reutilizables
const t = (over = {}) => ({
  id: 1, name: 'Open Invierno', format: 'SWISS', status: 'DRAFT',
  startDate: '2026-07-01', registered: 4, maxPlayers: 16, rounds: 5,
  location: 'Santiago', ...over,
});

// QueryClient con retry:false para que el error sea inmediato (ya está en renderPage)
```

> ⚠️ El mock de `Table` en el spec actual recibe `data`, pero el componente real pasa `rows`.
> Al cubrir la tabla de pairings, **ajustá el mock** a `rows` (`Table: ({ rows }) => <div
> data-testid="table" data-rows={rows?.length ?? 0} />`) o el conteo dará 0.

---

## 5. Criterio de "hecho" (Definition of Done)

- [x] `cd frontend/apps/organizer-panel && npm test` → todos los specs en verde (38 tests).
- [x] `npm test -- --coverage` muestra **`OrganizerTournaments.tsx` 84.03% líneas** (≥75% ✅).
- [x] Cobertura global de `organizer-panel` **75.32% líneas** (≥75% ✅, era 52.3%).
- [x] Actualizar la tabla de cobertura en `docs/PRUEBAS.md` §5.2 y en `TESTING.md` §1.
- [ ] CI (`frontend-tests`) verde en el PR. *(pendiente: abrir PR)*

## 6. Fuera de alcance (a propósito)
- Los estilos inline y los handlers `onMouseEnter/Leave` (puro CSS, no aportan a la métrica útil).
- El componente `CreateTournamentModal` y `RegistrationsPanel` internos (se mockean; tienen —o
  tendrán— sus propios specs).
</content>
</invoke>
