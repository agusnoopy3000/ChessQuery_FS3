package cl.chessquery.auth.service;

import cl.chessquery.auth.entity.AuthUser;
import cl.chessquery.auth.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Generación y validación de JWT (access tokens).
 * Claims: sub=userId, email, role, iat, exp.
 * Algoritmo: HS256.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-minutes:60}")
    private long expirationMinutes;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Valida la firma y la expiración del token.
     * @throws ApiException 401 si el token es inválido o expirado.
     */
    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new ApiException(401, "INVALID_TOKEN", "Token JWT inválido o expirado");
        }
    }

    public long expiresInSeconds() {
        return expirationMinutes * 60;
    }
}
