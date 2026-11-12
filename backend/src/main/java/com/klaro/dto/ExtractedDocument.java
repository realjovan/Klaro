package com.klaro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.klaro.model.Category;

/**
 * What the OCR/extraction step returns for one uploaded file.
 * type is "RECEIPT" or "BILL". Fields that could not be read are null.
 * category must be one of the fixed values in Category (use OTHER if unsure).
 */
public record ExtractedDocument(
        String type,
        String merchant,
        LocalDate date,
        BigDecimal total,
        String currency,
        Category category,
        String rawText,
        Double confidence) {
}
