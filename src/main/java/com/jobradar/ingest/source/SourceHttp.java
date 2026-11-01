package com.jobradar.ingest.source;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP + parsing helpers shared by adapters. getJson retries with exponential backoff (resilience4j "sources"). */
@Component
@RequiredArgsConstructor
public class SourceHttp {
    private final RestClient http;

    @Retry(name = "sources")
    public JsonNode getJson(String uri, Object... vars) {
        return http.get().uri(uri, vars).retrieve().body(JsonNode.class);
    }

    @Retry(name = "sources")
    public JsonNode getJson(String uri, java.util.Map<String, String> headers, Object... vars) {
        return http.get().uri(uri, vars).headers(h -> headers.forEach(h::set)).retrieve().body(JsonNode.class);
    }

    public static Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(v).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    public static boolean looksRemote(String location) {
        return location != null && location.toLowerCase(Locale.ROOT).contains("remote");
    }

    public static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    public static String titleCase(String slug) {
        if (slug == null || slug.isBlank()) return slug;
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}
