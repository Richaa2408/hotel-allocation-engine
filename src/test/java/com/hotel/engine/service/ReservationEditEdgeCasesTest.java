package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.service.ReservationService.Changes;
import com.hotel.engine.service.ReservationService.CreateResult;
import com.hotel.engine.service.ReservationService.NewReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Edge cases of editing a reservation, written to check two suspected bugs listed in the handbook
 * (docs/handbook/11-decisions-and-limitations.md):
 * <ul>
 *   <li>L31: shrinking a booking deletes a room request that its released assignment still references.</li>
 *   <li>L4: a waitlisted (or held) booking can be edited to impossible dates, because the date rules only
 *       run for bookings that hold inventory.</li>
 * </ul>
 * Each test asserts the behaviour the service should have; a failure confirms the bug.
 */
class ReservationEditEdgeCasesTest extends HotelTestBase {

    private static Changes rooms(int rooms) {
        return new Changes(null, null, null, null, rooms, null, null, null, null, null, null, null, null);
    }

    private static Changes dates(LocalDate in, LocalDate out) {
        return new Changes(null, null, in, out, null, null, null, null, null, null, null, null, null);
    }

    /** A suite is booked for nights 4-5, so a second suite request for the same nights is waitlisted. */
    private String waitlistedSuite() {
        reservations.create(stay("Meera Nair", RoomType.SUITE, 4, 6, 1), null);
        NewReservation wait = new NewReservation("Omar Shah", "omar.shah@example.org", null, RoomType.SUITE, day(4),
                day(6), 1, 1, false, null, null, false, null, true);
        CreateResult w = reservations.create(wait, null);
        assertEquals("WAITLISTED", w.outcome());
        return w.reservation().reference();
    }

    // ---------- L31: shrinking a booking ----------

    @Test
    @DisplayName("Control: shrinking a booking that has never had a room assigned works")
    void shrinkWithoutAssignmentHistory() {
        String ref = reservations.create(stay("Asha Kulkarni", RoomType.DELUXE, 3, 5, 2), null).reservation().reference();
        assertDoesNotThrow(() -> reservations.update(ref, rooms(1)));
        assertEquals(1, reservationRepo.findByReference(ref).orElseThrow().getRooms());
        assertEquals(1, requestRepo.findByReservationOrderBySeqAsc(reservationRepo.findByReference(ref).orElseThrow()).size());
        assertEquals(2, inventory.freeOn(RoomType.DELUXE, day(3)), "one of the two rooms was given back");
    }

    @Test
    @DisplayName("L31: shrinking a booking whose rooms were already assigned keeps working (assignment history kept)")
    void shrinkAfterRoomsWereAssigned() {
        String ref = reservations.create(stay("Kabir Das", RoomType.DELUXE, 0, 2, 2), null).reservation().reference();
        assignments.run(TODAY);
        assertEquals(2, reservations.view(reservationRepo.findByReference(ref).orElseThrow()).roomsAssigned(),
                "both rooms are assigned before the change");

        assertDoesNotThrow(() -> reservations.update(ref, rooms(1)));
        assertEquals(1, reservationRepo.findByReference(ref).orElseThrow().getRooms());
        assertEquals(2, inventory.freeOn(RoomType.DELUXE, day(0)), "one of the two rooms was given back");
    }

    // ---------- L4: editing a booking that holds no inventory ----------

    @Test
    @DisplayName("Control: a confirmed booking can't be moved to a check-out before its check-in")
    void confirmedReversedDatesRejected() {
        String ref = reservations.create(stay("Riya Sen", RoomType.DELUXE, 3, 5, 1), null).reservation().reference();
        assertThrows(InvalidInputException.class, () -> reservations.update(ref, dates(day(6), day(4))));
    }

    @Test
    @DisplayName("L4: a waitlisted booking can't be moved to a check-out before its check-in either")
    void waitlistedReversedDatesRejected() {
        String ref = waitlistedSuite();
        assertThrows(InvalidInputException.class, () -> reservations.update(ref, dates(day(6), day(4))));
        assertEquals(ReservationStatus.WAITLISTED, reservationRepo.findByReference(ref).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("L4: a waitlisted booking can't be moved beyond the 400-night booking window")
    void waitlistedBeyondWindowRejected() {
        String ref = waitlistedSuite();
        int horizon = InventoryService.HORIZON;
        assertThrows(InvalidInputException.class,
                () -> reservations.update(ref, dates(day(horizon + 10), day(horizon + 12))));
    }

    @Test
    @DisplayName("L4: a waitlisted booking can't be moved into the past")
    void waitlistedIntoPastRejected() {
        String ref = waitlistedSuite();
        assertThrows(InvalidInputException.class, () -> reservations.update(ref, dates(day(-5), day(-3))));
    }
}
