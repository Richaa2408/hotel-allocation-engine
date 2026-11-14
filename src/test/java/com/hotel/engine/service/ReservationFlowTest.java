package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.Enums.WatchKind;
import com.hotel.engine.service.ReservationService.Changes;
import com.hotel.engine.service.ReservationService.CreateResult;
import com.hotel.engine.service.ReservationService.NewReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationFlowTest extends HotelTestBase {

    @Test
    @DisplayName("A clean booking is screened, confirmed, split into one request per room, and takes inventory")
    void booksCleanGuest() {
        CreateResult r = reservations.create(stay("Asha Kulkarni", RoomType.DELUXE, 1, 4, 2), null);
        assertEquals("BOOKED", r.outcome());
        assertEquals("CLEAR", r.screening().verdict());
        assertEquals(ReservationStatus.CONFIRMED, r.reservation().status());
        assertEquals(2, requestRepo.findAll().size());
        assertEquals(1, inventory.freeOn(RoomType.DELUXE, day(2)));
    }

    @Test
    @DisplayName("Duplicate submission (same guest, type and dates) is refused; an Idempotency-Key retry returns the first booking")
    void duplicatesAndRetries() {
        CreateResult first = reservations.create(stay("Kabir Das", RoomType.STANDARD, 1, 2, 1), "key-123");
        CreateResult retry = reservations.create(stay("Kabir Das", RoomType.STANDARD, 1, 2, 1), "key-123");
        assertEquals(first.reservation().reference(), retry.reservation().reference());
        assertEquals(1, reservationRepo.count());
        assertThrows(ConflictException.class, () -> reservations.create(stay("Kabir Das", RoomType.STANDARD, 1, 2, 1), null));
        assertEquals(3, inventory.freeOn(RoomType.STANDARD, day(1)), "only one room was taken");
    }

    @Test
    @DisplayName("A watchlist match is held without taking rooms; a manager's approval confirms it")
    void heldThenApproved() {
        watchlist.add(WatchKind.EMAIL, "fraud.ring@example.com", "Chargeback");
        NewReservation cmd = new NewReservation("Victor Hale", "Fraud.Ring@Example.com", null, RoomType.SUITE, day(3),
                day(5), 1, 1, false, null, null, false, null, false);
        CreateResult r = reservations.create(cmd, null);
        assertEquals("HELD", r.outcome());
        assertEquals(1, inventory.freeOn(RoomType.SUITE, day(3)), "a held booking takes no inventory");
        var approved = reservations.approve(r.reservation().reference());
        assertEquals(ReservationStatus.CONFIRMED, approved.status());
        assertEquals(0, inventory.freeOn(RoomType.SUITE, day(3)));
    }

    @Test
    @DisplayName("Full: unavailable with alternatives; the waitlisted booking is confirmed when a cancellation frees rooms")
    void waitlistPromotion() {
        CreateResult suite = reservations.create(stay("Meera Nair", RoomType.SUITE, 2, 4, 1), null);
        CreateResult full = reservations.create(stay("Omar Shah", RoomType.SUITE, 2, 3, 1), null);
        assertEquals("UNAVAILABLE", full.outcome());
        assertNull(full.reservation());
        assertTrue(full.alternatives().stream().anyMatch(a -> a.roomType() == RoomType.DELUXE));

        NewReservation wait = new NewReservation("Omar Shah", "omar.shah@example.org", null, RoomType.SUITE, day(2),
                day(3), 1, 1, false, null, null, false, null, true);
        CreateResult w = reservations.create(wait, null);
        assertEquals("WAITLISTED", w.outcome());
        reservations.cancel(suite.reservation().reference(), "Plans changed");
        assertEquals(ReservationStatus.CONFIRMED, reservationRepo.findByReference(w.reservation().reference()).orElseThrow().getStatus());
        assertEquals(0, inventory.freeOn(RoomType.SUITE, day(2)));
        assertEquals(1, inventory.freeOn(RoomType.SUITE, day(3)));
    }

    @Test
    @DisplayName("Changing dates moves the inventory; an impossible change is refused and nothing moves")
    void changeDates() {
        String ref = reservations.create(stay("Riya Sen", RoomType.SUITE, 1, 2, 1), null).reservation().reference();
        reservations.update(ref, new Changes(null, null, day(5), day(7), null, null, null, null, null, null, null, null, null));
        assertEquals(1, inventory.freeOn(RoomType.SUITE, day(1)));
        assertEquals(0, inventory.freeOn(RoomType.SUITE, day(6)));
        reservations.create(stay("Tara Bose", RoomType.SUITE, 10, 11, 1), null);
        assertThrows(ConflictException.class, () -> reservations.update(ref,
                new Changes(null, null, day(9), day(11), null, null, null, null, null, null, null, null, null)));
        assertEquals(0, inventory.freeOn(RoomType.SUITE, day(6)), "the original stay still holds its nights");
        assertEquals(1, inventory.freeOn(RoomType.SUITE, day(9)));
    }

    @Test
    @DisplayName("Check-in needs a room; early check-out gives the remaining nights back")
    void checkInAndEarlyCheckOut() {
        String ref = reservations.create(stay("Diya Iyer", RoomType.DELUXE, 0, 3, 1), null).reservation().reference();
        assertThrows(ConflictException.class, () -> reservations.checkIn(ref));
        assignments.run(TODAY);
        assertEquals(ReservationStatus.CHECKED_IN, reservations.checkIn(ref).status());
        assertEquals(2, inventory.freeOn(RoomType.DELUXE, day(1)));
        clock.setFixedDate(day(1));
        reservations.checkOut(ref);
        assertEquals(3, inventory.freeOn(RoomType.DELUXE, day(1)), "tonight and tomorrow are free again");
        assertEquals(ReservationStatus.CHECKED_OUT, reservationRepo.findByReference(ref).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Concurrency: eight clerks book the last suite at once; exactly one gets it")
    void lastRoomOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                go.await();
                return reservations.create(stay("Clerk Guest" + n, RoomType.SUITE, 4, 6, 1), null).outcome();
            }));
        }
        go.countDown();
        int booked = 0;
        for (Future<String> f : results) {
            booked += f.get().equals("BOOKED") ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(1, booked);
        assertEquals(1, reservationRepo.count());
        assertEquals(0, inventory.freeOn(RoomType.SUITE, day(5)));
        assertFalse(inventory.check(RoomType.SUITE, day(4), day(6), 1, false).fits());
    }
}
