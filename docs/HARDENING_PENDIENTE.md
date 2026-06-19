# Hardening pendiente (T2)

> Documento para revisar con Agustín — **16-jun-2026**.
> Resume lo que queda abierto del hardening de seguridad (T2) y lo que decidimos dejar
> para más adelante. El resto de T2 ya está cerrado (SG 8080, axios PR #29, Supabase
> OBS-02 PR #30, guards de arranque H-03, autorización por propiedad H-04).

---

## Situaciones a revisar mañana

### 1. H-07 — El CI/CD no escanea las imágenes Docker  ·  🟡 PARCIAL (19-jun)

**Qué pasaba.** El pipeline (`.github/workflows/build-and-push.yml`) construía y
publicaba las imágenes a ECR **sin revisar si tenían vulnerabilidades conocidas**.

**Por qué importa.** Podríamos estar desplegando una imagen con un CVE conocido sin
enterarnos. Es la red de seguridad que falta antes de subir a AWS.

**Lo hecho (19-jun).** Se agregó un paso **Trivy en MODO REPORTE** al workflow
(escanea las 8 imágenes por `HIGH,CRITICAL` y deja el resultado en el log del CI,
pero **no falla el build** — `exit-code: '0'`). Se eligió reporte-y-no-gate **a
propósito** porque **H-06 está diferido**: un gate duro marcaría los CVEs de Spring
Boot 3.2.4 (que tienen fix en 3.3+) y **bloquearía el deploy del CI/CD (T6)** hasta
cerrar H-06. El paso queda dormido mientras el runner self-hosted siga offline.

**Lo que queda (anotado en el propio YAML).**
- **Endurecer el gate** cuando H-06 esté hecho y se monte el CI/CD real: cambiar a
  `exit-code: '1'` + `ignore-unfixed: true` para que el build **falle** ante
  HIGH/CRITICAL con parche disponible.
- **Activar scan-on-push en ECR** (segunda capa, del lado de AWS). Requiere sesión
  AWS Academy fresca; correr en el próximo encendido:
  ```bash
  # por repositorio (repetir por cada servicio) o a nivel registry:
  aws ecr put-registry-scanning-configuration --scan-type BASIC \
    --rules '[{"scanFrequency":"SCAN_ON_PUSH","repositoryFilters":[{"filter":"chessquery/*","filterType":"WILDCARD"}]}]'
  ```

**Tamaño.** Chico. El paso de CI ya está; falta endurecerlo (1 línea) tras H-06 y el
comando de ECR cuando haya sesión AWS.

---

### 2. H-06 — Frameworks fuera de soporte

**Qué pasa.** Los **6 servicios Java** corren **Spring Boot 3.2.4** y **Spring Cloud
2023.0.x**, que ya **no tienen soporte** y arrastran CVEs del ciclo 2024. Lo mismo del
lado de los **BFFs** (admin/organizer/player), que necesitan un salto de versión de
**NestJS** para cerrar sus vulnerabilidades.

**Por qué importa.** Estar en una versión sin soporte significa que no van a llegar
parches de seguridad nuevos.

**Qué habría que hacer.**
- Subir Spring Boot a una línea soportada (**3.3.x / 3.4.x**) + Spring Cloud **2024.x**.
- Subir **NestJS** a su major actual en los 3 BFFs.
- Correr la suite completa (**~530 tests**) para confirmar que no se rompió nada.

**Tamaño.** Grande — son **dos saltos de versión mayor** (Spring + NestJS) que pueden
romper APIs y exigen revisar toda la batería de tests. **Propuesta: diferir** (ver abajo).

---

## Para decidir con Agustín

| Tema | Propuesta | Resolución (19-jun) |
|------|-----------|---------------------|
| H-07 (escaneo en CI) | **Cerrar ahora**, cambio chico, va con T6 | ✅ **Trivy en modo reporte** agregado al workflow; endurecer a gate duro tras H-06. `scanOnPush` de ECR queda para el próximo encendido AWS |
| H-06 (bump de frameworks) | **Diferir** — es trabajo mayor, sin riesgo bloqueante para la entrega | ⏸️ **Diferido** (confirmado) |
| ADMIN (H-02) | endurecer rol desde `app_metadata` | ⏸️ **Diferido** (confirmado — el panel admin no entra en la entrega) |

---

## Diferido para esta entrega

Lo que **conscientemente dejamos para más adelante**:

### Hardening del rol ADMIN (H-02)
El panel de admin **no entra en esta entrega**, así que el endurecimiento del rol se
**perfecciona más adelante**. Hoy el gateway lee el rol desde `user_metadata` (editable
por el usuario), lo que en teoría permitiría auto-asignarse ADMIN
(`api-gateway/.../filter/SupabaseJwtAuthFilter.java`). El radio de daño ya está acotado
por la autorización por propiedad (H-04). El fix futuro: reconocer ADMIN **solo desde
`app_metadata`** (no editable) y setear el rol admin por ahí, cuando construyamos el panel.

### Vulnerabilidades de dependencias (parte de H-06)

**BFFs (NestJS) — 3 `high` en cada uno:**

| Paquete | Por qué queda diferido |
|---------|------------------------|
| `lodash` | Fix completo requiere el major de NestJS |
| `multer` | Idem — atado al ciclo de NestJS |
| `@nestjs/platform-express` | El fix exige **major de NestJS** |

> `npm audit fix` normal no las resuelve; pide `--force` (cambios que rompen). Por eso
> van junto al bump de framework, no sueltas.

**Servicios Java (Spring) — CVEs del ciclo 2024:**
acumulados por estar en Spring Boot 3.2.4 / Spring Cloud 2023.0.x fuera de soporte. Se
cierran al subir de versión (H-06).

---

_Estado al 16-jun-2026. Lo demás de T2 ya quedó cerrado y mergeado._
