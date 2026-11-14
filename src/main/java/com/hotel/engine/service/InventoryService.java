package com.hotel.engine.service;

import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree;
import com.hotel.engine.algorithm.segtree.NodeVisit;
import com.hotel.engine.algorithm.segtree.SegmentTreeTrace;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.MaintenanceBlockEntity;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How many rooms of each type can still be sold on each night, kept in segment trees.
 *
 * <p>Each room type has two trees over the next {@value #HORIZON} nights:
 * <ul>
 *   <li><b>all</b>: rooms of the type minus rooms out of service minus rooms booked;</li>
 *   <li><b>accessible</b>: accessible rooms of the type minus those out of service minus rooms booked
 *       by guests who need accessibility.</li>
 * </ul>
 * A booking that needs accessibility must fit both; any other booking only the first. Accessibility is a
 * feature of a room, not a separate type, so no room is counted twice. If accessible bookings never exceed
 * accessible rooms and all bookings never exceed all rooms, every night has a full room assignment
 * (Hall's condition: accessible guests take accessible rooms, everyone else fits in the rest).
 *
 * <p>The trees are rebuilt from the database at startup and when the date changes; every change in
 * between goes through {@link HotelLock} together with its database transaction.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public static final int HORIZON = 400;
    /** "Tue 17 Nov" for messages staff read. */
    static final java.time.format.DateTimeFormatter NIGHT =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH);
    static final Set<ReservationStatus> HOLDING = Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);

    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final MaintenanceRepository blocks;
    private final HotelClock clock;

    private LocalDate start;
    private final Map<RoomType, AvailabilitySegmentTree> all = new EnumMap<>(RoomType.class);
    private final Map<RoomType, AvailabilitySegmentTree> accessible = new EnumMap<>(RoomType.class);
    private final Map<RoomType, Integer> totals = new EnumMap<>(RoomType.class);
    private final Map<RoomType, Integer> accessibleTotals = new EnumMap<>(RoomType.class);

    public InventoryService(RoomRepository rooms, ReservationRepository reservations, MaintenanceRepository blocks,
                            HotelClock clock) {
        this.rooms = rooms;
        this.reservations = reservations;
        this.blocks = blocks;
        this.clock = clock;
    }

    // ---------- results ----------

    public record NightCount(LocalDate night, int free) {
    }

    /**
     * @param fits               true if every night has enough rooms (and enough accessible rooms, if needed)
     * @param fewestFree         smallest free count over the stay, from the range-minimum query
     * @param busiestNight       first night with that count
     * @param fewestAccessible   smallest free accessible count, when accessibility is needed; otherwise null
     * @param limitedBy          ROOMS or ACCESSIBLE_ROOMS when it doesn't fit; null when it fits
     * @param freeByNight        free rooms on each night of the stay (for the chart)
     * @param trace              the range-minimum query on the type's tree, for "explain this"
     */
    public record AvailabilityCheck(RoomType roomType, LocalDate checkIn, LocalDate checkOut, int nights, int rooms,
                                    boolean needsAccessible, boolean fits, int fewestFree, LocalDate busiestNight,
                                    Integer fewestAccessible, String limitedBy, List<NightCount> freeByNight,
                                    SegmentTreeTrace trace, int nodesVisited, int fullNodes) {
    }

    public record HeatCell(LocalDate night, int total, int free, int booked, int blocked, String level) {
    }

    public record HeatRow(RoomType roomType, int total, int accessibleTotal, List<HeatCell> cells) {
    }

    public record Alert(RoomType roomType, LocalDate night, int free, int total, String level, String message) {
    }

    // ---------- building ----------

    /** Recomputes every tree from rooms, maintenance blocks and reservations. O(R + B*n + S*n). */
    public synchronized void rebuild() {
        start = clock.today();
        LocalDate end = start.plusDays(HORIZON);
        Map<RoomType, int[]> free = new EnumMap<>(RoomType.class);
        Map<RoomType, int[]> freeAcc = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) {
            int total = (int) rooms.countByType(t);
            int acc = (int) rooms.countByTypeAndAccessibleTrue(t);
            totals.put(t, total);
            accessibleTotals.put(t, acc);
            int[] f = new int[HORIZON];
            Arrays.fill(f, total);
            int[] fa = new int[HORIZON];
            Arrays.fill(fa, acc);
            free.put(t, f);
            freeAcc.put(t, fa);
        }
        for (MaintenanceBlockEntity b : blocks.findActiveOverlapping(start, end)) {
            RoomEntity room = b.getRoom();
            forEachNight(b.getStartDate(), b.getEndDate(), i -> {
                free.get(room.getType())[i]--;
                if (room.isAccessible()) {
                    freeAcc.get(room.getType())[i]--;
                }
            });
        }
        for (ReservationEntity r : reservations.findOverlapping(start, end, HOLDING)) {
            forEachNight(r.getCheckIn(), r.getCheckOut(), i -> {
                free.get(r.getRoomType())[i] -= r.getRooms();
                if (r.isNeedsAccessible()) {
                    freeAcc.get(r.getRoomType())[i] -= r.getRooms();
                }
            });
        }
        for (RoomType t : RoomType.values()) {
            all.put(t, new AvailabilitySegmentTree(clampNegative(t, free.get(t))));
            accessible.put(t, new AvailabilitySegmentTree(clampNegative(t, freeAcc.get(t))));
        }
    }

    private int[] clampNegative(RoomType type, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0) {
                log.warn("{} oversold by {} on {}; treating as 0 free", type, -values[i], start.plusDays(i));
                values[i] = 0;
            }
        }
        return values;
    }

    private void forEachNight(LocalDate from, LocalDate to, java.util.function.IntConsumer action) {
        int a = (int) Math.max(0, from.toEpochDay() - start.toEpochDay());
        int b = (int) Math.min(HORIZON, to.toEpochDay() - start.toEpochDay());
        for (int i = a; i < b; i++) {
            action.accept(i);
        }
    }

    private void ensureCurrent() {
        if (start == null || !start.equals(clock.today())) {
            rebuild();
        }
    }

    // ---------- queries ----------

    /** Validates a new stay and answers whether it fits. */
    public synchronized AvailabilityCheck check(RoomType type, LocalDate checkIn, LocalDate checkOut, int roomsWanted,
                                                boolean needsAccessible) {
        ensureCurrent();
        validateStay(type, checkIn, checkOut, roomsWanted, needsAccessible, true);
        int[] range = range(checkIn, checkOut);
        SegmentTreeTrace q = all.get(type).queryMin(range[0], range[1]);
        int fewest = q.value();
        Integer fewestAcc = needsAccessible ? accessible.get(type).minOver(range[0], range[1]) : null;
        boolean roomsOk = fewest >= roomsWanted;
        boolean accOk = !needsAccessible || fewestAcc >= roomsWanted;
        String limitedBy = !roomsOk ? "ROOMS" : !accOk ? "ACCESSIBLE_ROOMS" : null;

        List<NightCount> perNight = new ArrayList<>();
        LocalDate busiest = null;
        int busiestValue = Integer.MAX_VALUE;
        AvailabilitySegmentTree tree = needsAccessible && roomsOk && !accOk ? accessible.get(type) : all.get(type);
        for (int i = range[0]; i <= range[1]; i++) {
            int value = all.get(type).valueAt(i);
            perNight.add(new NightCount(start.plusDays(i), value));
            int limiting = tree.valueAt(i);
            if (limiting < busiestValue) {
                busiestValue = limiting;
                busiest = start.plusDays(i);
            }
        }
        int full = (int) q.visits().stream().filter(v -> v.kind() == NodeVisit.Kind.FULL).count();
        return new AvailabilityCheck(type, checkIn, checkOut, range[1] - range[0] + 1, roomsWanted, needsAccessible,
                roomsOk && accOk, fewest, busiest, fewestAcc, limitedBy, perNight, q, q.visits().size(), full);
    }

    /**
     * Whether `roomCount` more rooms fit on every night of [from, to) inside the window, without the
     * new-booking date rules (used when changing a stay that has already started).
     */
    public synchronized boolean fits(RoomType type, LocalDate from, LocalDate to, int roomCount, boolean needsAccessible) {
        ensureCurrent();
        LocalDate a = from.isBefore(start) ? start : from;
        if (!a.isBefore(to)) {
            return true;
        }
        if (to.isAfter(start.plusDays(HORIZON))) {
            return false;
        }
        int[] range = range(a, to);
        return all.get(type).minOver(range[0], range[1]) >= roomCount
                && (!needsAccessible || accessible.get(type).minOver(range[0], range[1]) >= roomCount);
    }

    public synchronized int freeOn(RoomType type, LocalDate night) {
        ensureCurrent();
        int i = (int) (night.toEpochDay() - start.toEpochDay());
        return i < 0 || i >= HORIZON ? 0 : all.get(type).valueAt(i);
    }

    public synchronized int total(RoomType type) {
        ensureCurrent();
        return totals.get(type);
    }

    public synchronized int accessibleTotal(RoomType type) {
        ensureCurrent();
        return accessibleTotals.get(type);
    }

    /** Type x night grid for the inventory calendar. */
    public synchronized List<HeatRow> heatmap(LocalDate from, int days) {
        ensureCurrent();
        if (days < 1 || days > 120) {
            throw new InvalidInputException("Show between 1 and 120 days.");
        }
        LocalDate first = from == null || from.isBefore(start) ? start : from;
        if (first.plusDays(days).isAfter(start.plusDays(HORIZON))) {
            first = start.plusDays(HORIZON - days);
        }
        LocalDate last = first.plusDays(days);
        Map<RoomType, int[]> blocked = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) {
            blocked.put(t, new int[days]);
        }
        for (MaintenanceBlockEntity b : blocks.findActiveOverlapping(first, last)) {
            for (int d = 0; d < days; d++) {
                if (b.covers(first.plusDays(d))) {
                    blocked.get(b.getRoom().getType())[d]++;
                }
            }
        }
        List<HeatRow> rows = new ArrayList<>();
        for (RoomType t : RoomType.values()) {
            int total = totals.get(t);
            List<HeatCell> cells = new ArrayList<>();
            for (int d = 0; d < days; d++) {
                LocalDate night = first.plusDays(d);
                int free = all.get(t).valueAt((int) (night.toEpochDay() - start.toEpochDay()));
                int out = blocked.get(t)[d];
                cells.add(new HeatCell(night, total, free, Math.max(0, total - out - free), out, level(free, total)));
            }
            rows.add(new HeatRow(t, total, accessibleTotals.get(t), cells));
        }
        return rows;
    }

    /** Sold-out and low-availability nights in the next `days` nights. */
    public synchronized List<Alert> alerts(int days) {
        ensureCurrent();
        List<Alert> out = new ArrayList<>();
        for (RoomType t : RoomType.values()) {
            int total = totals.get(t);
            for (int i = 0; i < Math.min(days, HORIZON); i++) {
                int free = all.get(t).valueAt(i);
                String level = level(free, total);
                if (!level.equals("HEALTHY")) {
                    LocalDate night = start.plusDays(i);
                    String when = night.format(NIGHT);
                    out.add(new Alert(t, night, free, total, level, level.equals("SOLD_OUT")
                            ? t.label() + " rooms are sold out on " + when + "."
                            : "Only " + free + " of " + total + " " + t.label().toLowerCase() + " rooms left on " + when + "."));
                }
            }
        }
        return out;
    }

    /** SOLD_OUT at 0, LOW at 10% or less (at least 1 room), otherwise HEALTHY. */
    static String level(int free, int total) {
        if (free <= 0) {
            return "SOLD_OUT";
        }
        return free <= Math.max(1, Math.round(total * 0.10f)) ? "LOW" : "HEALTHY";
    }

    // ---------- changes (callers hold HotelLock) ----------

    /** Takes `rooms` rooms on every night of the stay that falls inside the window. */
    public synchronized SegmentTreeTrace reserve(RoomType type, LocalDate checkIn, LocalDate checkOut, int roomCount,
                                                 boolean needsAccessible) {
        return apply(type, checkIn, checkOut, -roomCount, needsAccessible);
    }

    /** Gives the rooms back (cancellation, early check-out, edit). */
    public synchronized SegmentTreeTrace release(RoomType type, LocalDate checkIn, LocalDate checkOut, int roomCount,
                                                 boolean needsAccessible) {
        return apply(type, checkIn, checkOut, roomCount, needsAccessible);
    }

    private SegmentTreeTrace apply(RoomType type, LocalDate from, LocalDate to, int delta, boolean needsAccessible) {
        ensureCurrent();
        LocalDate a = from.isBefore(start) ? start : from;
        LocalDate b = to.isAfter(start.plusDays(HORIZON)) ? start.plusDays(HORIZON) : to;
        if (!a.isBefore(b)) {
            return null;   // entirely in the past or beyond the window
        }
        int[] range = range(a, b);
        SegmentTreeTrace trace = all.get(type).rangeAdd(range[0], range[1], delta);
        if (needsAccessible) {
            accessible.get(type).add(range[0], range[1], delta);
        }
        return trace;
    }

    /** Whether one more room of this type can go out of service on every night of [from, to). */
    public synchronized boolean canBlock(RoomEntity room, LocalDate from, LocalDate to) {
        ensureCurrent();
        LocalDate a = from.isBefore(start) ? start : from;
        if (!a.isBefore(to)) {
            return true;
        }
        int[] range = range(a, to.isAfter(start.plusDays(HORIZON)) ? start.plusDays(HORIZON) : to);
        return all.get(room.getType()).minOver(range[0], range[1]) >= 1
                && (!room.isAccessible() || accessible.get(room.getType()).minOver(range[0], range[1]) >= 1);
    }

    public synchronized void block(RoomEntity room, LocalDate from, LocalDate to) {
        apply(room.getType(), from, to, -1, room.isAccessible());
    }

    public synchronized void unblock(RoomEntity room, LocalDate from, LocalDate to) {
        apply(room.getType(), from, to, 1, room.isAccessible());
    }

    // ---------- helpers ----------

    /**
     * The date and room-count rules for a changed stay that holds no inventory (waitlisted or held for
     * review): check-out after check-in, inside the booking window, enough rooms of the type exist, and
     * a check-in that was moved can't be in the past.
     */
    public synchronized void validateChangedStay(RoomType type, LocalDate checkIn, LocalDate checkOut, int roomsWanted,
                                                 boolean needsAccessible, boolean checkInMoved) {
        ensureCurrent();
        validateStay(type, checkIn, checkOut, roomsWanted, needsAccessible, checkInMoved);
    }

    void validateStay(RoomType type, LocalDate checkIn, LocalDate checkOut, int roomsWanted, boolean needsAccessible,
                      boolean newStay) {
        if (type == null) {
            throw new InvalidInputException("Choose a room type.");
        }
        if (checkIn == null || checkOut == null) {
            throw new InvalidInputException("Check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidInputException("Check-out must be after check-in.");
        }
        if (newStay && checkIn.isBefore(start)) {
            throw new InvalidInputException("Check-in can't be in the past.");
        }
        if (checkOut.isAfter(start.plusDays(HORIZON))) {
            throw new InvalidInputException("Bookings can be taken up to " + start.plusDays(HORIZON) + ".");
        }
        if (roomsWanted < 1) {
            throw new InvalidInputException("Ask for at least 1 room.");
        }
        int total = totals.get(type);
        if (roomsWanted > total) {
            throw new InvalidInputException("The hotel has only " + total + " " + type.label().toLowerCase()
                    + " room" + (total == 1 ? "" : "s") + " in total.");
        }
        if (needsAccessible && roomsWanted > accessibleTotals.get(type)) {
            throw new InvalidInputException("The hotel has only " + accessibleTotals.get(type) + " accessible "
                    + type.label().toLowerCase() + " room" + (accessibleTotals.get(type) == 1 ? "" : "s") + ".");
        }
    }

    private int[] range(LocalDate checkIn, LocalDate checkOut) {
        int a = (int) (checkIn.toEpochDay() - start.toEpochDay());
        int b = (int) (checkOut.toEpochDay() - start.toEpochDay()) - 1;
        return new int[]{a, b};
    }

    public synchronized LocalDate windowStart() {
        ensureCurrent();
        return start;
    }
}
