package com.klaro.dto;

/**
 * The assistant's answer. Send sessionId back with the next question to keep
 * the same conversation. degraded is true when one of the assistant's sources
 * was unavailable for this answer.
 */
public record ChatReply(String reply, String sessionId, boolean degraded) {
}
