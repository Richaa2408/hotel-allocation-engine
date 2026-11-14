package com.hotel.engine.service;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.AssignmentEntity;
import com.hotel.engine.model.AuditEventEntity;
import com.hotel.engine.model.Enums.AssignmentMethod;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomRequestEntity;
import com.hotel.engine.repository.AssignmentRepository;
import com.hotel.engine.repository.AuditRepository;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRepository;
import com.hotel.engine.repository.RoomRequestRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Numbers for the Overview and Analytics pages, all computed from the database and the inventory trees. */
@Service
public class AnalyticsService {

    private static final Set<ReservationStatus> STAYED = EnumSet.of(ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT);

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final RoomRequestRepository requests;
    private final AssignmentRepository assignmentRepo;
    private final MaintenanceRepository blocks;
    private final AuditRepository auditRepo;
    private final InventoryService inventory;
    private final AssignmentService assignments;
    private final WatchlistService watchlist;
    private final HotelClock clock;

    public AnalyticsService(ReservationRepository reservations, RoomRepository rooms, RoomRequestRepository requests,
                            AssignmentRepository assignmentRepo, MaintenanceRepository blocks, AuditRepository auditRepo,
                            InventoryService inventory, AssignmentService assignments, WatchlistService watchlist,
                            HotelClock clock) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.requests = requests;
        this.assignmentRepo = assignmentRepo;
        this.blocks = blocks;
        this.auditRepo = auditRepo;
        this.inventory = inventory;
        this.assignments = assignments;
        this.watchlist = watchlist;
        this.clock = clock;
    }

    // ---------- overview ----------

    public record Kpis(LocalDate date, int totalRooms, int roomTypes, int occupiedTonight, int sellableTonight,
                       double occupancy, int availableTonight, int outOfServiceTonight, int arrivalsToday,
                       int awaitingAllocation, int departuresToday, int inHouse, int pendingReviews, int waitlisted) {
    }

    public record TrendPoint(LocalDate night, Map<String, Double> occupancy, Map<String, Integer> booked) {
    }

    public record Activity(Instant at, String actor, String action, String reference, String detail) {
    }

    public record AlertItem(String kind, String severity, String message, String link) {
    }

    public record Overview(Kpis kpis, List<TrendPoint> trend, List<Activity> activity, List<AlertItem> alerts) {
    }

    public Overview overview() {
        LocalDate today = clock.today();
        return new Overview(kpis(today), trend(today.minusDays(30), today.plusDays(30)), activity(today),
                alerts(today));
    }

    Kpis kpis(LocalDate today) {
        int total = 0, free = 0;
        for (RoomType t : RoomType.values()) {
            total += inventory.total(t);
            free += inventory.freeOn(t, today);
        }
        int out = (int) blocks.findActiveOverlapping(today, today.plusDays(1)).size();
        int sellable = total - out;
        int occupied = Math.max(0, sellable - free);
        int arrivals = reservations.arrivingOn(today, EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)).size();
        int departures = reservations.departingOn(today, EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT)).size();
        return new Kpis(today, total, RoomType.values().length, occupied, sellable,
                sellable == 0 ? 0 : round1(100.0 * occupied / sellable), free, out, arrivals,
                assignments.board(today).pending(), departures,
                (int) reservations.countByStatus(ReservationStatus.CHECKED_IN),
                (int) reservations.countByStatus(ReservationStatus.PENDING_REVIEW),
                (int) reservations.countByStatus(ReservationStatus.WAITLISTED));
    }

    /** Booked rooms and occupancy % per night and type over [from, to), from reservations that stayed or will stay. */
    public List<TrendPoint> trend(LocalDate from, LocalDate to) {
        int days = (int) (to.toEpochDay() - from.toEpochDay());
        Map<RoomType, int[]> booked = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) {
            booked.put(t, new int[days]);
        }
        for (ReservationEntity r : reservations.findOverlapping(from, to, STAYED)) {
            for (int d = 0; d < days; d++) {
                LocalDate night = from.plusDays(d);
                if (!night.isBefore(r.getCheckIn()) && night.isBefore(r.getCheckOut())) {
                    booked.get(r.getRoomType())[d] += r.getRooms();
                }
            }
        }
        Map<RoomType, Integer> totals = new EnumMap<>(RoomType.class);
        int all = 0;
        for (RoomType t : RoomType.values()) {
            totals.put(t, inventory.total(t));
            all += inventory.total(t);
        }
        List<TrendPoint> points = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            Map<String, Double> occ = new LinkedHashMap<>();
            Map<String, Integer> count = new LinkedHashMap<>();
            int sum = 0;
            for (RoomType t : RoomType.values()) {
                int b = booked.get(t)[d];
                sum += b;
                count.put(t.name(), b);
                occ.put(t.name(), totals.get(t) == 0 ? 0 : round1(100.0 * b / totals.get(t)));
            }
            count.put("ALL", sum);
            occ.put("ALL", all == 0 ? 0 : round1(100.0 * sum / all));
            points.add(new TrendPoint(from.plusDays(d), occ, count));
        }
        return points;
    }

    List<Activity> activity(LocalDate today) {
        Instant since = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return auditRepo.findByAtGreaterThanEqualOrderByAtDesc(since).stream().limit(40)
                .map((AuditEventEntity e) -> new Activity(e.getAt(), e.getActor(), e.getAction(), e.getEntityRef(), e.getDetail()))
                .toList();
    }

    List<AlertItem> alerts(LocalDate today) {
        List<AlertItem> out = new ArrayList<>();
        int pending = assignments.board(today).pending();
        if (pending > 0) {
            out.add(new AlertItem("ASSIGNMENT", "WARNING", pending + " room request" + (pending == 1 ? "" : "s")
                    + " arriving today " + (pending == 1 ? "has" : "have") + " no room yet.", "/assignment"));
        }
        long reviews = reservations.countByStatus(ReservationStatus.PENDING_REVIEW);
        if (reviews > 0) {
            out.add(new AlertItem("RISK", "WARNING", reviews + " reservation" + (reviews == 1 ? " is" : "s are")
                    + " held after a watchlist match and need a manager's review.", "/reservations?status=PENDING_REVIEW"));
        }
        for (InventoryService.Alert a : inventory.alerts(14)) {
            if (a.level().equals("SOLD_OUT")) {
                out.add(new AlertItem("INVENTORY", "CRITICAL", a.message(), "/inventory"));
            }
        }
        long low = inventory.alerts(14).stream().filter(a -> a.level().equals("LOW")).count();
        if (low > 0) {
            out.add(new AlertItem("INVENTORY", "WARNING", low == 1
                    ? "One room type is down to 10% or fewer free rooms on a night in the next 14 days."
                    : "Room types are down to 10% or fewer free rooms on " + low + " nights in the next 14 days.",
                    "/inventory"));
        }
        long waitlisted = reservations.countByStatus(ReservationStatus.WAITLISTED);
        if (waitlisted > 0) {
            out.add(new AlertItem("WAITLIST", "INFO", waitlisted + " reservation" + (waitlisted == 1 ? " is" : "s are")
                    + " on the waitlist.", "/reservations?status=WAITLISTED"));
        }
        if (watchlist.stats().overCapacity()) {
            out.add(new AlertItem("RISK", "WARNING", "The watchlist has more entries than the filter was sized for, "
                    + "so false positives are rising. Resize it in Settings.", "/settings"));
        }
        return out;
    }

    // ---------- analytics ----------

    public record DayFlow(LocalDate date, int arrivals, int departures) {
    }

    public record TypeDemand(RoomType roomType, int roomNights, int capacityNights, double occupancy, int bookings) {
    }

    /** Rooms assigned for guests in house and today's arrivals, and how each assignment was made. */
    public record AssignmentStats(int requests, int assigned, double successRate, Map<String, Integer> byMethod) {
    }

    public record StatusCount(ReservationStatus status, long count) {
    }

    public record Analytics(List<TrendPoint> occupancy, List<DayFlow> flow, List<TypeDemand> demand,
                            AssignmentStats assignment, List<StatusCount> statuses, WatchlistService.Stats screening) {
    }

    public Analytics analytics(int pastDays, int futureDays) {
        int past = Math.min(Math.max(pastDays, 0), 90);
        int future = Math.min(Math.max(futureDays, 1), 90);
        LocalDate today = clock.today();
        List<TrendPoint> occupancy = trend(today.minusDays(past), today.plusDays(future));

        List<DayFlow> flow = new ArrayList<>();
        Set<ReservationStatus> counted = EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN,
                ReservationStatus.CHECKED_OUT);
        for (int d = -Math.min(past, 14); d < Math.min(future, 14); d++) {
            LocalDate day = today.plusDays(d);
            flow.add(new DayFlow(day, reservations.arrivingOn(day, counted).size(),
                    reservations.departingOn(day, counted).size()));
        }

        List<TypeDemand> demand = new ArrayList<>();
        int window = 30;
        Map<RoomType, int[]> nights = new EnumMap<>(RoomType.class);
        Map<RoomType, Integer> bookings = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) {
            nights.put(t, new int[1]);
            bookings.put(t, 0);
        }
        for (ReservationEntity r : reservations.findOverlapping(today, today.plusDays(window), STAYED)) {
            LocalDate a = r.getCheckIn().isBefore(today) ? today : r.getCheckIn();
            LocalDate b = r.getCheckOut().isAfter(today.plusDays(window)) ? today.plusDays(window) : r.getCheckOut();
            nights.get(r.getRoomType())[0] += (int) (b.toEpochDay() - a.toEpochDay()) * r.getRooms();
            bookings.merge(r.getRoomType(), 1, Integer::sum);
        }
        for (RoomType t : RoomType.values()) {
            int capacity = inventory.total(t) * window;
            demand.add(new TypeDemand(t, nights.get(t)[0], capacity,
                    capacity == 0 ? 0 : round1(100.0 * nights.get(t)[0] / capacity), bookings.get(t)));
        }

        // Room coverage where it matters now: guests in house and today's arrivals (current assignments only).
        List<ReservationEntity> current = new ArrayList<>(reservations.findByStatusIn(List.of(ReservationStatus.CHECKED_IN)));
        current.addAll(reservations.arrivingOn(today, List.of(ReservationStatus.CONFIRMED)));
        int reqCount = 0, assigned = 0;
        Map<String, Integer> byMethod = new LinkedHashMap<>();
        for (AssignmentMethod m : AssignmentMethod.values()) {
            byMethod.put(m.name(), 0);
        }
        if (!current.isEmpty()) {
            List<RoomRequestEntity> reqs = requests.findByReservationIn(current);
            reqCount = reqs.size();
            for (AssignmentEntity a : assignmentRepo.findByRequestInAndActiveTrue(reqs)) {
                assigned++;
                byMethod.merge(a.getMethod().name(), 1, Integer::sum);
            }
        }
        AssignmentStats stats = new AssignmentStats(reqCount, assigned,
                reqCount == 0 ? 0 : round1(100.0 * assigned / reqCount), byMethod);

        List<StatusCount> statuses = new ArrayList<>();
        for (ReservationStatus s : ReservationStatus.values()) {
            statuses.add(new StatusCount(s, reservations.countByStatus(s)));
        }
        return new Analytics(occupancy, flow, demand, stats, statuses, watchlist.stats());
    }

    public List<Activity> auditLog(int page, int size) {
        return auditRepo.findAllByOrderByAtDesc(org.springframework.data.domain.PageRequest.of(
                        Math.max(page, 0), Math.min(Math.max(size, 1), 200))).stream()
                .map(e -> new Activity(e.getAt(), e.getActor(), e.getAction(), e.getEntityRef(), e.getDetail()))
                .toList();
    }

    long roomCount() {
        return rooms.count();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
