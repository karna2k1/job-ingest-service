package com.jobradar.ingest.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertEmailParserTest {
    private final AlertEmailParser parser = new AlertEmailParser();

    @Test
    void parsesLinkedInAlertCards() {
        String html = """
                <table><tr><td>
                  <a href="https://www.linkedin.com/comm/jobs/view/4012345678/?trackingId=abc">Senior Java Developer</a>
                  <p>Acme Corp · Bengaluru, Karnataka, India</p>
                  <a href="https://www.linkedin.com/comm/jobs/view/4012345678/?x=1">View job</a>
                </td></tr><tr><td>
                  <a href="https://www.linkedin.com/comm/jobs/view/4099999999/">React Engineer</a>
                  <p>Globex · Remote</p>
                </td></tr></table>
                """;
        List<RawJob> jobs = parser.parse(html);

        assertThat(jobs).hasSize(2);
        RawJob first = jobs.get(0);
        assertThat(first.title()).isEqualTo("Senior Java Developer");
        assertThat(first.url()).isEqualTo("https://www.linkedin.com/jobs/view/4012345678");
        assertThat(first.company()).isEqualTo("Acme Corp");
        assertThat(first.location()).isEqualTo("Bengaluru, Karnataka, India");
        assertThat(first.source()).isEqualTo("LINKEDIN_ALERT");
        assertThat(jobs.get(1).remote()).isTrue();
    }

    @Test
    void parsesNaukriLinksAndIgnoresOthers() {
        String html = """
                <div><a href="https://www.naukri.com/job-listings-full-stack-developer-initech-hyderabad-3-to-5-years-311025009876?src=alert">Full Stack Developer</a>
                <span>Initech | Hyderabad</span></div>
                <a href="https://example.com/unsubscribe">Unsubscribe</a>
                """;
        List<RawJob> jobs = parser.parse(html);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).externalId()).isEqualTo("nk-311025009876");
        assertThat(jobs.get(0).url()).doesNotContain("?src");
        assertThat(jobs.get(0).company()).isEqualTo("Initech");
        assertThat(jobs.get(0).source()).isEqualTo("NAUKRI_ALERT");
    }
}
