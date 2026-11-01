package com.jobradar.ingest.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingest")
public record IngestProperties(
        Duration pollInterval,
        Duration initialDelay,
        String titleInclude,
        String locationInclude,
        List<String> greenhouseBoards,
        List<String> leverCompanies,
        List<String> ashbyBoards,
        boolean remotiveEnabled,
        Adzuna adzuna,
        Jsearch jsearch,
        Email email) {

    public record Jsearch(String apiKey, List<String> queries, String country, String datePosted, Duration minInterval) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank() && queries != null && !queries.isEmpty();
        }
    }

    public record Adzuna(String appId, String appKey, String country, String what) {
        public boolean enabled() {
            return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
        }
    }

    public record Email(String host, String username, String password, String folder) {
        public boolean enabled() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
}
