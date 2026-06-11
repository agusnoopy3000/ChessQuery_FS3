-- Normaliza los emails existentes a trim+lowercase, en línea con la regla
-- de la app (cl.chessquery.users.util.Emails): la columna es UNIQUE y se usa
-- para matchear identidades, así que mayúsculas/minúsculas mezcladas generan
-- perfiles duplicados o matches fallidos.
--
-- La cláusula NOT EXISTS evita violar el UNIQUE si ya existieran dos filas
-- que solo difieren en mayúsculas (caso raro; esas se dejan tal cual y se
-- resuelven a mano).
UPDATE player p
SET email = LOWER(TRIM(p.email))
WHERE p.email IS NOT NULL
  AND p.email <> LOWER(TRIM(p.email))
  AND NOT EXISTS (
      SELECT 1 FROM player q
      WHERE q.id <> p.id
        AND q.email = LOWER(TRIM(p.email))
  );
