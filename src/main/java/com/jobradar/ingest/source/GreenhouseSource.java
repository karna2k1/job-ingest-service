package com.jobradar.ingest.source;

import static com.jobradar.ingest.source.SourceHttp.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.ingest.config.IngestProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/** Greenhouse public job board API: boards-api.greenhouse.io/v1/boards/{board}/jobs */
@Component
@RequiredArgsConstructor
public class GreenhouseSource extends MultiBoardSource {
    private final SourceHttp http;
    private final IngestProperties props;

    @Override
    public String name() {
        return "GREENHOUSE";
    }

    @Override
    protected List<String> boards() {
        return props.greenhouseBoards();
    }

    @Override
    protected List<RawJob> fetchBoard(String board) {
        JsonNode root = http.getJson("https://boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true", board);
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            String location = j.path("location").path("name").asText(null);
            String content = text(j, "content");
            out.add(new RawJob(
                    text(j, "id"),
                    j.path("company_name").asText(SourceHttp.titleCase(board)),
                    text(j, "title"),
                    location,
                    SourceHttp.looksRemote(location),
                    text(j, "absolute_url"),
                    content == null ? null : Parser.unescapeEntities(content, false),
                    null,
                    SourceHttp.parseInstant(text(j, "first_published"))));
        }
        return out;
    }
}
