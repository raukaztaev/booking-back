package org.example.bookingback.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.example.bookingback.entity.Booking;
import org.example.bookingback.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByUserIdOrderByStartTimeAsc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByUserIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long userId,
            OffsetDateTime from,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByUserIdAndEndTimeLessThanEqualOrderByStartTimeAsc(
            Long userId,
            OffsetDateTime to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAsc(
            Long userId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByResourceIdOrderByStartTimeAsc(Long resourceId, Pageable pageable);

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByResourceIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long resourceId,
            OffsetDateTime from,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByResourceIdAndEndTimeLessThanEqualOrderByStartTimeAsc(
            Long resourceId,
            OffsetDateTime to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"resource", "resource.owner", "user"})
    Page<Booking> findByResourceIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAsc(
            Long resourceId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    @Query("""
            select count(b) > 0 from Booking b
            where b.resource.id = :resourceId
              and b.status <> :cancelledStatus
              and :startTime < b.endTime
              and :endTime > b.startTime
            """)
    boolean existsOverlappingBooking(
            @Param("resourceId") Long resourceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("cancelledStatus") BookingStatus cancelledStatus
    );
}
