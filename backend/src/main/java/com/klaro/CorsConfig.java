package com.klaro;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS (Cross-Origin Resource Sharing) for the API.
 *
 * Why it exists: browsers block a web page from calling a server on a
 * different origin (a different host or port) unless the server explicitly
 * allows it. During development, index.html may be opened from a file or a
 * local dev server (e.g. http://localhost:5500) while this backend runs on
 * http://localhost:8080, so without this config the browser would reject
 * the front-end's calls to /api.
 *
 * Only localhost origins are allowed, so this is not open to the whole web.
 * Revisit the allowed origins before deploying to production.
 */
// @Configuration marks this class as a source of Spring settings,
// which Spring reads once at startup.
@Configuration
public class CorsConfig {

    // @Bean registers the returned object with Spring so it gets applied.
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**") // apply only to API endpoints
                        // Allowed origins: any localhost port, plus "null",
                        // which is what browsers send when index.html is
                        // opened directly from disk (file://).
                        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "null")
                        // HTTP methods the front-end may use.
                        // OPTIONS is the browser's automatic "preflight" check.
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
