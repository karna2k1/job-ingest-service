package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adzuna search API (free key: developer.adzuna.com). Enabled only when ADZUNA_APP_ID/KEY are set. */
@Component
@RequiredArgsConstructor
public class AdzunaSource implements JobSource {
    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "ADZUNA";
    }

    @Override
    public boolean enabled() {
        return props.adzuna() != null && props.adzuna().enabled();
    }

    @Override
    public List<RawJob> fetch() {
        var cfg = props.adzuna();
        JsonNode root = http.getJson(
                "https://api.adzuna.com/v1/api/jobs/{country}/search/1?app_id={id}&app_key={key}&results_per_page=50&max_days_old=3&what={what}",
                cfg.country(), cfg.appId(), cfg.appKey(), cfg.what());
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("results")) {
            String location = j.path("location").path("display_name").asText(null);
            String salary = null;
            if (j.hasNonNull("salary_min") && j.hasNonNull("salary_max")) {
                salary = j.path("salary_min").asLong() + " - " + j.path("salary_max").asLong();
            }
            out.add(new RawJob(
                    text(j, "id"),
                    j.path("company").path("display_name").asText("Unknown"),
                    text(j, "title"),
                    location,
                    SourceHttp.looksRemote(location) || SourceHttp.looksRemote(text(j, "title")),
                    text(j, "redirect_url"),
                    text(j, "description"),
                    salary,
                    SourceHttp.parseInstant(text(j, "created"))));
        }
        return out;
    }
}
