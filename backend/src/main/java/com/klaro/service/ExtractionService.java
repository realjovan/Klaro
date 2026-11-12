package com.klaro.service;

import com.klaro.dto.ExtractedDocument;

/**
 * Reads an uploaded receipt or bill (image/PDF) and returns structured fields.
 *
 * To implement the real version: create a new @Service class implementing this
 * interface, then delete StubExtractionService (two @Service beans of the same
 * type would conflict).
 */
public interface ExtractionService {
    ExtractedDocument extract(byte[] file, String contentType);
}
