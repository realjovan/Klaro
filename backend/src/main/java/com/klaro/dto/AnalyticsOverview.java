package com.klaro.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the analytics views need for one period: the last {@code months}
 * months, ending with the current month ({@code from} to {@code to}, "yyyy-MM").
 *
 * totalExpenses           receipts + bills in the period
 * recordCount             number of receipts and bills counted
 * monthsCounted           months used for the average: the period, but not
 *                         before the month of the user's first record
 * averageMonthlyExpenses  totalExpenses / monthsCounted; null when there is no data
 * highestMonth/lowestMonth the months with the most / least spending, among
 *                         months that have any; null when there is no data
 * byMonth                 every month of the period, oldest first (0 when empty)
 * byCategory              spending per category in the period, largest first
 * utilities               consumption and cost per utility type in the period
 */
public record AnalyticsOverview(
        int months,
        String from,
        String to,
        BigDecimal totalExpenses,
        BigDecimal receiptsTotal,
        BigDecimal billsTotal,
        int recordCount,
        int monthsCounted,
        BigDecimal averageMonthlyExpenses,
        MonthlyTotal highestMonth,
        MonthlyTotal lowestMonth,
        List<MonthlySpending> byMonth,
        List<CategoryTotal> byCategory,
        List<UtilityStats> utilities) {
}
