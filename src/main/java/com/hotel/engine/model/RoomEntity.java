package com.hotel.engine.model;

import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A physical room. Accessibility is a feature of the room, not a separate room type. */
@Entity
@Table(name = "room")
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false)
    private boolean accessible;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.Housekeeping housekeeping = Enums.Housekeeping.CLEAN;

    protected RoomEntity() {
    }

    public RoomEntity(String number, RoomType type, int floor, boolean accessible, int capacity) {
        this.number = number;
        this.type = type;
        this.floor = floor;
        this.accessible = accessible;
        this.capacity = capacity;
    }

    /** The plain record the assignment engine works on. */
    public Room toDomain() {
        return new Room(number, type, floor, accessible);
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public RoomType getType() { return type; }
    public int getFloor() { return floor; }
    public boolean isAccessible() { return accessible; }
    public int getCapacity() { return capacity; }
    public Enums.Housekeeping getHousekeeping() { return housekeeping; }
    public void setHousekeeping(Enums.Housekeeping housekeeping) { this.housekeeping = housekeeping; }
}
