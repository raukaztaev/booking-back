package org.example.bookingback.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.example.bookingback.dto.booking.CreateBookingRequest;
import org.example.bookingback.entity.Booking;
import org.example.bookingback.entity.Resource;
import org.example.bookingback.entity.User;
import org.example.bookingback.entity.enums.BookingStatus;
import org.example.bookingback.exception.ConflictException;
import org.example.bookingback.exception.ForbiddenException;
import org.example.bookingback.repository.BookingHistoryRepository;
import org.example.bookingback.repository.BookingRepository;
import org.example.bookingback.repository.ResourceAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingHistoryRepository bookingHistoryRepository;
    @Mock
    private ResourceService resourceService;
    @Mock
    private ResourceAccessRepository resourceAccessRepository;

    @InjectMocks
    private BookingService bookingService;

    private User actor;
    private Resource resource;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(10L);
        actor.setEmail("user@test.local");

        User owner = new User();
        owner.setId(20L);
        owner.setEmail("manager@test.local");

        resource = new Resource();
        resource.setId(30L);
        resource.setName("Room A");
        resource.setOwner(owner);
        resource.setRestricted(false);
    }

    @Test
    void shouldRejectOverlappingBooking() {
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(2)
        );

        when(resourceService.getById(30L)).thenReturn(resource);
        when(bookingRepository.existsOverlappingBooking(eq(30L), any(), any(), eq(BookingStatus.CANCELLED))).thenReturn(true);

        assertThrows(ConflictException.class, () -> bookingService.create(request, actor, false));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void shouldRejectRestrictedResourceWithoutGrant() {
        resource.setRestricted(true);
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(2)
        );

        when(resourceService.getById(30L)).thenReturn(resource);
        when(resourceAccessRepository.existsByResourceIdAndUserId(30L, 10L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> bookingService.create(request, actor, false));
    }

    @Test
    void shouldCreatePendingBookingWhenValid() {
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().plusHours(3),
                OffsetDateTime.now().plusHours(4)
        );

        when(resourceService.getById(30L)).thenReturn(resource);
        when(bookingRepository.existsOverlappingBooking(eq(30L), any(), any(), eq(BookingStatus.CANCELLED))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(100L);
            return booking;
        });

        Booking created = bookingService.create(request, actor, false);

        assertEquals(100L, created.getId());
        assertEquals(BookingStatus.PENDING, created.getStatus());
        assertEquals(actor, created.getUser());
        assertEquals(resource, created.getResource());
    }
}
