package com.hotel.engine.api;

import com.hotel.engine.model.Enums.WatchKind;
import com.hotel.engine.service.WatchlistService;
import com.hotel.engine.service.WatchlistService.EntryView;
import com.hotel.engine.service.WatchlistService.FalsePositive;
import com.hotel.engine.service.WatchlistService.ScreeningResult;
import com.hotel.engine.service.WatchlistService.Stats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Watchlist management (managers) and screening (all staff). */
@RestController
public class RiskController {

    public record ScreenRequest(@Size(max = 254) String email, @Size(max = 32) String phone) {
    }

    public record EntryRequest(@NotNull WatchKind kind, @NotBlank @Size(max = 254) String value,
                               @Size(max = 200) String reason) {
    }

    private final WatchlistService watchlist;

    public RiskController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    @PostMapping("/api/screening/check")
    public ScreeningResult screen(@Valid @RequestBody ScreenRequest r) {
        return watchlist.screen(r.email(), r.phone());
    }

    @GetMapping("/api/screening/stats")
    public Stats stats() {
        return watchlist.stats();
    }

    @GetMapping("/api/screening/false-positive")
    public FalsePositive falsePositive() {
        return watchlist.findFalsePositive();
    }

    @GetMapping("/api/watchlist")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageView<EntryView> list(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size) {
        return PageView.of(watchlist.list(q, page, size));
    }

    @PostMapping("/api/watchlist")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public EntryView add(@Valid @RequestBody EntryRequest r) {
        return watchlist.add(r.kind(), r.value(), r.reason());
    }

    @DeleteMapping("/api/watchlist/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable long id) {
        watchlist.remove(id);
        return ResponseEntity.noContent().build();
    }
}
