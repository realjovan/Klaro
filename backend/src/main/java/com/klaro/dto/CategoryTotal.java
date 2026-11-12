package com.klaro.dto;

import java.math.BigDecimal;

import com.klaro.model.Category;

/** Total spent in one category, e.g. FOOD = 4,250.00. */
public record CategoryTotal(Category category, BigDecimal total) {
}
