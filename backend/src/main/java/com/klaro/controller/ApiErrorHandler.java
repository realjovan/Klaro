package com.klaro.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.klaro.service.ExtractionException;

/**
 * Turns errors into readable JSON responses the front-end can show:
 *   validation failure -> 400 {"errors":{"amountPaid":"must be greater than 0"}}
 *   document unreadable -> 502 {"error":"Could not reach the OCR service."}
 *   file too large      -> 413 {"error":"File is too large. The limit is 10 MB."}
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Map<String, String>> validationFailed(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return Map.of("errors", errors);
    }

    @ExceptionHandler(ExtractionException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> extractionFailed(ExtractionException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> fileTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("error", "File is too large. The limit is 10 MB.");
    }
}
