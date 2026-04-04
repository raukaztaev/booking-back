package org.example.bookingback.dto.resource;

import java.time.OffsetDateTime;

public record ResourceResponse(
        Long id,
        String name,
        String description,
        Integer capacity,
        boolean restricted,
        Long ownerId,
        String ownerEmail,
        OffsetDateTime createdAt
) {
}
