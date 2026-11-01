package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Remotive public API (remote software jobs). */
@Component
@RequiredArgsConstructor
public class RemotiveSource implements JobSource {
    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "REMOTIVE";
    }

    @Override
    public boolean enabled() {
        return props.remotiveEnabled();
    }

    @Override
    public List<RawJob> fetch() {
        JsonNode root = http.getJson("https://remotive.com/api/remote-jobs?category=software-dev&limit=100");
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            out.add(new RawJob(
                    text(j, "id"),
                    text(j, "company_name"),
                    text(j, "title"),
                    text(j, "candidate_required_location"),
                    true,
                    text(j, "url"),
                    text(j, "description"),
                    text(j, "salary"),
                    SourceHttp.parseInstant(text(j, "publication_date"))));
        }
        return out;
    }
}
