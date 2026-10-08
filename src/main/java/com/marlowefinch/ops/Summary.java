package com.marlowefinch.ops;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The stand-up line: the four KPIs plus the lowest on-time carrier and the busiest ticket category.
 * {@code onTimeRate}, {@code worstCarrier} and {@code busiestTicketCategory} are null when the range has no data.
 */
public record Summary(
        LocalDate from,
        LocalDate to,
        Double onTimeRate,
        long openTickets,
        BigDecimal revenue,
        long orders,
        String worstCarrier,
        String busiestTicketCategory) {
}
