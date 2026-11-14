package com.hotel.engine.service;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.repository.AssignmentRepository;
import com.hotel.engine.repository.AuditRepository;
import com.hotel.engine.repository.GuestRepository;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRepository;
import com.hotel.engine.repository.RoomRequestRepository;
import com.hotel.engine.repository.WatchlistRepository;
import com.hotel.engine.service.ReservationService.NewReservation;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

/**
 * A tiny hotel on a fixed date, rebuilt before every test, so expected numbers can be worked out by hand.
 *
 * <pre>
 *   Standard:  101 (floor 1, accessible), 102 (1), 103 (1), 201 (2)
 *   Deluxe:    301 (floor 3, accessible), 302 (3), 401 (4, accessible)
 *   Suite:     601 (floor 6)
 *   Executive: none
 * </pre>
 * The demo seed is switched off; tests run against H2.
 */
@SpringBootTest
@TestPropertySource(properties = "stayflow.seed.enabled=false")
abstract class HotelTestBase {

    static final LocalDate TODAY = LocalDate.of(2026, 12, 1);

    @Autowired HotelClock clock;
    @Autowired InventoryService inventory;
    @Autowired ReservationService reservations;
    @Autowired AssignmentService assignments;
    @Autowired MaintenanceService maintenance;
    @Autowired WatchlistService watchlist;
    @Autowired RoomRepository roomRepo;
    @Autowired ReservationRepository reservationRepo;
    @Autowired RoomRequestRepository requestRepo;
    @Autowired AssignmentRepository assignmentRepo;
    @Autowired MaintenanceRepository blockRepo;
    @Autowired GuestRepository guestRepo;
    @Autowired WatchlistRepository watchlistRepo;
    @Autowired AuditRepository auditRepo;

    @BeforeEach
    void resetHotel() {
        clock.setFixedDate(TODAY);
        assignmentRepo.deleteAllInBatch();
        requestRepo.deleteAllInBatch();
        reservationRepo.deleteAllInBatch();
        guestRepo.deleteAllInBatch();
        blockRepo.deleteAllInBatch();
        watchlistRepo.deleteAllInBatch();
        auditRepo.deleteAllInBatch();
        roomRepo.deleteAllInBatch();
        roomRepo.saveAll(List.of(
                new RoomEntity("101", RoomType.STANDARD, 1, true, 2),
                new RoomEntity("102", RoomType.STANDARD, 1, false, 2),
                new RoomEntity("103", RoomType.STANDARD, 1, false, 2),
                new RoomEntity("201", RoomType.STANDARD, 2, false, 2),
                new RoomEntity("301", RoomType.DELUXE, 3, true, 3),
                new RoomEntity("302", RoomType.DELUXE, 3, false, 3),
                new RoomEntity("401", RoomType.DELUXE, 4, true, 3),
                new RoomEntity("601", RoomType.SUITE, 6, false, 4)));
        watchlist.configure(WatchlistService.DEFAULT_CAPACITY, WatchlistService.DEFAULT_RATE);
        watchlist.resetCounters();
        inventory.rebuild();
    }

    LocalDate day(int offset) {
        return TODAY.plusDays(offset);
    }

    /** A booking request with the usual defaults: 1 adult per room, no special needs, email from the name. */
    NewReservation stay(String name, RoomType type, int from, int to, int rooms) {
        return new NewReservation(name, name.toLowerCase().replace(' ', '.') + "@example.org", null, type, day(from),
                day(to), rooms, rooms, false, null, null, false, null, false);
    }

    NewReservation accessibleStay(String name, RoomType type, int from, int to, Integer maxFloor) {
        return new NewReservation(name, name.toLowerCase().replace(' ', '.') + "@example.org", null, type, day(from),
                day(to), 1, 1, true, maxFloor, null, false, null, false);
    }
}
