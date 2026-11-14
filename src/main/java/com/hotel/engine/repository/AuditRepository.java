package com.hotel.engine.repository;

import com.hotel.engine.model.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByAtGreaterThanEqualOrderByAtDesc(Instant since);

    List<AuditEventEntity> findByEntityRefOrderByAtAsc(String entityRef);

    Page<AuditEventEntity> findAllByOrderByAtDesc(Pageable pageable);
}
