-- =============================================================================
-- MS-Users — V2: Tabla CLUB
-- =============================================================================

CREATE TABLE club (
    id               SERIAL       PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    country_id       INTEGER      REFERENCES country (id),
    city             VARCHAR(100),
    federation_code  VARCHAR(20)
);

-- Clubes de ajedrez chilenos reales para la demo
INSERT INTO club (name, country_id, city, federation_code)
SELECT c.name, co.id, c.city, c.fed_code
FROM (VALUES
    ('Club de Ajedrez Lasker',          'CHL', 'Santiago',    'CL-LAS'),
    ('Club de Ajedrez Torre',           'CHL', 'Santiago',    'CL-TOR'),
    ('Club de Ajedrez Universidad de Chile', 'CHL', 'Santiago', 'CL-UCH'),
    ('Club de Ajedrez Valparaíso',      'CHL', 'Valparaíso',  'CL-VAL'),
    ('Club de Ajedrez Concepción',      'CHL', 'Concepción',  'CL-CON'),
    ('Club de Ajedrez Antofagasta',     'CHL', 'Antofagasta', 'CL-ANT'),
    ('Club de Ajedrez Temuco',          'CHL', 'Temuco',      'CL-TEM')
) AS c(name, iso, city, fed_code)
JOIN country co ON co.iso_code = c.iso;
