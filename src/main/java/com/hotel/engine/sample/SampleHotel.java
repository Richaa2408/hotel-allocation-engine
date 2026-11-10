package com.hotel.engine.sample;

import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Synthetic but realistic data for a 40-room city hotel. Everything comes from a seeded
 * random generator, so the same seed always gives the same hotel (reproducible demos and tests).
 *
 * <p>Floors: 1-2 standard, 3 deluxe, 4 deluxe and suites. Accessible rooms are few and spread out,
 * as in most real hotels, which is what makes room assignment hard.
 */
public final class SampleHotel {

    private static final String[] SURNAMES = {
            "Mehta", "Rao", "Iyer", "Khan", "Das", "Pillai", "Nair", "Sharma", "Gupta", "Reddy",
            "Menon", "Bose", "Joshi", "Kulkarni", "Fernandes", "D'Souza", "Patel", "Singh", "Chopra", "Banerjee",
            "Hegde", "Shetty", "Kapoor", "Malhotra", "Verma", "Sinha", "Thomas", "George", "Ali", "Qureshi",
            "Bhat", "Kamath", "Naidu", "Ghosh", "Mishra", "Pandey", "Saxena", "Agarwal", "Desai", "Shah"};

    private static final String[] INITIALS = {"A.", "R.", "S.", "K.", "P.", "M.", "N.", "V.", "D.", "J."};

    private final long seed;

    public SampleHotel(long seed) {
        this.seed = seed;
    }

    /**
     * Each kind of data gets its own random sequence, so one part can't shift another. Without this,
     * the number of future bookings (which depends on today's weekday) changed tonight's arrivals.
     */
    private Random randomFor(int part) {
        return new Random(seed * 1_000_003L + part);
    }

    /** The hotel's 40 rooms. */
    public static List<Room> rooms() {
        List<Room> rooms = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rooms.add(new Room(String.valueOf(100 + i), RoomType.STANDARD, 1, i <= 2));
        }
        for (int i = 1; i <= 10; i++) {
            rooms.add(new Room(String.valueOf(200 + i), RoomType.STANDARD, 2, i == 1));
        }
        for (int i = 1; i <= 10; i++) {
            rooms.add(new Room(String.valueOf(300 + i), RoomType.DELUXE, 3, i <= 2));
        }
        for (int i = 1; i <= 6; i++) {
            rooms.add(new Room(String.valueOf(400 + i), RoomType.DELUXE, 4, i == 1));
        }
        for (int i = 7; i <= 10; i++) {
            rooms.add(new Room(String.valueOf(400 + i), RoomType.SUITE, 4, i == 7));
        }
        return rooms;
    }

    /** Rooms free tonight: the hotel minus `occupied` rooms held by guests who are staying on. */
    public List<Room> roomsFreeTonight(int occupied) {
        Random random = randomFor(1);
        List<Room> all = new ArrayList<>(rooms());
        for (int i = 0; i < occupied && !all.isEmpty(); i++) {
            all.remove(random.nextInt(all.size()));
        }
        return all;
    }

    /**
     * Today's arrivals. Demand leans towards deluxe rooms, and some guests need accessible rooms
     * or low floors, so a few arrivals compete for the same handful of rooms.
     */
    public List<Guest> arrivals(int count) {
        Random random = randomFor(2);
        List<Guest> guests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = random.nextDouble();
            RoomType type = t < 0.45 ? RoomType.STANDARD : t < 0.88 ? RoomType.DELUXE : RoomType.SUITE;
            boolean accessible = random.nextDouble() < 0.15;
            Integer maxFloor = random.nextDouble() < 0.12 ? 1 + random.nextInt(2) : null;
            boolean upgrade = type != RoomType.SUITE && random.nextDouble() < 0.35;
            String name = INITIALS[random.nextInt(INITIALS.length)] + " " + SURNAMES[i % SURNAMES.length];
            guests.add(new Guest(String.format("AR-%03d", i + 1), name, type, accessible, maxFloor, upgrade));
        }
        return guests;
    }

    /** A future booking request for seeding the availability calendar. */
    public record SeedBooking(String guestName, RoomType type, LocalDate checkIn, LocalDate checkOut, int rooms) {
    }

    /**
     * Booking requests spread over the calendar, busier at weekends. Some won't fit;
     * the loader simply skips those, as a real hotel would turn them away.
     */
    public List<SeedBooking> futureBookings(LocalDate start, int nights, int attempts) {
        Random random = randomFor(3);
        List<SeedBooking> out = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int night = random.nextInt(nights - 1);
            LocalDate checkIn = start.plusDays(night);
            int dow = checkIn.getDayOfWeek().getValue();             // 5 = Friday, 6 = Saturday
            if (dow < 5 && random.nextDouble() < 0.3) {
                continue;                                            // fewer weekday arrivals
            }
            int length = Math.min(1 + random.nextInt(4), nights - night);
            double t = random.nextDouble();
            RoomType type = t < 0.55 ? RoomType.STANDARD : t < 0.9 ? RoomType.DELUXE : RoomType.SUITE;
            int rooms = random.nextDouble() < 0.8 ? 1 : 2;
            String name = SURNAMES[random.nextInt(SURNAMES.length)];
            out.add(new SeedBooking(name, type, checkIn, checkIn.plusDays(length), rooms));
        }
        return out;
    }

    /** A blocklist entry with why it was added. */
    public record SeedBlock(boolean email, String value, String reason) {
    }

    /**
     * A few readable entries for demos plus `synthetic` generated ones so the Bloom filter has a
     * realistic load. All emails use reserved example domains, so none belongs to a real person.
     */
    public List<SeedBlock> blocklist(int synthetic) {
        Random random = randomFor(4);
        List<SeedBlock> out = new ArrayList<>(List.of(
                new SeedBlock(true, "fraud.ring@example.com", "Chargeback, Aug 2026"),
                new SeedBlock(true, "no.show.repeat@example.net", "Five no-shows with guaranteed rates"),
                new SeedBlock(true, "card.tester@example.org", "Many small declined card authorisations"),
                new SeedBlock(true, "damage.claim@example.com", "Unpaid room damage, Mar 2026"),
                new SeedBlock(false, "+91 90000 11111", "Fake ID presented at check-in"),
                new SeedBlock(false, "+91 90000 22222", "Chargeback, Jun 2026"),
                new SeedBlock(false, "+44 7700 900123", "Disputed charges across three stays")));
        String[] domains = {"example.com", "example.net", "example.org"};
        String[] reasons = {"Chargeback", "Repeated no-show", "Card testing", "Unpaid balance"};
        for (int i = 0; i < synthetic; i++) {
            String reason = reasons[random.nextInt(reasons.length)] + " (imported list)";
            if (random.nextDouble() < 0.7) {
                out.add(new SeedBlock(true, "guest" + (10000 + random.nextInt(900000)) + "@"
                        + domains[random.nextInt(domains.length)], reason));
            } else {
                out.add(new SeedBlock(false, "9" + (100000000 + random.nextInt(900000000)), reason));
            }
        }
        return out;
    }
}
