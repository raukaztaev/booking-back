package org.example.bookingback.dto.user;

import java.time.OffsetDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        boolean enabled,
        Set<String> roles,
        OffsetDateTime createdAt
) {
}
