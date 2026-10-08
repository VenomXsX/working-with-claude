package com.marlowefinch.ops;

import java.util.ArrayList;
import java.util.List;

/** Collects validation errors across the query parameters of one request (TODO-232). */
public final class QueryParams {

    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 500;

    private final List<String> errors = new ArrayList<>();

    List<String> errors() {
        return errors;
    }

    /** Parses limit; a missing or blank value yields {@code defaultValue}. Returns the default after recording an error. */
    public int limit(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= MIN_LIMIT && value <= MAX_LIMIT) {
                return value;
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        errors.add("limit must be an integer between " + MIN_LIMIT + " and " + MAX_LIMIT);
        return defaultValue;
    }

    /** Throws {@link InvalidRequestException} if any error was recorded. */
    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
    }
}
