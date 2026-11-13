package com.klaro.dto;

import java.math.BigDecimal;

import com.klaro.model.Category;

/**
 * How a category is doing against its monthly budget.
 * remaining is negative when over budget.
 * status: OK (under 80%), NEAR_LIMIT (80-100%), OVER (above 100%).
 */
public record BudgetStatus(Category category, BigDecimal monthlyLimit, BigDecimal spent,
        BigDecimal remaining, double percentUsed, String status) {
}
