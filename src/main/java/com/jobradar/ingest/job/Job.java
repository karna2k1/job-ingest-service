package com.jobradar.ingest.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String salary;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "dedupe_hash", nullable = false, unique = true, length = 64)
    private String dedupeHash;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;
}
