package com.hotel.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the activity log: who did what to which record. */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_ref")
    private String entityRef;

    @Column(nullable = false)
    private String detail;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(String actor, String action, String entityType, String entityRef, String detail) {
        this.at = Instant.now();
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityRef = entityRef;
        this.detail = detail.length() > 500 ? detail.substring(0, 497) + "..." : detail;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityRef() { return entityRef; }
    public String getDetail() { return detail; }
}
