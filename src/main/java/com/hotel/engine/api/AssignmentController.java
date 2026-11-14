package com.hotel.engine.api;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.model.Enums.Housekeeping;
import com.hotel.engine.service.AssignmentService;
import com.hotel.engine.service.AssignmentService.Board;
import com.hotel.engine.service.AssignmentService.LogItem;
import com.hotel.engine.service.AssignmentService.RoomTile;
import com.hotel.engine.service.AssignmentService.RunResult;
import com.hotel.engine.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Room assignment board, automatic assignment (max flow), manual moves, and rooms. */
@RestController
public class AssignmentController {

    public record RoomChoice(@NotBlank String roomNumber) {
    }

    public record HousekeepingChange(@NotNull Housekeeping housekeeping) {
    }

    private final AssignmentService assignments;
    private final RoomService rooms;

    public AssignmentController(AssignmentService assignments, RoomService rooms) {
        this.assignments = assignments;
        this.rooms = rooms;
    }

    @GetMapping("/api/assignments/board")
    public Board board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return assignments.board(date);
    }

    @PostMapping("/api/assignments/run")
    public RunResult run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return assignments.run(date);
    }

    @PutMapping("/api/assignments/requests/{requestId}/room")
    public Board assign(@PathVariable long requestId, @Valid @RequestBody RoomChoice choice) {
        return assignments.assignManually(requestId, choice.roomNumber());
    }

    @DeleteMapping("/api/assignments/requests/{requestId}/room")
    public Board unassign(@PathVariable long requestId) {
        return assignments.unassign(requestId);
    }

    @GetMapping("/api/assignments/log")
    public List<LogItem> log() {
        return assignments.recent();
    }

    @GetMapping("/api/rooms")
    public List<RoomTile> rooms(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return assignments.board(date).rooms();
    }

    @PatchMapping("/api/rooms/{number}/housekeeping")
    public ResponseEntity<Void> housekeeping(@PathVariable String number, @Valid @RequestBody HousekeepingChange c) {
        rooms.setHousekeeping(number, c.housekeeping());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/rooms/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomService.ImportResult importRooms(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 500_000) {
            throw new InvalidInputException("Upload a CSV file under 500 KB.");
        }
        return rooms.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
    }
}
