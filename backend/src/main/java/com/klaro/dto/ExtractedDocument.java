package com.klaro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the OCR/extraction step returns for one uploaded file.
 * type is "RECEIPT" or "BILL". Fields that could not be read are null.
 */
public record ExtractedDocument(
        String type,
        String merchant,
        LocalDate date,
        BigDecimal total,
        String currency,
        String category,
        String rawText,
        Double confidence) {
}
