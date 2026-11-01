package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JSearch (RapidAPI): official API over Google for Jobs, which carries LinkedIn, Naukri, Indeed and
 * Glassdoor listings. Free tier is ~200 requests/month, so it runs every 12h with a few queries.
 * Jobs are tagged by publisher, e.g. JSEARCH_LINKEDIN, for the per-source analytics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JSearchSource extends MultiBoardSource {
    private static final String HOST = "jsearch.p.rapidapi.com";

    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "JSEARCH";
    }

    @Override
    public boolean enabled() {
        return props.jsearch() != null && props.jsearch().enabled();
    }

    @Override
    public Duration minInterval() {
        return props.jsearch().minInterval();
    }

    /** Each configured query counts as one "board" (one API request). */
    @Override
    protected List<String> boards() {
        return props.jsearch().queries();
    }

    @Override
    protected List<RawJob> fetchBoard(String query) {
        var cfg = props.jsearch();
        // v5 API: /search-v2 returns {data: {jobs: [...], cursor}} with 10 jobs per request
        JsonNode root = http.getJson(
                "https://" + HOST + "/search-v2?query={q}&country={country}&date_posted={posted}",
                Map.of("X-RapidAPI-Key", cfg.apiKey(), "X-RapidAPI-Host", HOST),
                query, cfg.country(), cfg.datePosted());
        JsonNode data = root.path("data");
        JsonNode jobs = data.isArray() ? data : data.path("jobs");
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : jobs) {
            out.add(toRaw(j));
        }
        log.info("JSearch '{}': {} jobs", query, out.size());
        return out;
    }

    static RawJob toRaw(JsonNode j) {
        return new RawJob(
                text(j, "job_id"),
                j.path("employer_name").asText("Unknown"),
                text(j, "job_title"),
                location(j),
                j.path("job_is_remote").asBoolean(false),
                text(j, "job_apply_link"),
                text(j, "job_description"),
                salary(j),
                SourceHttp.parseInstant(text(j, "job_posted_at_datetime_utc")),
                sourceTag(text(j, "job_publisher")));
    }

    /** "Bengaluru, Karnataka, India": country code IN is spelled out so the India filter matches. */
    static String location(JsonNode j) {
        StringJoiner parts = new StringJoiner(", ");
        for (String f : List.of("job_city", "job_state")) {
            String v = text(j, f);
            if (v != null && !v.isBlank()) parts.add(v);
        }
        String country = text(j, "job_country");
        if (country != null && !country.isBlank()) parts.add("IN".equalsIgnoreCase(country) ? "India" : country);
        String loc = parts.toString();
        return loc.isBlank() ? text(j, "job_location") : loc;
    }

    static String salary(JsonNode j) {
        String s = text(j, "job_salary_string");
        if (s != null && !s.isBlank()) return s;
        if (!j.hasNonNull("job_min_salary") || !j.hasNonNull("job_max_salary")) return null;
        String cur = j.path("job_salary_currency").asText("");
        String period = j.path("job_salary_period").asText("");
        return (j.path("job_min_salary").asLong() + " - " + j.path("job_max_salary").asLong() + " " + cur + " " + period).strip();
    }

    static String sourceTag(String publisher) {
        if (publisher == null || publisher.isBlank()) return "JSEARCH";
        String p = publisher.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        String tag = "JSEARCH_" + p;
        return tag.length() > 40 ? tag.substring(0, 40) : tag;
    }
}
