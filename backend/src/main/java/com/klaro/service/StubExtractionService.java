package com.klaro.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.klaro.dto.ExtractedDocument;
import com.klaro.model.Category;

/** Placeholder that returns fixed fake data. Replace with the real OCR version. */
@Service
public class StubExtractionService implements ExtractionService {

    @Override
    public ExtractedDocument extract(byte[] file, String contentType) {
        return new ExtractedDocument(
                "RECEIPT", "Sample Store", LocalDate.now(), new BigDecimal("123.45"),
                "PHP", Category.FOOD, "SAMPLE STORE TOTAL 123.45", 0.5);
    }
}
