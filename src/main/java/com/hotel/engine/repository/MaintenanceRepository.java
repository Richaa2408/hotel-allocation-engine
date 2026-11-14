package com.hotel.engine.repository;

import com.hotel.engine.model.MaintenanceBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<MaintenanceBlockEntity, Long> {

    List<MaintenanceBlockEntity> findByActiveTrueOrderByStartDateAsc();

    /** Active blocks overlapping [from, to). */
    @Query("""
            select b from MaintenanceBlockEntity b
            where b.active = true and b.startDate < :to and b.endDate > :from
            """)
    List<MaintenanceBlockEntity> findActiveOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
