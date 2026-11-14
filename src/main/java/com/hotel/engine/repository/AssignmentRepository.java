package com.hotel.engine.repository;

import com.hotel.engine.model.AssignmentEntity;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.model.RoomRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

    Optional<AssignmentEntity> findByRequestAndActiveTrue(RoomRequestEntity request);

    List<AssignmentEntity> findByRequestInAndActiveTrue(Collection<RoomRequestEntity> requests);

    List<AssignmentEntity> findByRoomAndActiveTrue(RoomEntity room);

    /** Every assignment (active and released) of one request, oldest first. */
    List<AssignmentEntity> findByRequestOrderByAssignedAtAsc(RoomRequestEntity request);

    /** All assignments (active and released) for a reservation, oldest first: its room history. */
    @Query("select a from AssignmentEntity a where a.request.reservation = :reservation order by a.assignedAt asc")
    List<AssignmentEntity> history(@Param("reservation") ReservationEntity reservation);

    /** Active assignments whose stay overlaps [from, to): these rooms are taken for some of those nights. */
    @Query("""
            select a from AssignmentEntity a
            where a.active = true and a.request.reservation.status in :statuses
              and a.request.reservation.checkIn < :to and a.request.reservation.checkOut > :from
            """)
    List<AssignmentEntity> findActiveOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                 @Param("statuses") Collection<ReservationStatus> statuses);

    List<AssignmentEntity> findTop50ByOrderByAssignedAtDesc();
}
