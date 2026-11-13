package com.klaro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An unpaid bill. daysUntilDue is negative when the bill is overdue. */
public record UpcomingBill(Long id, String merchant, String type, LocalDate dueDate, BigDecimal amount,
        long daysUntilDue, boolean overdue) {
}
