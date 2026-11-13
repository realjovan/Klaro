package com.klaro.dto;

import java.math.BigDecimal;

import com.klaro.model.Category;

/**
 * Spending in one category for a month, e.g. FOOD = 4,250.00 (32.5% of the month, 6 items).
 * Utility bills count under UTILITIES.
 */
public record CategoryTotal(Category category, BigDecimal total, double sharePercent, int count) {
}
