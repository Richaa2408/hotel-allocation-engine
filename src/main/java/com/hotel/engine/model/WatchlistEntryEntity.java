package com.hotel.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An exact blocklist entry: the source of truth the Bloom filter is built from.
 * valueKey is the normalised key ("email:..." or "phone:...") the filter hashes.
 */
@Entity
@Table(name = "watchlist_entry")
public class WatchlistEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.WatchKind kind;

    @Column(name = "value_key", nullable = false, unique = true)
    private String valueKey;

    @Column(name = "display_value", nullable = false)
    private String displayValue;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WatchlistEntryEntity() {
    }

    public WatchlistEntryEntity(Enums.WatchKind kind, String valueKey, String displayValue, String reason) {
        this.kind = kind;
        this.valueKey = valueKey;
        this.displayValue = displayValue;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Enums.WatchKind getKind() { return kind; }
    public String getValueKey() { return valueKey; }
    public String getDisplayValue() { return displayValue; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
