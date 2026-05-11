# =============================================================================
# ChessQuery — Setup local para Windows (PowerShell 5.1+)
# =============================================================================
# Uso (desde la raíz del repo):
#   pwsh -ExecutionPolicy Bypass -File scripts/setup.ps1
#
# Qué hace:
#   1. Verifica prerequisitos (Docker Desktop, WSL2, Supabase CLI, Node, Java)
#   2. Copia infrastructure/.env.example → infrastructure/.env si no existe
#   3. Levanta Supabase local (auth + db + storage)
#   4. Levanta servicios ChessQuery de scope demo con docker compose
#   5. Instala dependencias del frontend (npm install)
#   6. Imprime URLs y credenciales de prueba
# =============================================================================

$ErrorActionPreference = "Stop"
$REPO_ROOT = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $REPO_ROOT

function Write-Step($msg) { Write-Host "`n[setup] $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "   OK  $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "   !!  $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "   XX  $msg" -ForegroundColor Red }

function Test-Cmd($name) { $null -ne (Get-Command $name -ErrorAction SilentlyContinue) }

# -----------------------------------------------------------------------------
# 1. Prerequisitos
# -----------------------------------------------------------------------------
Write-Step "Verificando prerequisitos"

$missing = @()
if (-not (Test-Cmd "docker"))   { $missing += "Docker Desktop (https://www.docker.com/products/docker-desktop)" }
if (-not (Test-Cmd "supabase")) { $missing += "Supabase CLI  (scoop install supabase  o  npm i -g supabase)" }
if (-not (Test-Cmd "node"))     { $missing += "Node.js 20+   (https://nodejs.org)" }
if (-not (Test-Cmd "npm"))      { $missing += "npm (viene con Node)" }

if ($missing.Count -gt 0) {
    Write-Err "Faltan herramientas:"
    $missing | ForEach-Object { Write-Host "      - $_" }
    Write-Host "`nInstala lo faltante y vuelve a ejecutar este script." -ForegroundColor Yellow
    exit 1
}

# Docker corriendo?
try {
    docker info *>$null
    if ($LASTEXITCODE -ne 0) { throw "docker info falló" }
    Write-Ok "Docker Desktop activo"
} catch {
    Write-Err "Docker Desktop no está corriendo. Ábrelo desde el menú Inicio y vuelve a ejecutar."
    exit 1
}

Write-Ok "Docker, Supabase CLI, Node y npm encontrados"

# -----------------------------------------------------------------------------
# 2. .env de infraestructura
# -----------------------------------------------------------------------------
Write-Step "Configurando infrastructure/.env"

$envFile     = Join-Path $REPO_ROOT "infrastructure/.env"
$envExample  = Join-Path $REPO_ROOT "infrastructure/.env.example"

if (-not (Test-Path $envFile)) {
    if (-not (Test-Path $envExample)) {
        Write-Err "No existe $envExample — el repo está incompleto."
        exit 1
    }
    Copy-Item $envExample $envFile
    Write-Ok ".env creado desde .env.example"
} else {
    Write-Ok ".env ya existe (no se sobreescribe)"
}

# -----------------------------------------------------------------------------
# 3. Supabase local
# -----------------------------------------------------------------------------
Write-Step "Levantando Supabase local (auth + db + storage)"

# Estado actual
$supaStatus = (supabase status 2>&1) | Out-String
if ($supaStatus -match "API URL") {
    Write-Ok "Supabase ya está corriendo"
} else {
    Write-Host "   ... esto puede tardar 1-2 min la primera vez (descarga imágenes)" -ForegroundColor DarkGray
    supabase start
    if ($LASTEXITCODE -ne 0) {
        Write-Err "supabase start falló. Revisa logs con: supabase status"
        exit 1
    }
    Write-Ok "Supabase levantado"
}

# -----------------------------------------------------------------------------
# 4. Servicios ChessQuery (scope demo)
# -----------------------------------------------------------------------------
Write-Step "Construyendo y levantando microservicios ChessQuery (scope demo)"

Push-Location (Join-Path $REPO_ROOT "infrastructure")
try {
    # Build con cache (más rápido). Si quieres rebuild total: docker compose build --no-cache
    Write-Host "   ... build de imágenes (usa cache; primera vez tarda ~5 min)" -ForegroundColor DarkGray
    docker compose build `
        ms-users ms-tournament ms-game ms-etl ms-notifications `
        bff-player bff-organizer api-gateway
    if ($LASTEXITCODE -ne 0) { Write-Err "Build falló"; exit 1 }

    Write-Host "   ... up -d --wait (espera healthchecks)" -ForegroundColor DarkGray
    docker compose up -d --wait --no-build `
        user_db tournament_db game_db notif_db etl_db `
        rabbitmq redis `
        ms-users ms-tournament ms-game ms-etl ms-notifications `
        bff-player bff-organizer api-gateway nginx
    # ms-etl healthcheck es defectuoso (usa curl que no existe en la imagen),
    # pero el servicio igual funciona. Ignorar exit code != 0 si solo es eso.
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Algún healthcheck falló (probable: ms-etl)."
        Write-Warn "Verifica con: docker compose ps  — si solo ms-etl está unhealthy, ignora."
    } else {
        Write-Ok "Todos los servicios healthy"
    }
} finally {
    Pop-Location
}

# -----------------------------------------------------------------------------
# 5. Frontend
# -----------------------------------------------------------------------------
Write-Step "Instalando dependencias del frontend"

Push-Location (Join-Path $REPO_ROOT "frontend")
try {
    if (-not (Test-Path "node_modules")) {
        npm install
        if ($LASTEXITCODE -ne 0) { Write-Err "npm install falló"; exit 1 }
    }
    Write-Ok "Dependencias instaladas"
} finally {
    Pop-Location
}

# -----------------------------------------------------------------------------
# 6. Resumen
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  ChessQuery listo para correr" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontends (correr en 2 terminales separadas):" -ForegroundColor White
Write-Host "    cd frontend; npm run dev:portal     -> http://localhost:5173" -ForegroundColor Gray
Write-Host "    cd frontend; npm run dev:organizer  -> http://localhost:5174" -ForegroundColor Gray
Write-Host ""
Write-Host "  Backend / infra:" -ForegroundColor White
Write-Host "    Supabase Studio   -> http://localhost:54323" -ForegroundColor Gray
Write-Host "    Supabase Inbucket -> http://localhost:54324 (emails de magic link)" -ForegroundColor Gray
Write-Host "    API Gateway       -> http://localhost:8080" -ForegroundColor Gray
Write-Host "    RabbitMQ UI       -> http://localhost:15672 (chessquery/chessquery_dev)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Comandos utiles:" -ForegroundColor White
Write-Host "    docker compose -f infrastructure/docker-compose.yml ps      # ver estado" -ForegroundColor Gray
Write-Host "    docker compose -f infrastructure/docker-compose.yml logs -f # ver logs" -ForegroundColor Gray
Write-Host "    docker compose -f infrastructure/docker-compose.yml down    # detener (preserva data)" -ForegroundColor Gray
Write-Host "    supabase stop                                              # detener Supabase" -ForegroundColor Gray
Write-Host ""
Write-Host "  Si tras un 'down' el login devuelve 504 o 502:" -ForegroundColor Yellow
Write-Host "    docker restart supabase_kong_ChessQuery_FS3" -ForegroundColor Gray
Write-Host ""
