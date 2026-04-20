package cl.chessquery.auth.controller;

import cl.chessquery.auth.dto.*;
import cl.chessquery.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registro, login, refresh y validación de tokens")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar nuevo usuario")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Login — retorna access token + refresh token")
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @Operation(summary = "Renovar access token con el refresh token")
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Revocar refresh token (logout)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    /**
     * Validar JWT — usado exclusivamente por el API Gateway.
     * No está expuesto al público vía Nginx.
     */
    @Operation(summary = "Validar JWT (uso interno del API Gateway)")
    @GetMapping("/validate")
    public ValidateResponse validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return authService.validate(authHeader);
    }
}
