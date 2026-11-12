package com.klaro.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * A monthly spending limit for one category (e.g. Food: 8,000 PHP).
 * Analytics compares actual spending against these limits, and the advice
 * feature uses the difference. Each instance is one row in the "budget" table.
 */
@Entity
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Must match the category names used on receipts (e.g. Food, Transport).
    @Column(nullable = false)
    private String category;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlyLimit;

    // ISO 4217 code such as PHP or USD.
    @Column(length = 3, nullable = false)
    private String currency = "PHP";

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public String getCurrency() {
        return currency;
    }

    // Used by PUT /api/budgets/{id} so an update targets an existing row.
    public void setId(Long id) {
        this.id = id;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

}
