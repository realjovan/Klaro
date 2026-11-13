package com.klaro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One chat question from the page.
 * documentId: the selected document, e.g. "receipt-12" or "bill-3" (optional).
 * sessionId:  the Lumen session from the previous reply; omit to start a new conversation.
 * language:   "en" or "tl" (Tagalog). Defaults to English.
 */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String question,
        String documentId,
        String sessionId,
        String language) {
}
