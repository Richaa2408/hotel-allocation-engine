package com.hotel.engine.service;

import com.hotel.engine.algorithm.bloom.BloomFilter;
import com.hotel.engine.algorithm.bloom.BloomTrace;
import com.hotel.engine.domain.InvalidInputException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Screens new bookings against a blocklist of emails and phone numbers linked to
 * chargebacks or fraud.
 *
 * <p>Two stages:
 * <ol>
 *   <li>Bloom filter: "definitely not listed" clears the guest immediately.</li>
 *   <li>"Possibly listed" goes to the exact blocklist, the authoritative record. Only a match there blocks
 *       the booking, so a Bloom filter false positive never turns a guest away.</li>
 * </ol>
 *
 * <p>Here the exact blocklist is an in-memory map standing in for a database table. In production that
 * lookup is the slow part (a network round trip), which is what the Bloom filter saves.
 */
@Service
public class ScreeningService {

    public static final int DEFAULT_CAPACITY = 10_000;
    public static final double DEFAULT_FALSE_POSITIVE_RATE = 0.01;

    public enum Kind { EMAIL, PHONE }

    private BloomFilter filter;
    private int capacity;
    private double targetRate;
    private final Map<String, String> exactBlocklist = new HashMap<>();   // key -> reason

    // Counters since the last reset.
    private long fieldsScreened;
    private long clearedByBloom;
    private long sentToExactCheck;
    private long confirmedMatches;
    private long falsePositives;

    public ScreeningService() {
        reset(DEFAULT_CAPACITY, DEFAULT_FALSE_POSITIVE_RATE);
    }

    /** Empties the blocklist and sizes a new filter. */
    public synchronized void reset(int expectedEntries, double falsePositiveRate) {
        try {
            filter = new BloomFilter(expectedEntries, falsePositiveRate);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage());
        }
        capacity = expectedEntries;
        targetRate = falsePositiveRate;
        exactBlocklist.clear();
        fieldsScreened = clearedByBloom = sentToExactCheck = confirmedMatches = falsePositives = 0;
    }

    // ---------- results ----------

    /**
     * @param field         EMAIL or PHONE
     * @param normalized    the value after normalizing (lowercase email, digits-only phone), which is what was hashed
     * @param bloom         which bits it hashed to and whether they were set
     * @param exactChecked  true if the Bloom filter said "possibly listed" and the exact list was consulted
     * @param listed        true only if the exact list confirmed it
     * @param outcome       CLEARED_BY_FILTER, FALSE_POSITIVE or CONFIRMED
     * @param reason        why it's listed, if confirmed
     */
    public record FieldCheck(
            Kind field,
            String normalized,
            BloomTrace bloom,
            boolean exactChecked,
            boolean listed,
            String outcome,
            String reason) {
    }

    /** @param verdict CLEAR or BLOCKED */
    public record ScreeningResult(String verdict, String message, List<FieldCheck> checks) {
    }

    public record Stats(
            int capacity,
            double targetFalsePositiveRate,
            int bitCount,
            int hashCount,
            int entries,
            int bitsSet,
            double expectedFalsePositiveRate,
            boolean overCapacity,
            long fieldsScreened,
            long clearedByBloom,
            long sentToExactCheck,
            long confirmedMatches,
            long falsePositives) {
    }

    // ---------- operations ----------

    /**
     * Adds an entry to the blocklist. Returns false if it was already listed.
     * Duplicates are caught by the exact list, so the filter's item count stays accurate.
     */
    public synchronized boolean addToBlocklist(Kind kind, String value, String reason) {
        String key = key(kind, value);
        if (exactBlocklist.containsKey(key)) {
            return false;
        }
        exactBlocklist.put(key, reason == null || reason.isBlank() ? "No reason recorded" : reason.trim());
        filter.add(key);
        return true;
    }

    /** Screens whichever of email and phone are given. At least one is required. */
    public synchronized ScreeningResult screen(String email, String phone) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        if (!hasEmail && !hasPhone) {
            throw new InvalidInputException("Enter an email or a phone number to screen.");
        }
        List<FieldCheck> checks = new ArrayList<>();
        if (hasEmail) {
            checks.add(screenField(Kind.EMAIL, email));
        }
        if (hasPhone) {
            checks.add(screenField(Kind.PHONE, phone));
        }

        List<FieldCheck> hits = checks.stream().filter(FieldCheck::listed).toList();
        if (!hits.isEmpty()) {
            FieldCheck first = hits.get(0);
            return new ScreeningResult("BLOCKED",
                    "Hold this booking: the " + first.field().name().toLowerCase() + " is on the blocklist ("
                            + first.reason() + ").", checks);
        }
        boolean anyFalsePositive = checks.stream().anyMatch(c -> c.outcome().equals("FALSE_POSITIVE"));
        return new ScreeningResult("CLEAR", anyFalsePositive
                ? "Clear. The filter flagged a possible match, but the exact blocklist has no record of it."
                : "Clear. Not on the blocklist.", checks);
    }

    private FieldCheck screenField(Kind kind, String raw) {
        String key = key(kind, raw);
        fieldsScreened++;
        BloomTrace trace = filter.check(key);
        if (!trace.possiblyPresent()) {
            clearedByBloom++;
            return new FieldCheck(kind, valuePart(key), trace, false, false, "CLEARED_BY_FILTER", null);
        }
        sentToExactCheck++;
        String reason = exactBlocklist.get(key);
        if (reason == null) {
            falsePositives++;
            return new FieldCheck(kind, valuePart(key), trace, true, false, "FALSE_POSITIVE", null);
        }
        confirmedMatches++;
        return new FieldCheck(kind, valuePart(key), trace, true, true, "CONFIRMED", reason);
    }

    public synchronized Stats stats() {
        return new Stats(capacity, targetRate, filter.bitCount(), filter.hashCount(), filter.itemCount(),
                filter.setBitCount(), filter.expectedFalsePositiveRate(), filter.itemCount() > capacity,
                fieldsScreened, clearedByBloom, sentToExactCheck, confirmedMatches, falsePositives);
    }

    // ---------- helpers ----------

    /**
     * Normalizes so that "Ravi@Mail.com " and "ravi@mail.com" (or "+91 98765-43210" and "919876543210")
     * hash to the same bits. The kind prefix keeps an email and a phone with the same text apart.
     */
    static String key(Kind kind, String value) {
        if (kind == null) {
            throw new InvalidInputException("Choose EMAIL or PHONE.");
        }
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Value is required.");
        }
        String normalized;
        if (kind == Kind.EMAIL) {
            normalized = value.trim().toLowerCase(Locale.ROOT);
            int at = normalized.indexOf('@');
            if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
                throw new InvalidInputException("\"" + value.trim() + "\" is not a valid email address.");
            }
        } else {
            normalized = value.replaceAll("\\D", "");
            if (normalized.length() < 7 || normalized.length() > 15) {
                throw new InvalidInputException("Phone numbers need 7 to 15 digits.");
            }
        }
        return kind.name().toLowerCase(Locale.ROOT) + ":" + normalized;
    }

    private static String valuePart(String key) {
        return key.substring(key.indexOf(':') + 1);
    }
}
