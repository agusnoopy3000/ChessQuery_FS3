package cl.chessquery.auth.dto;

public record ValidateResponse(Long userId, String email, String role) {}