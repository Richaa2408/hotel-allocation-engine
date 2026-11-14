package com.hotel.engine.repository;

import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoomRequestRepository extends JpaRepository<RoomRequestEntity, Long> {
    List<RoomRequestEntity> findByReservationOrderBySeqAsc(ReservationEntity reservation);

    List<RoomRequestEntity> findByReservationIn(Collection<ReservationEntity> reservations);

    void deleteByReservation(ReservationEntity reservation);
}
