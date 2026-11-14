package com.hotel.engine.seed;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.Housekeeping;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.Enums.Screening;
import com.hotel.engine.model.Enums.WatchKind;
import com.hotel.engine.model.GuestEntity;
import com.hotel.engine.model.MaintenanceBlockEntity;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.model.RoomRequestEntity;
import com.hotel.engine.model.UserAccount;
import com.hotel.engine.model.WatchlistEntryEntity;
import com.hotel.engine.repository.AssignmentRepository;
import com.hotel.engine.repository.AuditRepository;
import com.hotel.engine.repository.GuestRepository;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRepository;
import com.hotel.engine.repository.RoomRequestRepository;
import com.hotel.engine.repository.UserRepository;
import com.hotel.engine.repository.WatchlistRepository;
import com.hotel.engine.service.AssignmentService;
import com.hotel.engine.service.AuditService;
import com.hotel.engine.service.ContactKeys;
import com.hotel.engine.service.HotelClock;
import com.hotel.engine.service.HotelLock;
import com.hotel.engine.service.InventoryService;
import com.hotel.engine.service.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Loads the demo hotel into an empty database on startup, and again on an admin reset.
 *
 * <p>Everything comes from a seeded random generator, so the same seed gives the same hotel relative to
 * today: stays that have ended, guests in house, today's arrivals, future bookings, a waitlist, two bookings
 * held by watchlist matches, maintenance blocks and a watchlist. Bookings are only generated where every
 * night has room (and accessible room, when needed), so the data always obeys the inventory rules.
 * All guest emails use the reserved example.com/.net/.org domains.
 */
@Service
@Order(1)
public class SeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private static final String[] FIRST = {"Aarav", "Diya", "Ishaan", "Ananya", "Kabir", "Meera", "Rohan", "Saanvi",
            "Arjun", "Kavya", "Vihaan", "Priya", "Aditya", "Nisha", "Rahul", "Sneha", "Vikram", "Pooja", "Karan",
            "Riya", "Siddharth", "Tara", "Nikhil", "Zara", "Farhan", "Leela", "Omar", "Maya", "Daniel", "Grace"};
    private static final String[] LAST = {"Mehta", "Rao", "Iyer", "Khan", "Das", "Pillai", "Nair", "Sharma", "Gupta",
            "Reddy", "Menon", "Bose", "Joshi", "Kulkarni", "Fernandes", "D'Souza", "Patel", "Singh", "Chopra",
            "Banerjee", "Hegde", "Shetty", "Kapoor", "Malhotra", "Verma", "Sinha", "Thomas", "George", "Ali", "Shah"};
    private static final String[] DOMAINS = {"example.com", "example.net", "example.org"};

    private final SeedProperties props;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final GuestRepository guests;
    private final ReservationRepository reservations;
    private final RoomRequestRepository requests;
    private final AssignmentRepository assignmentRepo;
    private final MaintenanceRepository blocks;
    private final WatchlistRepository watchlistRepo;
    private final AuditRepository auditRepo;
    private final AssignmentService assignments;
    private final InventoryService inventory;
    private final WatchlistService watchlist;
    private final AuditService audit;
    private final HotelLock lock;
    private final HotelClock clock;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public SeedService(SeedProperties props, UserRepository users, RoomRepository rooms, GuestRepository guests,
                       ReservationRepository reservations, RoomRequestRepository requests,
                       AssignmentRepository assignmentRepo, MaintenanceRepository blocks,
                       WatchlistRepository watchlistRepo, AuditRepository auditRepo, AssignmentService assignments,
                       InventoryService inventory, WatchlistService watchlist, AuditService audit, HotelLock lock,
                       HotelClock clock, PasswordEncoder encoder, PlatformTransactionManager txManager) {
        this.props = props;
        this.users = users;
        this.rooms = rooms;
        this.guests = guests;
        this.reservations = reservations;
        this.requests = requests;
        this.assignmentRepo = assignmentRepo;
        this.blocks = blocks;
        this.watchlistRepo = watchlistRepo;
        this.auditRepo = auditRepo;
        this.assignments = assignments;
        this.inventory = inventory;
        this.watchlist = watchlist;
        this.audit = audit;
        this.lock = lock;
        this.clock = clock;
        this.encoder = encoder;
        this.tx = new TransactionTemplate(txManager);
    }

    public record Summary(long seed, int rooms, int reservations, int inHouse, int arrivalsToday, int waitlisted,
                          int held, int watchlist, int maintenance) {
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(s -> {
            for (SeedProperties.DemoUser u : props.users()) {
                if (!users.existsByUsername(u.username())) {
                    users.save(new UserAccount(u.username(), encoder.encode(u.password()), u.displayName(), u.role()));
                }
            }
        });
        if (props.enabled() && rooms.count() == 0) {
            Summary s = load(props.seed());
            log.info("Demo hotel loaded (seed {}): {} rooms, {} reservations, {} in house, {} arriving today, "
                            + "{} watchlist entries", s.seed(), s.rooms(), s.reservations(), s.inHouse(),
                    s.arrivalsToday(), s.watchlist());
        }
    }

    /** Deletes all hotel data (staff accounts stay) and generates the demo hotel again. */
    public Summary reset(long seed) {
        Summary s = lock.call(() -> {
            tx.executeWithoutResult(st -> {
                assignmentRepo.deleteAllInBatch();
                requests.deleteAllInBatch();
                reservations.deleteAllInBatch();
                guests.deleteAllInBatch();
                blocks.deleteAllInBatch();
                watchlistRepo.deleteAllInBatch();
                auditRepo.deleteAllInBatch();
                rooms.deleteAllInBatch();
            });
            return load(seed);
        });
        audit.log("DEMO_RESET", "SYSTEM", null, "Demo hotel reloaded with seed " + seed);
        return s;
    }

    private Summary load(long seed) {
        audit.setMuted(true);
        try {
            Summary s = lock.call(() -> tx.execute(st -> generate(seed)));
            inventory.rebuild();
            watchlist.rebuild();
            watchlist.resetCounters();
            return s;
        } finally {
            audit.setMuted(false);
        }
    }

    // ---------------------------------------------------------------------------------------

    private Summary generate(long seed) {
        Random random = new Random(seed);
        LocalDate today = clock.today();
        List<RoomEntity> roomList = rooms.saveAll(HotelLayout.rooms());
        rooms.flush();

        // Capacity per type per night over [today - 30, today + 100).
        LocalDate first = today.minusDays(30);
        int span = 130;
        Map<RoomType, int[]> cap = new EnumMap<>(RoomType.class);
        Map<RoomType, int[]> capAcc = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) {
            int total = (int) roomList.stream().filter(r -> r.getType() == t).count();
            int acc = (int) roomList.stream().filter(r -> r.getType() == t && r.isAccessible()).count();
            cap.put(t, filled(span, total));
            capAcc.put(t, filled(span, acc));
        }

        // Maintenance blocks take capacity first.
        Map<String, RoomEntity> byNumber = new TreeMap<>();
        roomList.forEach(r -> byNumber.put(r.getNumber(), r));
        List<MaintenanceBlockEntity> maintenance = List.of(
                new MaintenanceBlockEntity(byNumber.get("215"), today.minusDays(1), today.plusDays(3), "Bathroom leak repair", "system"),
                new MaintenanceBlockEntity(byNumber.get("410"), today.plusDays(5), today.plusDays(10), "Carpet replacement", "system"),
                new MaintenanceBlockEntity(byNumber.get("505"), today.plusDays(12), today.plusDays(14), "Air conditioning service", "system"));
        for (MaintenanceBlockEntity b : maintenance) {
            take(cap, capAcc, first, span, b.getRoom().getType(), b.getRoom().isAccessible(), b.getStartDate(), b.getEndDate(), 1);
        }
        blocks.saveAll(maintenance);

        int count = 0;
        // A fixed group for the demo: three guests needing accessible deluxe rooms on floor 3 or below,
        // arriving today. Inventory allows them (3 accessible deluxe rooms exist), but only two of those
        // rooms are low enough: the assignment page explains the shortage from the minimum cut.
        String[][] demo = {{"Lakshmi", "Iyer"}, {"Joseph", "Thomas"}, {"Fatima", "Ali"}};
        for (String[] name : demo) {
            if (fits(cap, capAcc, first, span, RoomType.DELUXE, true, today, today.plusDays(2), 1)) {
                take(cap, capAcc, first, span, RoomType.DELUXE, true, today, today.plusDays(2), 1);
                save(random, name[0], name[1], RoomType.DELUXE, today, today.plusDays(2), 1, 2, true, 3, null, false,
                        ReservationStatus.CONFIRMED, today);
                count++;
            }
        }

        int waitlisted = 0;
        for (LocalDate day = first; day.isBefore(today.plusDays(90)); day = day.plusDays(1)) {
            int dow = day.getDayOfWeek().getValue();
            double factor = (dow == 5 || dow == 6) ? 1.45 : dow == 7 ? 0.8 : 1.0;
            int arrivals = (int) Math.round((18 + random.nextInt(8)) * factor);
            for (int i = 0; i < arrivals; i++) {
                RoomType type = pickType(random);
                int nights = pickNights(random);
                LocalDate out = day.plusDays(nights);
                int roomsWanted = random.nextDouble() < 0.82 ? 1 : 2;
                boolean acc = random.nextDouble() < 0.08;
                Integer maxFloor = random.nextDouble() < 0.07 ? 1 + random.nextInt(3) : null;
                Integer preferred = random.nextDouble() < 0.2 ? 1 + random.nextInt(6) : null;
                boolean upgrade = type != RoomType.SUITE && random.nextDouble() < 0.3;
                String fn = FIRST[random.nextInt(FIRST.length)];
                String ln = LAST[random.nextInt(LAST.length)];
                if (random.nextDouble() < 0.04 && !day.isBefore(today)) {
                    save(random, fn, ln, type, day, out, roomsWanted, adults(random, roomsWanted), acc, maxFloor,
                            preferred, upgrade, ReservationStatus.CANCELLED, today);
                    count++;
                    continue;
                }
                if (fits(cap, capAcc, first, span, type, acc, day, out, roomsWanted)) {
                    take(cap, capAcc, first, span, type, acc, day, out, roomsWanted);
                    ReservationStatus status = !out.isAfter(today) ? ReservationStatus.CHECKED_OUT
                            : day.isBefore(today) ? ReservationStatus.CHECKED_IN : ReservationStatus.CONFIRMED;
                    save(random, fn, ln, type, day, out, roomsWanted, adults(random, roomsWanted), acc, maxFloor,
                            preferred, upgrade, status, today);
                    count++;
                } else if (day.isAfter(today.plusDays(2)) && random.nextDouble() < 0.25 && waitlisted < 12) {
                    save(random, fn, ln, type, day, out, roomsWanted, adults(random, roomsWanted), acc, maxFloor,
                            preferred, upgrade, ReservationStatus.WAITLISTED, today);
                    waitlisted++;
                    count++;
                }
            }
        }

        // Watchlist: readable entries for demos, then synthetic ones to give the filter a realistic load.
        List<WatchlistEntryEntity> entries = new ArrayList<>();
        String[][] readable = {
                {"EMAIL", "fraud.ring@example.com", "Chargeback, Aug 2026"},
                {"EMAIL", "no.show.repeat@example.net", "Five no-shows with guaranteed rates"},
                {"EMAIL", "card.tester@example.org", "Many small declined card authorisations"},
                {"EMAIL", "damage.claim@example.com", "Unpaid room damage, Mar 2026"},
                {"PHONE", "+91 90000 11111", "Fake ID presented at check-in"},
                {"PHONE", "+91 90000 22222", "Chargeback, Jun 2026"},
                {"PHONE", "+44 7700 900123", "Disputed charges across three stays"}};
        for (String[] e : readable) {
            WatchKind kind = WatchKind.valueOf(e[0]);
            String key = ContactKeys.of(kind, e[1]);
            entries.add(new WatchlistEntryEntity(kind, key, ContactKeys.display(key), e[2]));
        }
        String[] reasons = {"Chargeback", "Repeated no-show", "Card testing", "Unpaid balance"};
        for (int i = 0; i < 3_000; i++) {
            String reason = reasons[random.nextInt(reasons.length)] + " (imported list)";
            if (random.nextDouble() < 0.7) {
                String key = ContactKeys.email("listed" + (10_000 + random.nextInt(900_000)) + "@" + DOMAINS[random.nextInt(3)]);
                entries.add(new WatchlistEntryEntity(WatchKind.EMAIL, key, ContactKeys.display(key), reason));
            } else {
                String key = ContactKeys.phone("9" + (100_000_000 + random.nextInt(900_000_000)));
                entries.add(new WatchlistEntryEntity(WatchKind.PHONE, key, ContactKeys.display(key), reason));
            }
        }
        int listed = watchlist.addAll(entries);

        // Two future bookings held because the guest matched the watchlist.
        ReservationEntity h1 = save(random, "Victor", "Hale", RoomType.EXECUTIVE, today.plusDays(4), today.plusDays(6), 1, 1,
                false, null, null, false, ReservationStatus.PENDING_REVIEW, today);
        attachContact(h1, "fraud.ring@example.com", null, "Chargeback, Aug 2026");
        ReservationEntity h2 = save(random, "Ronan", "Pike", RoomType.STANDARD, today.plusDays(9), today.plusDays(10), 1, 1,
                false, null, null, false, ReservationStatus.PENDING_REVIEW, today);
        attachContact(h2, null, "+91 90000 11111", "Fake ID presented at check-in");
        count += 2;

        reservations.flush();

        // Guests already in house get rooms by max flow, earliest arrivals first.
        TreeMap<LocalDate, List<RoomRequestEntity>> inHouseByDay = new TreeMap<>();
        for (ReservationEntity r : reservations.findByStatusIn(List.of(ReservationStatus.CHECKED_IN))) {
            inHouseByDay.computeIfAbsent(r.getCheckIn(), d -> new ArrayList<>())
                    .addAll(requests.findByReservationOrderBySeqAsc(r));
        }
        int inHouse = 0;
        for (List<RoomRequestEntity> batch : inHouseByDay.values()) {
            assignments.assignForSeed(batch);
            inHouse += batch.size();
        }

        // Some rooms need cleaning after this morning's departures.
        int dirty = 0;
        for (RoomEntity r : rooms.findAllByOrderByFloorAscNumberAsc()) {
            if (r.getHousekeeping() == Housekeeping.CLEAN && random.nextDouble() < 0.12) {
                r.setHousekeeping(dirty++ % 3 == 0 ? Housekeeping.INSPECTED : Housekeeping.DIRTY);
            }
        }

        int arrivals = reservations.arrivingOn(today, List.of(ReservationStatus.CONFIRMED)).size();
        return new Summary(seed, roomList.size(), count, inHouse, arrivals, waitlisted, 2, listed, maintenance.size());
    }

    private ReservationEntity save(Random random, String first, String last, RoomType type, LocalDate in, LocalDate out,
                                   int roomCount, int adults, boolean acc, Integer maxFloor, Integer preferred,
                                   boolean upgrade, ReservationStatus status, LocalDate today) {
        String email = (first + "." + last).toLowerCase().replace("'", "") + (10 + random.nextInt(89)) + "@"
                + DOMAINS[random.nextInt(DOMAINS.length)];
        String phone = "+91 9" + (100_000_000 + random.nextInt(900_000_000));
        GuestEntity g = guests.save(new GuestEntity(first + " " + last, email, phone, ContactKeys.email(email),
                ContactKeys.phone(phone)));
        ReservationEntity r = new ReservationEntity(g, type, in, out, roomCount, adults, acc, maxFloor, preferred,
                upgrade, "system");
        r.setStatus(status);
        r.setScreening(Screening.CLEAR);
        LocalDate made = in.minusDays(1 + random.nextInt(45));
        if (made.isAfter(today)) {
            made = today;
        }
        r.backdate(made.atTime(LocalTime.of(9 + random.nextInt(9), random.nextInt(60)))
                .atZone(ZoneId.systemDefault()).toInstant());
        r = reservations.save(r);
        r.setReference(String.format("BK-%05d", r.getId()));
        for (int i = 1; i <= roomCount; i++) {
            requests.save(new RoomRequestEntity(r, i));
        }
        return r;
    }

    /** Replaces a held booking's generated contact details with the watchlisted ones it matched. */
    private void attachContact(ReservationEntity r, String email, String phone, String reason) {
        GuestEntity g = guests.save(new GuestEntity(r.getGuest().getFullName(), email, phone, ContactKeys.email(email),
                ContactKeys.phone(phone)));
        ReservationEntity fresh = new ReservationEntity(g, r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(),
                r.getAdults(), false, null, null, false, "system");
        fresh.setStatus(ReservationStatus.PENDING_REVIEW);
        fresh.setScreening(Screening.MATCH);
        fresh.setBlockReason(reason);
        requests.findByReservationOrderBySeqAsc(r).forEach(requests::delete);
        reservations.delete(r);
        fresh = reservations.save(fresh);
        fresh.setReference(String.format("BK-%05d", fresh.getId()));
        for (int i = 1; i <= fresh.getRooms(); i++) {
            requests.save(new RoomRequestEntity(fresh, i));
        }
    }

    private static RoomType pickType(Random random) {
        double t = random.nextDouble();
        return t < 0.45 ? RoomType.STANDARD : t < 0.78 ? RoomType.DELUXE : t < 0.95 ? RoomType.EXECUTIVE : RoomType.SUITE;
    }

    private static int pickNights(Random random) {
        double t = random.nextDouble();
        return t < 0.35 ? 1 : t < 0.65 ? 2 : t < 0.85 ? 3 : t < 0.95 ? 4 : 5;
    }

    private static int adults(Random random, int roomCount) {
        return Math.min(roomCount * 4, roomCount + random.nextInt(roomCount + 1));
    }

    private static int[] filled(int n, int value) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, value);
        return a;
    }

    private static boolean fits(Map<RoomType, int[]> cap, Map<RoomType, int[]> capAcc, LocalDate first, int span,
                                RoomType type, boolean acc, LocalDate in, LocalDate out, int roomCount) {
        for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1)) {
            int i = (int) (d.toEpochDay() - first.toEpochDay());
            if (i < 0 || i >= span) {
                return false;
            }
            if (cap.get(type)[i] < roomCount || (acc && capAcc.get(type)[i] < roomCount)) {
                return false;
            }
        }
        return true;
    }

    private static void take(Map<RoomType, int[]> cap, Map<RoomType, int[]> capAcc, LocalDate first, int span,
                             RoomType type, boolean acc, LocalDate in, LocalDate out, int roomCount) {
        for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1)) {
            int i = (int) (d.toEpochDay() - first.toEpochDay());
            if (i >= 0 && i < span) {
                cap.get(type)[i] -= roomCount;
                if (acc) {
                    capAcc.get(type)[i] -= roomCount;
                }
            }
        }
    }
}
