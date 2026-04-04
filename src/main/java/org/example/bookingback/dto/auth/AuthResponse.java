package org.example.bookingback.dto.auth;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserAuthView user
) {
    public record UserAuthView(Long id, String email, Set<String> roles) {
    }
}
