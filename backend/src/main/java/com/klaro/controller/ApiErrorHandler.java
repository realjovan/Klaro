package com.klaro.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.klaro.service.ChatException;
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

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> chatFailed(ChatException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage(), "code", ex.getCode(), "retryable", ex.isRetryable()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> fileTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("error", "File is too large. The limit is 10 MB.");
    }
}
