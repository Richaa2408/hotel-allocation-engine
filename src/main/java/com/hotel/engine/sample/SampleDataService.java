package com.hotel.engine.sample;

import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.service.AvailabilityService;
import com.hotel.engine.service.ScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the services with the sample hotel: future bookings on the calendar, a blocklist,
 * and today's arrivals with the rooms free tonight. Runs once at startup (turn it off with
 * hotel.sample.enabled=false) and again whenever the demo is reset.
 */
@Service
public class SampleDataService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataService.class);

    static final int ARRIVALS = 28;
    static final int STAYOVER_ROOMS = 14;
    static final int BOOKING_ATTEMPTS = 450;
    static final int SYNTHETIC_BLOCKLIST = 3000;

    private final AvailabilityService availability;
    private final ScreeningService screening;
    private final boolean enabled;
    private final long defaultSeed;

    private List<Guest> arrivals = List.of();
    private List<Room> roomsFreeTonight = List.of();

    public SampleDataService(AvailabilityService availability, ScreeningService screening,
                             @Value("${hotel.sample.enabled:true}") boolean enabled,
                             @Value("${hotel.sample.seed:42}") long defaultSeed) {
        this.availability = availability;
        this.screening = screening;
        this.enabled = enabled;
        this.defaultSeed = defaultSeed;
    }

    /**
     * @param bookingsMade    seed bookings that fit and were added to the calendar
     * @param bookingsRefused seed bookings turned away because a night was full
     */
    public record LoadSummary(long seed, LocalDate calendarStart, int nights, int bookingsMade,
                              int bookingsRefused, int blocklistEntries, int arrivals, int roomsFreeTonight) {
    }

    public record Arrivals(List<Guest> guests, List<Room> rooms) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            LoadSummary s = load(defaultSeed);
            log.info("Sample hotel loaded: {} bookings, {} blocklist entries, {} arrivals for {} free rooms (seed {})",
                    s.bookingsMade(), s.blocklistEntries(), s.arrivals(), s.roomsFreeTonight(), s.seed());
        }
    }

    /** Replaces all current data with the sample hotel for this seed. */
    public synchronized LoadSummary load(long seed) {
        SampleHotel hotel = new SampleHotel(seed);
        LocalDate today = LocalDate.now();
        int nights = AvailabilityService.DEFAULT_NIGHTS;

        Map<RoomType, Integer> inventory = new EnumMap<>(RoomType.class);
        for (Room room : SampleHotel.rooms()) {
            inventory.merge(room.type(), 1, Integer::sum);
        }
        availability.reset(today, nights, inventory);

        int made = 0;
        int refused = 0;
        for (SampleHotel.SeedBooking b : hotel.futureBookings(today, nights, BOOKING_ATTEMPTS)) {
            try {
                if (availability.book(b.guestName(), b.type(), b.checkIn(), b.checkOut(), b.rooms()).booked()) {
                    made++;
                } else {
                    refused++;
                }
            } catch (InvalidInputException e) {
                refused++;
            }
        }

        screening.reset(ScreeningService.DEFAULT_CAPACITY, ScreeningService.DEFAULT_FALSE_POSITIVE_RATE);
        for (SampleHotel.SeedBlock block : hotel.blocklist(SYNTHETIC_BLOCKLIST)) {
            screening.addToBlocklist(block.email() ? ScreeningService.Kind.EMAIL : ScreeningService.Kind.PHONE,
                    block.value(), block.reason());
        }

        arrivals = hotel.arrivals(ARRIVALS);
        roomsFreeTonight = hotel.roomsFreeTonight(STAYOVER_ROOMS);

        return new LoadSummary(seed, today, nights, made, refused, screening.stats().entries(),
                arrivals.size(), roomsFreeTonight.size());
    }

    public synchronized Arrivals arrivals() {
        return new Arrivals(arrivals, roomsFreeTonight);
    }

    public long defaultSeed() {
        return defaultSeed;
    }
}
