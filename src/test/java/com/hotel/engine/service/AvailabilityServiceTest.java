package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService();
        service.reset(START, 30, Map.of(RoomType.DELUXE, 1, RoomType.STANDARD, 3));
    }

    private LocalDate day(int offset) {
        return START.plusDays(offset);
    }

    @Test
    @DisplayName("Check-out night is free: back-to-back stays in the only room both fit")
    void backToBackStays() {
        assertTrue(service.book("Mehta", RoomType.DELUXE, day(2), day(4), 1).booked());
        assertTrue(service.book("Rao", RoomType.DELUXE, day(4), day(6), 1).booked(),
                "Mehta leaves the morning Rao arrives");
    }

    @Test
    @DisplayName("An overlapping stay is refused with the busiest night's count")
    void overlapRefused() {
        service.book("Mehta", RoomType.DELUXE, day(2), day(5), 1);
        AvailabilityService.BookingResult r = service.book("Rao", RoomType.DELUXE, day(4), day(6), 1);
        assertFalse(r.booked());
        assertEquals(0, r.check().fewestFree());
        assertTrue(r.message().startsWith("Not booked: only 0 deluxe rooms"));
    }

    @Test
    @DisplayName("Edge: the first and last nights of the calendar can be booked; one beyond can't")
    void calendarBoundaries() {
        assertTrue(service.check(RoomType.STANDARD, day(0), day(1), 1).fits());
        assertTrue(service.check(RoomType.STANDARD, day(29), day(30), 1).fits());
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.STANDARD, day(29), day(31), 1));
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.STANDARD, day(-1), day(1), 1));
    }

    @Test
    @DisplayName("Cancelling gives the rooms back so the next guest can book")
    void cancelReturnsRooms() {
        String id = service.book("Mehta", RoomType.DELUXE, day(2), day(5), 1).bookingId();
        assertFalse(service.check(RoomType.DELUXE, day(3), day(4), 1).fits());
        service.cancel(id);
        assertTrue(service.check(RoomType.DELUXE, day(3), day(4), 1).fits());
        assertTrue(service.bookings().isEmpty());
    }

    @Test
    @DisplayName("Per-night counts and the tree answer agree")
    void perNightCountsMatchQuery() {
        service.book("A", RoomType.STANDARD, day(1), day(4), 2);
        service.book("B", RoomType.STANDARD, day(3), day(6), 1);
        AvailabilityService.AvailabilityCheck c = service.check(RoomType.STANDARD, day(0), day(7), 1);
        assertEquals(7, c.nights());
        int min = c.freeByNight().stream().mapToInt(AvailabilityService.NightCount::free).min().orElseThrow();
        assertEquals(min, c.fewestFree());
        assertEquals(0, c.fewestFree());   // night 3: 3 rooms - 2 - 1
    }

    @Test
    @DisplayName("Invalid: check-out not after check-in, zero rooms, more rooms than exist, unknown booking")
    void rejectsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.DELUXE, day(3), day(3), 1));
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.DELUXE, day(4), day(3), 1));
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.DELUXE, day(1), day(2), 0));
        assertThrows(InvalidInputException.class, () -> service.check(RoomType.DELUXE, day(1), day(2), 2));
        assertThrows(InvalidInputException.class, () -> service.check(null, day(1), day(2), 1));
        assertThrows(InvalidInputException.class, () -> service.book(" ", RoomType.DELUXE, day(1), day(2), 1));
        assertThrows(InvalidInputException.class, () -> service.cancel("BK-9999"));
    }
}
