package com.klaro.service;

import org.springframework.stereotype.Service;

import com.klaro.dto.ExtractedDocument;

/** Reads documents with Lumen OCR, then pulls out the fields with ReceiptTextParser. */
@Service
public class LumenExtractionService implements ExtractionService {

    private final LumenOcrClient lumen;

    public LumenExtractionService(LumenOcrClient lumen) {
        this.lumen = lumen;
    }

    @Override
    public ExtractedDocument extract(byte[] file, String contentType, String fileName) {
        return ReceiptTextParser.parse(lumen.read(file, contentType, safeName(fileName)));
    }

    // Lumen gets the name in a header, so keep it to plain ASCII characters.
    private static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "upload";
        String name = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }
}
