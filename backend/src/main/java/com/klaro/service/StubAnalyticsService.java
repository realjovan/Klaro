package com.klaro.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MonthlyTotal;

/** Placeholder that returns fixed fake numbers. Replace with real queries. */
@Service
public class StubAnalyticsService implements AnalyticsService {

    @Override
    public List<CategoryTotal> spendingByCategory() {
        return List.of(
                new CategoryTotal("Food", new BigDecimal("4250.00")),
                new CategoryTotal("Transport", new BigDecimal("1800.00")),
                new CategoryTotal("Utilities", new BigDecimal("3600.00")));
    }

    @Override
    public List<MonthlyTotal> spendingByMonth() {
        return List.of(
                new MonthlyTotal("2026-09", new BigDecimal("8900.00")),
                new MonthlyTotal("2026-10", new BigDecimal("9650.00")));
    }

    @Override
    public List<MonthlyTotal> utilitiesByMonth() {
        return List.of(
                new MonthlyTotal("2026-09", new BigDecimal("3400.00")),
                new MonthlyTotal("2026-10", new BigDecimal("3600.00")));
    }
}
