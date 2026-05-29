# Self-Hosted GitHub Actions Runner

Guía para correr los workflows de CI en tu propia máquina (en lugar de los runners gratuitos de GitHub).

## Por qué self-hosted

- **Sin límite de minutos** (los runners gratuitos están bloqueados por billing)
- **Build más rápido** (usa cache local de Maven/npm/Docker)
- **Sin costo** mientras la máquina esté encendida

## Pre-requisitos en la máquina runner

Una sola vez:

```bash
# JDK 17 y Maven (los workflows los instalan por job, pero conviene tener)
sudo dnf install -y java-17-openjdk-devel    # Fedora
# o: sudo apt install -y openjdk-17-jdk     # Ubuntu/Debian

# Node 20 (vía nvm)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 20 && nvm alias default 20

# Docker (para futuros workflows de build de imágenes)
sudo dnf install -y docker docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
# Logout/login para que tome el grupo

# Herramientas auxiliares
sudo dnf install -y git curl jq
```

## Registrar el runner

1. Andá a https://github.com/agusnoopy3000/ChessQuery_FS3/settings/actions/runners/new
2. Elegí **Linux** / **x64**
3. Copiá el token que muestra (válido 1h)

```bash
mkdir -p ~/actions-runner && cd ~/actions-runner

# Descargar última versión (chequear https://github.com/actions/runner/releases)
RUNNER_VERSION=2.321.0
curl -O -L https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz
tar xzf actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz

# Registrar (usa el token de GitHub del paso anterior)
./config.sh \
  --url https://github.com/agusnoopy3000/ChessQuery_FS3 \
  --token <REG_TOKEN> \
  --name chessquery-runner-fedora \
  --labels self-hosted,Linux,X64 \
  --work _work \
  --unattended
```

## Correr como servicio (recomendado — sobrevive a reinicios)

```bash
cd ~/actions-runner
sudo ./svc.sh install $USER
sudo ./svc.sh start
sudo ./svc.sh status     # debe decir "active (running)"
```

Logs:

```bash
journalctl -u actions.runner.agusnoopy3000-ChessQuery_FS3.chessquery-runner-fedora -f
```

## Modo interactivo (alternativa — útil para debuggear)

```bash
cd ~/actions-runner
./run.sh
# CTRL+C para detener
```

## Verificar que el runner está activo

- En GitHub: https://github.com/agusnoopy3000/ChessQuery_FS3/settings/actions/runners
- Debe aparecer `chessquery-runner-fedora` con badge verde "Idle".

## Disparar el primer build

```bash
# Desde el repo
git commit --allow-empty -m "ci: trigger self-hosted run"
git push origin main
# o cualquier push real
```

Seguí el progreso:

```bash
gh run list --limit 3
gh run watch
```

## Mantenimiento

| Comando | Para qué |
|---|---|
| `sudo ./svc.sh stop` | Pausar runner (cualquier job en cola espera) |
| `sudo ./svc.sh start` | Reanudar |
| `sudo ./svc.sh uninstall` | Remover servicio systemd |
| `./config.sh remove --token <REMOVE_TOKEN>` | Desregistrar del repo |

Limpieza periódica del workspace (opcional, evita disco lleno):

```bash
# Cada job limpia su workspace, pero los caches de Maven/npm crecen.
du -sh ~/actions-runner/_work
# Si > 20 GB, limpiar manualmente:
rm -rf ~/.m2/repository/cl/chessquery
rm -rf ~/actions-runner/_work/*/_temp
```

## Seguridad

- **Solo registrá self-hosted runners en repos privados** o de confianza absoluta.
  En un repo público, cualquier PR podría ejecutar código arbitrario en tu máquina.
- El runner ejecuta como tu usuario — tiene acceso a todos tus archivos. Si querés
  aislarlo, considerá Docker o una VM dedicada.
- Los `secrets` del repo (AWS_*, etc.) se inyectan como variables de entorno
  durante el job; no se persisten al disco.

## Workflows configurados para self-hosted

| Workflow | `runs-on` | Estado |
|---|---|---|
| `.github/workflows/ci.yml` | `[self-hosted, Linux, X64]` | ✅ Activo |
| `.github/workflows/build-and-push.yml` | `ubuntu-latest` | ⏸️ Pendiente (requiere AWS) |
| `.github/workflows/deploy.yml` | `ubuntu-latest` | ⏸️ Pendiente (requiere AWS) |

Cuando estés listo para activar el deploy AWS:
1. Configurar secrets `AWS_*` en GitHub (ver `infrastructure/aws/RUNBOOK_ECS.md` §2)
2. Cambiar `runs-on:` en los dos workflows AWS a `[self-hosted, Linux, X64]`
3. Asegurar Docker en el runner

## Troubleshooting

| Síntoma | Causa | Fix |
|---|---|---|
| Job queda "Queued" indefinido | Runner offline | `sudo ./svc.sh status` y `start` |
| `mvn: command not found` | Maven no instalado | Lo instala `setup-maven` por job, pero confirmar que `setup-java` corrió antes |
| `npm: command not found` | Node no en PATH del servicio | Reinstalar nvm con `--default` o instalar Node via dnf/apt |
| `permission denied (docker)` | Usuario no en grupo `docker` | `sudo usermod -aG docker $USER` + logout |
| Disco lleno tras varios builds | Caches acumulados | Ver "Mantenimiento" arriba |
| Runner aparece "Offline" en GitHub | Servicio crasheado | Ver logs con `journalctl` |
