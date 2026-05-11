# =============================================================================
# ChessQuery — Preflight check (correr 5 min antes de la demo) — Windows
# =============================================================================
# Uso (desde la raíz del repo):
#   pwsh -ExecutionPolicy Bypass -File scripts/preflight.ps1
# =============================================================================

$ErrorActionPreference = "Continue"
$OK = 0; $FAIL = 0; $WARN = 0

function C-Ok($m)   { Write-Host "  OK   $m" -ForegroundColor Green;  $script:OK++ }
function C-Fail($m) { Write-Host "  FAIL $m" -ForegroundColor Red;    $script:FAIL++ }
function C-Warn($m) { Write-Host "  WARN $m" -ForegroundColor Yellow; $script:WARN++ }
function Section($m){ Write-Host ""; Write-Host "-- $m --" -ForegroundColor Cyan }

function Check-Http($name, $url, $expect = 200) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($r.StatusCode -eq $expect) { C-Ok "$name -> $url ($($r.StatusCode))" }
        else { C-Fail "$name -> $url ($($r.StatusCode), esperaba $expect)" }
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code) { C-Fail "$name -> $url ($code)" }
        else       { C-Fail "$name -> $url (no responde)" }
    }
}

Section "Servicios HTTP"
Check-Http "ms-users"         "http://localhost:8081/actuator/health"
Check-Http "ms-tournament"    "http://localhost:8082/actuator/health"
Check-Http "ms-game"          "http://localhost:8083/actuator/health"
Check-Http "ms-notifications" "http://localhost:8085/actuator/health"
Check-Http "ms-etl"           "http://localhost:8086/health"
Check-Http "api-gateway"      "http://localhost:8080/actuator/health"
Check-Http "bff-player"       "http://localhost:3001/health"
Check-Http "bff-organizer"    "http://localhost:3002/health"

Section "Supabase"
Check-Http "Supabase Auth (JWKS)" "http://127.0.0.1:54321/auth/v1/.well-known/jwks.json"
Check-Http "Supabase Inbucket"    "http://127.0.0.1:54324"
# Studio es opcional (lo apagamos para reducir RAM en demo)
try {
    $s = Invoke-WebRequest -Uri "http://127.0.0.1:54323" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    if ($s.StatusCode -eq 200) { C-Ok "Supabase Studio activo (opcional)" }
} catch { C-Warn "Supabase Studio apagado (OK para demo, encender solo si lo necesitas)" }

$SK = if ($env:SUPABASE_SERVICE_KEY) { $env:SUPABASE_SERVICE_KEY } else {
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"
}

try {
    $b = Invoke-RestMethod -Uri "http://127.0.0.1:54321/storage/v1/bucket" -Headers @{ Authorization = "Bearer $SK" } -TimeoutSec 5
    if ($b | Where-Object { $_.name -eq "chessquery-pgn" }) {
        C-Ok "Storage bucket chessquery-pgn existe"
    } else {
        C-Warn "Storage bucket chessquery-pgn no encontrado (PGN upload puede fallar)"
    }
} catch { C-Warn "No pude consultar buckets de Storage" }

Section "RabbitMQ"
try {
    $cred = [System.Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("chessquery:chessquery_dev"))
    $q = Invoke-RestMethod -Uri "http://localhost:15672/api/queues" -Headers @{ Authorization = "Basic $cred" } -TimeoutSec 5
    foreach ($Q in @("notifications.game.events", "users.elo.queue", "notifications.user.events")) {
        if ($q | Where-Object { $_.name -eq $Q }) { C-Ok "Queue $Q existe" }
        else { C-Warn "Queue $Q no encontrada" }
    }
} catch { C-Fail "RabbitMQ Management API no responde" }

Section "Bases de datos"
$dbs = @(
    @{ Name = "user_db";       Container = "chessquery_user_db";       Db = "user_db" }
    @{ Name = "tournament_db"; Container = "chessquery_tournament_db"; Db = "tournament_db" }
    @{ Name = "game_db";       Container = "chessquery_game_db";       Db = "game_db" }
    @{ Name = "notif_db";      Container = "chessquery_notif_db";      Db = "notif_db" }
    @{ Name = "etl_db";        Container = "chessquery_etl_db";        Db = "etl_db" }
)
foreach ($d in $dbs) {
    $r = docker exec $d.Container pg_isready -U chessquery -d $d.Db 2>$null
    if ($LASTEXITCODE -eq 0) { C-Ok "$($d.Name) (postgres ready)" }
    else { C-Fail "$($d.Name) no responde" }
}

Section "Frontends"
Check-Http "chess-portal"    "http://localhost:5173"
Check-Http "organizer-panel" "http://localhost:5174"

Section "Players seed"
foreach ($E in @("carla@demo.cl", "bruno@demo.cl", "ana@demo.cl")) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8081/users/by-email?email=$E" -TimeoutSec 5
        C-Ok "$E provisionado (id=$($r.id))"
    } catch {
        C-Warn "$E no provisionado todavia (se creara en su primer login)"
    }
}

Write-Host ""
Write-Host "============================================================"
Write-Host "  OK=$OK  WARN=$WARN  FAIL=$FAIL"
Write-Host "============================================================"
if ($FAIL -gt 0) {
    Write-Host "  Hay fallos criticos. Revisa antes de la demo." -ForegroundColor Red
    Write-Host "  Fixes rapidos:"
    Write-Host "    - Login devuelve 504/502:  docker restart supabase_kong_ChessQuery_FS3"
    Write-Host "    - Servicio caido:          docker compose -f infrastructure/docker-compose.yml restart <servicio>"
    Write-Host "    - Frontends abajo:         cd frontend; npm run dev:portal  (otro tab: dev:organizer)"
    exit 1
} elseif ($WARN -gt 0) {
    Write-Host "  Hay warnings. Demo puede correr pero revisa." -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "  Todo OK. Demo lista." -ForegroundColor Green
    exit 0
}
