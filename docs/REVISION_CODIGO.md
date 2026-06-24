# Revisión de código — ChessQuery

Revisión por capas (gateway, microservicios, BFFs, frontends). Foco: mejoras y
código obsoleto que no complementa la app. Severidad: 🔴 alta · 🟡 media · 🟢 baja.

## Estado general (bueno)
- **Calidad TS**: 0 `as any` / `@ts-ignore` / `eslint-disable` en el front.
- **Sin basura de debug**: 0 `System.out.println` / `printStackTrace` / `console.log`.
- **Cobertura**: 53 archivos de test Java, 11 BFF, 10 front. Consumers idempotentes,
  email con fallback defensivo, capas limpias (gateway → BFF → MS por dominio).

---

## 🔴 Código obsoleto / muerto

### O1 · Storage MinIO/S3 legacy en `ms-game` (con AWS SDK colgando)
`ms-game/.../storage/`: `MinioStorageService` y `S3Config` están `@Deprecated` y
solo se activan con `storage.provider=minio`. El default y lo que se usa es
**Supabase** (`SupabaseStorageService`). El path muerto arrastra la dependencia
pesada `software.amazon.awssdk:s3` (+presigner) en `ms-game/pom.xml`.
- **Acción**: borrar `MinioStorageService`, `S3Config`, la rama `minio` de
  `StorageConfig` y la dep AWS SDK → imagen de ms-game más liviana y menos CVEs.

### O2 · `bff-admin` sin consumidor
`bff-admin` está construido y ruteado en el gateway (`/api/admin/**`) y agrega
datos de users/tournaments/games/analytics/etl, **pero no existe app de admin en
el frontend** (`frontend/apps/` solo tiene chess-portal y organizer-panel).
- **Acción**: o se construye el panel admin, o se deja de desplegar/rutear
  `bff-admin` (hoy es un servicio vivo sin uso en el producto entregado).

### O3 · Componentes `Input` y `PlayerCard` de ui-lib sin uso
Exportados desde `@chessquery/ui-lib` pero **no los importa ninguna app** (las
pantallas usan su propio `Field`/inputs inline). `Input` incluso recibió mejoras
de accesibilidad que hoy no se ven.
- **Acción**: o se adoptan en las pantallas (recomendado, ver M1), o se eliminan.

### O4 · Artefactos sueltos en la raíz
- `ms-*-coverage.log` (4 archivos, 26-may): logs viejos de cobertura → borrar + `.gitignore`.
- `archetypes/chessquery-ms-archetype`: arquetipo Maven de scaffolding, no usado
  por ningún build/CI → sacar del repo o documentar como tooling dev-only.

---

## 🟡 Mejoras (DRY / mantenibilidad)

### M1 · Duplicación entre las dos apps del front
Casi-duplicados entre `chess-portal` y `organizer-panel`:
- `src/api.ts` (~21KB c/u, normalizadores repetidos)
- `src/portal-utils.ts`
- `src/components/NotificationBell.tsx` (cada cambio hay que hacerlo 2 veces)
- `src/pages/Login.tsx` (componentes `Field`/`Checkbox` duplicados 3×, contando Register)
- partes de `TournamentDetail.tsx`
- **Acción**: extraer a `@chessquery/shared` (normalizadores/api base) y
  `@chessquery/ui-lib` (un `NotificationBell` parametrizable, `Field`/`Checkbox`
  accesibles únicos). Reduce superficie de bugs y trabajo duplicado.

### M2 · Consolidar inputs accesibles
`Field`/`Checkbox` viven inline en Login/Register (×3) y ui-lib tiene un `Input`
sin uso. Unificar en **un** `Field`/`Checkbox` accesible en ui-lib y borrar los
duplicados + el `Input` huérfano (cierra O3).

### M3 · Polling vs Realtime
Notificaciones (8s) y standings/pairings del torneo (8s) usan polling; la partida
ya usa Supabase Realtime. Migrar standings/notificaciones a Realtime baja tráfico
y da updates instantáneos. (Ya anotado en `PLAN_PERFORMANCE_FRONT.md`.)

---

## 🟢 Menores
- **Docs sprawl**: 35 `.md` en `docs/` + `RUN_ARCH.md` (828 líneas). Consolidar
  los planes/notas que ya se ejecutaron (varios son históricos).
- **Nav móvil**: el drawer responsive vive en `feat/nav-movil-drawer`, sin
  mergear → en esta rama el sidebar se apila en ≤980px.
- **`Presentacion 2/`**: assets de presentación (HTML) mezclados en la raíz del
  repo de código; podrían ir a una carpeta `presentation/` o fuera del repo.

---

## Priorización sugerida (entrega académica)
1. **O4** (borrar logs/artefactos) — trivial, limpia el repo. 
2. **O1** (quitar storage legacy + AWS SDK) — bajo riesgo, build más limpio.
3. **M1/M2** (DRY del front + inputs) — mayor esfuerzo; alto valor de mantenimiento.
4. **O2** (decidir destino de bff-admin) — decisión de alcance.
> M3 y nav móvil quedan para después de validar el flujo de torneo en vivo.
