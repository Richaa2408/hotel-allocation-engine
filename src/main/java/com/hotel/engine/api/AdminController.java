package com.hotel.engine.api;

import com.hotel.engine.model.Enums.Role;
import com.hotel.engine.seed.SeedService;
import com.hotel.engine.service.AdminService;
import com.hotel.engine.service.AdminService.UserView;
import com.hotel.engine.service.WatchlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Settings: staff accounts, Bloom filter size, demo reset. Admin only. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    public record NewUser(@NotBlank @Size(max = 30) String username, @Size(max = 100) String displayName,
                         @NotNull Role role, @NotBlank String password) {
    }

    public record UserChange(@Size(max = 100) String displayName, Role role, Boolean enabled, String password) {
    }

    public record BloomConfig(int capacity, double falsePositiveRate) {
    }

    private final AdminService admin;
    private final WatchlistService watchlist;
    private final SeedService seed;

    public AdminController(AdminService admin, WatchlistService watchlist, SeedService seed) {
        this.admin = admin;
        this.watchlist = watchlist;
        this.seed = seed;
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return admin.list();
    }

    @PostMapping("/users")
    public UserView create(@Valid @RequestBody NewUser u) {
        return admin.create(u.username(), u.displayName(), u.role(), u.password());
    }

    @PatchMapping("/users/{id}")
    public UserView change(@PathVariable long id, @Valid @RequestBody UserChange c) {
        return admin.update(id, c.displayName(), c.role(), c.enabled(), c.password());
    }

    @PutMapping("/bloom")
    public WatchlistService.Stats bloom(@RequestBody BloomConfig c) {
        return watchlist.configure(c.capacity(), c.falsePositiveRate());
    }

    @PostMapping("/reset")
    public SeedService.Summary reset(@RequestParam(defaultValue = "42") long seed) {
        return this.seed.reset(seed);
    }
}
