package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.service.ReservationService.CreateResult;
import com.hotel.engine.service.ReservationService.NewReservation;
import com.hotel.engine.service.ReservationService.ReservationView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reservations to and from CSV. Each imported row goes through the normal booking pipeline. */
@Service
public class ReservationCsvService {

    static final String[] COLUMNS = {"guestName", "email", "phone", "roomType", "checkIn", "checkOut", "rooms",
            "adults", "needsAccessible", "maxFloor", "preferredFloor", "acceptsUpgrade", "notes"};

    private final ReservationService reservations;

    public ReservationCsvService(ReservationService reservations) {
        this.reservations = reservations;
    }

    public record RowResult(int line, String guestName, String outcome, String reference, String message) {
    }

    public record ImportResult(int rows, int booked, int waitlisted, int held, int unavailable, int failed,
                               List<RowResult> results) {
    }

    public String export(String q, ReservationStatus status, RoomType type, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder(Csv.line("reference", "status", "guestName", "email", "phone", "roomType",
                "checkIn", "checkOut", "nights", "rooms", "adults", "needsAccessible", "maxFloor", "preferredFloor",
                "acceptsUpgrade", "assignedRooms", "screening", "createdBy", "createdAt"));
        for (ReservationView r : reservations.all(q, status, type, from, to)) {
            sb.append(Csv.line(r.reference(), r.status(), r.guestName(), r.email(), r.phone(), r.roomType(),
                    r.checkIn(), r.checkOut(), r.nights(), r.rooms(), r.adults(), r.needsAccessible(), r.maxFloor(),
                    r.preferredFloor(), r.acceptsUpgrade(), String.join(" ", r.roomNumbers()), r.screening(),
                    r.createdBy(), r.createdAt()));
        }
        return sb.toString();
    }

    /** Template with the expected header and one example row. */
    public String template() {
        return Csv.line((Object[]) COLUMNS)
                + Csv.line("Asha Kulkarni", "asha.k@example.org", "+91 98200 11223", "DELUXE",
                LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), 1, 2, false, "", 3, true, "Late arrival");
    }

    public ImportResult importCsv(String text, boolean joinWaitlist) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("The file is empty.");
        }
        List<List<String>> rows = Csv.parse(text);
        if (rows.size() > 501) {
            throw new InvalidInputException("Import at most 500 reservations at a time.");
        }
        int start = !rows.isEmpty() && rows.get(0).get(0).trim().equalsIgnoreCase("guestName") ? 1 : 0;
        List<RowResult> results = new ArrayList<>();
        int booked = 0, waitlisted = 0, held = 0, unavailable = 0, failed = 0;
        for (int i = start; i < rows.size(); i++) {
            List<String> f = rows.get(i);
            String name = f.isEmpty() ? "" : f.get(0).trim();
            try {
                NewReservation cmd = toCommand(f, joinWaitlist);
                CreateResult r = reservations.create(cmd, null);
                switch (r.outcome()) {
                    case "BOOKED" -> booked++;
                    case "WAITLISTED" -> waitlisted++;
                    case "HELD" -> held++;
                    default -> unavailable++;
                }
                results.add(new RowResult(i + 1, name, r.outcome(),
                        r.reservation() == null ? null : r.reservation().reference(), r.message()));
            } catch (RuntimeException e) {   // one bad row must not stop the rest
                failed++;
                results.add(new RowResult(i + 1, name, "FAILED", null, e.getMessage()));
            }
        }
        return new ImportResult(rows.size() - start, booked, waitlisted, held, unavailable, failed, results);
    }

    private static NewReservation toCommand(List<String> f, boolean joinWaitlist) {
        if (f.size() < 8) {
            throw new InvalidInputException("Needs at least 8 columns: " + String.join(",", List.of(COLUMNS).subList(0, 8)));
        }
        try {
            return new NewReservation(f.get(0).trim(), f.get(1).trim(), f.get(2).trim(),
                    RoomType.valueOf(f.get(3).trim().toUpperCase(Locale.ROOT)),
                    LocalDate.parse(f.get(4).trim()), LocalDate.parse(f.get(5).trim()),
                    Integer.parseInt(f.get(6).trim()), Integer.parseInt(f.get(7).trim()),
                    bool(get(f, 8)), intOrNull(get(f, 9)), intOrNull(get(f, 10)), bool(get(f, 11)), get(f, 12),
                    joinWaitlist);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Dates must look like 2026-11-12.");
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Room type must be STANDARD, DELUXE, EXECUTIVE or SUITE, and rooms and adults whole numbers.");
        }
    }

    private static String get(List<String> f, int i) {
        return i < f.size() ? f.get(i).trim() : "";
    }

    private static boolean bool(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1");
    }

    private static Integer intOrNull(String s) {
        return s.isBlank() ? null : Integer.valueOf(s);
    }
}
