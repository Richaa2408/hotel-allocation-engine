package com.hotel.engine.repository;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    Optional<RoomEntity> findByNumber(String number);

    List<RoomEntity> findAllByOrderByFloorAscNumberAsc();

    long countByType(RoomType type);

    long countByTypeAndAccessibleTrue(RoomType type);
}
