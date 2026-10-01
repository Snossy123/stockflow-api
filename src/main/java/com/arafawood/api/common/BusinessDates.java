package com.arafawood.api.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class BusinessDates {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private BusinessDates() {}

    public static LocalDate today() {
        return LocalDate.now(CAIRO);
    }

    public static LocalDate resolvePostingDate(LocalDate docDate) {
        LocalDate today = today();
        return today.equals(docDate) ? docDate : today;
    }

    public static Instant occurredAt(LocalDate postingDate) {
        if (postingDate == null || today().equals(postingDate)) {
            return Instant.now();
        }
        return postingDate.atStartOfDay(CAIRO).toInstant();
    }
}
