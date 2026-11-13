package com.klaro.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the Lumen AI Chat API (staging/test only):
 *   GET  /instances                                   pick the assistant to talk to (1 credit, once)
 *   POST /instances/{id}/sessions                     open a conversation (2 credits)
 *   POST /instances/{id}/sessions/{sessionID}/messages one question -> one answer (5 credits)
 *
 * Uses the same LUMEN_API_KEY / LUMEN_API_SECRET as OCR; the key needs the
 * "AI Chat" grant. Set klaro.lumen.chat-instance-id to choose an instance;
 * otherwise the first active one is used.
 */
@Component
public class LumenChatClient {

    private static final Logger log = LoggerFactory.getLogger(LumenChatClient.class);

    // Lumen error codes look like AI_CHAT_REPLY_TIMEOUT; find one in an error body.
    private static final Pattern ERROR_CODE = Pattern.compile("\\b([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\\b");
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "READY", "ENABLED", "ONLINE", "PUBLISHED");

    private final RestClient http;
    private final String apiKey;
    private final String apiSecret;
    private final String configuredInstanceId;
    private volatile String instanceId; // looked up once, then reused

    public LumenChatClient(@Value("${klaro.lumen.chat-base-url}") String baseUrl,
            @Value("${klaro.lumen.api-key:}") String apiKey,
            @Value("${klaro.lumen.api-secret:}") String apiSecret,
            @Value("${klaro.lumen.chat-instance-id:}") String configuredInstanceId) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        // Lumen gives up on a reply after 25 s and answers 504; wait a bit longer than that.
        timeouts.setReadTimeout(Duration.ofSeconds(35));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.configuredInstanceId = configuredInstanceId;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /** Opens a new conversation and returns its session id. */
    public String openSession(String title) {
        Map<String, Object> body = call(() -> http.post()
                .uri("/instances/{id}/sessions", instanceId())
                .headers(this::authenticate)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title))
                .retrieve().body(Map.class));
        String id = string(map(body.get("Session")).get("id"));
        if (id == null) throw unexpected("open a chat session");
        return id;
    }

    /** Sends one message in a session and returns the assistant's answer. */
    public Answer send(String sessionId, String message) {
        Map<String, Object> body = call(() -> http.post()
                .uri("/instances/{id}/sessions/{sessionID}/messages", instanceId(), sessionId)
                .headers(this::authenticate)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message))
                .retrieve().body(Map.class));
        Map<String, Object> answer = map(body.get("Answer"));
        String text = string(answer.get("answer"));
        if (text == null || text.isBlank()) throw unexpected("get an answer");
        Object degraded = answer.get("degradedSources");
        return new Answer(text.trim(), degraded instanceof List<?> list && !list.isEmpty());
    }

    public record Answer(String text, boolean degraded) {
    }

    // The configured instance, or the first active one the key can use.
    private String instanceId() {
        if (!configuredInstanceId.isBlank()) return configuredInstanceId;
        String cached = instanceId;
        if (cached != null) return cached;

        Map<String, Object> body = call(() -> http.get().uri("/instances")
                .headers(this::authenticate)
                .retrieve().body(Map.class));
        List<Map<String, Object>> instances = list(body.get("Instances"));
        if (instances.isEmpty()) {
            throw new ChatException("NO_INSTANCE", HttpStatus.SERVICE_UNAVAILABLE,
                    "No AI Chat assistant is set up for this Lumen organization yet.", false);
        }
        Map<String, Object> chosen = instances.stream()
                .filter(i -> ACTIVE_STATUSES.contains(String.valueOf(i.get("Status")).toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(instances.get(0));
        String id = string(chosen.get("InstanceID"));
        if (id == null) throw unexpected("find the AI Chat assistant");
        log.info("Using Lumen AI Chat instance {} ({})", id, chosen.get("Name"));
        instanceId = id;
        return id;
    }

    private void authenticate(HttpHeaders headers) {
        headers.set("lumen-api-key", apiKey);
        headers.set("lumen-api-secret", apiSecret);
    }

    // Runs a call and turns any failure into a ChatException with a readable message.
    private Map<String, Object> call(java.util.function.Supplier<Map<?, ?>> request) {
        if (!isConfigured()) {
            throw new ChatException("AI_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "AI chat is not set up. Set LUMEN_API_KEY and LUMEN_API_SECRET.", false);
        }
        try {
            return unwrap(request.get());
        } catch (RestClientResponseException e) {
            throw fromLumen(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Lumen AI Chat unreachable or timed out: {}", e.getMessage());
            throw new ChatException("AI_CHAT_UNREACHABLE", HttpStatus.GATEWAY_TIMEOUT,
                    "Klaro took too long to answer. Please try again.", true);
        } catch (RestClientException e) {
            log.warn("Lumen AI Chat call failed: {}", e.getMessage());
            throw new ChatException("AI_CHAT_FAILED", HttpStatus.BAD_GATEWAY,
                    "Could not reach Klaro's AI right now. Please try again.", true);
        }
    }

    private ChatException fromLumen(int status, String body) {
        Matcher m = ERROR_CODE.matcher(body == null ? "" : body);
        String code = m.find() ? m.group(1) : "HTTP_" + status;
        log.warn("Lumen AI Chat answered {} {}", status, code);
        return switch (code) {
            case "AI_CHAT_REPLY_TIMEOUT" -> new ChatException(code, HttpStatus.GATEWAY_TIMEOUT,
                    "Klaro took too long to answer. Please try again.", true);
            case "AI_CHAT_MODEL_RATE_LIMITED" -> new ChatException(code, HttpStatus.TOO_MANY_REQUESTS,
                    "Klaro is busy right now. Please try again in a moment.", true);
            case "AI_CHAT_MODEL_UPSTREAM_ERROR", "AI_CHAT_MODEL_UNAVAILABLE", "PERMISSIONS_UPDATING" ->
                new ChatException(code, HttpStatus.BAD_GATEWAY,
                        "Klaro's AI is having trouble right now. Please try again shortly.", true);
            case "API_CREDITS_INSUFFICIENT", "LC_INSUFFICIENT" -> new ChatException(code, HttpStatus.SERVICE_UNAVAILABLE,
                    "Klaro's AI is out of Lumen credits. Ask the team to top up.", false);
            case "ACCESS_DENIED", "VIEW_ONLY_ACCESS" -> new ChatException(code, HttpStatus.SERVICE_UNAVAILABLE,
                    "The Lumen API key doesn't have AI Chat access.", false);
            case "AI_CHAT_NOT_CONFIGURED", "FEATURE_DISABLED" -> new ChatException(code, HttpStatus.SERVICE_UNAVAILABLE,
                    "AI chat isn't available on this Lumen environment.", false);
            default -> new ChatException(code, status == 404 || status == 409 || status == 403
                    ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
                    "Could not get an answer from Klaro's AI. Please try again.", true);
        };
    }

    private ChatException unexpected(String what) {
        return new ChatException("AI_CHAT_UNEXPECTED", HttpStatus.BAD_GATEWAY,
                "Could not " + what + ". Please try again.", true);
    }

    // Lumen's other APIs wrap responses as {data, requestId}; accept both shapes.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<?, ?> body) {
        if (body == null) return Map.of();
        Object data = body.get("data");
        return (Map<String, Object>) (data instanceof Map<?, ?> inner ? inner : body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> l
                ? l.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList()
                : List.of();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
