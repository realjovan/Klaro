package com.klaro.service;

import java.util.List;

import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MonthlyTotal;

/**
 * Spending and utility analytics.
 *
 * To implement the real version: create a new @Service class implementing this
 * interface, then delete StubAnalyticsService.
 */
public interface AnalyticsService {

    /** Total receipt spending per category. */
    List<CategoryTotal> spendingByCategory();

    /** Total receipt spending per month. */
    List<MonthlyTotal> spendingByMonth();

    /** Total utility bill amount per month. */
    List<MonthlyTotal> utilitiesByMonth();
}
