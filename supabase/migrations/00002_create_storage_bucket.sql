-- =============================================================================
-- Migración 00002: Bucket de storage para archivos PGN
-- 
-- Propósito: Crear el bucket 'chessquery-pgn' para almacenar archivos PGN
-- de partidas de ajedrez, con políticas RLS apropiadas.
-- =============================================================================

-- Crear bucket para archivos PGN (privado, acceso via presigned URLs)
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES (
  'chessquery-pgn',
  'chessquery-pgn',
  false,
  1048576  -- 1MB máximo por archivo PGN
);

-- =============================================================================
-- Row Level Security (RLS) para storage.objects
-- =============================================================================

-- Policy: Usuarios autenticados pueden subir archivos PGN
CREATE POLICY "Authenticated users can upload PGN"
  ON storage.objects
  FOR INSERT
  WITH CHECK (
    bucket_id = 'chessquery-pgn'
    AND auth.role() = 'authenticated'
  );

-- Policy: Service role puede gestionar todos los archivos PGN (CRUD completo)
CREATE POLICY "Service role can manage all PGN files"
  ON storage.objects
  FOR ALL
  USING (
    bucket_id = 'chessquery-pgn'
    AND auth.role() = 'service_role'
  );
