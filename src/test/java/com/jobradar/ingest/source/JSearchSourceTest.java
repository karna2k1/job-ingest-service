package com.jobradar.ingest.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.ingest.config.IngestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JSearchSourceTest {
    private static final String RESPONSE = """
            {"status":"OK","data":{"cursor":"abc","jobs":[
              {"job_id":"abc123","employer_name":"Acme India","job_title":"Java Full Stack Developer",
               "job_city":"Bengaluru","job_state":"Karnataka","job_country":"IN","job_is_remote":false,
               "job_apply_link":"https://www.linkedin.com/jobs/view/123","job_publisher":"LinkedIn",
               "job_description":"Spring Boot and React","job_posted_at_datetime_utc":"2026-11-01T10:00:00.000Z",
               "job_min_salary":1200000,"job_max_salary":1800000,"job_salary_currency":"INR","job_salary_period":"YEAR"},
              {"job_id":"def456","employer_name":"Globex","job_title":"React Developer","job_country":"IN",
               "job_is_remote":true,"job_apply_link":"https://www.naukri.com/job-listings-x-456","job_publisher":"Naukri.com"}
            ]}}""";

    @Test
    void mapsJobsAndTagsPublisher() throws Exception {
        SourceHttp http = mock(SourceHttp.class);
        when(http.getJson(anyString(), anyMap(), any(Object[].class))).thenReturn(new ObjectMapper().readTree(RESPONSE));
        var props = new IngestProperties(Duration.ZERO, Duration.ZERO, ".*", "", List.of(), List.of(), List.of(), false, null,
                new IngestProperties.Jsearch("key", List.of("java developer in India"), "in", "today", Duration.ofHours(12)), null);

        List<RawJob> jobs = new JSearchSource(http, props).fetch();

        assertThat(jobs).hasSize(2);
        RawJob first = jobs.get(0);
        assertThat(first.company()).isEqualTo("Acme India");
        assertThat(first.location()).isEqualTo("Bengaluru, Karnataka, India");
        assertThat(first.source()).isEqualTo("JSEARCH_LINKEDIN");
        assertThat(first.salary()).isEqualTo("1200000 - 1800000 INR YEAR");
        assertThat(first.postedAt()).isNotNull();
        assertThat(jobs.get(1).location()).isEqualTo("India");
        assertThat(jobs.get(1).remote()).isTrue();
        assertThat(jobs.get(1).source()).isEqualTo("JSEARCH_NAUKRI_COM");
    }
}
