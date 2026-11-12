package com.klaro.dto;

import java.math.BigDecimal;

/** Total spent in one category, e.g. Food = 4,250.00. */
public record CategoryTotal(String category, BigDecimal total) {
}
