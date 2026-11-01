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

    @Test
    void parsesRealLinkedInCardLayout() {
        // shape of current LinkedIn alerts: logo link, title link, and a wrapper link around the whole card
        String html = """
                <a href="https://www.linkedin.com/comm/jobs/view/4485189866/?trk=a"><img alt="Algoleap" src="x"></a>
                <a href="https://www.linkedin.com/comm/jobs/view/4485189866/?trk=b"><table><tr><td>
                  <a href="https://www.linkedin.com/comm/jobs/view/4485189866/?trk=c">Java Full Stack Developer</a>
                </td></tr><tr><td><p>Algoleap · Hyderabad (On-site)</p></td></tr></table></a>
                <a href="https://www.linkedin.com/comm/jobs/view/4480405346/?trk=d">Jobs similar to Java Fullstack Engineer at Apple</a>
                """;
        List<RawJob> jobs = parser.parse(html);

        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.title()).isEqualTo("Java Full Stack Developer");
        assertThat(j.company()).isEqualTo("Algoleap");
        assertThat(j.location()).isEqualTo("Hyderabad");
        assertThat(j.remote()).isFalse();
    }

    @Test
    void parsesNaukriTopOpeningsDigest() {
        String html = """
                <table><tr><td>
                  <a href="https://www.naukri.com/jd/job-listings-software-engineer-java-spring-boot-nest-digital-bengaluru-1-to-3-years-231026500715?xp=1&src=ja2026">Software Engineer-Java / Spring Boot</a>
                  <div><a href="https://www.ambitionbox.com/reviews/nest-digital-reviews?utm=x">3.9 ★ 120 reviews</a></div>
                </td></tr></table>
                """;
        List<RawJob> jobs = parser.parse(html, "Top Openings for Software Developer", "Naukri");

        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.title()).isEqualTo("Software Engineer-Java / Spring Boot");
        assertThat(j.company()).isEqualTo("Nest Digital");
        assertThat(j.location()).isEqualTo("Bengaluru");
        assertThat(j.url()).isEqualTo("https://www.naukri.com/job-listings-software-engineer-java-spring-boot-nest-digital-bengaluru-1-to-3-years-231026500715");
        assertThat(j.externalId()).isEqualTo("nk-231026500715");
    }

    @Test
    void parsesNaukriRecruiterMailFromSubject() {
        String html = """
                <p>Dear candidate, we have an opening...</p>
                <a href="http://my.naukri.com/AL/ResdexRMJMail/alid/{}/redirectParam/applyBroadcastMail?xz=1_0_66&f=301026833117&mid=301026833117">Apply</a>
                """;
        List<RawJob> jobs = parser.parse(html, "✉️ Job | Senior Full Stack Engineer- 3+Yrs- Bangalore in Bengaluru | Pune", "Citiustech");

        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.title()).isEqualTo("Senior Full Stack Engineer");
        assertThat(j.company()).isEqualTo("Citiustech");
        assertThat(j.location()).isEqualTo("Bengaluru");
        assertThat(j.externalId()).isEqualTo("nk-mail-301026833117");
        assertThat(j.source()).isEqualTo("NAUKRI_ALERT");
    }

    @Test
    void ignoresMarketingMail() {
        assertThat(parser.parse("<a href=\"https://www.naukri.com/mnjuser/profile\">Update your profile</a>",
                "Urgent: Your resume is not visible to recruiters", "Naukri")).isEmpty();
    }
}
