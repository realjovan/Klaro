package com.klaro.service;

import java.time.YearMonth;
import java.util.List;

import com.klaro.dto.AnalyticsSummary;
import com.klaro.dto.BudgetStatus;
import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MerchantTotal;
import com.klaro.dto.MonthlySpending;
import com.klaro.dto.MonthlyTotal;
import com.klaro.dto.UpcomingBill;
import com.klaro.dto.UtilityStats;

/**
 * Spending and utility analytics, computed from saved receipts, bills and budgets.
 * All amounts are in PHP. A null month means the current month.
 * "months" means a window of that many months ending with the current month.
 */
public interface AnalyticsService {

    /** Headline numbers for the current month (Overview page). */
    AnalyticsSummary summary();

    /** Spending per category in a month, largest first. */
    List<CategoryTotal> spendingByCategory(YearMonth month);

    /** Spending per month, oldest first, including months with no spending. */
    List<MonthlySpending> spendingByMonth(int months);

    /** Each budget compared with that month's spending, closest to the limit first. */
    List<BudgetStatus> budgets(YearMonth month);

    /** Cost and consumption per utility type, most expensive first. */
    List<UtilityStats> utilities(int months);

    /** Total utility bills per billing month, oldest first. */
    List<MonthlyTotal> utilitiesByMonth(int months);

    /** Merchants with the most receipt spending, largest first. */
    List<MerchantTotal> topMerchants(int months, int limit);

    /** Unpaid bills that are overdue or due within the given days, soonest first. */
    List<UpcomingBill> upcomingBills(int days);
}
