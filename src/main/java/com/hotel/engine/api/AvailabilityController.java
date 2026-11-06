package com.hotel.engine.api;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.service.AvailabilityService;
import com.hotel.engine.service.AvailabilityService.AvailabilityCheck;
import com.hotel.engine.service.AvailabilityService.BookingResult;
import com.hotel.engine.service.AvailabilityService.BookingView;
import com.hotel.engine.service.AvailabilityService.Calendar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Checking and booking stays against the per-type availability calendar. */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    public record StayRequest(
            @NotNull RoomType roomType,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) int rooms) {
    }

    public record BookingRequest(
            @NotBlank String guestName,
            @NotNull RoomType roomType,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) int rooms) {
    }

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public AvailabilityCheck check(@Valid @RequestBody StayRequest r) {
        return service.check(r.roomType(), r.checkIn(), r.checkOut(), r.rooms());
    }

    @PostMapping("/bookings")
    public BookingResult book(@Valid @RequestBody BookingRequest r) {
        return service.book(r.guestName(), r.roomType(), r.checkIn(), r.checkOut(), r.rooms());
    }

    @GetMapping("/bookings")
    public List<BookingView> bookings() {
        return service.bookings();
    }

    @DeleteMapping("/bookings/{id}")
    public BookingResult cancel(@PathVariable String id) {
        return service.cancel(id);
    }

    /** Whole calendar and tree for one room type, e.g. /api/availability/calendar?type=DELUXE */
    @GetMapping("/calendar")
    public Calendar calendar(@RequestParam RoomType type) {
        return service.calendar(type);
    }
}
