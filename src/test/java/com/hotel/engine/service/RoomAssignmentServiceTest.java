package com.hotel.engine.service;

import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.sample.SampleHotel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAssignmentServiceTest {

    private final RoomAssignmentService service = new RoomAssignmentService();

    private static final List<Room> ROOMS = List.of(
            new Room("101", RoomType.STANDARD, 1, true),
            new Room("102", RoomType.STANDARD, 1, false),
            new Room("201", RoomType.DELUXE, 2, false),
            new Room("202", RoomType.DELUXE, 2, true),
            new Room("301", RoomType.SUITE, 3, false));

    private static final List<Guest> GUESTS = List.of(
            new Guest("B1", "Mehta", RoomType.STANDARD, false, null, true),
            new Guest("B2", "Rao", RoomType.STANDARD, true, null, false),
            new Guest("B3", "Iyer", RoomType.DELUXE, true, 2, false),
            new Guest("B4", "Khan", RoomType.DELUXE, false, null, false),
            new Guest("B5", "Das", RoomType.SUITE, false, null, false),
            new Guest("B6", "Pillai", RoomType.DELUXE, true, 1, false),
            new Guest("B7", "Nair", RoomType.DELUXE, false, null, false));

    @Test
    @DisplayName("Max flow places 5 of 7 where first-fit places only 4")
    void beatsFirstFit() {
        AssignmentResult r = service.assign(GUESTS, ROOMS);
        assertEquals(5, r.assignedCount());
        assertEquals(4, r.firstFitAssignedCount());
        Map<String, String> roomOf = new HashMap<>();
        r.assignments().forEach(a -> roomOf.put(a.guestName(), a.roomNumber()));
        assertEquals("101", roomOf.get("Rao"), "Rao needs the only accessible standard room");
        assertEquals("102", roomOf.get("Mehta"), "Mehta is moved to the other standard room");
    }

    @Test
    @DisplayName("Explains each unplaced guest: no suitable room vs. outnumbered")
    void explainsUnplacedGuests() {
        AssignmentResult r = service.assign(GUESTS, ROOMS);
        Map<String, AssignmentResult.UnassignedGuest> byName = new HashMap<>();
        r.unassigned().forEach(u -> byName.put(u.guestName(), u));
        assertEquals(0, byName.get("Pillai").suitableRooms());
        assertTrue(byName.get("Pillai").reason().startsWith("No available room is deluxe, accessible, on floor 1"));
        assertEquals(2, byName.get("Nair").suitableRooms());
        assertTrue(byName.get("Nair").reason().contains("needed by other guests"));
        assertEquals(List.of("201", "202"), r.bottleneckRooms().stream().filter(n -> n.startsWith("2")).sorted().toList());
    }

    @Test
    @DisplayName("An upgrade is used only when the guest accepts one, and is flagged")
    void upgradesAreFlagged() {
        List<Room> rooms = List.of(new Room("401", RoomType.SUITE, 4, false));
        AssignmentResult yes = service.assign(
                List.of(new Guest("U1", "Bose", RoomType.STANDARD, false, null, true)), rooms);
        assertEquals(1, yes.assignedCount());
        assertTrue(yes.assignments().get(0).upgraded());

        AssignmentResult no = service.assign(
                List.of(new Guest("U2", "Joshi", RoomType.STANDARD, false, null, false)), rooms);
        assertEquals(0, no.assignedCount());
    }

    @Test
    @DisplayName("On 20 generated hotels every assignment is valid and never worse than first-fit")
    void assignmentsAreAlwaysValid() {
        for (long seed = 1; seed <= 20; seed++) {
            SampleHotel hotel = new SampleHotel(seed);
            List<Guest> guests = hotel.arrivals(28);
            List<Room> rooms = hotel.roomsFreeTonight(14);
            Map<String, Guest> guestById = new HashMap<>();
            guests.forEach(g -> guestById.put(g.id(), g));
            Map<String, Room> roomByNumber = new HashMap<>();
            rooms.forEach(rm -> roomByNumber.put(rm.number(), rm));

            AssignmentResult r = service.assign(guests, rooms);
            Set<String> usedRooms = new HashSet<>();
            for (AssignmentResult.RoomAssignment a : r.assignments()) {
                assertTrue(usedRooms.add(a.roomNumber()), "room " + a.roomNumber() + " given twice, seed " + seed);
                assertTrue(guestById.get(a.guestId()).canStayIn(roomByNumber.get(a.roomNumber())),
                        a.guestName() + " can't stay in " + a.roomNumber() + ", seed " + seed);
            }
            assertEquals(r.assignedCount(), r.assignments().size());
            assertEquals(guests.size(), r.assignments().size() + r.unassigned().size());
            assertTrue(r.assignedCount() >= r.firstFitAssignedCount(), "seed " + seed);
        }
    }

    @Test
    @DisplayName("Edge: the trace is left out for large inputs but the answer is still complete")
    void traceOmittedWhenLarge() {
        List<Guest> guests = new ArrayList<>();
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            guests.add(new Guest("G" + i, "Guest " + i, RoomType.STANDARD, false, null, false));
        }
        for (int i = 0; i < 60; i++) {
            rooms.add(new Room("R" + i, RoomType.STANDARD, 1, false));
        }
        AssignmentResult big = service.assign(guests, rooms);
        assertNull(big.trace());
        assertEquals(60, big.assignedCount());
        assertNotNull(service.assign(GUESTS, ROOMS).trace());
    }

    @Test
    @DisplayName("Invalid: empty lists and duplicate booking references or room numbers")
    void rejectsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> service.assign(List.of(), ROOMS));
        assertThrows(InvalidInputException.class, () -> service.assign(GUESTS, List.of()));
        assertThrows(InvalidInputException.class, () -> service.assign(List.of(GUESTS.get(0), GUESTS.get(0)), ROOMS));
        assertThrows(InvalidInputException.class, () -> service.assign(GUESTS, List.of(ROOMS.get(0), ROOMS.get(0))));
    }
}
