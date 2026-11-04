package com.hotel.engine.api;

import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.Room;
import com.hotel.engine.service.AssignmentResult;
import com.hotel.engine.service.RoomAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Room assignment for today's arrivals. */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    /** Size caps keep one request from tying up the server; benchmarks call the service directly. */
    public record AssignmentRequest(
            @NotEmpty @Size(max = 2000) List<@Valid Guest> guests,
            @NotEmpty @Size(max = 2000) List<@Valid Room> rooms) {
    }

    private final RoomAssignmentService service;

    public AssignmentController(RoomAssignmentService service) {
        this.service = service;
    }

    @PostMapping
    public AssignmentResult assign(@Valid @RequestBody AssignmentRequest request) {
        return service.assign(request.guests(), request.rooms());
    }
}
