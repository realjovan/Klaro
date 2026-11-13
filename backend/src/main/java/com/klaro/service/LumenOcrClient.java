package com.klaro.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Calls the Lumen OCR API (https://sandbox.docs.lumensuite.io/explorer/ocr).
 *
 * Reading is asynchronous on Lumen's side, so read() does three steps:
 *   1. POST /documents with the raw file          -> DocumentID, Status PENDING
 *   2. GET  /documents/{id} until READY or PARTIAL -> polling every 2 seconds, up to 45 seconds
 *   3. GET  /documents/{id}/pages?IncludeWords=true -> the recognised lines
 *
 * Credentials come from LUMEN_API_KEY and LUMEN_API_SECRET (see
 * application.properties). Every call costs Lumen credits: upload 6, each GET 1.
 */
@Component
public class LumenOcrClient {

    private static final Logger log = LoggerFactory.getLogger(LumenOcrClient.class);

    // File types Lumen accepts (anything else is rejected with 415).
    private static final Set<String> SUPPORTED_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/tiff", "image/webp");
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration MAX_WAIT = Duration.ofSeconds(45); // give up after this

    private final RestClient http;
    private final String apiKey;
    private final String apiSecret;

    public LumenOcrClient(@Value("${klaro.lumen.base-url}") String baseUrl,
            @Value("${klaro.lumen.api-key:}") String apiKey,
            @Value("${klaro.lumen.api-secret:}") String apiSecret) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /** Uploads the file, waits for Lumen to read it, and returns its pages. */
    public List<Page> read(byte[] file, String contentType, String name) {
        String type = normalise(contentType);
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new ExtractionException("This file type can't be read. Use a PDF, PNG, JPEG, TIFF or WebP file.");
        }
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            throw new ExtractionException("OCR is not configured. Set LUMEN_API_KEY and LUMEN_API_SECRET.");
        }

        try {
            DocumentInfo doc = http.post().uri("/documents")
                    .headers(this::authenticate)
                    .contentType(MediaType.parseMediaType(type))
                    .header("X-Lumen-Document-Name", name)
                    .body(file)
                    .retrieve().body(DocumentEnvelope.class).data();

            // Poll until Lumen has read the file. A single slow or failed status
            // check is retried instead of failing the whole upload.
            Instant deadline = Instant.now().plus(MAX_WAIT);
            String status = doc.status();
            while (!isFinished(status)) {
                if (Instant.now().isAfter(deadline)) {
                    throw new ExtractionException("Reading the document took too long. Please try again.");
                }
                Thread.sleep(POLL_INTERVAL.toMillis());
                try {
                    status = http.get().uri("/documents/{id}", doc.documentId())
                            .headers(this::authenticate)
                            .retrieve().body(DocumentEnvelope.class).data().status();
                } catch (RestClientException e) {
                    log.warn("Lumen status check failed, retrying: {}", e.getMessage());
                }
            }
            if ("FAILED".equals(status)) {
                throw new ExtractionException("The document could not be read.");
            }

            // Fetch the recognised text, with one retry.
            for (int attempt = 1; ; attempt++) {
                try {
                    List<Page> pages = http.get().uri("/documents/{id}/pages?IncludeWords=true", doc.documentId())
                            .headers(this::authenticate)
                            .retrieve().body(PagesEnvelope.class).data().items();
                    return pages == null ? List.of() : pages;
                } catch (RestClientException e) {
                    if (attempt >= 2) throw e;
                    log.warn("Lumen pages request failed, retrying: {}", e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("Reading the document was interrupted.");
        } catch (RestClientException e) {
            // Log the details for us; the user only sees a short message.
            log.warn("Lumen OCR call failed: {}", e.getMessage());
            throw new ExtractionException("Could not reach the OCR service.", e);
        }
    }

    private void authenticate(HttpHeaders headers) {
        headers.set("lumen-api-key", apiKey);
        headers.set("lumen-api-secret", apiSecret);
    }

    private static boolean isFinished(String status) {
        return "READY".equals(status) || "PARTIAL".equals(status) || "FAILED".equals(status);
    }

    // "image/jpeg; charset=..." -> "image/jpeg", and the common "image/jpg" alias.
    private static String normalise(String contentType) {
        if (contentType == null) return "";
        String type = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(type) ? "image/jpeg" : type;
    }

    // ---- Lumen response shapes (only the fields we use). Every response is {data, requestId}. ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentEnvelope(DocumentInfo data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentInfo(@JsonProperty("DocumentID") String documentId,
            @JsonProperty("Status") String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagesEnvelope(PagesData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagesData(@JsonProperty("Items") List<Page> items) {}

    /** One page: its overall confidence and the recognised lines of text. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(@JsonProperty("PageNumber") int pageNumber,
            @JsonProperty("Confidence") Confidence confidence,
            @JsonProperty("Lines") List<Line> lines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confidence(Double value) {}

    /** A piece of text and where it sits on the page (0-1, top-left origin). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String text, Box box) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Box(double x, double y, double w, double h) {}
}
