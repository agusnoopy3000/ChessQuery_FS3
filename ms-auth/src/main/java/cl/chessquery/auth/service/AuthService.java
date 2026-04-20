package cl.chessquery.auth.service;

import cl.chessquery.auth.dto.*;
import cl.chessquery.auth.entity.AuthUser;
import cl.chessquery.auth.entity.RefreshToken;
import cl.chessquery.auth.exception.ApiException;
import cl.chessquery.auth.repository.AuthUserRepository;
import cl.chessquery.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    // ─── Register ─────────────────────────────────────────────────────────────

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (authUserRepo.existsByEmail(req.email())) {
            throw new ApiException(409, "EMAIL_TAKEN", "El email ya está registrado");
        }
        AuthUser user = AuthUser.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role())
                .isActive(true)
                .build();
        user = authUserRepo.save(user);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest req, String deviceInfo) {
        AuthUser user = authUserRepo.findByEmail(req.email())
                .orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS", "Credenciales inválidas"));

        if (!user.isActive()) {
            throw new ApiException(403, "ACCOUNT_DISABLED", "Cuenta deshabilitada");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Credenciales inválidas");
        }

        user.setLastLogin(Instant.now());
        authUserRepo.save(user);

        String accessToken  = jwtService.generateAccessToken(user);
        String rawRefresh   = createRefreshToken(user.getId(), deviceInfo);

        return new LoginResponse(accessToken, rawRefresh, jwtService.expiresInSeconds());
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Transactional
    public RefreshResponse refresh(String rawToken) {
        RefreshToken rt = findValidRefreshToken(rawToken);

        AuthUser user = authUserRepo.findById(rt.getUserId())
                .orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuario no encontrado"));

        return new RefreshResponse(
                jwtService.generateAccessToken(user),
                jwtService.expiresInSeconds()
        );
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String rawToken) {
        String hash = sha256(rawToken);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
        });
    }

    // ─── Validate (usado por el API Gateway) ──────────────────────────────────

    public ValidateResponse validate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(401, "MISSING_TOKEN", "Token no proporcionado");
        }
        Claims claims = jwtService.validateAndExtract(authHeader.substring(7));
        return new ValidateResponse(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String createRefreshToken(Long userId, String deviceInfo) {
        String raw  = UUID.randomUUID().toString();
        String hash = sha256(raw);
        refreshTokenRepo.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .isRevoked(false)
                .deviceInfo(deviceInfo)
                .build());
        return raw;
    }

    private RefreshToken findValidRefreshToken(String raw) {
        RefreshToken rt = refreshTokenRepo.findByTokenHash(sha256(raw))
                .orElseThrow(() -> new ApiException(401, "INVALID_REFRESH_TOKEN", "Refresh token inválido"));
        if (rt.isRevoked()) {
            throw new ApiException(401, "TOKEN_REVOKED", "Refresh token revocado");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(401, "TOKEN_EXPIRED", "Refresh token expirado");
        }
        return rt;
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
