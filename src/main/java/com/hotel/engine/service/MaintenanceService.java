package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.MaintenanceBlockEntity;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.repository.MaintenanceRepository;
import com.hotel.engine.repository.RoomRepository;
import com.hotel.engine.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rooms out of service for a date range. Blocking a room is a range update of -1 on its type's segment
 * tree (and the accessible tree, for an accessible room), refused if it would leave fewer rooms than are
 * already booked. Guests assigned to the room for those nights are moved to other rooms automatically.
 */
@Service
public class MaintenanceService {

    private final MaintenanceRepository blocks;
    private final RoomRepository rooms;
    private final InventoryService inventory;
    private final AssignmentService assignments;
    private final ReservationService reservations;
    private final AuditService audit;
    private final HotelClock clock;

    public MaintenanceService(MaintenanceRepository blocks, RoomRepository rooms, InventoryService inventory,
                              AssignmentService assignments, ReservationService reservations, AuditService audit,
                              HotelClock clock) {
        this.blocks = blocks;
        this.rooms = rooms;
        this.inventory = inventory;
        this.assignments = assignments;
        this.reservations = reservations;
        this.audit = audit;
        this.clock = clock;
    }

    public record BlockView(long id, String roomNumber, RoomType roomType, int floor, boolean accessible,
                            LocalDate startDate, LocalDate endDate, int nights, String reason, String createdBy,
                            Instant createdAt) {
        static BlockView of(MaintenanceBlockEntity b) {
            RoomEntity r = b.getRoom();
            return new BlockView(b.getId(), r.getNumber(), r.getType(), r.getFloor(), r.isAccessible(),
                    b.getStartDate(), b.getEndDate(), (int) (b.getEndDate().toEpochDay() - b.getStartDate().toEpochDay()),
                    b.getReason(), b.getCreatedBy(), b.getCreatedAt());
        }
    }

    /**
     * @param moved    guests moved to another room
     * @param unplaced guests who could not be moved (staff must call them)
     */
    public record BlockResult(BlockView block, List<String> moved, List<String> unplaced) {
    }

    public List<BlockView> list() {
        LocalDate today = clock.today();
        return blocks.findByActiveTrueOrderByStartDateAsc().stream()
                .filter(b -> b.getEndDate().isAfter(today))
                .map(BlockView::of).toList();
    }

    public BlockResult create(String roomNumber, LocalDate from, LocalDate to, String reason) {
        return reservations.locked(() -> {
            RoomEntity room = rooms.findByNumber(roomNumber == null ? "" : roomNumber.trim())
                    .orElseThrow(() -> new NotFoundException("No room " + roomNumber + "."));
            if (from == null || to == null || !to.isAfter(from)) {
                throw new InvalidInputException("The end date must be after the start date.");
            }
            if (from.isBefore(clock.today())) {
                throw new InvalidInputException("Maintenance can't start in the past.");
            }
            if (reason == null || reason.isBlank()) {
                throw new InvalidInputException("Give a reason, for example \"Bathroom leak repair\".");
            }
            for (MaintenanceBlockEntity b : blocks.findActiveOverlapping(from, to)) {
                if (b.getRoom().getId().equals(room.getId())) {
                    throw new ConflictException("Room " + room.getNumber() + " is already out of service from "
                            + b.getStartDate() + " to " + b.getEndDate() + ".");
                }
            }
            if (!inventory.canBlock(room, from, to)) {
                throw new ConflictException("Can't take room " + room.getNumber() + " out of service: every "
                        + (room.isAccessible() ? "accessible " : "") + room.getType().label().toLowerCase()
                        + " room is already booked on at least one of those nights.");
            }
            MaintenanceBlockEntity block = blocks.save(new MaintenanceBlockEntity(room, from, to, reason.trim(),
                    CurrentUser.name()));
            inventory.block(room, from, to);
            AssignmentService.ReassignSummary moved = assignments.reassignAfterBlock(room, from, to, reason.trim());
            audit.log("MAINTENANCE_ADDED", "ROOM", room.getNumber(), "Out of service " + from + " to " + to + ": "
                    + reason.trim() + (moved.moved().isEmpty() ? "" : ". " + moved.moved().size() + " guest(s) moved"));
            return new BlockResult(BlockView.of(block), moved.moved(), moved.unplaced());
        });
    }

    public void remove(long id) {
        reservations.locked(() -> {
            MaintenanceBlockEntity b = blocks.findById(id)
                    .orElseThrow(() -> new NotFoundException("No maintenance block " + id + "."));
            if (!b.isActive()) {
                throw new ConflictException("That block was already removed.");
            }
            b.setActive(false);
            blocks.save(b);
            inventory.unblock(b.getRoom(), b.getStartDate(), b.getEndDate());
            audit.log("MAINTENANCE_REMOVED", "ROOM", b.getRoom().getNumber(),
                    "Back in service (was blocked " + b.getStartDate() + " to " + b.getEndDate() + ")");
            reservations.promoteWaitlist();
            return null;
        });
    }
}
