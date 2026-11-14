package com.hotel.engine.model;

import com.hotel.engine.domain.RoomType;
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
import java.time.LocalDate;

/** A booking for one or more rooms of one type. Nights used: checkIn up to the night before checkOut. */
@Entity
@Table(name = "reservation")
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "guest_id")
    private GuestEntity guest;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private int rooms;

    @Column(nullable = false)
    private int adults;

    @Column(name = "needs_accessible", nullable = false)
    private boolean needsAccessible;

    @Column(name = "max_floor")
    private Integer maxFloor;

    @Column(name = "preferred_floor")
    private Integer preferredFloor;

    @Column(name = "accepts_upgrade", nullable = false)
    private boolean acceptsUpgrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.Screening screening;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(GuestEntity guest, RoomType roomType, LocalDate checkIn, LocalDate checkOut,
                             int rooms, int adults, boolean needsAccessible, Integer maxFloor,
                             Integer preferredFloor, boolean acceptsUpgrade, String createdBy) {
        this.guest = guest;
        this.roomType = roomType;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.rooms = rooms;
        this.adults = adults;
        this.needsAccessible = needsAccessible;
        this.maxFloor = maxFloor;
        this.preferredFloor = preferredFloor;
        this.acceptsUpgrade = acceptsUpgrade;
        this.createdBy = createdBy;
        this.status = Enums.ReservationStatus.CONFIRMED;
        this.screening = Enums.Screening.CLEAR;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    /** Sets the creation time to when a generated demo booking would have been made. */
    public void backdate(Instant createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public int nights() {
        return (int) (checkOut.toEpochDay() - checkIn.toEpochDay());
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public GuestEntity getGuest() { return guest; }
    public RoomType getRoomType() { return roomType; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public int getRooms() { return rooms; }
    public void setRooms(int rooms) { this.rooms = rooms; }
    public int getAdults() { return adults; }
    public void setAdults(int adults) { this.adults = adults; }
    public boolean isNeedsAccessible() { return needsAccessible; }
    public void setNeedsAccessible(boolean needsAccessible) { this.needsAccessible = needsAccessible; }
    public Integer getMaxFloor() { return maxFloor; }
    public void setMaxFloor(Integer maxFloor) { this.maxFloor = maxFloor; }
    public Integer getPreferredFloor() { return preferredFloor; }
    public void setPreferredFloor(Integer preferredFloor) { this.preferredFloor = preferredFloor; }
    public boolean isAcceptsUpgrade() { return acceptsUpgrade; }
    public void setAcceptsUpgrade(boolean acceptsUpgrade) { this.acceptsUpgrade = acceptsUpgrade; }
    public Enums.ReservationStatus getStatus() { return status; }
    public void setStatus(Enums.ReservationStatus status) { this.status = status; }
    public Enums.Screening getScreening() { return screening; }
    public void setScreening(Enums.Screening screening) { this.screening = screening; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
