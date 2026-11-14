package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.AssignmentEntity;
import com.hotel.engine.model.Enums.AssignmentMethod;
import com.hotel.engine.model.Enums.Housekeeping;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.MaintenanceBlockEntity;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.model.RoomRequestEntity;
import com.hotel.engine.repository.AssignmentRepository;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRepository;
import com.hotel.engine.repository.RoomRequestRepository;
import com.hotel.engine.security.CurrentUser;
import com.hotel.engine.service.AssignmentResult.ShortageGroup;
import com.hotel.engine.service.AssignmentResult.UnassignedGuest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Places room requests in physical rooms with maximum bipartite matching (Edmonds-Karp, via
 * {@link RoomAssignmentService}), and keeps the assignment history.
 *
 * <p>A room is a candidate for a request only if it meets the guest's hard rules (type, accessibility,
 * highest floor) and is free for the request's whole stay: no other active assignment overlapping those
 * nights and no maintenance block. Checking only the arrival night could put two stays in one room.
 */
@Service
public class AssignmentService {

    private static final java.time.format.DateTimeFormatter DAY =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH);

    static final Set<ReservationStatus> STAYING = EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);

    private final RoomAssignmentService matcher;
    private final RoomRepository rooms;
    private final RoomRequestRepository requests;
    private final AssignmentRepository assignmentRepo;
    private final ReservationRepository reservations;
    private final MaintenanceRepository blocks;
    private final AuditService audit;
    private final HotelLock lock;
    private final HotelClock clock;
    private final TransactionTemplate tx;

    public AssignmentService(RoomAssignmentService matcher, RoomRepository rooms, RoomRequestRepository requests,
                             AssignmentRepository assignmentRepo, ReservationRepository reservations,
                             MaintenanceRepository blocks, AuditService audit, HotelLock lock, HotelClock clock,
                             PlatformTransactionManager txManager) {
        this.matcher = matcher;
        this.rooms = rooms;
        this.requests = requests;
        this.assignmentRepo = assignmentRepo;
        this.reservations = reservations;
        this.blocks = blocks;
        this.audit = audit;
        this.lock = lock;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- views ----------

    /** One arriving room request on the board. hardRules never bend; preference is only a tie-breaker. */
    public record RequestCard(long requestId, String reference, int seq, int roomsInBooking, String guestName,
                              RoomType roomType, LocalDate checkIn, LocalDate checkOut, int nights, int adults,
                              String hardRules, String preference, boolean needsAccessible, Integer maxFloor,
                              Integer preferredFloor, boolean acceptsUpgrade, ReservationStatus status,
                              String roomNumber, String method) {
    }

    /**
     * @param state FREE, ASSIGNED (arriving tonight), OCCUPIED (in-house or another stay), OUT_OF_SERVICE (maintenance)
     */
    public record RoomTile(String number, RoomType type, int floor, boolean accessible, int capacity,
                           Housekeeping housekeeping, String state, String reference, String guestName,
                           LocalDate until, String blockReason) {
    }

    public record Board(LocalDate date, List<RequestCard> requests, List<RoomTile> rooms, int pending, int assigned,
                        int freeRooms) {
    }

    public record Unplaced(long requestId, String reference, String guestName, String hardRules, int suitableRooms,
                           String reason, String suggestion) {
    }

    public record Shortage(List<String> references, List<String> guestNames, List<String> roomNumbers, int shortage,
                           String summary) {
    }

    /**
     * @param placed         requests given a room by max flow in this run
     * @param firstFitPlaced how many the first-fit baseline would have placed on the same input
     */
    public record RunResult(LocalDate date, int requests, int placed, int firstFitPlaced, int candidateRooms,
                            int augmentingPaths, List<Unplaced> unplaced, List<Shortage> shortages, Board board,
                            String message) {
    }

    public record RoomHistoryItem(int seq, String roomNumber, int floor, AssignmentMethod method, String reason,
                                  String by, Instant assignedAt, Instant releasedAt, boolean active) {
    }

    public record LogItem(Instant at, String reference, String guestName, String roomNumber, AssignmentMethod method,
                          String reason, String by, boolean active) {
    }

    public record ReassignSummary(List<String> moved, List<String> unplaced) {
    }

    // ---------- board ----------

    public Board board(LocalDate date) {
        LocalDate day = date == null ? clock.today() : date;
        List<ReservationEntity> arriving = reservations.arrivingOn(day, STAYING);
        List<RoomRequestEntity> reqs = arriving.isEmpty() ? List.of() : requests.findByReservationIn(arriving);
        Map<Long, AssignmentEntity> active = new HashMap<>();
        for (AssignmentEntity a : reqs.isEmpty() ? List.<AssignmentEntity>of() : assignmentRepo.findByRequestInAndActiveTrue(reqs)) {
            active.put(a.getRequest().getId(), a);
        }
        List<RequestCard> cards = new ArrayList<>();
        reqs.stream().sorted(Comparator.comparing((RoomRequestEntity q) -> q.getReservation().getReference())
                        .thenComparingInt(RoomRequestEntity::getSeq))
                .forEach(q -> {
                    ReservationEntity r = q.getReservation();
                    AssignmentEntity a = active.get(q.getId());
                    cards.add(new RequestCard(q.getId(), r.getReference(), q.getSeq(), r.getRooms(),
                            r.getGuest().getFullName(), r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.nights(),
                            r.getAdults(), toGuest(q).needsSummary(),
                            r.getPreferredFloor() == null ? null : "Prefers floor " + r.getPreferredFloor(),
                            r.isNeedsAccessible(), r.getMaxFloor(), r.getPreferredFloor(), r.isAcceptsUpgrade(),
                            r.getStatus(), a == null ? null : a.getRoom().getNumber(),
                            a == null ? null : a.getMethod().name()));
                });

        // Room states for the night of `day`.
        Map<Long, AssignmentEntity> tonight = new HashMap<>();
        for (AssignmentEntity a : assignmentRepo.findActiveOverlapping(day, day.plusDays(1), STAYING)) {
            tonight.put(a.getRoom().getId(), a);
        }
        Map<Long, MaintenanceBlockEntity> blocked = new HashMap<>();
        for (MaintenanceBlockEntity b : blocks.findActiveOverlapping(day, day.plusDays(1))) {
            blocked.put(b.getRoom().getId(), b);
        }
        List<RoomTile> tiles = new ArrayList<>();
        int free = 0;
        for (RoomEntity room : rooms.findAllByOrderByFloorAscNumberAsc()) {
            AssignmentEntity a = tonight.get(room.getId());
            MaintenanceBlockEntity b = blocked.get(room.getId());
            String state;
            if (b != null) {
                state = "OUT_OF_SERVICE";
            } else if (a == null) {
                state = "FREE";
                free++;
            } else if (a.getRequest().getReservation().getCheckIn().equals(day)
                    && a.getRequest().getReservation().getStatus() == ReservationStatus.CONFIRMED) {
                state = "ASSIGNED";
            } else {
                state = "OCCUPIED";
            }
            ReservationEntity r = a == null ? null : a.getRequest().getReservation();
            tiles.add(new RoomTile(room.getNumber(), room.getType(), room.getFloor(), room.isAccessible(),
                    room.getCapacity(), room.getHousekeeping(), state, r == null ? null : r.getReference(),
                    r == null ? null : r.getGuest().getFullName(), r == null ? null : r.getCheckOut(),
                    b == null ? null : b.getReason()));
        }
        int assigned = (int) cards.stream().filter(c -> c.roomNumber() != null).count();
        return new Board(day, cards, tiles, cards.size() - assigned, assigned, free);
    }

    // ---------- automatic assignment ----------

    /** Assigns every arrival for `date` that has no room yet. Rooms already given out are kept. */
    public RunResult run(LocalDate date) {
        LocalDate day = date == null ? clock.today() : date;
        return locked(() -> {
            List<ReservationEntity> arriving = reservations.arrivingOn(day, EnumSet.of(ReservationStatus.CONFIRMED));
            List<RoomRequestEntity> pending = new ArrayList<>();
            if (!arriving.isEmpty()) {
                Set<Long> done = new java.util.HashSet<>();
                List<RoomRequestEntity> reqs = requests.findByReservationIn(arriving);
                assignmentRepo.findByRequestInAndActiveTrue(reqs).forEach(a -> done.add(a.getRequest().getId()));
                reqs.stream().filter(q -> !done.contains(q.getId())).forEach(pending::add);
            }
            if (pending.isEmpty()) {
                return new RunResult(day, 0, 0, 0, 0, 0, List.of(), List.of(), board(day),
                        arriving.isEmpty() ? "No confirmed arrivals on " + day.format(DAY) + "."
                                : "Every arrival on " + day.format(DAY) + " already has a room.");
            }
            Placement p = place(pending, AssignmentMethod.MAX_FLOW, null);
            audit.log("ASSIGNMENT_RUN", "ASSIGNMENT", day.toString(), "Max flow placed " + p.result.assignedCount()
                    + " of " + pending.size() + " requests; first-fit would have placed " + p.result.firstFitAssignedCount());
            String message = p.result.assignedCount() == pending.size()
                    ? "All " + pending.size() + " requests have a room."
                    : p.result.assignedCount() + " of " + pending.size() + " requests placed. "
                    + (pending.size() - p.result.assignedCount() == 1 ? "1 needs attention."
                    : (pending.size() - p.result.assignedCount()) + " need attention.");
            int paths = p.result.trace() == null ? p.result.assignedCount() : p.result.trace().steps().size();
            return new RunResult(day, pending.size(), p.result.assignedCount(), p.result.firstFitAssignedCount(),
                    p.candidateRooms, paths, p.unplaced, p.shortages, board(day), message);
        });
    }

    private record Placement(AssignmentResult result, int candidateRooms, List<Unplaced> unplaced,
                             List<Shortage> shortages) {
    }

    /**
     * Runs max flow for the given requests over all rooms, with room r allowed for request g only if r is
     * free for g's whole stay, then saves the matches. Caller holds the lock inside a transaction.
     */
    Placement place(List<RoomRequestEntity> requestsToPlace, AssignmentMethod method, String reason) {
        // Booking order (reservation id, then room within the booking), so first-fit and the max-flow
        // tie-breaks are repeatable: the database returns these rows in no fixed order.
        List<RoomRequestEntity> pending = requestsToPlace.stream()
                .sorted(Comparator.comparing((RoomRequestEntity q) -> q.getReservation().getId())
                        .thenComparingInt(RoomRequestEntity::getSeq))
                .toList();
        LocalDate from = pending.stream().map(q -> effectiveStart(q.getReservation())).min(LocalDate::compareTo).orElseThrow();
        LocalDate to = pending.stream().map(q -> q.getReservation().getCheckOut()).max(LocalDate::compareTo).orElseThrow();
        List<RoomEntity> allRooms = rooms.findAllByOrderByFloorAscNumberAsc();
        Occupancy occ = occupancy(from, to, Set.of());

        List<Guest> guests = pending.stream().map(this::toGuest).toList();
        List<Room> domainRooms = allRooms.stream().map(RoomEntity::toDomain).toList();
        boolean[] usable = new boolean[allRooms.size()];
        AssignmentResult result = matcher.assign(guests, domainRooms, (g, r) -> {
            RoomRequestEntity q = pending.get(g);
            boolean ok = occ.freeFor(allRooms.get(r), effectiveStart(q.getReservation()), q.getReservation().getCheckOut());
            usable[r] |= ok;
            return ok;
        });

        Map<String, RoomRequestEntity> byId = new HashMap<>();
        pending.forEach(q -> byId.put(String.valueOf(q.getId()), q));
        Map<String, RoomEntity> roomByNumber = new HashMap<>();
        allRooms.forEach(r -> roomByNumber.put(r.getNumber(), r));
        for (AssignmentResult.RoomAssignment m : result.assignments()) {
            RoomRequestEntity q = byId.get(m.guestId());
            RoomEntity room = roomByNumber.get(m.roomNumber());
            assignmentRepo.save(new AssignmentEntity(q, room, method,
                    reason != null ? reason : m.upgraded() ? "Upgraded to " + room.getType().label().toLowerCase() : null,
                    CurrentUser.name()));
            if (q.getReservation().getStatus() == ReservationStatus.CHECKED_IN) {
                room.setHousekeeping(Housekeeping.OCCUPIED);
            }
        }
        List<Unplaced> unplaced = new ArrayList<>();
        for (UnassignedGuest u : result.unassigned()) {
            RoomRequestEntity q = byId.get(u.guestId());
            unplaced.add(new Unplaced(q.getId(), q.getReservation().getReference(),
                    q.getReservation().getGuest().getFullName(), toGuest(q).needsSummary(), u.suitableRooms(),
                    u.reason(), u.suggestion()));
        }
        List<Shortage> shortages = new ArrayList<>();
        for (ShortageGroup s : result.shortages()) {
            shortages.add(new Shortage(
                    s.guestIds().stream().map(id -> byId.get(id).getReservation().getReference()).distinct().toList(),
                    s.guestNames(), s.roomNumbers(), s.shortage(), s.summary()));
        }
        int candidates = 0;
        for (boolean u : usable) {
            candidates += u ? 1 : 0;
        }
        return new Placement(result, candidates, unplaced, shortages);
    }

    private LocalDate effectiveStart(ReservationEntity r) {
        LocalDate today = clock.today();
        return r.getStatus() == ReservationStatus.CHECKED_IN && r.getCheckIn().isBefore(today) ? today : r.getCheckIn();
    }

    Guest toGuest(RoomRequestEntity q) {
        ReservationEntity r = q.getReservation();
        String name = r.getGuest().getFullName() + (r.getRooms() > 1 ? " (room " + q.getSeq() + ")" : "");
        return new Guest(String.valueOf(q.getId()), name, r.getRoomType(), r.isNeedsAccessible(), r.getMaxFloor(),
                r.isAcceptsUpgrade(), r.getPreferredFloor());
    }

    // ---------- manual changes ----------

    /** Puts a request in a specific room. Hard rules still apply; staff can't break accessibility to fill a room. */
    public Board assignManually(long requestId, String roomNumber) {
        return locked(() -> {
            RoomRequestEntity q = requests.findById(requestId)
                    .orElseThrow(() -> new NotFoundException("No room request " + requestId + "."));
            ReservationEntity r = q.getReservation();
            if (!STAYING.contains(r.getStatus())) {
                throw new ConflictException(r.getReference() + " isn't confirmed or checked in.");
            }
            RoomEntity room = rooms.findByNumber(roomNumber == null ? "" : roomNumber.trim())
                    .orElseThrow(() -> new NotFoundException("No room " + roomNumber + "."));
            Guest guest = toGuest(q);
            if (!guest.canStayIn(room.toDomain())) {
                throw new ConflictException("Room " + room.getNumber() + " doesn't meet " + r.getGuest().getFullName()
                        + "'s needs (" + guest.needsSummary() + ").");
            }
            LocalDate start = effectiveStart(r);
            Occupancy occ = occupancy(start, r.getCheckOut(), Set.of(q.getId()));
            if (!occ.freeFor(room, start, r.getCheckOut())) {
                throw new ConflictException("Room " + room.getNumber() + " isn't free for the whole stay ("
                        + start + " to " + r.getCheckOut() + ").");
            }
            AssignmentEntity current = assignmentRepo.findByRequestAndActiveTrue(q).orElse(null);
            if (current != null) {
                current.release();
                if (r.getStatus() == ReservationStatus.CHECKED_IN) {
                    current.getRoom().setHousekeeping(Housekeeping.DIRTY);
                }
            }
            assignmentRepo.save(new AssignmentEntity(q, room, AssignmentMethod.MANUAL,
                    current == null ? "Assigned by hand" : "Moved from room " + current.getRoom().getNumber(),
                    CurrentUser.name()));
            if (r.getStatus() == ReservationStatus.CHECKED_IN) {
                room.setHousekeeping(Housekeeping.OCCUPIED);
            }
            audit.log("ROOM_ASSIGNED", "RESERVATION", r.getReference(), "Room " + room.getNumber() + " assigned by hand"
                    + (current == null ? "" : " (was " + current.getRoom().getNumber() + ")"));
            return board(r.getCheckIn());
        });
    }

    public Board unassign(long requestId) {
        return locked(() -> {
            RoomRequestEntity q = requests.findById(requestId)
                    .orElseThrow(() -> new NotFoundException("No room request " + requestId + "."));
            if (q.getReservation().getStatus() == ReservationStatus.CHECKED_IN) {
                throw new ConflictException("The guest is in the room. Move them to another room instead.");
            }
            releaseRequest(q, "Unassigned by hand");
            audit.log("ROOM_UNASSIGNED", "RESERVATION", q.getReservation().getReference(), "Room taken off by hand");
            return board(q.getReservation().getCheckIn());
        });
    }

    /**
     * A room went out of service for [from, to): take every stay in it during those nights out of it and
     * place them again in other free rooms. Caller holds the lock inside a transaction.
     */
    ReassignSummary reassignAfterBlock(RoomEntity room, LocalDate from, LocalDate to, String why) {
        List<RoomRequestEntity> affected = new ArrayList<>();
        for (AssignmentEntity a : assignmentRepo.findByRoomAndActiveTrue(room)) {
            ReservationEntity r = a.getRequest().getReservation();
            if (STAYING.contains(r.getStatus()) && r.getCheckIn().isBefore(to) && r.getCheckOut().isAfter(from)) {
                a.release();
                affected.add(a.getRequest());
            }
        }
        assignmentRepo.flush();
        if (affected.isEmpty()) {
            return new ReassignSummary(List.of(), List.of());
        }
        Placement p = place(affected, AssignmentMethod.REASSIGNED, "Room " + room.getNumber() + " out of service: " + why);
        List<String> moved = new ArrayList<>();
        for (AssignmentResult.RoomAssignment m : p.result.assignments()) {
            RoomRequestEntity q = affected.stream().filter(x -> String.valueOf(x.getId()).equals(m.guestId())).findFirst().orElseThrow();
            moved.add(q.getReservation().getReference() + " moved from " + room.getNumber() + " to " + m.roomNumber());
            audit.log("ROOM_REASSIGNED", "RESERVATION", q.getReservation().getReference(),
                    "Moved from room " + room.getNumber() + " to " + m.roomNumber() + " (room out of service)");
        }
        List<String> unplaced = p.unplaced.stream()
                .map(u -> u.reference() + " (" + u.guestName() + ") has no room: " + u.reason()).toList();
        p.unplaced.forEach(u -> audit.log("ROOM_REASSIGN_FAILED", "RESERVATION", u.reference(),
                "Room " + room.getNumber() + " out of service and no other room fits: " + u.reason()));
        return new ReassignSummary(moved, unplaced);
    }

    // ---------- used by ReservationService (caller holds the lock) ----------

    void releaseForReservation(ReservationEntity r, String reason) {
        for (RoomRequestEntity q : requests.findByReservationOrderBySeqAsc(r)) {
            releaseRequest(q, reason);
        }
    }

    void releaseRequest(RoomRequestEntity q, String reason) {
        assignmentRepo.findByRequestAndActiveTrue(q).ifPresent(a -> {
            a.release();
            assignmentRepo.save(a);
        });
    }

    List<String> assignedRoomNumbers(ReservationEntity r) {
        List<RoomRequestEntity> reqs = requests.findByReservationOrderBySeqAsc(r);
        if (reqs.isEmpty()) {
            return List.of();
        }
        return assignmentRepo.findByRequestInAndActiveTrue(reqs).stream()
                .sorted(Comparator.comparingInt(a -> a.getRequest().getSeq()))
                .map(a -> a.getRoom().getNumber()).toList();
    }

    RoomEntity activeRoom(RoomRequestEntity q) {
        return assignmentRepo.findByRequestAndActiveTrue(q).map(AssignmentEntity::getRoom).orElse(null);
    }

    void setHousekeeping(ReservationEntity r, Housekeeping state) {
        List<RoomRequestEntity> reqs = requests.findByReservationOrderBySeqAsc(r);
        if (!reqs.isEmpty()) {
            assignmentRepo.findByRequestInAndActiveTrue(reqs).forEach(a -> a.getRoom().setHousekeeping(state));
        }
    }

    /** For a checked-in guest staying longer: the first assigned room that is taken on the extra nights, or null. */
    String firstClashForExtension(ReservationEntity r, LocalDate newCheckOut) {
        List<RoomRequestEntity> reqs = requests.findByReservationOrderBySeqAsc(r);
        Set<Long> own = new java.util.HashSet<>();
        reqs.forEach(q -> own.add(q.getId()));
        Occupancy occ = occupancy(r.getCheckOut(), newCheckOut, own);
        for (AssignmentEntity a : assignmentRepo.findByRequestInAndActiveTrue(reqs)) {
            if (!occ.freeFor(a.getRoom(), r.getCheckOut(), newCheckOut)) {
                return "Room " + a.getRoom().getNumber() + " is taken after " + r.getCheckOut()
                        + ". Move the guest to another room first, then extend the stay.";
            }
        }
        return null;
    }

    public List<RoomHistoryItem> history(ReservationEntity r) {
        return assignmentRepo.history(r).stream()
                .map(a -> new RoomHistoryItem(a.getRequest().getSeq(), a.getRoom().getNumber(), a.getRoom().getFloor(),
                        a.getMethod(), a.getReason(), a.getAssignedBy(), a.getAssignedAt(), a.getReleasedAt(),
                        a.isActive()))
                .toList();
    }

    public List<LogItem> recent() {
        return assignmentRepo.findTop50ByOrderByAssignedAtDesc().stream()
                .map(a -> new LogItem(a.getAssignedAt(), a.getRequest().getReservation().getReference(),
                        a.getRequest().getReservation().getGuest().getFullName(), a.getRoom().getNumber(),
                        a.getMethod(), a.getReason(), a.getAssignedBy(), a.isActive()))
                .toList();
    }

    // ---------- occupancy ----------

    /** Which rooms are taken on which nights in a date range, from assignments and maintenance blocks. */
    private final class Occupancy {
        private final Map<Long, List<LocalDate[]>> busy = new HashMap<>();

        boolean freeFor(RoomEntity room, LocalDate from, LocalDate to) {
            if (room.getHousekeeping() == Housekeeping.OUT_OF_SERVICE && !from.isAfter(clock.today())) {
                return false;
            }
            for (LocalDate[] span : busy.getOrDefault(room.getId(), List.of())) {
                if (span[0].isBefore(to) && span[1].isAfter(from)) {
                    return false;
                }
            }
            return true;
        }

        void add(Long roomId, LocalDate from, LocalDate to) {
            busy.computeIfAbsent(roomId, k -> new ArrayList<>()).add(new LocalDate[]{from, to});
        }
    }

    /** Busy spans overlapping [from, to), ignoring the requests in `except` (when moving a request). */
    private Occupancy occupancy(LocalDate from, LocalDate to, Set<Long> except) {
        Occupancy occ = new Occupancy();
        for (AssignmentEntity a : assignmentRepo.findActiveOverlapping(from, to, STAYING)) {
            if (!except.contains(a.getRequest().getId())) {
                ReservationEntity r = a.getRequest().getReservation();
                occ.add(a.getRoom().getId(), r.getCheckIn(), r.getCheckOut());
            }
        }
        for (MaintenanceBlockEntity b : blocks.findActiveOverlapping(from, to)) {
            occ.add(b.getRoom().getId(), b.getStartDate(), b.getEndDate());
        }
        return occ;
    }

    private <T> T locked(Supplier<T> work) {
        return lock.call(() -> tx.execute(status -> work.get()));
    }

    /** Assigns specific requests (seeding in-house guests); caller handles locking and transactions. */
    public int assignForSeed(List<RoomRequestEntity> pending) {
        if (pending.isEmpty()) {
            return 0;
        }
        return place(pending, AssignmentMethod.MAX_FLOW, null).result.assignedCount();
    }

    static void requireDate(LocalDate d) {
        if (d == null) {
            throw new InvalidInputException("Choose a date.");
        }
    }
}
