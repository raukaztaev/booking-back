package org.example.bookingback.dto.booking;

import java.time.OffsetDateTime;
import org.example.bookingback.entity.enums.BookingStatus;

public record BookingResponse(
        Long id,
        Long resourceId,
        String resourceName,
        Long userId,
        String userEmail,
        BookingStatus status,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime createdAt
) {
}
