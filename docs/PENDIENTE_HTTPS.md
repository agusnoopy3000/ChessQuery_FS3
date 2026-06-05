# Pendiente — Plan para habilitar HTTPS en ChessQuery

> **Estado: PENDIENTE / EN ANÁLISIS.** No está decidido todavía: depende del **alcance**
> que le demos al proyecto. Este documento deja registrado qué haría falta, las opciones y
> el esfuerzo, para retomarlo cuando corresponda.

Hoy todo corre en **HTTP**:
- Frontend (S3): `http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com` y `.../organizer-panel`
- Backend (ALB): `http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com`

---

## ⚠️ Regla clave: hay que hacer los dos frentes a la vez
Si el sitio queda en `https://` pero la API sigue en `http://`, el navegador **bloquea**
las llamadas ("mixed content"). Por eso **frontend y backend deben pasar a HTTPS juntos**.

## 🔑 El bloqueante principal: un dominio propio
HTTPS válido necesita un **certificado**, y un certificado válido necesita un **dominio**
(ej. `chessquery.cl`). **AWS NO emite certificados para los nombres por defecto**
(`...elb.amazonaws.com`, `...s3-website...`). 

→ **Sin dominio no hay HTTPS "de verdad"** (solo autofirmado, que igual muestra "no seguro").
→ Excepción: **CloudFront** entrega una URL `https://xxxx.cloudfront.net` con certificado
válido incluido, **sin dominio** — sirve para el frontend, pero no para el ALB.

---

## Checklist técnico (cuando se decida avanzar)

### Frente 1 — Backend (API vía ALB)
1. Crear **certificado en ACM** para el dominio (validación por DNS).
2. Agregar **listener HTTPS :443** al ALB con ese certificado (hoy solo tiene :80).
3. (Opcional) Redirigir :80 → :443.
4. Apuntar el dominio (ej. `api.chessquery.cl`) al ALB vía DNS.

### Frente 2 — Frontend (S3)
> Los endpoints "website" de S3 **no soportan HTTPS**: hay que poner **CloudFront** delante.
1. Crear distribución **CloudFront** apuntando a cada bucket (portal y organizer-panel).
2. Certificado **ACM en us-east-1** (CloudFront lo exige en esa región).
3. Apuntar el dominio (ej. `app.chessquery.cl`) a CloudFront (o usar la URL `.cloudfront.net`).

### Frente 3 — Cambios en código/config (chicos pero obligatorios)
1. En el **frontend**: cambiar la URL de la API de `http://...alb...` → `https://api.chessquery.cl`,
   **re-buildear y re-subir a S3**.
2. En el **api-gateway**: agregar los nuevos orígenes `https://...` al **CORS**
   (hoy solo permite los HTTP).

---

## Opciones según alcance

| Opción | Qué resuelve | Necesita dominio | Esfuerzo aprox. |
|---|---|---|---|
| **A. CloudFront solo para frontend** | Quita el "no seguro" del **sitio** (URL `.cloudfront.net`). El backend sigue HTTP → ojo con "mixed content". | No | ~1–2 h |
| **B. HTTPS completo (front + back)** | Todo en HTTPS, candado real. | **Sí** | ~medio día (con dominio ya en mano) |

> La opción A por sí sola **no es suficiente** si el front HTTPS tiene que llamar a un backend
> HTTP (el navegador lo bloquea). Sirve como paso intermedio solo si se acepta esa limitación
> o si el backend también pasa a HTTPS.

---

## Consideraciones en AWS Academy
- **ACM y CloudFront**: normalmente **disponibles** en Academy.
- **Dominio**: hay que conseguirlo aparte (comprarlo o un subdominio gratuito). Es el bloqueante real.
- **Credenciales rotan cada ~4 h**: molesto durante la configuración, no bloqueante.

---

## Decisión pendiente
Definir, según el **alcance del proyecto**, si:
- se deja en HTTP (entorno de pruebas/demo, como está hoy), o
- se hace la opción **A** (frontend seguro, rápido, sin dominio), o
- se invierte en un **dominio** para la opción **B** (HTTPS completo).

**Por ahora queda como pendiente, a analizar.**
