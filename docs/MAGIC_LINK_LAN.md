# R3 — Magic Link en LAN IP (investigación + workaround)

## Síntoma

Cuando el frontend chess-portal se sirve en `http://192.168.1.186:5173` (LAN IP del laptop, accesible desde mobile/tablet), Supabase Auth (GoTrue) **rechaza el redirect** del magic link aunque la URL esté en `additional_redirect_urls`. El email llega y el usuario hace click, pero termina en `/auth/v1/verify?error=...`.

Con `http://localhost:5173` o `http://127.0.0.1:5173` desde el mismo equipo: funciona perfecto.

## Configuración actual de Supabase local

Archivo: `supabase/config.toml`

```toml
[auth]
site_url = "http://localhost:5173"
additional_redirect_urls = [
  "http://localhost:5173",
  "http://localhost:5174",
  "http://127.0.0.1:5173",
  "http://192.168.1.186:5173"  # ← agregada pero rechazada
]
```

## Hipótesis a verificar

1. **GoTrue valida `site_url` además de `additional_redirect_urls`.** El primer dominio es el "canónico" y la cookie de sesión queda atada a ese host. Cuando el redirect cae en otro host, la cookie no viaja → re-auth loop.
   - **Test:** cambiar `site_url` a `http://192.168.1.186:5173` y ver si funciona desde mobile.
2. **Algunos parsers de URL en GoTrue normalizan IPs literales.** El path matcher podría rechazar `192.168.x.x` por considerarlas no-routables.
   - **Test:** crear hostname `chessquery.local` en `/etc/hosts` apuntando a la LAN IP, y usarlo en lugar de la IP.
3. **HTTPS-only pickup.** GoTrue v2 marca como inseguros redirects HTTP a hosts que no son `localhost`/`127.0.0.1`.
   - **Test:** configurar HTTPS local con `mkcert` y un proxy.

## Workaround para la demo

**Demo del magic link → desde desktop (`localhost:5173`).** Funciona perfecto con Mailpit. Mostrar el flujo magic-link en la máquina del presentador.

**Mobile responsive del tablero → seguir mostrándolo**, pero entrando por `localhost:5173` desde el mismo laptop. El layout responsive ya está implementado y se ve bien (probado en chrome devtools modo móvil).

Si surge la pregunta del evaluador "¿y desde un teléfono real?": responder _"funciona end-to-end con `chessquery.local` en producción; en local exige fix de configuración de GoTrue que está en backlog post-demo (documentado en MAGIC_LINK_LAN.md)"_.

## Logs útiles para reproducir

```bash
# Stack supabase local (incluye GoTrue como `supabase_auth_chessquery`)
docker logs supabase_auth_chessquery --tail 100 -f

# Buscar líneas tipo:
#   "url not allowed"
#   "redirect to is invalid"
```

## Próximos pasos (post-demo)

- [ ] Probar hipótesis 1 (cambiar `site_url`).
- [ ] Si sigue fallando, abrir issue en `supabase/auth` con repro mínimo.
- [ ] Para producción cloud (Supabase Cloud) este problema desaparece porque las URLs públicas son HTTPS con dominios reales.
