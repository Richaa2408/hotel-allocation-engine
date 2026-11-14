package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.AuditEventEntity;
import com.hotel.engine.model.Enums.Housekeeping;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.model.Enums.Screening;
import com.hotel.engine.model.GuestEntity;
import com.hotel.engine.model.ReservationEntity;
import com.hotel.engine.model.RoomRequestEntity;
import com.hotel.engine.repository.AuditRepository;
import com.hotel.engine.repository.GuestRepository;
import com.hotel.engine.repository.ReservationRepository;
import com.hotel.engine.repository.RoomRequestRepository;
import com.hotel.engine.service.AssignmentService.RoomHistoryItem;
import com.hotel.engine.service.InventoryService.AvailabilityCheck;
import com.hotel.engine.service.WatchlistService.ScreeningResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The reservation lifecycle: create (screen, check, book), edit, cancel, approve a held booking,
 * check in, check out, and the waitlist.
 *
 * <p>Every change runs under {@link HotelLock} and in one database transaction. The segment trees are
 * changed inside the same locked step, and the whole step is undone (trees rebuilt from the database)
 * if the transaction fails, so the trees always match what was committed.
 */
@Service
public class ReservationService {

    static final Set<ReservationStatus> ACTIVE = EnumSet.of(ReservationStatus.CONFIRMED,
            ReservationStatus.PENDING_REVIEW, ReservationStatus.WAITLISTED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservations;
    private final GuestRepository guests;
    private final RoomRequestRepository requests;
    private final AuditRepository auditLog;
    private final InventoryService inventory;
    private final WatchlistService watchlist;
    private final AssignmentService assignments;
    private final AuditService audit;
    private final HotelLock lock;
    private final HotelClock clock;
    private final TransactionTemplate tx;

    public ReservationService(ReservationRepository reservations, GuestRepository guests,
                              RoomRequestRepository requests, AuditRepository auditLog, InventoryService inventory,
                              WatchlistService watchlist, AssignmentService assignments, AuditService audit,
                              HotelLock lock, HotelClock clock, PlatformTransactionManager txManager) {
        this.reservations = reservations;
        this.guests = guests;
        this.requests = requests;
        this.auditLog = auditLog;
        this.inventory = inventory;
        this.watchlist = watchlist;
        this.assignments = assignments;
        this.audit = audit;
        this.lock = lock;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- commands and results ----------

    public record NewReservation(String guestName, String email, String phone, RoomType roomType, LocalDate checkIn,
                                 LocalDate checkOut, int rooms, int adults, boolean needsAccessible, Integer maxFloor,
                                 Integer preferredFloor, boolean acceptsUpgrade, String notes, boolean joinWaitlist) {
    }

    /** Fields to change; null means "leave as is". */
    public record Changes(String guestName, RoomType roomType, LocalDate checkIn, LocalDate checkOut, Integer rooms,
                          Integer adults, Boolean needsAccessible, Integer maxFloor, Boolean clearMaxFloor,
                          Integer preferredFloor, Boolean clearPreferredFloor, Boolean acceptsUpgrade, String notes) {
    }

    public record Alternative(RoomType roomType, int fewestFree) {
    }

    /**
     * @param outcome BOOKED, WAITLISTED, HELD (watchlist match, waiting for a manager) or UNAVAILABLE (nothing saved)
     */
    public record CreateResult(String outcome, String message, ReservationView reservation,
                               AvailabilityCheck availability, ScreeningResult screening,
                               List<Alternative> alternatives) {
    }

    public record ReservationView(long id, String reference, String guestName, String email, String phone,
                                  RoomType roomType, LocalDate checkIn, LocalDate checkOut, int nights, int rooms,
                                  int adults, boolean needsAccessible, Integer maxFloor, Integer preferredFloor,
                                  boolean acceptsUpgrade, ReservationStatus status, Screening screening,
                                  String blockReason, String notes, int roomsAssigned, List<String> roomNumbers,
                                  String createdBy, Instant createdAt, Instant updatedAt) {
    }

    public record RequestView(long requestId, int seq, String roomNumber, Integer floor) {
    }

    public record HistoryEvent(Instant at, String actor, String action, String detail) {
    }

    public record ReservationDetail(ReservationView reservation, List<RequestView> rooms,
                                    List<RoomHistoryItem> roomHistory, List<HistoryEvent> history) {
    }

    // ---------- create ----------

    public CreateResult create(NewReservation cmd, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) {
            throw new InvalidInputException("Idempotency-Key must be at most 64 characters.");
        }
        return locked(() -> {
            if (key != null) {
                Optional<ReservationEntity> earlier = reservations.findByIdempotencyKey(key);
                if (earlier.isPresent()) {   // a retried submission: return the first result, create nothing
                    ReservationEntity r = earlier.get();
                    return new CreateResult(outcomeOf(r), "Already submitted as " + r.getReference() + ".",
                            view(r), null, null, List.of());
                }
            }
            String name = required(cmd.guestName(), "Enter the guest's name.");
            String emailKey = ContactKeys.email(cmd.email());
            String phoneKey = ContactKeys.phone(cmd.phone());
            if (emailKey == null && phoneKey == null) {
                throw new InvalidInputException("Enter an email or a phone number so the guest can be screened.");
            }
            validateDetails(cmd.adults(), cmd.rooms(), cmd.maxFloor(), cmd.preferredFloor());
            AvailabilityCheck availability = inventory.check(cmd.roomType(), cmd.checkIn(), cmd.checkOut(),
                    cmd.rooms(), cmd.needsAccessible());   // also validates the dates and room counts
            rejectDuplicate(emailKey, phoneKey, cmd.roomType(), cmd.checkIn(), cmd.checkOut());

            ScreeningResult screening = watchlist.screenKeys(emailKey, phoneKey);
            if (screening.matched()) {
                ReservationEntity r = save(cmd, name, emailKey, phoneKey, key, ReservationStatus.PENDING_REVIEW,
                        Screening.MATCH, screening.reason());
                audit.log("RESERVATION_HELD", "RESERVATION", r.getReference(),
                        r.getGuest().getFullName() + " matched the watchlist (" + screening.reason() + "). Held for review.");
                return new CreateResult("HELD", "Held for review: " + screening.message() + " Nothing was booked.",
                        view(r), availability, screening, List.of());
            }
            Screening screeningValue = screening.flaggedButCleared() ? Screening.CLEAR_AFTER_CHECK : Screening.CLEAR;

            if (availability.fits()) {
                ReservationEntity r = save(cmd, name, emailKey, phoneKey, key, ReservationStatus.CONFIRMED,
                        screeningValue, null);
                inventory.reserve(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                audit.log("RESERVATION_CREATED", "RESERVATION", r.getReference(), describe(r));
                return new CreateResult("BOOKED", "Booked. Reference " + r.getReference() + ".", view(r),
                        availability, screening, List.of());
            }

            List<Alternative> alternatives = alternatives(cmd);
            if (cmd.joinWaitlist()) {
                ReservationEntity r = save(cmd, name, emailKey, phoneKey, key, ReservationStatus.WAITLISTED,
                        screeningValue, null);
                audit.log("RESERVATION_WAITLISTED", "RESERVATION", r.getReference(), describe(r));
                return new CreateResult("WAITLISTED", "Added to the waitlist as " + r.getReference()
                        + ". It will be confirmed automatically if rooms free up.", view(r), availability, screening,
                        alternatives);
            }
            return new CreateResult("UNAVAILABLE", unavailableMessage(availability), null, availability, screening,
                    alternatives);
        });
    }

    private String unavailableMessage(AvailabilityCheck a) {
        String type = a.roomType().label().toLowerCase();
        if ("ACCESSIBLE_ROOMS".equals(a.limitedBy())) {
            return "Not available: only " + a.fewestAccessible() + " accessible " + type + " room"
                    + (a.fewestAccessible() == 1 ? "" : "s") + " free on " + a.busiestNight() + ", and "
                    + a.rooms() + " needed. Nothing was booked.";
        }
        return "Not available: only " + a.fewestFree() + " " + type + " room" + (a.fewestFree() == 1 ? "" : "s")
                + " free on " + a.busiestNight() + ", and " + a.rooms() + " needed. Nothing was booked.";
    }

    private List<Alternative> alternatives(NewReservation cmd) {
        List<Alternative> out = new ArrayList<>();
        for (RoomType other : RoomType.values()) {
            if (other == cmd.roomType()) {
                continue;
            }
            try {
                AvailabilityCheck c = inventory.check(other, cmd.checkIn(), cmd.checkOut(), cmd.rooms(),
                        cmd.needsAccessible());
                if (c.fits()) {
                    out.add(new Alternative(other, c.fewestFree()));
                }
            } catch (InvalidInputException e) {
                // fewer rooms of this type in total than requested: not an alternative
            }
        }
        return out;
    }

    private void rejectDuplicate(String emailKey, String phoneKey, RoomType type, LocalDate in, LocalDate out) {
        for (ReservationEntity r : reservations.sameStay(type, in, out, ACTIVE)) {
            GuestEntity g = r.getGuest();
            if ((emailKey != null && emailKey.equals(g.getEmailKey()))
                    || (phoneKey != null && phoneKey.equals(g.getPhoneKey()))) {
                throw new ConflictException("This looks like a duplicate of " + r.getReference() + " ("
                        + g.getFullName() + ", same room type and dates). Open that booking instead.");
            }
        }
    }

    private ReservationEntity save(NewReservation cmd, String name, String emailKey, String phoneKey,
                                   String idempotencyKey, ReservationStatus status, Screening screening,
                                   String blockReason) {
        GuestEntity guest = guests.save(new GuestEntity(name, blankToNull(cmd.email()), blankToNull(cmd.phone()),
                emailKey, phoneKey));
        ReservationEntity r = new ReservationEntity(guest, cmd.roomType(), cmd.checkIn(), cmd.checkOut(), cmd.rooms(),
                cmd.adults(), cmd.needsAccessible(), cmd.maxFloor(), cmd.preferredFloor(), cmd.acceptsUpgrade(),
                com.hotel.engine.security.CurrentUser.name());
        r.setStatus(status);
        r.setScreening(screening);
        r.setBlockReason(blockReason);
        r.setIdempotencyKey(idempotencyKey);
        r.setNotes(blankToNull(cmd.notes()));
        r = reservations.saveAndFlush(r);
        r.setReference(String.format("BK-%05d", r.getId()));
        for (int i = 1; i <= r.getRooms(); i++) {
            requests.save(new RoomRequestEntity(r, i));
        }
        return r;
    }

    // ---------- change ----------

    public ReservationView update(String reference, Changes c) {
        return locked(() -> {
            ReservationEntity r = find(reference);
            ReservationStatus status = r.getStatus();
            if (status == ReservationStatus.CANCELLED || status == ReservationStatus.CHECKED_OUT) {
                throw new ConflictException(r.getReference() + " is " + status.name().toLowerCase().replace('_', ' ')
                        + " and can't be changed.");
            }
            RoomType type = c.roomType() != null ? c.roomType() : r.getRoomType();
            LocalDate in = c.checkIn() != null ? c.checkIn() : r.getCheckIn();
            LocalDate out = c.checkOut() != null ? c.checkOut() : r.getCheckOut();
            int rooms = c.rooms() != null ? c.rooms() : r.getRooms();
            boolean acc = c.needsAccessible() != null ? c.needsAccessible() : r.isNeedsAccessible();
            Integer maxFloor = Boolean.TRUE.equals(c.clearMaxFloor()) ? null
                    : c.maxFloor() != null ? c.maxFloor() : r.getMaxFloor();
            Integer preferred = Boolean.TRUE.equals(c.clearPreferredFloor()) ? null
                    : c.preferredFloor() != null ? c.preferredFloor() : r.getPreferredFloor();
            int adults = c.adults() != null ? c.adults() : r.getAdults();
            validateDetails(adults, rooms, maxFloor, preferred);

            boolean stayChanged = type != r.getRoomType() || !in.equals(r.getCheckIn()) || !out.equals(r.getCheckOut())
                    || rooms != r.getRooms() || acc != r.isNeedsAccessible();
            boolean rulesChanged = stayChanged || !Objects.equals(maxFloor, r.getMaxFloor())
                    || (c.acceptsUpgrade() != null && c.acceptsUpgrade() != r.isAcceptsUpgrade());

            if (status == ReservationStatus.CHECKED_IN && (type != r.getRoomType() || !in.equals(r.getCheckIn())
                    || rooms != r.getRooms() || acc != r.isNeedsAccessible())) {
                throw new ConflictException("The guest has checked in: only the check-out date can change. "
                        + "Move rooms from Room Assignment instead.");
            }

            if (stayChanged && !status.holdsInventory()) {
                // Waitlisted or held: no rooms to move, but the stay must still be one that could be booked.
                inventory.validateChangedStay(type, in, out, rooms, acc, !in.equals(r.getCheckIn()));
            }

            if (stayChanged && status.holdsInventory()) {
                if (!out.isAfter(in)) {
                    throw new InvalidInputException("Check-out must be after check-in.");
                }
                if (status == ReservationStatus.CONFIRMED && !in.equals(r.getCheckIn()) && in.isBefore(clock.today())) {
                    throw new InvalidInputException("Check-in can't move into the past.");
                }
                inventory.release(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                if (!inventory.fits(type, in, out, rooms, acc)) {
                    inventory.reserve(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                    throw new ConflictException("No change made: there aren't enough " + type.label().toLowerCase()
                            + " rooms for the new stay. Check the inventory calendar for free dates.");
                }
                if (status == ReservationStatus.CHECKED_IN && out.isAfter(r.getCheckOut())) {
                    String clash = assignments.firstClashForExtension(r, out);
                    if (clash != null) {
                        inventory.reserve(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                        throw new ConflictException(clash);
                    }
                }
                inventory.reserve(type, in, out, rooms, acc);
            }

            String before = describe(r);
            if (rooms != r.getRooms()) {
                resizeRequests(r, rooms);
            }
            r.setRoomType(type);
            r.setCheckIn(in);
            r.setCheckOut(out);
            r.setRooms(rooms);
            r.setNeedsAccessible(acc);
            r.setMaxFloor(maxFloor);
            r.setPreferredFloor(preferred);
            r.setAdults(adults);
            if (c.acceptsUpgrade() != null) {
                r.setAcceptsUpgrade(c.acceptsUpgrade());
            }
            if (c.guestName() != null) {
                r.getGuest().setFullName(required(c.guestName(), "Enter the guest's name."));
            }
            if (c.notes() != null) {
                r.setNotes(blankToNull(c.notes()));
            }
            r.touch();
            if (rulesChanged && status == ReservationStatus.CONFIRMED) {
                assignments.releaseForReservation(r, "Stay changed; room needs to be assigned again");
            }
            reservations.save(r);
            audit.log("RESERVATION_CHANGED", "RESERVATION", r.getReference(), "Was: " + before + ". Now: " + describe(r));
            if (stayChanged && status.holdsInventory()) {
                promoteWaitlist();
            }
            return view(r);
        });
    }

    private void resizeRequests(ReservationEntity r, int rooms) {
        List<RoomRequestEntity> current = requests.findByReservationOrderBySeqAsc(r);
        for (int i = current.size() + 1; i <= rooms; i++) {
            requests.save(new RoomRequestEntity(r, i));
        }
        for (int i = current.size() - 1; i >= rooms; i--) {
            // The request's assignment rows reference it, so they go first; the audit line keeps the record.
            RoomRequestEntity removed = current.get(i);
            List<String> held = assignments.deleteRequestHistory(removed);
            requests.delete(removed);
            if (!held.isEmpty()) {
                audit.log("ROOM_REMOVED", "RESERVATION", r.getReference(), "Room " + removed.getSeq()
                        + " of the booking removed; it had been in room " + String.join(", then ", held));
            }
        }
    }

    public ReservationView cancel(String reference, String reason) {
        return locked(() -> {
            ReservationEntity r = find(reference);
            if (r.getStatus() == ReservationStatus.CANCELLED || r.getStatus() == ReservationStatus.CHECKED_OUT
                    || r.getStatus() == ReservationStatus.CHECKED_IN) {
                throw new ConflictException(r.getReference() + " is " + r.getStatus().name().toLowerCase().replace('_', ' ')
                        + (r.getStatus() == ReservationStatus.CHECKED_IN ? "; check the guest out instead." : " already."));
            }
            boolean held = r.getStatus().holdsInventory();
            if (held) {
                inventory.release(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
            }
            assignments.releaseForReservation(r, "Reservation cancelled");
            r.setStatus(ReservationStatus.CANCELLED);
            r.touch();
            reservations.save(r);
            audit.log("RESERVATION_CANCELLED", "RESERVATION", r.getReference(),
                    describe(r) + (reason == null || reason.isBlank() ? "" : ". Reason: " + reason.trim()));
            if (held) {
                promoteWaitlist();
            }
            return view(r);
        });
    }

    /** A manager clears a booking held by a watchlist match: confirm it if rooms are free, else waitlist it. */
    public ReservationView approve(String reference) {
        return locked(() -> {
            ReservationEntity r = find(reference);
            if (r.getStatus() != ReservationStatus.PENDING_REVIEW) {
                throw new ConflictException(r.getReference() + " isn't waiting for review.");
            }
            r.setScreening(Screening.APPROVED);
            if (!r.getCheckIn().isBefore(clock.today())
                    && inventory.fits(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible())) {
                inventory.reserve(r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                r.setStatus(ReservationStatus.CONFIRMED);
                audit.log("RESERVATION_APPROVED", "RESERVATION", r.getReference(), "Approved after review and confirmed");
            } else {
                r.setStatus(ReservationStatus.WAITLISTED);
                audit.log("RESERVATION_APPROVED", "RESERVATION", r.getReference(),
                        "Approved after review; no rooms free, so added to the waitlist");
            }
            r.touch();
            reservations.save(r);
            return view(r);
        });
    }

    public ReservationView checkIn(String reference) {
        return locked(() -> {
            ReservationEntity r = find(reference);
            LocalDate today = clock.today();
            if (r.getStatus() != ReservationStatus.CONFIRMED) {
                throw new ConflictException("Only confirmed reservations can check in; " + r.getReference() + " is "
                        + r.getStatus().name().toLowerCase().replace('_', ' ') + ".");
            }
            if (today.isBefore(r.getCheckIn()) || !today.isBefore(r.getCheckOut())) {
                throw new ConflictException(r.getReference() + " can check in from " + r.getCheckIn()
                        + " until the night before " + r.getCheckOut() + ".");
            }
            List<String> rooms = assignments.assignedRoomNumbers(r);
            if (rooms.size() < r.getRooms()) {
                throw new ConflictException("Assign " + (r.getRooms() - rooms.size()) + " more room"
                        + (r.getRooms() - rooms.size() == 1 ? "" : "s") + " before checking in. Use Room Assignment.");
            }
            assignments.setHousekeeping(r, Housekeeping.OCCUPIED);
            r.setStatus(ReservationStatus.CHECKED_IN);
            r.touch();
            reservations.save(r);
            audit.log("CHECKED_IN", "RESERVATION", r.getReference(), r.getGuest().getFullName() + " checked in to "
                    + String.join(", ", rooms));
            return view(r);
        });
    }

    public ReservationView checkOut(String reference) {
        return locked(() -> {
            ReservationEntity r = find(reference);
            if (r.getStatus() != ReservationStatus.CHECKED_IN) {
                throw new ConflictException(r.getReference() + " isn't checked in.");
            }
            LocalDate today = clock.today();
            boolean early = today.isBefore(r.getCheckOut());
            if (early) {   // the nights from tonight on are no longer needed
                inventory.release(r.getRoomType(), today, r.getCheckOut(), r.getRooms(), r.isNeedsAccessible());
                r.setCheckOut(today.isAfter(r.getCheckIn()) ? today : r.getCheckIn().plusDays(1));
            }
            assignments.setHousekeeping(r, Housekeeping.DIRTY);
            assignments.releaseForReservation(r, "Guest checked out");
            r.setStatus(ReservationStatus.CHECKED_OUT);
            r.touch();
            reservations.save(r);
            audit.log("CHECKED_OUT", "RESERVATION", r.getReference(), r.getGuest().getFullName() + " checked out"
                    + (early ? " early; remaining nights released" : ""));
            if (early) {
                promoteWaitlist();
            }
            return view(r);
        });
    }

    /** Confirms waitlisted bookings, oldest first, whose whole stay now fits. Caller holds the lock. */
    void promoteWaitlist() {
        LocalDate today = clock.today();
        for (ReservationEntity w : reservations.findByStatusOrderByCreatedAtAsc(ReservationStatus.WAITLISTED)) {
            if (w.getCheckIn().isBefore(today)) {
                continue;
            }
            if (inventory.fits(w.getRoomType(), w.getCheckIn(), w.getCheckOut(), w.getRooms(), w.isNeedsAccessible())) {
                inventory.reserve(w.getRoomType(), w.getCheckIn(), w.getCheckOut(), w.getRooms(), w.isNeedsAccessible());
                w.setStatus(ReservationStatus.CONFIRMED);
                w.touch();
                reservations.save(w);
                audit.log("WAITLIST_PROMOTED", "RESERVATION", w.getReference(),
                        "Rooms freed up: confirmed from the waitlist");
            }
        }
    }

    // ---------- reading ----------

    public Page<ReservationView> search(String q, ReservationStatus status, RoomType type, LocalDate from, LocalDate to,
                                        int page, int size, String sort) {
        Sort order = switch (sort == null ? "" : sort) {
            case "checkIn" -> Sort.by("checkIn").ascending().and(Sort.by("id"));
            case "-checkIn" -> Sort.by("checkIn").descending().and(Sort.by("id"));
            case "guest" -> Sort.by("guest.fullName").ascending();
            default -> Sort.by("createdAt").descending();
        };
        Specification<ReservationEntity> spec = ReservationRepository.filters(q, status, type, from, to);
        return reservations.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), order))
                .map(this::view);
    }

    public List<ReservationView> all(String q, ReservationStatus status, RoomType type, LocalDate from, LocalDate to) {
        Specification<ReservationEntity> spec = ReservationRepository.filters(q, status, type, from, to);
        return reservations.findAll(spec, Sort.by("checkIn", "id")).stream().map(this::view).toList();
    }

    public ReservationDetail detail(String reference) {
        ReservationEntity r = find(reference);
        List<RequestView> rooms = new ArrayList<>();
        for (RoomRequestEntity q : requests.findByReservationOrderBySeqAsc(r)) {
            var a = assignments.activeRoom(q);
            rooms.add(new RequestView(q.getId(), q.getSeq(), a == null ? null : a.getNumber(),
                    a == null ? null : a.getFloor()));
        }
        List<HistoryEvent> history = auditLog.findByEntityRefOrderByAtAsc(r.getReference()).stream()
                .map((AuditEventEntity e) -> new HistoryEvent(e.getAt(), e.getActor(), e.getAction(), e.getDetail()))
                .toList();
        return new ReservationDetail(view(r), rooms, assignments.history(r), history);
    }

    public ReservationView view(ReservationEntity r) {
        List<String> rooms = r.getStatus() == ReservationStatus.CANCELLED ? List.of() : assignments.assignedRoomNumbers(r);
        GuestEntity g = r.getGuest();
        return new ReservationView(r.getId(), r.getReference(), g.getFullName(), g.getEmail(), g.getPhone(),
                r.getRoomType(), r.getCheckIn(), r.getCheckOut(), r.nights(), r.getRooms(), r.getAdults(),
                r.isNeedsAccessible(), r.getMaxFloor(), r.getPreferredFloor(), r.isAcceptsUpgrade(), r.getStatus(),
                r.getScreening(), r.getBlockReason(), r.getNotes(), rooms.size(), rooms, r.getCreatedBy(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    ReservationEntity find(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new InvalidInputException("Booking reference is required.");
        }
        return reservations.findByReference(reference.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("No reservation " + reference.trim() + "."));
    }

    // ---------- helpers ----------

    /** Runs a change under the hotel lock in one transaction; on failure the trees are rebuilt from the database. */
    <T> T locked(Supplier<T> work) {
        return lock.call(() -> {
            try {
                return tx.execute(status -> work.get());
            } catch (RuntimeException e) {
                inventory.rebuild();   // undo any tree changes made before the failure
                throw e;
            }
        });
    }

    private static String outcomeOf(ReservationEntity r) {
        return switch (r.getStatus()) {
            case PENDING_REVIEW -> "HELD";
            case WAITLISTED -> "WAITLISTED";
            default -> "BOOKED";
        };
    }

    private static void validateDetails(int adults, int rooms, Integer maxFloor, Integer preferredFloor) {
        if (rooms < 1 || rooms > 20) {
            throw new InvalidInputException("Book between 1 and 20 rooms.");
        }
        if (adults < 1 || adults > rooms * 4) {
            throw new InvalidInputException("Adults must be between 1 and " + rooms * 4 + " for " + rooms + " room"
                    + (rooms == 1 ? "" : "s") + ".");
        }
        if (maxFloor != null && (maxFloor < 0 || maxFloor > 50)) {
            throw new InvalidInputException("Highest floor must be between 0 and 50.");
        }
        if (preferredFloor != null && (preferredFloor < 0 || preferredFloor > 50)) {
            throw new InvalidInputException("Preferred floor must be between 0 and 50.");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(message);
        }
        if (value.trim().length() > 100) {
            throw new InvalidInputException("Names can be at most 100 characters.");
        }
        return value.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String describe(ReservationEntity r) {
        return r.getGuest().getFullName() + ", " + r.getRooms() + " " + r.getRoomType().label().toLowerCase()
                + (r.getRooms() == 1 ? " room" : " rooms") + ", " + r.getCheckIn() + " to " + r.getCheckOut()
                + (r.isNeedsAccessible() ? ", accessible" : "");
    }
}
