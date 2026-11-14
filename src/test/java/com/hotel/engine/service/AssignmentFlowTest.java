package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.service.AssignmentService.RequestCard;
import com.hotel.engine.service.AssignmentService.RunResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentFlowTest extends HotelTestBase {

    private String roomOf(String reference) {
        return reservations.detail(reference).rooms().get(0).roomNumber();
    }

    @Test
    @DisplayName("A room is only offered if it's free for the guest's whole stay, not just the arrival night")
    void wholeStayMatters() {
        String a = reservations.create(stay("Long Stay", RoomType.SUITE, 0, 3, 1), null).reservation().reference();
        assignments.run(TODAY);
        assertEquals("601", roomOf(a));
        // Deluxe guests arriving tomorrow: 302 taken by hand for nights 1-2 blocks a 2-night stay there.
        String b = reservations.create(stay("Blocker", RoomType.DELUXE, 1, 2, 1), null).reservation().reference();
        String c = reservations.create(stay("Two Nights", RoomType.DELUXE, 1, 3, 1), null).reservation().reference();
        long bRequest = reservations.detail(b).rooms().get(0).requestId();
        assignments.assignManually(bRequest, "302");
        RunResult run = assignments.run(day(1));
        assertEquals(1, run.placed());
        assertNotEquals("302", roomOf(c));
    }

    @Test
    @DisplayName("Min cut names the constrained group: two guests need accessible deluxe on floor 3 or below, one room fits")
    void shortageGroup() {
        reservations.create(accessibleStay("Lakshmi Iyer", RoomType.DELUXE, 0, 2, 3), null);
        reservations.create(accessibleStay("Joseph Thomas", RoomType.DELUXE, 0, 2, 3), null);
        RunResult run = assignments.run(TODAY);
        assertEquals(2, run.requests());
        assertEquals(1, run.placed());
        assertEquals(1, run.shortages().size());
        var s = run.shortages().get(0);
        assertEquals(1, s.shortage());
        assertEquals(List.of("301"), s.roomNumbers());
        assertTrue(s.summary().contains("only 1 room fits"), s.summary());
    }

    @Test
    @DisplayName("Max flow beats first-fit: a flexible guest is kept out of the only accessible room")
    void beatsFirstFit() {
        reservations.create(stay("Flexible", RoomType.STANDARD, 0, 1, 1), null);
        reservations.create(accessibleStay("Needs Access", RoomType.STANDARD, 0, 1, null), null);
        reservations.create(stay("Third", RoomType.STANDARD, 0, 1, 1), null);
        reservations.create(stay("Fourth", RoomType.STANDARD, 0, 1, 1), null);
        RunResult run = assignments.run(TODAY);
        assertEquals(4, run.placed());
        assertTrue(run.firstFitPlaced() <= run.placed());
        RequestCard access = run.board().requests().stream().filter(r -> r.guestName().equals("Needs Access")).findFirst().orElseThrow();
        assertEquals("101", access.roomNumber());
    }

    @Test
    @DisplayName("Maintenance on an assigned room moves the guest to another room and records why")
    void maintenanceMovesGuest() {
        String ref = reservations.create(stay("Moving Guest", RoomType.DELUXE, 1, 3, 1), null).reservation().reference();
        long request = reservations.detail(ref).rooms().get(0).requestId();
        assignments.assignManually(request, "302");
        var result = maintenance.create("302", day(1), day(2), "Leak");
        assertEquals(1, result.moved().size());
        assertNotEquals("302", roomOf(ref));
        var history = reservations.detail(ref).roomHistory();
        assertEquals(2, history.size());
        assertTrue(history.get(1).reason().contains("out of service"));
    }

    @Test
    @DisplayName("Hard rules hold for manual moves: an accessible guest can't be put in a non-accessible room")
    void manualRespectsHardRules() {
        String ref = reservations.create(accessibleStay("Wheelchair User", RoomType.DELUXE, 0, 1, null), null)
                .reservation().reference();
        long request = reservations.detail(ref).rooms().get(0).requestId();
        assertThrows(ConflictException.class, () -> assignments.assignManually(request, "302"));
        assignments.assignManually(request, "401");
        assertEquals("401", roomOf(ref));
    }

    @Test
    @DisplayName("Edge: running with no arrivals reports it plainly instead of failing")
    void noArrivals() {
        RunResult run = assignments.run(day(20));
        assertEquals(0, run.requests());
        assertTrue(run.message().startsWith("No confirmed arrivals"));
    }
}
