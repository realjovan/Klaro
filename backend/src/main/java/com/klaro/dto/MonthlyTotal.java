package com.klaro.dto;

import java.math.BigDecimal;

/** Total for one month. month is formatted "yyyy-MM", e.g. "2026-10". */
public record MonthlyTotal(String month, BigDecimal total) {
}
