package com.jobradar.ingest.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "source_health")
@Getter
@NoArgsConstructor
public class SourceHealth {
    @Id
    @Column(length = 40)
    private String source;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "last_fetched", nullable = false)
    private int lastFetched;

    @Column(name = "last_new", nullable = false)
    private int lastNew;

    @Column(name = "total_new", nullable = false)
    private long totalNew;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    public SourceHealth(String source) {
        this.source = source;
    }

    public void success(int fetched, int created) {
        Instant now = Instant.now();
        lastRunAt = now;
        lastSuccessAt = now;
        lastError = null;
        lastFetched = fetched;
        lastNew = created;
        totalNew += created;
        consecutiveFailures = 0;
    }

    public void failure(String error) {
        lastRunAt = Instant.now();
        lastError = error == null ? "unknown" : (error.length() > 500 ? error.substring(0, 500) : error);
        lastFetched = 0;
        lastNew = 0;
        consecutiveFailures++;
    }
}
