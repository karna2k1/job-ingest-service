package com.jobradar.ingest.pipeline;

import com.jobradar.ingest.config.IngestProperties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Keeps jobs located in the target region (default: India) plus remote jobs open to it.
 * "US-Remote" or "Remote (Canada)" is rejected; "Remote", "Worldwide", "APAC" is kept.
 */
@Component
public class LocationFilter {
    static final Pattern GLOBAL_REMOTE = Pattern.compile(
            "(?i)^\\s*(fully\\s+)?(remote|anywhere|worldwide|global)\\s*$"
                    + "|\\b(worldwide|anywhere|apac|asia)\\b");

    private final Pattern include;

    public LocationFilter(IngestProperties props) {
        String regex = props.locationInclude();
        this.include = regex == null || regex.isBlank() ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public boolean accept(String location, boolean remote) {
        if (include == null) return true;
        if (location == null || location.isBlank()) return remote;
        if (include.matcher(location).find()) return true;
        return remote && GLOBAL_REMOTE.matcher(location).find();
    }
}
