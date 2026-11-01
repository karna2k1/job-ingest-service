package com.jobradar.ingest.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobradar.ingest.source.RawJob;
import org.junit.jupiter.api.Test;

class JobNormalizerTest {
    private final JobNormalizer normalizer = new JobNormalizer();

    @Test
    void sameJobOnTwoBoardsHasSameHash() {
        assertThat(JobNormalizer.dedupeHash("Acme Inc.", "Senior Java Developer", "Bengaluru"))
                .isEqualTo(JobNormalizer.dedupeHash("acme inc", "Senior  Java Developer ", "BENGALURU"));
    }

    @Test
    void stripsHtmlAndUsesSourceOverride() {
        var raw = new RawJob("1", "Acme", "Java Dev", null, false, "https://x/1",
                "<p>Build <b>Spring Boot</b> services</p>", null, null, "LINKEDIN_ALERT");
        var job = normalizer.toJob("EMAIL_ALERT", raw);
        assertThat(job.getDescription()).isEqualTo("Build Spring Boot services");
        assertThat(job.getSource()).isEqualTo("LINKEDIN_ALERT");
    }

    @Test
    void rejectsJobsWithoutUrl() {
        assertThat(normalizer.toJob("X", new RawJob("1", "A", "Dev", null, false, " ", null, null, null))).isNull();
    }
}
