# Plan — Navegación móvil (menú hamburguesa / drawer)

> Opción (a) de la verificación responsive. Estado actual: el flujo es responsive
> (auth, tablero, grids colapsan), pero en ≤980px el `Shell` apila el sidebar
> completo **arriba** del contenido (no hay hamburguesa). Funciona, pero empuja el
> contenido hacia abajo en teléfonos. Este plan lo lleva a un patrón mobile-first.

## Objetivo
En móvil (≤980px): barra superior compacta con logo + botón **hamburguesa**; la
navegación se abre como **drawer** (panel lateral deslizante) y se cierra al elegir
una opción o tocar el fondo. En desktop (>980px): sin cambios (sidebar fijo actual).

## Alcance (archivos)
- `frontend/packages/ui-lib/src/components/Shell.tsx` — estado de apertura + topbar móvil + drawer.
- `frontend/packages/ui-lib/src/theme/theme.css` — estilos `.app-topbar-mobile`, `.sidebar` como drawer en ≤980px, backdrop, animación de entrada.
- (Sin tocar las páginas: heredan del `Shell`.)

## Pasos
1. **Estado en `Shell`** (componente cliente):
   - `const [navOpen, setNavOpen] = useState(false)`.
   - Cerrar al navegar: pasar `onNavigate` a los items o cerrar en el `onClick` existente.
   - Cerrar con tecla `Escape` y al click en backdrop.
2. **Topbar móvil** (solo ≤980px, oculto en desktop):
   - Logo + nombre + botón hamburguesa (`aria-label="Abrir menú"`, `aria-expanded`, `aria-controls`).
   - Altura ≤ 56px, sticky top.
3. **Sidebar como drawer** en ≤980px:
   - `position: fixed; inset: 0 auto 0 0; width: min(280px, 84vw); transform: translateX(-100%)`.
   - `.sidebar.open { transform: translateX(0) }` con `transition: transform .24s`.
   - **Backdrop** semitransparente (`.nav-backdrop`) con fade; click cierra.
   - `z-index` por encima del contenido; el contenido NO se reordena (el drawer flota).
4. **Accesibilidad:**
   - `role="dialog"`/`aria-modal` opcional en el drawer; focus al primer item al abrir; devolver foco al botón al cerrar.
   - Respetar `prefers-reduced-motion` (ya global): el drawer aparece sin slide.
   - Bloquear scroll del body mientras el drawer está abierto (`overflow:hidden`).
5. **Desktop intacto:** todo lo anterior dentro de `@media (max-width:980px)`. En >980px el `.sidebar` sigue como columna fija y la topbar móvil queda `display:none`.

## Criterios de aceptación
- En 375px: la primera pantalla muestra contenido (no la navegación completa); el menú se abre/cierra con la hamburguesa.
- Sin scroll horizontal en ninguna pantalla del flujo (registro → login → portal → jugar → fin).
- Navegar cierra el drawer; `Escape` y backdrop también.
- Desktop sin cambios visuales.
- `prefers-reduced-motion`: sin animación de slide.

## Verificación
- `tsc --noEmit` + specs del portal.
- (Opcional, recomendado) Playwright headless: screenshots a 375px de cada pantalla como evidencia para la defensa.
- Rebuild + `aws s3 sync` a `chessquery-chess-portal` y `chessquery-organizer-panel`.

## Estimación
Acotado: ~1 archivo de componente + ~30-40 líneas de CSS. Riesgo bajo (no toca páginas).
