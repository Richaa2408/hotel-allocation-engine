package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.service.InventoryService.AvailabilityCheck;
import com.hotel.engine.service.InventoryService.HeatCell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryServiceTest extends HotelTestBase {

    @Test
    @DisplayName("An accessible booking must fit the accessible tree even when other rooms of the type are free")
    void accessibleRoomsCountedSeparately() {
        assertEquals("BOOKED", reservations.create(accessibleStay("Ana Rao", RoomType.STANDARD, 1, 3, null), null).outcome());
        AvailabilityCheck second = inventory.check(RoomType.STANDARD, day(1), day(3), 1, true);
        assertFalse(second.fits());
        assertEquals("ACCESSIBLE_ROOMS", second.limitedBy());
        assertEquals(3, second.fewestFree(), "three standard rooms are still free, just not accessible ones");
        assertTrue(inventory.check(RoomType.STANDARD, day(1), day(3), 1, false).fits());
    }

    @Test
    @DisplayName("Edge: asking for more accessible rooms than exist is rejected, not answered 'unavailable'")
    void moreAccessibleThanExist() {
        assertThrows(InvalidInputException.class, () -> inventory.check(RoomType.STANDARD, day(1), day(2), 2, true));
        assertThrows(InvalidInputException.class, () -> inventory.check(RoomType.STANDARD, day(1), day(2), 5, false));
    }

    @Test
    @DisplayName("Range minimum: one full night makes the whole stay unavailable, and names that night")
    void busiestNightDecides() {
        reservations.create(stay("Group A", RoomType.STANDARD, 2, 3, 4), null);   // night 2 sold out
        AvailabilityCheck c = inventory.check(RoomType.STANDARD, day(1), day(5), 1, false);
        assertFalse(c.fits());
        assertEquals(0, c.fewestFree());
        assertEquals(day(2), c.busiestNight());
        assertEquals(4, c.nights());
        assertTrue(inventory.check(RoomType.STANDARD, day(3), day(5), 4, false).fits(), "check-out night is free");
    }

    @Test
    @DisplayName("Maintenance takes a room out of inventory, and is refused if bookings already need every room")
    void maintenanceBlocks() {
        maintenance.create("102", day(1), day(4), "Leak");
        assertEquals(3, inventory.freeOn(RoomType.STANDARD, day(2)));
        reservations.create(stay("Group B", RoomType.STANDARD, 5, 6, 4), null);
        assertThrows(ConflictException.class, () -> maintenance.create("103", day(5), day(6), "Paint"));
        long id = blockRepo.findAll().get(0).getId();
        maintenance.remove(id);
        assertEquals(4, inventory.freeOn(RoomType.STANDARD, day(2)));
    }

    @Test
    @DisplayName("Heatmap levels: SOLD_OUT at zero, LOW at 10% or one room, HEALTHY otherwise")
    void heatmapLevels() {
        reservations.create(stay("Suite Guest", RoomType.SUITE, 0, 1, 1), null);
        reservations.create(stay("Std Group", RoomType.STANDARD, 1, 2, 3), null);
        var rows = inventory.heatmap(TODAY, 3);
        HeatCell suiteTonight = rows.stream().filter(r -> r.roomType() == RoomType.SUITE).findFirst().orElseThrow().cells().get(0);
        HeatCell stdNight1 = rows.stream().filter(r -> r.roomType() == RoomType.STANDARD).findFirst().orElseThrow().cells().get(1);
        assertEquals("SOLD_OUT", suiteTonight.level());
        assertEquals(1, suiteTonight.booked());
        assertEquals("LOW", stdNight1.level());
        assertEquals(1, stdNight1.free());
    }

    @Test
    @DisplayName("Invalid: past check-in, check-out not after check-in, beyond the booking window, no room type")
    void rejectsInvalidStays() {
        assertThrows(InvalidInputException.class, () -> inventory.check(RoomType.DELUXE, day(-1), day(1), 1, false));
        assertThrows(InvalidInputException.class, () -> inventory.check(RoomType.DELUXE, day(2), day(2), 1, false));
        assertThrows(InvalidInputException.class,
                () -> inventory.check(RoomType.DELUXE, day(10), day(InventoryService.HORIZON + 1), 1, false));
        assertThrows(InvalidInputException.class, () -> inventory.check(null, day(1), day(2), 1, false));
        assertNull(inventory.check(RoomType.DELUXE, day(1), day(2), 1, false).limitedBy());
    }
}
