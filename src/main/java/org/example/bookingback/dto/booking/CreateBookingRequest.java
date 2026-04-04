package org.example.bookingback.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateBookingRequest(
        @NotNull Long resourceId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {
}
