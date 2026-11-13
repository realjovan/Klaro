package com.klaro.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cost and consumption for one kind of utility (electricity, water, internet, ...).
 * Months are billing months (the end of the billing period, or the due date if no period is set).
 *
 * latest*            the most recent month with a bill
 * *ChangePercent     latest month compared with the month before it that had a bill; null if no earlier month
 * averageMonthly*    average over the months that had a bill (usage: months that had a reading)
 * totalUsage         all consumption in the period, in usageUnit
 * monthsWithReadings months that had a consumption amount
 * averageCostPerUnit total cost / total consumption, e.g. pesos per kWh; null without consumption data
 * Usage fields are null when no bill of this type has a consumption amount.
 */
public record UtilityStats(
        String type,
        int billsCounted,
        int monthsWithBills,
        String latestMonth,
        BigDecimal latestAmount,
        BigDecimal averageMonthlyCost,
        Double costChangePercent,
        BigDecimal latestUsage,
        BigDecimal averageMonthlyUsage,
        Double usageChangePercent,
        BigDecimal totalUsage,
        int monthsWithReadings,
        String usageUnit,
        BigDecimal averageCostPerUnit,
        List<MonthlyTotal> costByMonth) {
}
