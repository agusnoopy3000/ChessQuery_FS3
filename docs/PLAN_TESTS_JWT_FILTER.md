# Plan de pruebas — Prioridad 2: `SupabaseJwtAuthFilter`

**Objetivo:** subir `api-gateway` de **76.6% → ≥80% líneas** cubriendo el filtro JWT,
hoy en **60.1% líneas (432/719) / 58.3% ramas**. Es la **única** clase floja del módulo
(el resto está en 99–100%), pero también la más crítica: es la puerta de seguridad del sistema.

**Esfuerzo estimado:** 2–3 sesiones. Más técnico que la Prioridad 1 porque las líneas sin
cubrir son justo las más difíciles de simular (red HTTP a Supabase, criptografía EC).

---

## 1. Por qué está bajo hoy

El test actual (`SupabaseJwtAuthFilterTest`, 15 casos) ya cubre **muy bien** el flujo HS256:
rutas públicas, header ausente/mal formado, token expirado/inválido, subject no-UUID,
propagación de headers, extracción de rol (user_metadata / claim directo / app_metadata /
default PLAYER), provision claims y el fallback de `kid` desconocido.

Lo que **NO se ejercita** (y suma la mayoría de las 287 líneas sin cubrir) es toda la
maquinaria **ES256 / JWKS**, que en los tests nunca se activa porque la URL de Supabase no
es alcanzable:

| Método sin cubrir | Líneas | Qué hace |
|---|---|---|
| `refreshJwks()` | 159–187 | Descarga el JWKS por HTTP y puebla el cache. |
| `ecKeyFromJwk()` | 189–208 | Reconstruye una clave pública EC (P-256/384/521) desde el JWK. |
| `resolveKey()` — rama ES con hit | 141–143 | Devuelve la clave EC real del cache (hoy solo se prueba el *miss* → fallback HMAC). |
| `startJwksRefresh` / `stopJwksRefresh` | 121–131 | Ciclo de vida del scheduler de refresh. |
| `extractRole` — rama `catch` | 334–336 | Manejo de excepción al leer claims. |

---

## 2. Estrategia

El problema central: el filtro hace `httpClient.send(...)` a una URL real. Para testear
`refreshJwks` sin Supabase, hay **dos caminos** (elegir uno):

- **(A) Servidor HTTP stub** con **WireMock** o `okhttp3.mockwebserver`: levantás un endpoint
  local que devuelve un JWKS JSON canónico, e inicializás el filtro apuntando a esa URL.
  Cubre `refreshJwks` + `ecKeyFromJwk` + la rama ES256 con hit de punta a punta. **Recomendado.**
- **(B) Reflexión** para inyectar directamente en `jwksCache` una clave EC generada en el test
  y forzar `jwksFetchedAt`. Más liviano (sin dependencia nueva) pero deja `refreshJwks` sin
  cubrir. Sirve como complemento, no como reemplazo.

> Sugerencia: **(A) para `refreshJwks`/`ecKeyFromJwk`** y **(B) para la rama de validación
> ES256 exitosa**, que es lo que más rápido sube la cobertura de `resolveKey`.

---

## 3. Casos de prueba a escribir

### 3.1 Carga del JWKS (camino A — MockWebServer/WireMock)
- **`refreshJwks_pueblaCacheConClaveEC`**: el stub devuelve un JWKS con una clave EC P-256
  válida (`kty:"EC"`, `crv:"P-256"`, `x`, `y`, `kid`). Tras invocar el refresh (vía
  `@PostConstruct` o llamando al método por reflexión), `jwksCache` contiene esa `kid`.
- **`refreshJwks_status404_noTocaCache`**: el stub responde 404 → cache queda vacío, sin
  excepción (rama 166–169).
- **`refreshJwks_jsonSinArrayKeys_noFalla`**: body `{}` o `{"keys":"x"}` → return temprano
  (rama 171–172).
- **`refreshJwks_timeout_capturaExcepcion`**: el stub no responde → cae en el `catch`
  (rama 184–186) sin propagar.

### 3.2 Parseo de JWK (`ecKeyFromJwk`)
- **`ecKeyFromJwk_P256_devuelveClave`** / **`P-384`** / **`P-521`** → 3 curvas soportadas
  (ramas 193–195).
- **`ecKeyFromJwk_curvaNoSoportada_devuelveNull`**: `crv:"P-192"` → null + warn (rama 196).
- **`ecKeyFromJwk_xYInvalidos_devuelveNull`**: base64 corrupto → `catch` (rama 204–206).

### 3.3 Validación ES256 exitosa (camino B — clave inyectada)
- **`filter_tokenES256ConKidEnCache_validaYPropagaHeaders`**: generar un par EC, firmar un
  token ES256 con `kid` conocido, inyectar la pública en `jwksCache` → `resolveKey` devuelve
  la clave EC (rama 141–143, hoy sin cubrir) y el filtro llega a `chain.filter`.

### 3.4 Borde de `extractRole`
- **`extractRole_userMetadataConTipoRaro_caeEnDefaultPlayer`**: token con `user_metadata` que
  no es Map (p. ej. un string) → fuerza el `catch` (334–336) y retorna `"PLAYER"`.

### 3.5 Ciclo de vida (opcional, barato)
- **`startStopJwksRefresh_noLanza`**: invocar `startJwksRefresh()` y `stopJwksRefresh()` por
  reflexión y verificar que no explota (cubre 121–131).

---

## 4. Notas técnicas

- **Generar claves EC en el test:**
  ```java
  KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
  g.initialize(new ECGenParameterSpec("secp256r1"));
  KeyPair kp = g.generateKeyPair();
  // firmar: Jwts.builder()...signWith(kp.getPrivate(), Jwts.SIG.ES256)
  // JWKS x/y: extraer de ((ECPublicKey) kp.getPublic()).getW()
  ```
- **Dependencia nueva (camino A):** agregar `wiremock` o `mockwebserver` con `<scope>test</scope>`
  en `api-gateway/pom.xml`. Si se prefiere cero dependencias nuevas, ir por el camino B + un
  test de `ecKeyFromJwk` invocado por reflexión (no necesita HTTP).
- **Exclusión JaCoCo:** confirmar que `filter/**` NO esté en las exclusiones globales
  (`config/`, `dto/`, `entity/`, `exception/`) — no lo está, así que estas líneas sí cuentan.

---

## 5. Criterio de "hecho" (Definition of Done)

- [x] `cd api-gateway && mvn clean test` → BUILD SUCCESS, 0 fallos (44 tests).
- [x] JaCoCo muestra **`SupabaseJwtAuthFilter` 96.8% líneas** (≥80% ✅, era 60.1%).
- [x] Cobertura global de `api-gateway` **97.9% líneas** (≥80% ✅, era 76.6%).
- [x] Actualizar la tabla de cobertura en `docs/PRUEBAS.md` §5.2 y en `TESTING.md` §1.
- [ ] CI (`java-tests` matriz `api-gateway`) verde en el PR. *(pendiente: abrir PR)*

> **Nota de implementación:** se eligió el **camino (A) sin dependencias nuevas** usando
> `com.sun.net.httpserver.HttpServer` del JDK en lugar de WireMock/MockWebServer, combinado con
> reflexión (camino B) para la rama ES256-con-hit y los métodos privados. No se tocó el `pom.xml`.

## 6. Fuera de alcance (a propósito)
- El `ScheduledExecutorService` real corriendo cada 4 min (no es testeable de forma
  determinística; basta con cubrir `start/stop` y `refreshJwks` directo).
- La latencia/concurrencia del refresh en background (es una optimización de runtime, no lógica
  de negocio verificable en unit test).
</content>
