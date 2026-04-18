package org.example.bookingback.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.example.bookingback.dto.booking.CreateBookingRequest;
import org.example.bookingback.entity.Booking;
import org.example.bookingback.entity.Resource;
import org.example.bookingback.entity.User;
import org.example.bookingback.entity.enums.BookingStatus;
import org.example.bookingback.exception.BadRequestException;
import org.example.bookingback.exception.ConflictException;
import org.example.bookingback.exception.ForbiddenException;
import org.example.bookingback.exception.NotFoundException;
import org.example.bookingback.repository.BookingHistoryRepository;
import org.example.bookingback.repository.BookingRepository;
import org.example.bookingback.repository.ResourceAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    private User owner;
    private Resource resource;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(10L);
        actor.setEmail("user@test.local");

        owner = new User();
        owner.setId(20L);
        owner.setEmail("manager@test.local");

        resource = new Resource();
        resource.setId(30L);
        resource.setName("Room A");
        resource.setOwner(owner);
        resource.setRestricted(false);
    }

    @Test
    @DisplayName("Не даем создать бронь, если время уже занято")
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
    @DisplayName("Не даем бронировать закрытую комнату без доступа")
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
    @DisplayName("Разрешаем бронирование restricted-ресурса при явном доступе")
    void shouldAllowRestrictedResourceWithGrant() {
        resource.setRestricted(true);
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now().plusHours(3)
        );

        when(resourceService.getById(30L)).thenReturn(resource);
        when(resourceAccessRepository.existsByResourceIdAndUserId(30L, 10L)).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking(eq(30L), any(), any(), eq(BookingStatus.CANCELLED))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking created = bookingService.create(request, actor, false);

        assertEquals(BookingStatus.PENDING, created.getStatus());
        verify(bookingHistoryRepository).save(any());
    }

    @Test
    @DisplayName("Создаем обычную бронь, если все ок")
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
        assertEquals(request.startTime(), created.getStartTime());
        assertEquals(request.endTime(), created.getEndTime());
    }

    @Test
    @DisplayName("Не даем сделать кривой переход по статусу")
    void shouldRejectIllegalStatusTransition() {
        Booking booking = new Booking();
        booking.setId(200L);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUser(actor);
        booking.setResource(resource);

        when(bookingRepository.findWithDetailsById(200L)).thenReturn(java.util.Optional.of(booking));

        assertThrows(BadRequestException.class, () ->
                bookingService.updateStatus(200L, BookingStatus.CONFIRMED, actor, true)
        );
    }

    @Test
    @DisplayName("Не даем создать бронь в прошлом")
    void shouldRejectBookingInPast() {
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().minusHours(2),
                OffsetDateTime.now().minusHours(1)
        );

        assertThrows(BadRequestException.class, () -> bookingService.create(request, actor, false));
    }

    @Test
    @DisplayName("Не даем создать бронь с перепутанным временем")
    void shouldRejectBookingWithInvalidTimeRange() {
        CreateBookingRequest request = new CreateBookingRequest(
                30L,
                OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now().plusHours(1)
        );

        assertThrows(BadRequestException.class, () -> bookingService.create(request, actor, false));
    }

    @Test
    @DisplayName("Пользователь не может сам себе подтвердить бронь")
    void shouldRejectConfirmByBooker() {
        Booking booking = booking(300L, BookingStatus.PENDING, actor, owner);

        when(bookingRepository.findWithDetailsById(300L)).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () ->
                bookingService.updateStatus(300L, BookingStatus.CONFIRMED, actor, false)
        );
    }

    @Test
    @DisplayName("Booker может получить свою бронь по id")
    void shouldReturnBookingForBookerInGetByIdForActor() {
        Booking booking = booking(308L, BookingStatus.PENDING, actor, owner);
        when(bookingRepository.findWithDetailsById(308L)).thenReturn(Optional.of(booking));

        Booking result = bookingService.getByIdForActor(308L, actor, false);

        assertSame(booking, result);
    }

    @Test
    @DisplayName("Постороннему пользователю не отдаем чужую бронь по id")
    void shouldHideBookingForForeignActorInGetByIdForActor() {
        User booker = new User();
        booker.setId(501L);
        Booking booking = booking(309L, BookingStatus.PENDING, booker, owner);
        when(bookingRepository.findWithDetailsById(309L)).thenReturn(Optional.of(booking));

        assertThrows(NotFoundException.class, () -> bookingService.getByIdForActor(309L, actor, false));
    }

    @Test
    @DisplayName("Владелец ресурса может подтверждать бронь")
    void shouldAllowOwnerToConfirm() {
        User booker = new User();
        booker.setId(55L);
        Booking booking = booking(301L, BookingStatus.PENDING, booker, owner);

        when(bookingRepository.findWithDetailsById(301L)).thenReturn(Optional.of(booking));

        Booking updated = bookingService.updateStatus(301L, BookingStatus.CONFIRMED, owner, false);

        assertEquals(BookingStatus.CONFIRMED, updated.getStatus());
        verify(bookingHistoryRepository).save(any());
    }

    @Test
    @DisplayName("Владелец ресурса не может вернуть статус в PENDING")
    void shouldRejectOwnerInvalidTargetStatus() {
        User booker = new User();
        booker.setId(56L);
        Booking booking = booking(302L, BookingStatus.PENDING, booker, owner);

        when(bookingRepository.findWithDetailsById(302L)).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () ->
                bookingService.updateStatus(302L, BookingStatus.PENDING, owner, false)
        );
    }

    @Test
    @DisplayName("Постороннему пользователю не отдаем бронь при смене статуса")
    void shouldHideBookingForUnauthorizedActorOnUpdateStatus() {
        User booker = new User();
        booker.setId(77L);
        Booking booking = booking(303L, BookingStatus.PENDING, booker, owner);

        when(bookingRepository.findWithDetailsById(303L)).thenReturn(Optional.of(booking));

        assertThrows(NotFoundException.class, () ->
                bookingService.updateStatus(303L, BookingStatus.CANCELLED, actor, false)
        );
    }

    @Test
    @DisplayName("Delete для владельца брони переводит ее в CANCELLED")
    void shouldCancelOnDeleteForOwnerBooking() {
        Booking booking = booking(304L, BookingStatus.CONFIRMED, actor, owner);
        when(bookingRepository.findWithDetailsById(304L)).thenReturn(Optional.of(booking));

        bookingService.delete(304L, actor, false);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookingHistoryRepository).save(any());
    }

    @Test
    @DisplayName("Delete не пишет историю, если бронь уже отменена")
    void shouldSkipHistoryWhenDeletingAlreadyCancelledBooking() {
        Booking booking = booking(305L, BookingStatus.CANCELLED, actor, owner);
        when(bookingRepository.findWithDetailsById(305L)).thenReturn(Optional.of(booking));

        bookingService.delete(305L, actor, false);

        verify(bookingHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Delete скрывает чужую бронь для обычного пользователя")
    void shouldHideForeignBookingOnDelete() {
        User booker = new User();
        booker.setId(91L);
        Booking booking = booking(306L, BookingStatus.PENDING, booker, owner);
        when(bookingRepository.findWithDetailsById(306L)).thenReturn(Optional.of(booking));

        assertThrows(NotFoundException.class, () -> bookingService.delete(306L, actor, false));
        verify(bookingHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Manager/Admin может отменять бронь без ограничений владельца")
    void shouldAllowManagerOrAdminToCancelBooking() {
        User booker = new User();
        booker.setId(42L);
        Booking booking = booking(307L, BookingStatus.CONFIRMED, booker, owner);
        when(bookingRepository.findWithDetailsById(307L)).thenReturn(Optional.of(booking));

        bookingService.delete(307L, actor, true);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookingHistoryRepository, atLeastOnce()).save(any());
    }

    private Booking booking(Long id, BookingStatus status, User booker, User resourceOwner) {
        Resource bookingResource = new Resource();
        bookingResource.setId(999L);
        bookingResource.setOwner(resourceOwner);

        Booking booking = new Booking();
        booking.setId(id);
        booking.setStatus(status);
        booking.setUser(booker);
        booking.setResource(bookingResource);
        return booking;
    }
}
