package com.hotel.engine.service;

import com.hotel.engine.algorithm.bloom.BloomFilter;
import com.hotel.engine.algorithm.bloom.BloomTrace;
import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.model.Enums.WatchKind;
import com.hotel.engine.model.WatchlistEntryEntity;
import com.hotel.engine.repository.WatchlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screens guests against the fraud and chargeback watchlist in two stages.
 *
 * <ol>
 *   <li>Bloom filter in memory: "definitely not listed" clears the guest with no database lookup.</li>
 *   <li>"Possibly listed" is checked against the watchlist table, the source of truth. Only a match
 *       there counts, so a false positive never holds up an honest guest.</li>
 * </ol>
 *
 * <p>A standard Bloom filter can't delete, so removing an entry rebuilds the filter from the table.
 */
@Service
public class WatchlistService {

    public static final int DEFAULT_CAPACITY = 10_000;
    public static final double DEFAULT_RATE = 0.01;

    private final WatchlistRepository repo;
    private final AuditService audit;

    private BloomFilter filter;
    private int capacity = DEFAULT_CAPACITY;
    private double targetRate = DEFAULT_RATE;
    private Instant builtAt;

    private long screened;
    private long clearedByFilter;
    private long exactLookups;
    private long confirmed;
    private long falsePositives;

    public WatchlistService(WatchlistRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    // ---------- results ----------

    /**
     * @param outcome CLEARED_BY_FILTER, FALSE_POSITIVE or CONFIRMED
     * @param reason  why it's listed, when CONFIRMED
     */
    public record FieldCheck(WatchKind field, String normalized, BloomTrace bloom, boolean exactChecked,
                             boolean listed, String outcome, String reason) {
    }

    /** @param verdict CLEAR or MATCH */
    public record ScreeningResult(String verdict, String message, List<FieldCheck> checks) {
        public boolean matched() {
            return verdict.equals("MATCH");
        }

        public boolean flaggedButCleared() {
            return checks.stream().anyMatch(c -> c.outcome().equals("FALSE_POSITIVE"));
        }

        public String reason() {
            return checks.stream().filter(FieldCheck::listed).map(FieldCheck::reason).findFirst().orElse(null);
        }
    }

    public record Stats(int capacity, double targetFalsePositiveRate, int bitCount, int hashCount, int entries,
                        int bitsSet, double expectedFalsePositiveRate, boolean overCapacity, Instant builtAt,
                        long screened, long clearedByFilter, long exactLookups, long confirmed,
                        long falsePositives) {
    }

    public record EntryView(long id, WatchKind kind, String value, String reason, Instant createdAt) {
        static EntryView of(WatchlistEntryEntity e) {
            return new EntryView(e.getId(), e.getKind(), e.getDisplayValue(), e.getReason(), e.getCreatedAt());
        }
    }

    // ---------- filter lifecycle ----------

    /** Builds the filter from every watchlist row. O(n * k). */
    public synchronized void rebuild() {
        BloomFilter fresh = new BloomFilter(capacity, targetRate);
        for (WatchlistEntryEntity e : repo.findAll()) {
            fresh.add(e.getValueKey());
        }
        filter = fresh;
        builtAt = Instant.now();
    }

    private BloomFilter filter() {
        if (filter == null) {
            rebuild();
        }
        return filter;
    }

    /** Changes the filter's size and target error rate (admin), then rebuilds it. */
    public synchronized Stats configure(int newCapacity, double newRate) {
        if (newCapacity < 100 || newCapacity > 10_000_000) {
            throw new InvalidInputException("Planned entries must be between 100 and 10,000,000.");
        }
        if (!(newRate >= 0.0001 && newRate <= 0.5)) {
            throw new InvalidInputException("Target false-positive rate must be between 0.01% and 50%.");
        }
        capacity = newCapacity;
        targetRate = newRate;
        rebuild();
        audit.log("BLOOM_CONFIGURED", "WATCHLIST", null,
                "Filter resized for " + newCapacity + " entries at " + (newRate * 100) + "% target rate");
        return stats();
    }

    // ---------- entries ----------

    @Transactional
    public synchronized EntryView add(WatchKind kind, String value, String reason) {
        String key = ContactKeys.of(kind, value);
        if (repo.existsByValueKey(key)) {
            throw new ConflictException(ContactKeys.display(key) + " is already on the watchlist.");
        }
        String why = reason == null || reason.isBlank() ? "No reason recorded" : reason.trim();
        WatchlistEntryEntity saved = repo.save(new WatchlistEntryEntity(kind, key, ContactKeys.display(key), why));
        filter().add(key);
        audit.log("WATCHLIST_ADDED", "WATCHLIST", ContactKeys.display(key), why);
        return EntryView.of(saved);
    }

    /**
     * Bulk add for seeding an empty watchlist: duplicates within the batch are dropped in memory, so no
     * per-entry database lookup is needed. Adds each key to the filter.
     */
    @Transactional
    public synchronized int addAll(List<WatchlistEntryEntity> entries) {
        java.util.Map<String, WatchlistEntryEntity> unique = new java.util.LinkedHashMap<>();
        for (WatchlistEntryEntity e : entries) {
            unique.putIfAbsent(e.getValueKey(), e);
        }
        if (repo.count() > 0) {
            unique.values().removeIf(e -> repo.existsByValueKey(e.getValueKey()));
        }
        repo.saveAll(unique.values());
        unique.keySet().forEach(filter()::add);
        return unique.size();
    }

    @Transactional
    public synchronized void remove(long id) {
        WatchlistEntryEntity e = repo.findById(id).orElseThrow(() -> new NotFoundException("No watchlist entry " + id + "."));
        repo.delete(e);
        repo.flush();
        rebuild();   // bits can't be cleared: one bit may belong to several entries
        audit.log("WATCHLIST_REMOVED", "WATCHLIST", e.getDisplayValue(), "Removed; filter rebuilt");
    }

    public Page<EntryView> list(String q, int page, int size) {
        return repo.findAll(WatchlistRepository.matches(q),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("createdAt").descending()))
                .map(EntryView::of);
    }

    // ---------- screening ----------

    /** Screens whichever contact details are given; at least one is required. */
    public synchronized ScreeningResult screen(String email, String phone) {
        String emailKey = ContactKeys.email(email);
        String phoneKey = ContactKeys.phone(phone);
        if (emailKey == null && phoneKey == null) {
            throw new InvalidInputException("Enter an email or a phone number to screen.");
        }
        return screenKeys(emailKey, phoneKey);
    }

    /** Same as screen() for keys that are already normalised (the reservation pipeline). */
    public synchronized ScreeningResult screenKeys(String emailKey, String phoneKey) {
        List<FieldCheck> checks = new ArrayList<>();
        if (emailKey != null) {
            checks.add(check(WatchKind.EMAIL, emailKey));
        }
        if (phoneKey != null) {
            checks.add(check(WatchKind.PHONE, phoneKey));
        }
        Optional<FieldCheck> hit = checks.stream().filter(FieldCheck::listed).findFirst();
        if (hit.isPresent()) {
            return new ScreeningResult("MATCH", "On the watchlist: the " + hit.get().field().name().toLowerCase()
                    + " matches an entry (" + hit.get().reason() + ").", checks);
        }
        boolean flagged = checks.stream().anyMatch(c -> c.outcome().equals("FALSE_POSITIVE"));
        return new ScreeningResult("CLEAR", flagged
                ? "Clear. The quick filter flagged a possible match, but the full watchlist has no record of it."
                : "Clear. Not on the watchlist.", checks);
    }

    private FieldCheck check(WatchKind kind, String key) {
        screened++;
        BloomTrace trace = filter().check(key);
        if (!trace.possiblyPresent()) {
            clearedByFilter++;
            return new FieldCheck(kind, ContactKeys.display(key), trace, false, false, "CLEARED_BY_FILTER", null);
        }
        exactLookups++;
        Optional<WatchlistEntryEntity> entry = repo.findByValueKey(key);
        if (entry.isEmpty()) {
            falsePositives++;
            return new FieldCheck(kind, ContactKeys.display(key), trace, true, false, "FALSE_POSITIVE", null);
        }
        confirmed++;
        return new FieldCheck(kind, ContactKeys.display(key), trace, true, true, "CONFIRMED", entry.get().getReason());
    }

    /** @param tries how many generated emails were tried before finding one */
    public record FalsePositive(String email, int tries, BloomTrace bloom) {
    }

    /**
     * Searches generated emails that are not on the watchlist for one the filter still reports as "possibly
     * listed": a real false positive of the live filter, useful for demonstrating the exact check.
     */
    public synchronized FalsePositive findFalsePositive() {
        BloomFilter f = filter();
        for (int i = 0; i < 3_000_000; i++) {
            String email = "visitor" + i + "@example.org";
            String key = "email:" + email;
            if (f.mightContain(key) && !repo.existsByValueKey(key)) {
                return new FalsePositive(email, i + 1, f.check(key));
            }
        }
        throw new ConflictException("No false positive found in 3,000,000 tries: the filter is very sparse.");
    }

    /** Membership in the filter only, without counting it as a screening (used by the lab). */
    public synchronized boolean filterSays(String key) {
        return filter().mightContain(key);
    }

    public synchronized Stats stats() {
        BloomFilter f = filter();
        int entries = (int) repo.count();
        return new Stats(capacity, targetRate, f.bitCount(), f.hashCount(), entries, f.setBitCount(),
                f.expectedFalsePositiveRate(), entries > capacity, builtAt, screened, clearedByFilter,
                exactLookups, confirmed, falsePositives);
    }

    public synchronized void resetCounters() {
        screened = clearedByFilter = exactLookups = confirmed = falsePositives = 0;
    }
}
