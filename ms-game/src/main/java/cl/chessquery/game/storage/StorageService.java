package cl.chessquery.game.storage;

/**
 * Abstracción de almacenamiento PGN. Las implementaciones se seleccionan
 * por la propiedad {@code storage.provider} (supabase | minio).
 */
public interface StorageService {

    /**
     * Sube el contenido de un PGN bajo la key indicada.
     *
     * @param key         Ruta lógica dentro del bucket (e.g. games/2026/05/4521.pgn).
     * @param pgnContent  Bytes del archivo PGN.
     * @return La key final almacenada (puede coincidir con la key recibida).
     */
    String uploadPgn(String key, byte[] pgnContent);

    /**
     * Genera una URL firmada con expiración de 1 hora para descargar el PGN.
     */
    String generatePresignedUrl(String key);
}
