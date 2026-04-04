package org.example.bookingback.mapper;

import org.example.bookingback.dto.booking.BookingResponse;
import org.example.bookingback.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getResource().getId(),
                booking.getResource().getName(),
                booking.getUser().getId(),
                booking.getUser().getEmail(),
                booking.getStatus(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getCreatedAt()
        );
    }
}
