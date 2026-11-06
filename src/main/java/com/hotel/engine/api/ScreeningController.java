package com.hotel.engine.api;

import com.hotel.engine.service.ScreeningService;
import com.hotel.engine.service.ScreeningService.Kind;
import com.hotel.engine.service.ScreeningService.ScreeningResult;
import com.hotel.engine.service.ScreeningService.Stats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Blocklist screening for new bookings. */
@RestController
@RequestMapping("/api/screening")
public class ScreeningController {

    public record ScreenRequest(@Size(max = 254) String email, @Size(max = 32) String phone) {
    }

    public record BlocklistEntry(@NotNull Kind kind, @NotBlank @Size(max = 254) String value,
                                 @Size(max = 200) String reason) {
    }

    /** @param added false if the entry was already on the list */
    public record BlocklistResponse(boolean added, Stats stats) {
    }

    private final ScreeningService service;

    public ScreeningController(ScreeningService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public ScreeningResult check(@Valid @RequestBody ScreenRequest r) {
        return service.screen(r.email(), r.phone());
    }

    @PostMapping("/blocklist")
    public BlocklistResponse add(@Valid @RequestBody BlocklistEntry e) {
        boolean added = service.addToBlocklist(e.kind(), e.value(), e.reason());
        return new BlocklistResponse(added, service.stats());
    }

    @GetMapping("/stats")
    public Stats stats() {
        return service.stats();
    }
}
