package com.hotel.engine.repository;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.ReservationEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long>,
        JpaSpecificationExecutor<ReservationEntity> {

    Optional<ReservationEntity> findByReference(String reference);

    Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);

    List<ReservationEntity> findByStatusIn(Collection<ReservationStatus> statuses);

    // Explicit queries: a derived name like findByCheckIn... would be read as "check In (...)".

    @Query("select r from ReservationEntity r where r.checkIn = :day and r.status in :statuses")
    List<ReservationEntity> arrivingOn(@Param("day") LocalDate day,
                                      @Param("statuses") Collection<ReservationStatus> statuses);

    @Query("select r from ReservationEntity r where r.checkOut = :day and r.status in :statuses")
    List<ReservationEntity> departingOn(@Param("day") LocalDate day,
                                       @Param("statuses") Collection<ReservationStatus> statuses);

    List<ReservationEntity> findByStatusOrderByCreatedAtAsc(ReservationStatus status);

    long countByStatus(ReservationStatus status);

    /** Same type and dates in the given statuses; the caller compares guest keys (duplicate rule). */
    @Query("""
            select r from ReservationEntity r
            where r.roomType = :type and r.checkIn = :checkIn and r.checkOut = :checkOut and r.status in :statuses
            """)
    List<ReservationEntity> sameStay(@Param("type") RoomType type, @Param("checkIn") LocalDate checkIn,
                                     @Param("checkOut") LocalDate checkOut,
                                     @Param("statuses") Collection<ReservationStatus> statuses);

    /** Reservations in the given statuses whose stay overlaps [from, to). */
    @Query("""
            select r from ReservationEntity r
            where r.status in :statuses and r.checkIn < :to and r.checkOut > :from
            """)
    List<ReservationEntity> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                            @Param("statuses") Collection<ReservationStatus> statuses);

    // ---------- search filters (each skipped when its value is null) ----------

    /** All the filters that apply, combined with AND. Spring Data 4 rejects null parts, so they are left out. */
    static Specification<ReservationEntity> filters(String text, ReservationStatus status, RoomType type,
                                                    LocalDate from, LocalDate to) {
        return java.util.stream.Stream.of(matches(text), hasStatus(status), hasType(type), overlaps(from, to))
                .filter(java.util.Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    static Specification<ReservationEntity> matches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String like = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var guest = root.join("guest");
            return cb.or(
                    cb.like(cb.lower(root.get("reference")), like),
                    cb.like(cb.lower(guest.get("fullName")), like),
                    cb.like(cb.lower(cb.coalesce(guest.get("email"), "")), like));
        };
    }

    static Specification<ReservationEntity> hasStatus(ReservationStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<ReservationEntity> hasType(RoomType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("roomType"), type);
    }

    /** Stays overlapping [from, to); either bound may be null. */
    static Specification<ReservationEntity> overlaps(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThan(root.get("checkOut"), from),
                to == null ? cb.conjunction() : cb.lessThan(root.get("checkIn"), to));
    }
}
