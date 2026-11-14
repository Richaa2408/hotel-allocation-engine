package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.model.Enums.WatchKind;

import java.util.Locale;

/**
 * Normalises guest contact details so the same person matches however they typed them:
 * "Ravi@Mail.com " and "ravi@mail.com" give the same key, as do "+91 98765-43210" and "919876543210".
 * The "email:" / "phone:" prefix keeps an email and a phone with the same text apart.
 */
public final class ContactKeys {

    private ContactKeys() {
    }

    /** "email:ravi@mail.com", or null if blank. Throws if it isn't an email address. */
    public static String email(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1
                || normalized.contains(" ")) {
            throw new InvalidInputException("\"" + raw.trim() + "\" is not a valid email address.");
        }
        return "email:" + normalized;
    }

    /** "phone:919876543210", or null if blank. Throws unless it has 7 to 15 digits. */
    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 7 || digits.length() > 15) {
            throw new InvalidInputException("Phone numbers need 7 to 15 digits.");
        }
        return "phone:" + digits;
    }

    public static String of(WatchKind kind, String raw) {
        String key = kind == WatchKind.EMAIL ? email(raw) : phone(raw);
        if (key == null) {
            throw new InvalidInputException("Enter a value.");
        }
        return key;
    }

    /** The part after the prefix: what staff see. */
    public static String display(String key) {
        return key == null ? null : key.substring(key.indexOf(':') + 1);
    }
}
