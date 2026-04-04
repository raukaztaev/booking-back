package org.example.bookingback.dto.booking;

import jakarta.validation.constraints.NotNull;
import org.example.bookingback.entity.enums.BookingStatus;

public record UpdateBookingStatusRequest(@NotNull BookingStatus status) {
}
