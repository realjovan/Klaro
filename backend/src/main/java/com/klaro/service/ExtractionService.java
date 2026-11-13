package com.klaro.service;

import com.klaro.dto.ExtractedDocument;

/**
 * Reads an uploaded receipt or bill (image/PDF) and returns structured fields.
 * Throws ExtractionException when the document can't be read.
 *
 * The current implementation is LumenExtractionService (Lumen OCR + simple
 * text rules). To swap it, write another @Service implementing this interface
 * and delete LumenExtractionService (two would conflict).
 */
public interface ExtractionService {
    ExtractedDocument extract(byte[] file, String contentType, String fileName);
}
