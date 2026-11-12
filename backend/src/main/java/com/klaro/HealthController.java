package com.klaro;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple "is the server alive?" endpoint.
 *
 * Purpose: lets you (or the front-end, or a hosting platform) confirm the
 * backend started correctly without touching the database or any other logic.
 *
 * Try it: GET http://localhost:8080/api/health
 */
// @RestController tells Spring this class handles web requests and that
// returned objects should be sent back as JSON.
@RestController
// Every endpoint in this class lives under the /api prefix.
@RequestMapping("/api")
public class HealthController {

    // Handles GET /api/health.
    // Map.of(...) is turned into JSON automatically:
    // {"status":"ok","service":"klaro-backend"}
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "klaro-backend");
    }
}
