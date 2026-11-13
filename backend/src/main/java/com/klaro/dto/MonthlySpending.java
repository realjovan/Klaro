package com.klaro.dto;

import java.math.BigDecimal;

/** Spending in one month ("yyyy-MM"), split into receipts and bills. total = receipts + bills. */
public record MonthlySpending(String month, BigDecimal receipts, BigDecimal bills, BigDecimal total) {
}
