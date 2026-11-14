package com.hotel.engine.api;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.service.InventoryService;
import com.hotel.engine.service.InventoryService.Alert;
import com.hotel.engine.service.InventoryService.AvailabilityCheck;
import com.hotel.engine.service.InventoryService.HeatRow;
import com.hotel.engine.service.MaintenanceService;
import com.hotel.engine.service.MaintenanceService.BlockResult;
import com.hotel.engine.service.MaintenanceService.BlockView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Inventory calendar, availability checks and maintenance blocks. */
@RestController
public class InventoryController {

    public record CheckRequest(@NotNull RoomType roomType, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut,
                               @Min(1) @Max(20) int rooms, boolean needsAccessible) {
    }

    public record BlockRequest(@NotBlank String roomNumber, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                               @NotBlank @Size(max = 200) String reason) {
    }

    private final InventoryService inventory;
    private final MaintenanceService maintenance;

    public InventoryController(InventoryService inventory, MaintenanceService maintenance) {
        this.inventory = inventory;
        this.maintenance = maintenance;
    }

    @GetMapping("/api/inventory/heatmap")
    public List<HeatRow> heatmap(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(defaultValue = "28") int days) {
        return inventory.heatmap(from, days);
    }

    @PostMapping("/api/inventory/check")
    public AvailabilityCheck check(@Valid @RequestBody CheckRequest r) {
        return inventory.check(r.roomType(), r.checkIn(), r.checkOut(), r.rooms(), r.needsAccessible());
    }

    @GetMapping("/api/inventory/alerts")
    public List<Alert> alerts(@RequestParam(defaultValue = "30") @Min(1) @Max(120) int days) {
        return inventory.alerts(days);
    }

    @GetMapping("/api/maintenance")
    public List<BlockView> blocks() {
        return maintenance.list();
    }

    @PostMapping("/api/maintenance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public BlockResult block(@Valid @RequestBody BlockRequest r) {
        return maintenance.create(r.roomNumber(), r.startDate(), r.endDate(), r.reason());
    }

    @DeleteMapping("/api/maintenance/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> unblock(@PathVariable long id) {
        maintenance.remove(id);
        return ResponseEntity.noContent().build();
    }
}
