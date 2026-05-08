# R19 — Dry-run de la demo (plantilla cronometrada)

> **Cómo usar este documento:** ejecutar el dry-run completo, dos pestañas
> de navegador, setup limpio (`make demo-reset`). Ir cronometrando cada
> flujo y anotando fricciones. Después de la corrida, archivar este file
> con la fecha como `DEMO_DRYRUN_2026-05-DD.md` y abrir uno nuevo si se
> repite.

## Setup previo

```bash
# Terminal 1 — supabase
cd ChessQuery_FS3
supabase status   # debe mostrar todo running, si no: supabase start

# Terminal 2 — backend
cd ChessQuery_FS3/infrastructure
make demo-reset
make up
make ps           # esperar que TODOS estén "(healthy)"

# Terminal 3 — verificación de health
for p in 8080 8081 8082 8083 8084 8085; do
  curl -fs http://localhost:$p/actuator/health | grep -q '"UP"' \
    && echo "✓ $p" || echo "✗ $p"
done

# Browsers
# - Pestaña A (privada): http://localhost:5173
# - Pestaña B (privada): http://localhost:5173
# - Pestaña C (privada, organizer): http://localhost:5174
# - Mailpit/Inbucket: http://127.0.0.1:54324
# - Supabase Studio: http://127.0.0.1:54323
```

## Cronómetro

Iniciar al hacer click en "Crear cuenta" en pestaña A.

| Flujo | Tiempo objetivo | Tiempo real | Notas |
|---|---|---|---|
| **Flujo 1** — registro + live game + PGN | 4 min | __:__ | |
| **Flujo 2** — torneo + inscripción + pairings | 5 min | __:__ | |
| **Flujo 3** — standings + ELO recalculado | +2 min | __:__ | |
| **Flujo 4** — búsqueda fuzzy + perfil público | +1 min | __:__ | |
| **Flujo 5** — circuit breaker (opcional) | +1 min | __:__ | |
| **Total** | ~12 min | __:__ | |

## Checklist de features visibles (verificar uno por uno)

Marcar lo que efectivamente se vio funcionando durante el dry-run:

- [ ] Magic link recibido en Mailpit con URL visible.
- [ ] Indicador 🟢 "Rival conectado" + toast (R8).
- [ ] Header con nombre · ELO · 🇨🇱 (R7).
- [ ] Material capturado y delta abajo de cada jugador (R4).
- [ ] Apertura detectada inline 📖 + ECO.
- [ ] Indicador pulsante "Es tu turno".
- [ ] Sonidos move/capture/check.
- [ ] Reloj decrementando (R5) — si la sesión tiene time control configurado.
- [ ] Pre-move encolado y ejecutado al volver el turno (R9).
- [ ] Picker de promoción Q/R/B/N (R10) — fuerza un peón a la 8.
- [ ] Botones ← → de historial; board read-only (R12).
- [ ] Botón "🤝 Ofrecer tablas" → modal en rival → cierre 1/2-1/2 (R11).
- [ ] Modal de fin de partida con resultado grande + revancha.
- [ ] Campana 🔔 con badge de unread, dropdown con notificación de "Partida #N finalizada" (N1).
- [ ] Email transaccional en Mailpit con resultado de la partida (N2).
- [ ] Studio → tabla `game` muestra fila nueva con `pgn_url`.

## Fricciones encontradas

> Anotar acá cada cosa que rompió la fluidez. Severidad: 🔴 crítico (rompe
> la demo), 🟡 medio (incómodo pero superable), 🟢 bajo (cosmético).

| Sev. | Flujo | Síntoma | Plan B aplicado |
|---|---|---|---|
|     |       |         |                 |

## Métricas

- Latencia entre máquinas (broadcast move): ~ ___ ms (medir con devtools).
- Tiempo de `make demo-reset` ejecución: ___ s (objetivo < 30 s).
- Tiempo desde `make up` hasta todos `healthy`: ___ s (objetivo < 90 s).

## Acciones derivadas

- [ ] Trasladar fricciones 🔴 a issues / hotfix antes de la demo.
- [ ] Actualizar `PLAN_DEMO.md` "Plan B" con cualquier nuevo síntoma.
- [ ] Si algún flujo se desvió mucho del tiempo objetivo, recortar guion
      o agregar nota "skip si va apretado".
