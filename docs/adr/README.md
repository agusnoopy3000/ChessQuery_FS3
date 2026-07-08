# Architecture Decision Records (ADR)

Registro de decisiones arquitectónicas de ChessQuery. Formato corto:
**contexto → opciones → decisión → consecuencias**.

Toda decisión estructural (nuevo servicio, cambio de modelo de datos transversal,
estrategia de integración) se documenta como ADR **antes** de codificar. Los ADRs
son inmutables una vez `Aceptado`; para revertir se crea un ADR nuevo que
`Reemplaza a` el anterior.

| ADR | Título | Estado |
|---|---|---|
| [0001](0001-elo-por-modalidad.md) | ELO segmentado por modalidad de tiempo | Propuesto |
| [0002](0002-ubicacion-modulo-desafios.md) | Ubicación del módulo de desafíos y logros | Propuesto |
| [0003](0003-arquitectura-modo-espectador.md) | Arquitectura del modo espectador (link público) | Propuesto |

Estados posibles: `Propuesto` · `Aceptado` · `Rechazado` · `Reemplazado por ADR-XXXX`.
