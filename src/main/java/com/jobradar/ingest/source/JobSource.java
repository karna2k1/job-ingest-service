package com.jobradar.ingest.source;

import java.time.Duration;
import java.util.List;

/** Pluggable adapter. Add a new source by implementing this as a Spring bean. */
public interface JobSource {
    /** Stable id stored with each job, e.g. GREENHOUSE. */
    String name();

    /** False when the source is not configured (e.g. missing API key). */
    boolean enabled();

    List<RawJob> fetch();

    /** Minimum time between scheduled runs, for quota-limited APIs. Manual runs ignore it. */
    default Duration minInterval() {
        return Duration.ZERO;
    }

    /** True when the user already chose the locations (job alerts), so the region filter is skipped. */
    default boolean trustedLocation() {
        return false;
    }
}
