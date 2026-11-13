package com.klaro.dto;

import java.math.BigDecimal;

/** How much was spent at one merchant, and on how many receipts. */
public record MerchantTotal(String merchant, BigDecimal total, int visits) {
}
