# ♟ lazychess — manual de uso

Panel de control en terminal para manejar **toda** la infra, el despliegue y el git
de ChessQuery desde un solo lugar. Apunta a la cuenta **AWS Academy del equipo**
(`672782205900`, `us-east-1`).

---

## Índice

1. [Qué es](#1-qué-es)
2. [Cómo ejecutarlo (instalación / alias)](#2-cómo-ejecutarlo)
3. [Dependencias](#3-dependencias)
4. [Cómo moverte en el panel](#4-cómo-moverte-en-el-panel)
5. [Comandos por categoría](#5-comandos-por-categoría)
   - [INFRA](#infra--prender-apagar-y-mirar) · [DESPLIEGUE](#despliegue--publicar-y-observar) · [GIT](#git--árbol-ramas-y-versiones) · [APP](#app--accesos-y-verificación)
6. [Flujos típicos](#6-flujos-típicos)
7. [Notas y buenas prácticas](#7-notas-y-buenas-prácticas)

---

## 1. Qué es

Un único script en **bash** (`tools/lazychess`). Tiene dos formas de uso:

- **Panel navegable** (sin argumentos): un menú con dashboard del estado, donde te
  mueves con flechas o escribiendo el comando.
- **Directo** (con argumento): `lazychess status`, `lazychess up`, etc. Útil para ir
  al grano o usarlo en scripts.

---

## 2. Cómo ejecutarlo

Hay tres maneras, de la más cómoda a la más básica:

### Opción A — instalarlo (recomendado)

Crea un acceso `lazychess` global, sin tocar nada del sistema:

```bash
tools/lazychess install
```

Hace un symlink en `~/.local/bin/lazychess` apuntando a donde tengas el repo (lo
detecta solo, así que sirve igual en otra máquina/distro). Después escribes solo:

```bash
lazychess
```

> Si `~/.local/bin` no está en tu `PATH`, el comando te avisa y te dice qué línea
> agregar a tu `~/.bashrc`:
> ```bash
> export PATH="$HOME/.local/bin:$PATH"
> ```
>
> Para quitarlo: `lazychess uninstall`.

### Opción B — alias en tu shell

Si prefieres un alias, agrégalo a tu `~/.bashrc` (ajusta la ruta del repo) y reabre la terminal:

```bash
alias lazychess="$HOME/ChessQuery_FS3/tools/lazychess"
```

### Opción C — directo, sin instalar nada

Desde la carpeta del proyecto:

```bash
tools/lazychess          # o ./tools/lazychess
```

---

## 3. Dependencias

| Herramienta | Para qué | Instalar |
|---|---|---|
| `aws` (AWS CLI) | toda la parte de infra/despliegue | Arch: `pacman -S aws-cli` · Fedora: `dnf install awscli2` |
| `gh` (GitHub CLI) | runner y runs de Actions (`ci`) | Arch: `pacman -S github-cli` · Fedora: `dnf install gh` |
| `jq`, `curl` | parseo y chequeos HTTP | Arch: `pacman -S jq curl` · Fedora: `dnf install jq curl` |
| `tig` | árbol de git navegable (`arbol`) | Arch: `pacman -S tig` · Fedora: `dnf install tig` |
| `docker` | build de imágenes (`deploy`, `etl`) | el daemon prendido: `sudo systemctl start docker` |

Al arrancar, lazychess avisa si falta alguna (y te muestra el comando de **tu** distro).

---

## 4. Cómo moverte en el panel

Al escribir `lazychess` se abre el panel con 4 categorías. Dentro de cada una están
sus comandos.

| Tecla | Acción |
|---|---|
| `↑` `↓` | mover el cursor `❯` |
| `Enter` | abrir la categoría / ejecutar el comando resaltado |
| escribir | teclear el nombre de un comando (ej. `status`, `deploy v0.3.1`) y `Enter` |
| `←` o `Esc` | volver atrás |
| `exit` | salir de la app |
| `Ctrl-C` | cancelar el comando en curso (no cierra la app) |

Ayuda en cualquier momento:

```bash
lazychess help            # lista todos los comandos
lazychess help up         # detalle ampliado de un comando
```

> **Accesibilidad:** con `NO_COLOR=1` se desactivan los colores; el ítem seleccionado
> igual se distingue por el `❯`, y los estados llevan etiqueta de texto `[OK]/[!!]/[NO]`.

---

## 5. Comandos por categoría

### INFRA — prender, apagar y mirar

| Comando | Qué hace |
|---|---|
| `creds` | Pega el bloque `[default]` del lab (AWS Details → CLI). Las credenciales de Academy caducan ~4h; si algo da error de creds, vuelve a correr esto. |
| `status` | Estado detallado: RDS, servicios ECS, target del ALB, autoscaling del ETL y alarmas de CloudWatch. No cambia nada. |
| `up [rev]` | Prende en orden: RDS → stack ECS → espera que el target quede *healthy* (~6-8 min). Opcional: número de revisión de task-def (`up 13`). |
| `down` | Apaga stack y ETL a 0 tasks y detiene la RDS. Pide confirmación. **No borra datos** ni cambia los links. |

### DESPLIEGUE — publicar y observar

| Comando | Qué hace |
|---|---|
| `deploy [vX.Y.Z]` | Build de las 9 imágenes → ECR → registra task-def nueva → redeploy. Necesita Docker prendido. ~10-20 min. |
| `etl [vX.Y.Z]` | Re-despliega el ETL apuntando a la IP actual del stack. Córrelo **después** de cada `deploy` o `up` del stack. |
| `logs [stack\|etl]` | Sigue los logs de CloudWatch en vivo. Sales del seguimiento con `Ctrl-C`. |
| `test` | Corre toda la suite (`scripts/test-all.sh`): Java + BFFs + Frontend. Necesita JDK 17, Maven y Node 20. |

### GIT — árbol, ramas y versiones

| Comando | Qué hace |
|---|---|
| `arbol` | Árbol/grafo **navegable** de main y todas las ramas (usa `tig`). Te mueves con `↑↓`, `Enter` muestra el commit a fondo, `q` sale. Trae una paleta cozy propia (`tools/lazychess.tigrc`). Si no tienes `tig`, imprime el grafo y te dice cómo instalarlo. |
| `flujo` | Grafo de commits **aireado** (con espacio entre cada uno) y colores cálidos. Solo lectura: ideal para seguir el flujo de ramas sin la densidad de `arbol`. |
| `ramas` | Ramas remotas ordenadas por actividad, con cuántos commits van por delante de main y el mensaje de su último commit. |
| `prs` | PRs abiertos y ramas con commits sin mergear a main. |
| `ci` | Si el runner self-hosted está online y los últimos runs de GitHub Actions. |

> También existe `commits [rama]` (historial lineal en texto de una rama) aunque no
> aparece en el menú, porque `arbol` y `flujo` ya cubren ver los commits.

### APP — accesos y verificación

| Comando | Qué hace |
|---|---|
| `urls` | Links estables de la app (portal, organizador, API). No cambian aunque apagues todo. |
| `consola` | Abre links a las consolas de AWS (ECS, CloudWatch, RDS, S3) y al dashboard de Supabase. |
| `smoke` | 4 chequeos HTTP rápidos: API viva (200), auth (401), ruta inexistente (404) y portal S3. |
| `costo` | Estimación de gasto prendido vs apagado y la regla de oro (`down` al terminar). |

---

## 6. Flujos típicos

**Sesión de trabajo en AWS:**

```bash
lazychess creds      # 1. pega el bloque del lab (creds ~4h)
lazychess status     # 2. mira el estado
lazychess up         # 3. prende todo (~6-8 min)
   # ...trabajas...
lazychess down       # 4. apaga al terminar (no borra datos)
```

**Publicar una versión nueva:**

```bash
lazychess deploy v0.3.1   # build + push + task-def + redeploy
lazychess etl v0.3.1      # re-deploy del ETL (la IP del stack cambió)
lazychess smoke           # verifica que responde bien
```

---

## 7. Notas y buenas prácticas

- **Credenciales:** las de AWS Academy rotan cada ~4h. Si un comando falla por creds,
  corre `creds` de nuevo y pega el bloque fresco del lab.
- **Costo:** prendido cuesta ≈ `$0.26/hora`. Regla de oro: `down` al terminar la sesión.
- **Docker:** `deploy` y `etl` necesitan el daemon corriendo (`sudo systemctl start docker`).
- **No pierde datos:** `down` solo apaga cómputo; los datos quedan en RDS y los links no cambian.
