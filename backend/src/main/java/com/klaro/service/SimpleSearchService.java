package com.klaro.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.klaro.model.Receipt;
import com.klaro.repository.ReceiptRepository;

/** Basic placeholder: case-insensitive match on merchant, category and OCR text. */
@Service
public class SimpleSearchService implements SearchService {

    private final ReceiptRepository receipts;

    public SimpleSearchService(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Override
    public List<Receipt> search(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return receipts.findAll().stream()
                .filter(r -> contains(r.getMerchant(), q)
                        || (r.getCategory() != null && contains(r.getCategory().name(), q))
                        || contains(r.getRawText(), q))
                .toList();
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }
}
