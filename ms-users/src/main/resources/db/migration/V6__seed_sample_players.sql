-- =============================================================================
-- MS-Users — V6: Datos de prueba para demo
-- 10 jugadores chilenos con nombres y ratings realistas.
-- Password de sus cuentas auth: "Chess2026!" (gestionado en auth_db).
-- El id de cada jugador debe coincidir con el id en auth_db.AUTH_USER.
-- =============================================================================

INSERT INTO player
    (id, first_name, last_name, rut, email, country_id, region, club_id,
     birth_date, gender, fide_id, elo_national, elo_fide_standard, created_at)
SELECT
    p.pid,
    p.fname, p.lname, p.rut, p.email,
    co.id,
    p.region,
    cl.id,
    p.bdate::DATE,
    p.gender,
    p.fide_id,
    p.elo_nat, p.elo_fide,
    NOW()
FROM (VALUES
    -- Nota: Los primeros 3 ids (1,2,3) corresponden a admin, organizador y jugador de prueba.
    -- Los jugadores del seed arrancan en id=4.
    (4,  'Rodrigo',    'Sepúlveda',  '15234567-8', 'rodrigo.sepulveda@demo.cl',  'Metropolitana',   'Club de Ajedrez Lasker',               '1992-03-15', 'M', '3600001', 2100, 2050),
    (5,  'Camila',     'Torres',     '16345678-9', 'camila.torres@demo.cl',      'Valparaíso',      'Club de Ajedrez Valparaíso',           '1998-07-22', 'F', '3600002', 1850, 1800),
    (6,  'Diego',      'Fuentes',    '17456789-0', 'diego.fuentes@demo.cl',      'Metropolitana',   'Club de Ajedrez Torre',                '1995-11-03', 'M', '3600003', 1950, 1920),
    (7,  'Valentina',  'Morales',    '18567890-1', 'valentina.morales@demo.cl',  'Biobío',          'Club de Ajedrez Concepción',           '2005-01-30', 'F', NULL,      1620, NULL),
    (8,  'Ignacio',    'Pérez',      '19678901-2', 'ignacio.perez@demo.cl',      'Metropolitana',   'Club de Ajedrez Universidad de Chile',  '1988-06-14', 'M', '3600004', 2050, 2010),
    (9,  'Javiera',    'Herrera',    '20789012-3', 'javiera.herrera@demo.cl',    'Valparaíso',      'Club de Ajedrez Valparaíso',           '2003-09-08', 'F', NULL,      1450, NULL),
    (10, 'Sebastián',  'Rojas',      '21890123-4', 'sebastian.rojas@demo.cl',    'Metropolitana',   'Club de Ajedrez Lasker',               '2000-04-25', 'M', '3600005', 1780, 1750),
    (11, 'Constanza',  'Núñez',      '22901234-5', 'constanza.nunez@demo.cl',    'Biobío',          'Club de Ajedrez Concepción',           '2008-12-12', 'F', NULL,      1350, NULL),
    (12, 'Matías',     'Ramírez',    '23012345-6', 'matias.ramirez@demo.cl',     'Metropolitana',   'Club de Ajedrez Torre',                '1990-08-19', 'M', '3600006', 1920, 1890),
    (13, 'Antonia',    'López',      '24123456-7', 'antonia.lopez@demo.cl',      'Metropolitana',   'Club de Ajedrez Universidad de Chile',  '2010-05-07', 'F', NULL,      1200, NULL)
) AS p(pid, fname, lname, rut, email, region, club_name, bdate, gender, fide_id, elo_nat, elo_fide)
JOIN country co ON co.iso_code = 'CHL'
JOIN club    cl ON cl.name = p.club_name;

-- Forzar la secuencia BIGSERIAL para que el próximo id sea 14
SELECT setval('player_id_seq', 14, false);

-- Títulos vigentes para jugadores elegibles
INSERT INTO player_title_history (player_id, title, title_date, is_current, source)
VALUES
    (4,  'FM', '2018-05-01', TRUE, 'FIDE'),  -- Rodrigo Sepúlveda: FM
    (8,  'FM', '2016-11-01', TRUE, 'FIDE'),  -- Ignacio Pérez: FM
    (12, 'CM', '2021-03-01', TRUE, 'FIDE');  -- Matías Ramírez: CM

-- Historial de ratings para los jugadores con FIDE ID (últimas 3 entradas)
INSERT INTO rating_history (player_id, rating_type, rating_value, rating_prev_value, delta, recorded_at, source)
VALUES
    (4,  'FIDE_STANDARD', 2050, 2020, 30,  NOW() - INTERVAL '6 months', 'FIDE'),
    (4,  'FIDE_STANDARD', 2040, 2050, -10, NOW() - INTERVAL '3 months', 'FIDE'),
    (4,  'FIDE_STANDARD', 2050, 2040, 10,  NOW(),                        'FIDE'),
    (8,  'FIDE_STANDARD', 2010, 1990, 20,  NOW() - INTERVAL '6 months', 'FIDE'),
    (8,  'FIDE_STANDARD', 2010, 2010, 0,   NOW() - INTERVAL '3 months', 'FIDE'),
    (12, 'FIDE_STANDARD', 1890, 1870, 20,  NOW() - INTERVAL '6 months', 'FIDE'),
    (12, 'FIDE_STANDARD', 1890, 1890, 0,   NOW(),                        'FIDE');
