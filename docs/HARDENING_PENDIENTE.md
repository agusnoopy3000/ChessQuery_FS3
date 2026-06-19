# Hardening — lo que queda DIFERIDO

> Estado al **19-jun-2026**. La mayor parte de T2 ya está cerrada y mergeada a `main`
> (SG 8080, deps PR #29, OBS-02 PR #30, guards de arranque H-03, autorización por
> propiedad H-04 y **H-07 — escaneo Trivy, PR #35**). Este documento deja por escrito
> **solo lo que decidimos diferir** y el **porqué** — material para el informe y la defensa.

---

## 1. H-06 — Frameworks fuera de soporte  ·  ⏸️ DIFERIDO

**Qué pasa.** Los **6 servicios Java** corren **Spring Boot 3.2.4** y **Spring Cloud
2023.0.x**, fuera de soporte (arrastran CVEs del ciclo 2024). Igual del lado de los
**BFFs** (admin/organizer/player): los 3 `high` de `npm audit` (`lodash`, `multer`,
`@nestjs/platform-express`) necesitan un **major de NestJS**.

**Por qué importa.** Sin soporte no llegan parches de seguridad nuevos.

**Qué habría que hacer.**
- Subir Spring Boot a una línea soportada (**3.3.x / 3.4.x**) + Spring Cloud **2024.x**.
- Subir **NestJS** a su major actual en los 3 BFFs (cierra los 3 `high`; `npm audit fix`
  normal no los resuelve, piden `--force`).
- Correr la suite completa (**659 tests**) para confirmar que no se rompió nada.

**Por qué se difiere.** Son **dos saltos de major** (Spring + NestJS) que pueden romper
APIs — el `api-gateway` (Spring Cloud Gateway) es el más sensible — y exigen revisar
toda la batería de tests. **No es bloqueante para la entrega:** la app funciona y el
radio de daño está acotado (sin panel admin expuesto, autorización por propiedad H-04,
SG cerrado). Es deuda de mantenimiento, no un agujero explotable hoy. **Va en un PR
dedicado, idealmente después de la entrega.**

> ⚠️ **Acoplado con H-07:** el gate Trivy (PR #35) corre con `ignore-unfixed: true`, que
> **no** ignora los CVEs de Spring 3.2.4 (tienen fix en 3.3+). Cuando el runner
> self-hosted vuelva online, ese gate **podría poner rojos los PRs**. Si pasa: aflojar el
> gate temporalmente (modo reporte) **o** acelerar este H-06. Verificar en el primer run real.

---

## 2. H-02 — Hardening del rol ADMIN  ·  ⏸️ DIFERIDO

**Qué pasa.** El gateway lee el rol desde `user_metadata` (editable por el usuario), lo
que en teoría permite auto-asignarse ADMIN
(`api-gateway/.../filter/SupabaseJwtAuthFilter.java`, método `extractRole`).

**Por qué se difiere.** El **panel de admin no entra en esta entrega**, así que el
endurecimiento del rol se perfecciona más adelante. El radio de daño ya está acotado por
la autorización por propiedad (H-04). ORGANIZER self-service queda como está (intencional).

**Fix futuro.** Reconocer ADMIN **solo desde `app_metadata`** (no editable por el usuario)
y setear el rol admin por ahí, cuando construyamos el panel.

---

_Lo demás de T2 quedó cerrado y mergeado. Ver `docs/ROADMAP_V1.md` (cierre 19-jun) y
`docs/SECURITY_AUDIT_REPORT.md`._
