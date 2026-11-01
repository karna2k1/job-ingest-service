package com.jobradar.ingest.source;

import java.time.Instant;

/**
 * What a JobSource returns before normalisation and dedupe. description may be HTML.
 * source overrides the adapter name when one adapter yields several origins (e.g. LINKEDIN vs NAUKRI alerts).
 */
public record RawJob(String externalId, String company, String title, String location, boolean remote,
                     String url, String description, String salary, Instant postedAt, String source) {

    public RawJob(String externalId, String company, String title, String location, boolean remote,
                  String url, String description, String salary, Instant postedAt) {
        this(externalId, company, title, location, remote, url, description, salary, postedAt, null);
    }
}
