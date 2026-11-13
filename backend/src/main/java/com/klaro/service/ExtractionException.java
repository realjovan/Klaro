package com.klaro.service;

/**
 * Thrown when a document could not be read (OCR not configured, unsupported
 * file type, Lumen error or timeout). ApiErrorHandler turns it into a 502 with
 * a readable message, and the front-end falls back to manual entry.
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
