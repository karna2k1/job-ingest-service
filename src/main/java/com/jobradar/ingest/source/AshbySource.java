package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ashby public job board API: api.ashbyhq.com/posting-api/job-board/{org} */
@Component
@RequiredArgsConstructor
public class AshbySource extends MultiBoardSource {
    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "ASHBY";
    }

    @Override
    protected List<String> boards() {
        return props.ashbyBoards();
    }

    @Override
    protected List<RawJob> fetchBoard(String org) {
        JsonNode root = http.getJson("https://api.ashbyhq.com/posting-api/job-board/{org}", org);
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            if (!j.path("isListed").asBoolean(true)) continue;
            String location = text(j, "location");
            out.add(new RawJob(
                    text(j, "id"),
                    SourceHttp.titleCase(org),
                    text(j, "title") == null ? null : text(j, "title").strip(),
                    location,
                    j.path("isRemote").asBoolean(false) || SourceHttp.looksRemote(location),
                    text(j, "jobUrl"),
                    text(j, "descriptionPlain"),
                    null,
                    SourceHttp.parseInstant(text(j, "publishedAt"))));
        }
        return out;
    }
}
