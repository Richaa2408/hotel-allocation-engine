package com.hotel.engine.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** "Today" for the hotel. Tests can pin it to a fixed date with setFixedDate. */
@Component
public class HotelClock {

    private volatile LocalDate fixed;
    private final Clock clock = Clock.systemDefaultZone();

    public LocalDate today() {
        LocalDate f = fixed;
        return f != null ? f : LocalDate.now(clock);
    }

    public void setFixedDate(LocalDate date) {
        this.fixed = date;
    }
}
