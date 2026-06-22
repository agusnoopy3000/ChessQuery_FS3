# Plan — Performance de cara al usuario (front)

> Objetivo: que la app cargue rápido (sobre todo la login que abre la QR), se
> sienta profesional y no muestre vacíos estáticos mientras carga.

## ✅ Hecho en esta tanda
- **Code-splitting por ruta** (`React.lazy` + `Suspense`) en ambas apps: las
  vistas internas (LiveGame con Chessground, torneos, portal, perfil) salen del
  bundle inicial. La login/landing carga primero.
  - Portal: bundle de login **195KB → 152KB gzip**; LiveGame (30KB gzip) y Sileo
    (48KB gzip) cargan **post-login**.
- **Sileo lazy**: `Toaster` y `NotificationBell` cargan recién al loguearse →
  el motor de física/SVG de los toasts no pesa en la login.
- **Fallbacks de Suspense** (spinner centrado) → sin pantalla en blanco al
  cambiar de ruta.
- **Skeleton de tablero** en la carga de la partida en vivo (antes era el texto
  plano "Cargando partida…").
- Toasts/notificaciones migrados a **Sileo** (animación física, consistente).

## 🔜 Próximos pasos (priorizados)

### P0 — Infra de entrega
- **CloudFront + HTTPS** delante de los buckets S3. Hoy es S3 website por HTTP:
  sin CDN, sin compresión brotli en el edge, y los móviles muestran "sitio no
  seguro". CloudFront da: HTTPS, brotli/gzip, cache en edge, menor latencia.
  (Ya documentado como pendiente en `docs/PENDIENTE_HTTPS.md`.)

### P1 — Red y datos
- **Polling → Supabase Realtime** donde se pueda: standings y pairings del
  torneo refrescan cada 8s; la partida ya usa Realtime. Pasar standings a
  Realtime baja tráfico y da updates instantáneos.
- **React Query**: `staleTime` por query (dashboards toleran 60s), `prefetch`
  on-hover de torneos/portal para que la navegación sea instantánea.
- **Debounce** en el buscador de torneos (hoy filtra en cada tecla).

### P1 — Bundle
- **manualChunks** de vendors (react, chessground, supabase, react-query) →
  mejor cache entre deploys (el vendor no cambia cada build).
- **`rollup-plugin-visualizer`** para auditar el bundle y cortar lo que no se usa.
- **Fonts**: subset/`font-display: swap` (ya hay swap); evaluar self-host para
  no depender de Google Fonts en runtime.

### P2 — Percepción / "sin vacíos"
- Skeletons consistentes en TODA pantalla con fetch (la mayoría ya los tiene).
- `prefers-reduced-motion` ya respetado globalmente.
- Empty states con copy + CTA (mayoría hecha); revisar perfil/dashboard de un
  usuario nuevo (ELO en null → mensaje claro, no vacío).

## Verificación
- `npm run build` (mirar tamaños de chunks) + `npm run test` (37 portal / 38 org).
- Lighthouse sobre la login y el portal (objetivo: FCP/LCP bajos en 3G simulado).
- Revisar a 375px que ninguna pantalla quede en blanco durante la carga.
