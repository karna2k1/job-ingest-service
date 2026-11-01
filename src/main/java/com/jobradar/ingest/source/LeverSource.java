package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lever public postings API: api.lever.co/v0/postings/{company}?mode=json */
@Component
@RequiredArgsConstructor
public class LeverSource extends MultiBoardSource {
    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "LEVER";
    }

    @Override
    protected List<String> boards() {
        return props.leverCompanies();
    }

    @Override
    protected List<RawJob> fetchBoard(String company) {
        JsonNode root = http.getJson("https://api.lever.co/v0/postings/{company}?mode=json", company);
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root) {
            String location = j.path("categories").path("location").asText(null);
            StringBuilder desc = new StringBuilder(j.path("descriptionPlain").asText(""));
            for (JsonNode list : j.path("lists")) {
                desc.append('\n').append(list.path("text").asText("")).append('\n').append(list.path("content").asText(""));
            }
            boolean remote = "remote".equalsIgnoreCase(j.path("workplaceType").asText("")) || SourceHttp.looksRemote(location);
            long created = j.path("createdAt").asLong(0);
            out.add(new RawJob(
                    text(j, "id"),
                    SourceHttp.titleCase(company),
                    text(j, "text"),
                    location,
                    remote,
                    text(j, "hostedUrl"),
                    desc.toString(),
                    null,
                    created > 0 ? Instant.ofEpochMilli(created) : null));
        }
        return out;
    }
}
