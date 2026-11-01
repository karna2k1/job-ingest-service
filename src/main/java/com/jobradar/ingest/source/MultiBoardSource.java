package com.jobradar.ingest.source;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Base for sources that poll several boards: one failing board doesn't sink the others. */
@Slf4j
public abstract class MultiBoardSource implements JobSource {

    protected abstract List<String> boards();

    protected abstract List<RawJob> fetchBoard(String board);

    @Override
    public boolean enabled() {
        return boards() != null && !boards().isEmpty();
    }

    @Override
    public List<RawJob> fetch() {
        List<RawJob> out = new ArrayList<>();
        int failures = 0;
        RuntimeException last = null;
        for (String board : boards()) {
            try {
                out.addAll(fetchBoard(board.strip()));
            } catch (RuntimeException e) {
                failures++;
                last = e;
                log.warn("{} board '{}' failed: {}", name(), board, e.getMessage());
            }
        }
        if (failures == boards().size() && last != null) throw last;
        return out;
    }
}
