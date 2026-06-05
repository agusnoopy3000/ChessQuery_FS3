# ChessQuery — Cómo está desplegado (guía simple para el equipo)

> Documento **no técnico**. La idea es que cualquiera del equipo entienda, en pocos minutos,
> dónde vive ChessQuery, qué piezas lo componen y cómo se prende/apaga.

---
Ahora el flujo de la aplicación sobre la infra AWS:

🔄 Flujo ChessQuery (request de punta a punta)

                                 ☁️  AWS (us-east-1)
 👤 Usuario
   │
   │ 1. Abre el sitio (navegador)
   ▼
 🪣 S3  ── chess-portal / organizer-panel  (HTML/JS estático del frontend)
   │
   │ 2. Login / Registro
   ▼
 🔐 Supabase Auth (fuera de AWS) ── valida usuario, emite token (JWT)
   │                                   └─ webhook "usuario registrado" ─┐
   │ 3. El front llama a la API con el token                            │
   ▼                                                                    │
 🌐 ALB (puerta de entrada pública, HTTP)                               │
   │                                                                    │
   ▼                                                                    │
 ┌──────────────────── ECS Fargate · 1 task (10 contenedores) ───────┐ │
 │                                                                    │ │
 │  🚪 api-gateway  ── valida el token y enruta cada pedido          │ │
 │       │                                                            │ │
 │       ├─► 🧩 bff-player / bff-organizer  (arman la respuesta)     │ │
 │       │        │                                                   │ │
 │       │        ├─► 👤 ms-users        (perfiles, ELO, Lichess) ◄──┘ │
 │       │        ├─► 🏆 ms-tournament   (torneos, rondas)            │
 │       │        ├─► ♟️  ms-game         (partidas en vivo)           │
 │       │        ├─► 📊 ms-analytics    (estadísticas)               │
 │       │        └─► 🔔 ms-notifications (campana + correos)          │
 │       │                                                            │
 │  📨 RabbitMQ  ── los servicios se avisan entre sí (eventos)        │
 │  ⚡ Redis     ── caché / datos rápidos                             │
 └────────────────────────────────────────────────────────────────┬─┘
                                                                    │
                                                                    ▼
                                            🗄️  RDS PostgreSQL (una BD por servicio)

Ejemplo concreto — "invito a un amigo a jugar":
1. Entro al portal (S3) ya logueado (token de Supabase).
2. El front llama al ALB → api-gateway valida mi token.
3. El gateway pasa al bff-player, que le pide a ms-game crear la partida.
4. ms-game guarda la partida en RDS y publica un evento en RabbitMQ.
5. ms-notifications escucha ese evento → crea la notificación en la campana y manda el cor dos únicos correos).
6. Mi amigo ve la invitación al entrar y acepta → juegan en vivo.

## 1. En una frase

ChessQuery corre en **la nube de Amazon (AWS)**. El sitio web que ve el usuario está
en un servicio de archivos (S3), y toda la "inteligencia" (cuentas, torneos, partidas,
notificaciones) corre en un solo servidor administrado que se prende y apaga cuando lo
necesitamos. Las cuentas y el login los maneja un servicio externo llamado **Supabase**.

---

## 2. Las piezas, explicadas fácil

| Pieza | Qué es (analogía) | Para qué sirve en ChessQuery |
|---|---|---|
| **S3** | Una "carpeta pública en internet" | Guarda y sirve el sitio web (la parte visual: botones, pantallas). |
| **Supabase** | El "portero con lista de invitados" | Login y registro. Verifica quién sos y entrega un pase (token). |
| **ALB** | La "puerta de entrada" | Recibe todos los pedidos desde internet y los manda adentro. |
| **ECS (Fargate)** | El "servidor donde vive la app" | Ejecuta todos los servicios del backend. Se prende/apaga a demanda. |
| **RDS (PostgreSQL)** | La "base de datos / archivero" | Guarda todo: usuarios, torneos, partidas, ratings, notificaciones. |

Todo esto está en la región **EE.UU. Este (us-east-1)** de AWS.

---

## 3. Los servicios del backend (qué hace cada uno)

Todos viven **juntos en un mismo servidor** (una "caja" con varios programas adentro):

- **api-gateway** — el recepcionista: revisa el pase (token) y manda cada pedido al servicio correcto.
- **bff-player / bff-organizer** — dos "asistentes" que arman las respuestas a medida del
  portal de jugador y del panel de organizador.
- **ms-users** — cuentas, perfiles, ELO y ratings de **Lichess**.
- **ms-tournament** — torneos y rondas.
- **ms-game** — partidas en vivo.
- **ms-analytics** — estadísticas.
- **ms-notifications** — la campana de notificaciones y los **correos**.
- **RabbitMQ** — el "sistema de avisos internos": cuando algo pasa (ej. termina una partida),
  un servicio avisa a los otros.
- **Redis** — una "memoria rápida" para datos que se consultan seguido.

> 📧 **Correos:** ChessQuery solo envía **dos** correos: el de **bienvenida** (al registrarse)
> y el de **invitación a partida**. El resto de los avisos quedan dentro de la app (campana).

---

## 4. Cómo se ve un pedido, paso a paso

1. El usuario abre el sitio (que vive en **S3**).
2. Se loguea: **Supabase** confirma su identidad y le da un pase (token).
3. El sitio hace un pedido (ej. "creá esta partida") que entra por la **puerta (ALB)**.
4. El **api-gateway** revisa el pase y lo deriva al servicio que corresponde.
5. El servicio guarda/lee lo que necesita en la **base de datos (RDS)** y, si hace falta,
   avisa a otros servicios por **RabbitMQ**.
6. La respuesta vuelve al usuario.

---

## 5. Las direcciones (links)

| Qué | Link |
|---|---|
| **Portal de jugador** | http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com |
| **Panel de organizador** | http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com |
| **Backend (API)** | http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com |

> Por ahora es **HTTP** (sin candado de "seguro"). El navegador puede avisar que el sitio
> "no es seguro": es esperado en esta etapa de pruebas.

---

## 6. Prender y apagar (para no gastar)

Trabajamos con créditos de **AWS Academy** (limitados), así que la app **no queda prendida 24/7**:

- **Para probar:** se prende la base de datos (RDS) y el servidor (ECS). Tarda unos minutos.
- **Al terminar:** se apagan los dos. El sitio (S3) queda visible, pero el login y los datos
  no responden hasta volver a prender.

> Lo que cuesta dinero es el **servidor (ECS)** y la **base de datos (RDS)**; por eso esos dos
> son los que se apagan. El sitio en S3 y la puerta (ALB) cuestan muy poco.

**Estado actual: APAGADO** (ECS y RDS detenidos para no gastar).

---

## 7. Detalles bueno saber (sin entrar en lo técnico)

- **Credenciales de AWS Academy** vencen cada ~4 horas. Por eso, para desplegar cambios,
  hay que refrescarlas antes. (Es una limitación del entorno académico, no un error nuestro.)
- **Despliegue de cambios:** se hace de forma manual y controlada desde el equipo de desarrollo.
- **Pendientes para una versión "de producción real"** (no bloquean las pruebas de hoy):
  HTTPS (el candado de seguro), correo con dominio propio, y que la app quede prendida siempre.
