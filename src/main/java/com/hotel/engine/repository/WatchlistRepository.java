package com.hotel.engine.repository;

import com.hotel.engine.model.WatchlistEntryEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistEntryEntity, Long>,
        JpaSpecificationExecutor<WatchlistEntryEntity> {

    Optional<WatchlistEntryEntity> findByValueKey(String valueKey);

    boolean existsByValueKey(String valueKey);

    /** Matches the value or the reason; matches everything when there is no search text. */
    static Specification<WatchlistEntryEntity> matches(String text) {
        if (text == null || text.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String like = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("displayValue")), like),
                cb.like(cb.lower(root.get("reason")), like));
    }
}
