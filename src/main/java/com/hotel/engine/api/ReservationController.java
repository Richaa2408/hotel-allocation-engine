package com.hotel.engine.api;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.Enums.ReservationStatus;
import com.hotel.engine.service.ReservationCsvService;
import com.hotel.engine.service.ReservationService;
import com.hotel.engine.service.ReservationService.Changes;
import com.hotel.engine.service.ReservationService.CreateResult;
import com.hotel.engine.service.ReservationService.NewReservation;
import com.hotel.engine.service.ReservationService.ReservationDetail;
import com.hotel.engine.service.ReservationService.ReservationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Reservations: list, create (screen, check, book), change, cancel, approve, check in and out, CSV. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    public record CreateRequest(
            @NotBlank @Size(max = 100) String guestName,
            @Size(max = 254) String email,
            @Size(max = 32) String phone,
            @NotNull RoomType roomType,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) @Max(20) int rooms,
            @Min(1) @Max(80) int adults,
            boolean needsAccessible,
            @Min(0) @Max(50) Integer maxFloor,
            @Min(0) @Max(50) Integer preferredFloor,
            boolean acceptsUpgrade,
            @Size(max = 500) String notes,
            boolean joinWaitlist) {
    }

    public record ChangeRequest(@Size(max = 100) String guestName, RoomType roomType, LocalDate checkIn,
                                LocalDate checkOut, @Min(1) @Max(20) Integer rooms, @Min(1) @Max(80) Integer adults,
                                Boolean needsAccessible, @Min(0) @Max(50) Integer maxFloor, Boolean clearMaxFloor,
                                @Min(0) @Max(50) Integer preferredFloor, Boolean clearPreferredFloor,
                                Boolean acceptsUpgrade, @Size(max = 500) String notes) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    private final ReservationService service;
    private final ReservationCsvService csv;

    public ReservationController(ReservationService service, ReservationCsvService csv) {
        this.service = service;
        this.csv = csv;
    }

    @GetMapping
    public PageView<ReservationView> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) ReservationStatus status,
                                            @RequestParam(required = false) RoomType type,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size,
                                            @RequestParam(required = false) String sort) {
        return PageView.of(service.search(q, status, type, from, to, page, size, sort));
    }

    /** An Idempotency-Key header makes a retried submission return the first result instead of booking twice. */
    @PostMapping
    public CreateResult create(@Valid @RequestBody CreateRequest r,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.create(new NewReservation(r.guestName(), r.email(), r.phone(), r.roomType(), r.checkIn(),
                r.checkOut(), r.rooms(), r.adults(), r.needsAccessible(), r.maxFloor(), r.preferredFloor(),
                r.acceptsUpgrade(), r.notes(), r.joinWaitlist()), idempotencyKey);
    }

    @GetMapping("/{reference}")
    public ReservationDetail detail(@PathVariable String reference) {
        return service.detail(reference);
    }

    @PatchMapping("/{reference}")
    public ReservationView change(@PathVariable String reference, @Valid @RequestBody ChangeRequest r) {
        return service.update(reference, new Changes(r.guestName(), r.roomType(), r.checkIn(), r.checkOut(), r.rooms(),
                r.adults(), r.needsAccessible(), r.maxFloor(), r.clearMaxFloor(), r.preferredFloor(),
                r.clearPreferredFloor(), r.acceptsUpgrade(), r.notes()));
    }

    @PostMapping("/{reference}/cancel")
    public ReservationView cancel(@PathVariable String reference, @Valid @RequestBody(required = false) CancelRequest r) {
        return service.cancel(reference, r == null ? null : r.reason());
    }

    @PostMapping("/{reference}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ReservationView approve(@PathVariable String reference) {
        return service.approve(reference);
    }

    @PostMapping("/{reference}/check-in")
    public ReservationView checkIn(@PathVariable String reference) {
        return service.checkIn(reference);
    }

    @PostMapping("/{reference}/check-out")
    public ReservationView checkOut(@PathVariable String reference) {
        return service.checkOut(reference);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) ReservationStatus status,
                                         @RequestParam(required = false) RoomType type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reservations.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.export(q, status, type, from, to));
    }

    @GetMapping(value = "/import-template", produces = "text/csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reservations-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.template());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ReservationCsvService.ImportResult importCsv(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "false") boolean joinWaitlist)
            throws IOException {
        if (file.isEmpty() || file.getSize() > 1_000_000) {
            throw new InvalidInputException("Upload a CSV file under 1 MB.");
        }
        return csv.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), joinWaitlist);
    }
}
