package com.klaro.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MonthlyTotal;
import com.klaro.service.AnalyticsService;

/**
 * Analytics endpoints:
 *   GET /api/analytics/spending/by-category
 *   GET /api/analytics/spending/by-month
 *   GET /api/analytics/utilities/by-month
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/spending/by-category")
    public List<CategoryTotal> spendingByCategory() {
        return analytics.spendingByCategory();
    }

    @GetMapping("/spending/by-month")
    public List<MonthlyTotal> spendingByMonth() {
        return analytics.spendingByMonth();
    }

    @GetMapping("/utilities/by-month")
    public List<MonthlyTotal> utilitiesByMonth() {
        return analytics.utilitiesByMonth();
    }
}
