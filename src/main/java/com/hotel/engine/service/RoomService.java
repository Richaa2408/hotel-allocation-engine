package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.Housekeeping;
import com.hotel.engine.model.RoomEntity;
import com.hotel.engine.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Physical rooms: listing, housekeeping status and CSV import. */
@Service
public class RoomService {

    private final RoomRepository rooms;
    private final InventoryService inventory;
    private final ReservationService reservations;
    private final AuditService audit;

    public RoomService(RoomRepository rooms, InventoryService inventory, ReservationService reservations,
                       AuditService audit) {
        this.rooms = rooms;
        this.inventory = inventory;
        this.reservations = reservations;
        this.audit = audit;
    }

    public record ImportResult(int added, int skipped, List<String> problems) {
    }

    public void setHousekeeping(String number, Housekeeping state) {
        if (state == null) {
            throw new InvalidInputException("Choose a housekeeping status.");
        }
        reservations.locked(() -> {
            RoomEntity room = rooms.findByNumber(number).orElseThrow(() -> new NotFoundException("No room " + number + "."));
            Housekeeping before = room.getHousekeeping();
            room.setHousekeeping(state);
            rooms.save(room);
            audit.log("HOUSEKEEPING", "ROOM", room.getNumber(), before + " to " + state);
            return null;
        });
    }

    /**
     * Adds rooms from CSV lines "number,type,floor,accessible,capacity" (a header line is skipped).
     * Existing room numbers are skipped; the inventory trees are rebuilt afterwards.
     */
    public ImportResult importCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new InvalidInputException("The file is empty.");
        }
        return reservations.locked(() -> {
            int added = 0;
            int skipped = 0;
            List<String> problems = new ArrayList<>();
            String[] lines = csv.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || (i == 0 && line.toLowerCase().startsWith("number"))) {
                    continue;
                }
                String[] f = line.split(",", -1);
                try {
                    if (f.length < 5) {
                        throw new InvalidInputException("needs 5 columns: number,type,floor,accessible,capacity");
                    }
                    String number = f[0].trim();
                    if (number.isEmpty() || number.length() > 10) {
                        throw new InvalidInputException("room number must be 1 to 10 characters");
                    }
                    RoomType type = RoomType.valueOf(f[1].trim().toUpperCase());
                    int floor = Integer.parseInt(f[2].trim());
                    boolean accessible = Boolean.parseBoolean(f[3].trim()) || f[3].trim().equalsIgnoreCase("yes");
                    int capacity = Integer.parseInt(f[4].trim());
                    if (floor < 0 || floor > 50 || capacity < 1 || capacity > 10) {
                        throw new InvalidInputException("floor must be 0 to 50 and capacity 1 to 10");
                    }
                    if (rooms.findByNumber(number).isPresent()) {
                        skipped++;
                        continue;
                    }
                    rooms.save(new RoomEntity(number, type, floor, accessible, capacity));
                    added++;
                } catch (InvalidInputException e) {
                    problems.add("Line " + (i + 1) + ": " + e.getMessage());
                } catch (IllegalArgumentException e) {   // includes NumberFormatException and bad enum names
                    problems.add("Line " + (i + 1) + ": couldn't read \"" + line + "\"");
                }
            }
            rooms.flush();
            inventory.rebuild();
            audit.log("ROOMS_IMPORTED", "ROOM", null, added + " rooms added, " + skipped + " already existed, "
                    + problems.size() + " lines skipped");
            return new ImportResult(added, skipped, problems);
        });
    }

    public RoomEntity find(String number) {
        return rooms.findByNumber(number).orElseThrow(() -> new NotFoundException("No room " + number + "."));
    }
}
