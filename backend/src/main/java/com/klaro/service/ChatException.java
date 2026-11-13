package com.klaro.service;

import org.springframework.http.HttpStatus;

/**
 * A chat turn that failed. code is Lumen's error code when there is one (e.g.
 * AI_CHAT_REPLY_TIMEOUT), or our own (AI_NOT_CONFIGURED). The message is safe
 * to show to the user. ApiErrorHandler turns this into a JSON response.
 */
public class ChatException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final boolean retryable;

    public ChatException(String code, HttpStatus status, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
