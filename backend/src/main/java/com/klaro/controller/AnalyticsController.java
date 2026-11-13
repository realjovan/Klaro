package com.klaro.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.klaro.dto.AnalyticsSummary;
import com.klaro.dto.BudgetStatus;
import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MerchantTotal;
import com.klaro.dto.MonthlySpending;
import com.klaro.dto.MonthlyTotal;
import com.klaro.dto.UpcomingBill;
import com.klaro.dto.UtilityStats;
import com.klaro.service.AnalyticsService;

/**
 * Analytics endpoints. All amounts are in PHP. Field meanings are documented on
 * the DTO records in com.klaro.dto.
 *
 *   GET /api/analytics/summary                          headline numbers for the Overview
 *   GET /api/analytics/spending/by-category?month=2026-10   per category (default: this month)
 *   GET /api/analytics/spending/by-month?months=6       per month, oldest first
 *   GET /api/analytics/budgets?month=2026-10            budget vs spending (default: this month)
 *   GET /api/analytics/utilities?months=6               cost and consumption per utility type
 *   GET /api/analytics/utilities/by-month?months=6      total utility bills per month
 *   GET /api/analytics/merchants/top?months=3&limit=5   where the most money goes
 *   GET /api/analytics/bills/upcoming?days=30           unpaid bills: overdue or due soon
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public AnalyticsSummary summary() {
        return analytics.summary();
    }

    @GetMapping("/spending/by-category")
    public List<CategoryTotal> spendingByCategory(@RequestParam(required = false) String month) {
        return analytics.spendingByCategory(parseMonth(month));
    }

    @GetMapping("/spending/by-month")
    public List<MonthlySpending> spendingByMonth(@RequestParam(defaultValue = "6") int months) {
        return analytics.spendingByMonth(clamp(months, 1, 24));
    }

    @GetMapping("/budgets")
    public List<BudgetStatus> budgets(@RequestParam(required = false) String month) {
        return analytics.budgets(parseMonth(month));
    }

    @GetMapping("/utilities")
    public List<UtilityStats> utilities(@RequestParam(defaultValue = "6") int months) {
        return analytics.utilities(clamp(months, 1, 24));
    }

    @GetMapping("/utilities/by-month")
    public List<MonthlyTotal> utilitiesByMonth(@RequestParam(defaultValue = "6") int months) {
        return analytics.utilitiesByMonth(clamp(months, 1, 24));
    }

    @GetMapping("/merchants/top")
    public List<MerchantTotal> topMerchants(@RequestParam(defaultValue = "3") int months,
            @RequestParam(defaultValue = "5") int limit) {
        return analytics.topMerchants(clamp(months, 1, 24), clamp(limit, 1, 20));
    }

    @GetMapping("/bills/upcoming")
    public List<UpcomingBill> upcomingBills(@RequestParam(defaultValue = "30") int days) {
        return analytics.upcomingBills(clamp(days, 0, 365));
    }

    // "2026-10" -> October 2026. Missing means the current month.
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return null;
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must look like 2026-10");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
