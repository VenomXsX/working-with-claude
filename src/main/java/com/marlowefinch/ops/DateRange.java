package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today". {@link #resolve} validates the
 * raw parameters (ISO dates, {@code from <= to}, at most {@link #MAX_SPAN_DAYS} days) and
 * throws {@link InvalidRequestException} listing every problem. The record itself does not
 * validate, so a backwards range can still be built directly. See TODO-232.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;

    /** Maximum difference in days between to and from (exclusive of the start day): 366 allowed, 367 rejected. */
    public static final int MAX_SPAN_DAYS = 366;

    public static DateRange resolve(String from, String to, Clock clock) {
        QueryParams params = new QueryParams();
        DateRange range = resolve(from, to, clock, params);
        params.throwIfInvalid();
        return range;
    }

    /** Like {@link #resolve(String, String, Clock)} but records errors into {@code params}; returns null if invalid. */
    public static DateRange resolve(String from, String to, Clock clock, QueryParams params) {
        List<String> errors = params.errors();
        LocalDate today = LocalDate.now(clock);
        LocalDate end = parse("to", to, today, errors);
        LocalDate start = parse("from", from, today.minusDays(DEFAULT_DAYS), errors);
        if (start == null || end == null) {
            return null;
        }
        if (start.isAfter(end)) {
            errors.add("from must be on or before to");
            return null;
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
            errors.add("the range may span at most " + MAX_SPAN_DAYS + " days");
            return null;
        }
        return new DateRange(start, end);
    }

    private static LocalDate parse(String name, String raw, LocalDate defaultValue, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(name + " must be an ISO date (YYYY-MM-DD)");
            return null;
        }
    }
}
