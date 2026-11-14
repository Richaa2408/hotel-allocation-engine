package com.hotel.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One room of a reservation: a reservation for 3 rooms has 3 requests, each matched to one physical room. */
@Entity
@Table(name = "room_request")
public class RoomRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reservation_id")
    private ReservationEntity reservation;

    @Column(nullable = false)
    private int seq;

    protected RoomRequestEntity() {
    }

    public RoomRequestEntity(ReservationEntity reservation, int seq) {
        this.reservation = reservation;
        this.seq = seq;
    }

    public Long getId() { return id; }
    public ReservationEntity getReservation() { return reservation; }
    public int getSeq() { return seq; }
}
