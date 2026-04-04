package org.example.bookingback.controller;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.example.bookingback.dto.booking.BookingResponse;
import org.example.bookingback.dto.booking.CreateBookingRequest;
import org.example.bookingback.dto.booking.UpdateBookingStatusRequest;
import org.example.bookingback.mapper.BookingMapper;
import org.example.bookingback.security.UserPrincipal;
import org.example.bookingback.service.BookingService;
import org.example.bookingback.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;
    private final BookingMapper bookingMapper;

    public BookingController(BookingService bookingService, UserService userService, BookingMapper bookingMapper) {
        this.bookingService = bookingService;
        this.userService = userService;
        this.bookingMapper = bookingMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookingMapper.toResponse(bookingService.create(
                request,
                userService.getById(principal.getId()),
                isManagerOrAdmin(principal)
        ));
    }

    @GetMapping("/my")
    public Page<BookingResponse> myBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable
    ) {
        return bookingService.getMyBookings(principal.getId(), from, to, pageable).map(bookingMapper::toResponse);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return bookingMapper.toResponse(bookingService.getByIdForActor(
                id,
                userService.getById(principal.getId()),
                isManagerOrAdmin(principal)
        ));
    }

    @GetMapping("/resource/{resourceId}")
    public Page<BookingResponse> byResource(
            @PathVariable Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable
    ) {
        return bookingService.getByResource(resourceId, from, to, pageable).map(bookingMapper::toResponse);
    }

    @PatchMapping("/{id}/status")
    public BookingResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bookingMapper.toResponse(bookingService.updateStatus(
                id,
                request.status(),
                userService.getById(principal.getId()),
                isManagerOrAdmin(principal)
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        bookingService.delete(id, userService.getById(principal.getId()), isManagerOrAdmin(principal));
    }

    private boolean isManagerOrAdmin(UserPrincipal principal) {
        return principal.roleNames().stream().anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_MANAGER"));
    }
}
