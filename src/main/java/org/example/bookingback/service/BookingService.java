package org.example.bookingback.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.example.bookingback.dto.booking.CreateBookingRequest;
import org.example.bookingback.entity.Booking;
import org.example.bookingback.entity.BookingHistory;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingService {

    private static final Set<BookingStatus> OWNER_UPDATABLE_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final ResourceService resourceService;
    private final ResourceAccessRepository resourceAccessRepository;

    public BookingService(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            ResourceService resourceService,
            ResourceAccessRepository resourceAccessRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.resourceService = resourceService;
        this.resourceAccessRepository = resourceAccessRepository;
    }

    public Booking create(CreateBookingRequest request, User actor, boolean managerOrAdmin) {
        validateTimeRange(request.startTime(), request.endTime());
        Resource resource = resourceService.getById(request.resourceId());
        validateResourceAccess(resource, actor, managerOrAdmin);
        ensureNoOverlap(resource.getId(), request.startTime(), request.endTime());

        Booking booking = new Booking();
        booking.setResource(resource);
        booking.setUser(actor);
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);
        writeHistory(booking, "CREATED");
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<Booking> getMyBookings(Long userId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        if (from != null && to != null) {
            return bookingRepository.findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAsc(
                    userId, from, to, pageable
            );
        }
        if (from != null) {
            return bookingRepository.findByUserIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(userId, from, pageable);
        }
        if (to != null) {
            return bookingRepository.findByUserIdAndEndTimeLessThanEqualOrderByStartTimeAsc(userId, to, pageable);
        }
        return bookingRepository.findByUserIdOrderByStartTimeAsc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Booking getByIdForActor(Long bookingId, User actor, boolean managerOrAdmin) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!managerOrAdmin
                && !booking.getUser().getId().equals(actor.getId())
                && !booking.getResource().getOwner().getId().equals(actor.getId())) {
            throw new NotFoundException("Booking not found");
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<Booking> getByResource(Long resourceId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        resourceService.getById(resourceId);
        if (from != null && to != null) {
            return bookingRepository.findByResourceIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAsc(
                    resourceId, from, to, pageable
            );
        }
        if (from != null) {
            return bookingRepository.findByResourceIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(resourceId, from, pageable);
        }
        if (to != null) {
            return bookingRepository.findByResourceIdAndEndTimeLessThanEqualOrderByStartTimeAsc(resourceId, to, pageable);
        }
        return bookingRepository.findByResourceIdOrderByStartTimeAsc(resourceId, pageable);
    }

    public Booking updateStatus(Long bookingId, BookingStatus targetStatus, User actor, boolean managerOrAdmin) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        boolean isOwner = booking.getResource().getOwner().getId().equals(actor.getId());
        boolean isBooker = booking.getUser().getId().equals(actor.getId());
        if (!managerOrAdmin && !isOwner && !isBooker) {
            throw new NotFoundException("Booking not found");
        }
        if (!managerOrAdmin && isOwner && !OWNER_UPDATABLE_STATUSES.contains(targetStatus)) {
            throw new ForbiddenException("Resource owner cannot apply this status");
        }
        if (!managerOrAdmin && isBooker && targetStatus != BookingStatus.CANCELLED) {
            throw new ForbiddenException("Booker can only cancel own booking");
        }
        validateTransition(booking.getStatus(), targetStatus);
        booking.setStatus(targetStatus);
        writeHistory(booking, "STATUS_CHANGED_TO_" + targetStatus.name());
        return booking;
    }

    public void delete(Long bookingId, User actor, boolean managerOrAdmin) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!managerOrAdmin && !booking.getUser().getId().equals(actor.getId())) {
            throw new NotFoundException("Booking not found");
        }
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.CANCELLED);
            writeHistory(booking, "CANCELLED_BY_DELETE");
        }
    }

    private void validateTimeRange(OffsetDateTime start, OffsetDateTime end) {
        if (!start.isBefore(end)) {
            throw new BadRequestException("startTime must be before endTime");
        }
        if (start.isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Cannot create booking in the past");
        }
    }

    private void validateResourceAccess(Resource resource, User actor, boolean managerOrAdmin) {
        if (!resource.isRestricted()) {
            return;
        }
        if (managerOrAdmin || resource.getOwner().getId().equals(actor.getId())) {
            return;
        }
        if (!resourceAccessRepository.existsByResourceIdAndUserId(resource.getId(), actor.getId())) {
            throw new ForbiddenException("Restricted resource is not available for this user");
        }
    }

    private void ensureNoOverlap(Long resourceId, OffsetDateTime start, OffsetDateTime end) {
        boolean overlapping = bookingRepository.existsOverlappingBooking(resourceId, start, end, BookingStatus.CANCELLED);
        if (overlapping) {
            throw new ConflictException("Booking time overlaps with an existing booking");
        }
    }

    private void validateTransition(BookingStatus current, BookingStatus target) {
        if (current == target) {
            throw new BadRequestException("Booking already has this status");
        }
        if (current == BookingStatus.PENDING && (target == BookingStatus.CONFIRMED || target == BookingStatus.CANCELLED)) {
            return;
        }
        if (current == BookingStatus.CONFIRMED && target == BookingStatus.CANCELLED) {
            return;
        }
        throw new BadRequestException("Illegal booking status transition");
    }

    private void writeHistory(Booking booking, String action) {
        BookingHistory entry = new BookingHistory();
        entry.setBooking(booking);
        entry.setAction(action);
        bookingHistoryRepository.save(entry);
    }
}
