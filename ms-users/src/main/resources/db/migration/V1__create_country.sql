-- =============================================================================
-- MS-Users — V1: Catálogo de países (ISO 3166-1 alpha-3)
-- =============================================================================

CREATE TABLE country (
    id               SERIAL      PRIMARY KEY,
    iso_code         CHAR(3)     NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    fide_federation  VARCHAR(10)
);

-- Países requeridos para la demo + principales de América
INSERT INTO country (iso_code, name, fide_federation) VALUES
    ('CHL', 'Chile',          'CHI'),
    ('ARG', 'Argentina',      'ARG'),
    ('PER', 'Peru',           'PER'),
    ('BRA', 'Brazil',         'BRA'),
    ('USA', 'United States',  'USA'),
    ('MEX', 'Mexico',         'MEX'),
    ('COL', 'Colombia',       'COL'),
    ('URY', 'Uruguay',        'URU'),
    ('ESP', 'Spain',          'ESP'),
    ('RUS', 'Russia',         'RUS');
