package com.klaro.dto;

import java.math.BigDecimal;

/**
 * The headline numbers for the top of the Overview page, for the current month.
 *
 * spentThisMonth          receipts dated this month + bills due this month
 * averageDailySpending    receipts so far / days elapsed (bills are left out because they are one-off)
 * projectedMonthTotal     averageDailySpending x days in the month + bills due this month
 * projectedChangePercent  projectedMonthTotal compared with last month's total; null if last month was 0
 * averageMonthlySpending  average of the last complete months (up to 6) since the first record;
 *                         null when there is no complete month yet (monthsAveraged = 0)
 * topCategory             the category with the most spending this month; null if nothing yet
 * nextBillDue             the soonest unpaid bill that is not overdue; null if none
 */
public record AnalyticsSummary(
        String month,
        BigDecimal spentThisMonth,
        BigDecimal receiptsThisMonth,
        BigDecimal billsThisMonth,
        BigDecimal spentLastMonth,
        BigDecimal averageDailySpending,
        BigDecimal projectedMonthTotal,
        Double projectedChangePercent,
        BigDecimal averageMonthlySpending,
        int monthsAveraged,
        CategoryTotal topCategory,
        int unpaidBills,
        BigDecimal unpaidBillsTotal,
        int overdueBills,
        UpcomingBill nextBillDue,
        int budgetsTracked,
        int budgetsOver,
        int budgetsNearLimit) {
}
