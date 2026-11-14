package com.hotel.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A room request placed in a physical room. Released rows (active = false) form the history. */
@Entity
@Table(name = "room_assignment")
public class AssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "request_id")
    private RoomRequestEntity request;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id")
    private RoomEntity room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.AssignmentMethod method;

    @Column(nullable = false)
    private boolean active = true;

    private String reason;

    @Column(name = "assigned_by", nullable = false)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected AssignmentEntity() {
    }

    public AssignmentEntity(RoomRequestEntity request, RoomEntity room, Enums.AssignmentMethod method,
                            String reason, String assignedBy) {
        this.request = request;
        this.room = room;
        this.method = method;
        this.reason = reason;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public void release() {
        this.active = false;
        this.releasedAt = Instant.now();
    }

    public Long getId() { return id; }
    public RoomRequestEntity getRequest() { return request; }
    public RoomEntity getRoom() { return room; }
    public Enums.AssignmentMethod getMethod() { return method; }
    public boolean isActive() { return active; }
    public String getReason() { return reason; }
    public String getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getReleasedAt() { return releasedAt; }
}
