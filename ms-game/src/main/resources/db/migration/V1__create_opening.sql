-- =============================================================================
-- MS-Game — V1: Tabla OPENING con seed de aperturas populares
-- =============================================================================

CREATE TABLE opening (
    id         SERIAL       PRIMARY KEY,
    eco_code   VARCHAR(10)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    variation  VARCHAR(200),
    pgn_moves  TEXT
);

CREATE INDEX idx_opening_eco_code ON opening (eco_code);

-- =============================================================================
-- SEED: Aperturas populares con código ECO
-- pgn_moves almacena los movimientos en formato algebraico simple
-- (sin numeración, separados por espacio) para facilitar el matching por LIKE
-- =============================================================================

INSERT INTO opening (eco_code, name, variation, pgn_moves) VALUES

-- ── Aperturas de Rey (e4) ────────────────────────────────────────────────────

-- Italiana / Giuoco Piano (C50-C59)
('C50', 'Italiana', NULL,                                       'e4 e5 Nf3 Nc6 Bc4'),
('C51', 'Italiana', 'Gambito Evans',                            'e4 e5 Nf3 Nc6 Bc4 Bc5 b4'),
('C52', 'Italiana', 'Gambito Evans aceptado',                   'e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4'),
('C53', 'Italiana', 'Giuoco Piano',                             'e4 e5 Nf3 Nc6 Bc4 Bc5 c3'),
('C54', 'Italiana', 'Giuoco Piano, variante central',           'e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4'),
('C55', 'Italiana', 'Dos Caballos',                             'e4 e5 Nf3 Nc6 Bc4 Nf6'),
('C56', 'Italiana', 'Dos Caballos, ataque Fried Liver',         'e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7'),
('C57', 'Italiana', 'Dos Caballos, ataque Max Lange',           'e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5'),
('C58', 'Italiana', 'Dos Caballos, línea principal',            'e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5'),
('C59', 'Italiana', 'Dos Caballos, variante Kieseritzky',       'e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5 Bb5+'),

-- Española / Ruy López (C60-C99)
('C60', 'Española', NULL,                                       'e4 e5 Nf3 Nc6 Bb5'),
('C61', 'Española', 'Defensa del Pájaro',                       'e4 e5 Nf3 Nc6 Bb5 Nd4'),
('C62', 'Española', 'Variante Steinitz antigua',                'e4 e5 Nf3 Nc6 Bb5 d6'),
('C64', 'Española', 'Defensa Clásica',                          'e4 e5 Nf3 Nc6 Bb5 Bc5'),
('C65', 'Española', 'Defensa Berlín',                           'e4 e5 Nf3 Nc6 Bb5 Nf6'),
('C67', 'Española', 'Defensa Berlín, variante Río',             'e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4'),
('C68', 'Española', 'Variante de cambio',                       'e4 e5 Nf3 Nc6 Bb5 a6 Bxc6'),
('C70', 'Española', 'Variante Morphy',                          'e4 e5 Nf3 Nc6 Bb5 a6 Ba4'),
('C78', 'Española', 'Variante Möller/Arkhangelsk',              'e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O'),
('C80', 'Española', 'Defensa abierta (Variante Morphy abierta)','e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4'),
('C84', 'Española', 'Defensa cerrada',                          'e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7'),
('C86', 'Española', 'Worrall',                                  'e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Qe2'),
('C92', 'Española', 'Chigorin',                                 'e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Na5'),
('C97', 'Española', 'Breyer',                                   'e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8'),

-- Francesa (C00-C19)
('C00', 'Francesa', NULL,                                       'e4 e6'),
('C01', 'Francesa', 'Variante de cambio',                       'e4 e6 d4 d5 exd5'),
('C02', 'Francesa', 'Variante avanzada',                        'e4 e6 d4 d5 e5'),
('C03', 'Francesa', 'Tarrasch',                                 'e4 e6 d4 d5 Nd2'),
('C06', 'Francesa', 'Tarrasch, variante principal',             'e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 Bd3 c5 c3 Nc6 Ne2'),
('C10', 'Francesa', 'Rubinstein',                               'e4 e6 d4 d5 Nc3 dxe4'),
('C11', 'Francesa', 'Clásica',                                  'e4 e6 d4 d5 Nc3 Nf6'),
('C14', 'Francesa', 'Clásica, Steinitz',                        'e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7'),
('C15', 'Francesa', 'Winawer',                                  'e4 e6 d4 d5 Nc3 Bb4'),
('C17', 'Francesa', 'Winawer, variante venenosa',               'e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Ne7'),
('C18', 'Francesa', 'Winawer, variante avanzada',               'e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3+ bxc3 Qc7'),

-- Caro-Kann (B10-B19)
('B10', 'Caro-Kann', NULL,                                      'e4 c6'),
('B12', 'Caro-Kann', 'Variante avanzada',                       'e4 c6 d4 d5 e5'),
('B13', 'Caro-Kann', 'Variante de cambio',                      'e4 c6 d4 d5 exd5'),
('B14', 'Caro-Kann', 'Panov-Botvinnik',                         'e4 c6 d4 d5 exd5 cxd5 c4'),
('B17', 'Caro-Kann', 'Karpov',                                  'e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nd7'),
('B18', 'Caro-Kann', 'Clásica',                                 'e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5'),
('B19', 'Caro-Kann', 'Clásica, variante principal',             'e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6'),

-- Siciliana (B20-B99)
('B20', 'Siciliana', NULL,                                      'e4 c5'),
('B21', 'Siciliana', 'Grand Prix',                              'e4 c5 Nc3'),
('B22', 'Siciliana', 'Alapin',                                  'e4 c5 c3'),
('B23', 'Siciliana', 'Grand Prix, variante cerrada',            'e4 c5 Nc3 Nc6 f4'),
('B30', 'Siciliana', 'Nezhmetdinov-Rossolimo',                  'e4 c5 Nf3 Nc6 Bb5'),
('B40', 'Siciliana', 'Variante inglesa',                        'e4 c5 Nf3 e6'),
('B44', 'Siciliana', 'Taimanov',                                'e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6'),
('B45', 'Siciliana', 'Taimanov, variante del caballo',          'e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3'),
('B48', 'Siciliana', 'Taimanov con Be3',                        'e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7 Be3'),
('B54', 'Siciliana', 'Dragón Yugoslavo',                        'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3'),
('B57', 'Siciliana', 'Clásica',                                 'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 Nc6 Bc4'),
('B60', 'Siciliana', 'Richter-Rauzer',                          'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 Nc6 Bg5'),
('B70', 'Siciliana', 'Dragón',                                  'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6'),
('B72', 'Siciliana', 'Dragón, variante clásica',                'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 Be2'),
('B76', 'Siciliana', 'Dragón Yugoslavo, variante principal',    'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6 Bc4'),
('B80', 'Siciliana', 'Scheveningen',                            'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6'),
('B81', 'Siciliana', 'Scheveningen, Keres',                     'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6 g4'),
('B84', 'Siciliana', 'Scheveningen, Najdorf Be2',               'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6 Be2 a6'),
('B90', 'Siciliana', 'Najdorf',                                 'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6'),
('B91', 'Siciliana', 'Najdorf, variante inglesa',               'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5'),
('B92', 'Siciliana', 'Najdorf Be2',                             'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2'),
('B93', 'Siciliana', 'Najdorf f4',                              'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f4'),
('B94', 'Siciliana', 'Najdorf Bg5',                             'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5'),
('B96', 'Siciliana', 'Najdorf, variante venenosa',              'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4'),
('B97', 'Siciliana', 'Najdorf, variante Polugaevsky',           'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Qb6'),
('B99', 'Siciliana', 'Najdorf, línea principal',                'e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Be7 Qf3'),

-- Pirc (B07-B09)
('B07', 'Pirc',     NULL,                                       'e4 d6 d4 Nf6'),
('B08', 'Pirc',     'Clásica',                                  'e4 d6 d4 Nf6 Nc3 g6 Nf3'),
('B09', 'Pirc',     'Ataque austríaco',                         'e4 d6 d4 Nf6 Nc3 g6 f4'),

-- ── Aperturas de Dama (d4) ────────────────────────────────────────────────────

-- Gambito de Dama (D20-D69)
('D00', 'Gambito de Dama', NULL,                                'd4 d5'),
('D06', 'Gambito de Dama', 'Simétrico',                         'd4 d5 c4 c5'),
('D10', 'Gambito de Dama', 'Eslavo, variante de cambio',        'd4 d5 c4 c6 Nc3 dxc4'),
('D15', 'Gambito de Dama', 'Eslavo',                            'd4 d5 c4 c6 Nf3 Nf6'),
('D20', 'Gambito de Dama', 'Aceptado',                          'd4 d5 c4 dxc4'),
('D27', 'Gambito de Dama', 'Aceptado, variante clásica',        'd4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6'),
('D30', 'Gambito de Dama', 'Rehusado',                          'd4 d5 c4 e6'),
('D35', 'Gambito de Dama', 'Variante de cambio',                'd4 d5 c4 e6 Nc3 Nf6 cxd5'),
('D37', 'Gambito de Dama', 'Variante del caballero',            'd4 d5 c4 e6 Nc3 Nf6 Nf3'),
('D43', 'Gambito de Dama', 'Semi-Eslavo',                       'd4 d5 c4 e6 Nc3 Nf6 Nf3 c6'),
('D44', 'Gambito de Dama', 'Semi-Eslavo, variante de gambit',   'd4 d5 c4 e6 Nc3 Nf6 Nf3 c6 Bg5 dxc4'),
('D45', 'Gambito de Dama', 'Semi-Eslavo, Botvinnik',            'd4 d5 c4 e6 Nc3 Nf6 Nf3 c6 e3'),
('D46', 'Gambito de Dama', 'Semi-Eslavo, Merano',               'd4 d5 c4 e6 Nc3 Nf6 Nf3 c6 e3 Nbd7 Bd3'),
('D50', 'Gambito de Dama', 'Con Bg5',                           'd4 d5 c4 e6 Nc3 Nf6 Bg5'),
('D51', 'Gambito de Dama', 'Con Bg5, Defensa Cambridge Springs','d4 d5 c4 e6 Nc3 Nf6 Bg5 Nbd7'),
('D60', 'Gambito de Dama', 'Ortodoxa',                          'd4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3'),
('D61', 'Gambito de Dama', 'Ortodoxa, Capablanca',              'd4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Rc1'),

-- Nimzo-India (E20-E59)
('E20', 'Nimzo-India', NULL,                                    'd4 Nf6 c4 e6 Nc3 Bb4'),
('E21', 'Nimzo-India', 'Variante de los tres caballos',         'd4 Nf6 c4 e6 Nc3 Bb4 Nf3'),
('E32', 'Nimzo-India', 'Variante clásica',                      'd4 Nf6 c4 e6 Nc3 Bb4 Qc2'),
('E40', 'Nimzo-India', 'Variante del Gambito',                  'd4 Nf6 c4 e6 Nc3 Bb4 e3'),
('E46', 'Nimzo-India', 'Variante Reshevsky',                    'd4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5'),
('E54', 'Nimzo-India', 'Variante Geller',                       'd4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O dxc4 Bxc4'),
('E60', 'India de Rey', NULL,                                   'd4 Nf6 c4 g6'),
('E61', 'India de Rey', 'Variante de la defensa moderna',       'd4 Nf6 c4 g6 Nc3 Bg7 Nf3'),
('E62', 'India de Rey', 'Variante Fianchetto',                  'd4 Nf6 c4 g6 Nc3 Bg7 Nf3 O-O g3'),
('E67', 'India de Rey', 'Fianchetto, variante Panno',           'd4 Nf6 c4 g6 Nc3 Bg7 Nf3 O-O g3 d6 Bg2 Nc6'),
('E70', 'India de Rey', 'Variante de cuatro peones',            'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4'),
('E76', 'India de Rey', 'Variante de cuatro peones, variante',  'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4 O-O Nf3'),
('E80', 'India de Rey', 'Sämisch',                              'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3'),
('E84', 'India de Rey', 'Sämisch, variante Panno',              'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 Nc6'),
('E92', 'India de Rey', 'Averbakh',                             'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Bg5'),
('E97', 'India de Rey', 'Variante Mar del Plata',               'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Nf3 e5 O-O Nc6 d5 Ne7'),
('E98', 'India de Rey', 'Variante Mar del Plata, variante',     'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Nf3 e5 O-O Nc6 d5 Ne7 Ne1'),
('E99', 'India de Rey', 'Variante Mar del Plata, línea aguda',  'd4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Be2 O-O Nf3 e5 O-O Nc6 d5 Ne7 Ne1 Nd7 f3 f5 g4'),

-- Grünfeld (D70-D99)
('D70', 'Grünfeld', NULL,                                       'd4 Nf6 c4 g6 Nc3 d5'),
('D72', 'Grünfeld', 'Variante de cambio con Ne2',               'd4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Nf3'),
('D80', 'Grünfeld', 'Línea principal',                          'd4 Nf6 c4 g6 Nc3 d5 Bf4'),
('D85', 'Grünfeld', 'Variante de cambio, línea principal',      'd4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Bc4'),
('D87', 'Grünfeld', 'Variante rusa',                            'd4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Bc4 c5 Ne2 Nc6 Be3 O-O'),
('D97', 'Grünfeld', 'Variante rusa, ataque',                    'd4 Nf6 c4 g6 Nc3 d5 Nf3 Bg7 Qb3'),

-- ── Aperturas de flanco ───────────────────────────────────────────────────────

-- Inglesa (A10-A39)
('A10', 'Inglesa', NULL,                                        'c4'),
('A15', 'Inglesa', 'Anglo-India',                               'c4 Nf6'),
('A20', 'Inglesa', 'Variante del rey',                          'c4 e5'),
('A22', 'Inglesa', 'Con caballo en c3',                         'c4 e5 Nc3 Nf6'),
('A25', 'Inglesa', 'Defensa Siciliana',                         'c4 e5 Nc3 Nc6'),
('A30', 'Inglesa', 'Simétrica',                                 'c4 c5'),
('A34', 'Inglesa', 'Simétrica, variante principal',             'c4 c5 Nc3 Nf6 Nf3 d5'),
('A36', 'Inglesa', 'Simétrica ultra',                           'c4 c5 Nc3 g6'),

-- Holandesa (A80-A99)
('A80', 'Holandesa', NULL,                                      'd4 f5'),
('A81', 'Holandesa', 'Variante del fianchetto',                 'd4 f5 g3'),
('A84', 'Holandesa', 'Variante clásica',                        'd4 f5 c4 Nf6 Nc3 e6'),
('A85', 'Holandesa', 'Con Nc3',                                 'd4 f5 c4 Nf6 Nc3'),
('A87', 'Holandesa', 'Leningrado',                              'd4 f5 c4 Nf6 Nc3 g6 Nf3 Bg7'),
('A90', 'Holandesa', 'Defensa moderna',                         'd4 f5 c4 e6 g3'),
('A96', 'Holandesa', 'Clásica, variante Ilyin-Zhenevsky',       'd4 f5 c4 e6 g3 Nf6 Bg2 Be7 Nf3 O-O O-O d6');
