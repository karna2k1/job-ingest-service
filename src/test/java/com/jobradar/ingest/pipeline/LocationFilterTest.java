package com.jobradar.ingest.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobradar.ingest.config.IngestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationFilterTest {
    private final LocationFilter india = new LocationFilter(new IngestProperties(Duration.ZERO, Duration.ZERO, ".*",
            "india|bengaluru|bangalore|hyderabad|pune|gurugram", List.of(), List.of(), List.of(), false, null, null, null));

    @Test
    void keepsIndianCities() {
        assertThat(india.accept("Bengaluru, Karnataka", false)).isTrue();
        assertThat(india.accept("Bangalore", false)).isTrue();
        assertThat(india.accept("Remote - India", true)).isTrue();
        assertThat(india.accept("San Francisco, CA; Bengaluru", false)).isTrue();
    }

    @Test
    void keepsOnlyGloballyOpenRemote() {
        assertThat(india.accept("Remote", true)).isTrue();
        assertThat(india.accept("Worldwide", true)).isTrue();
        assertThat(india.accept("Remote (APAC)", true)).isTrue();
        assertThat(india.accept("US-Remote", true)).isFalse();
        assertThat(india.accept("Remote (Canada)", true)).isFalse();
    }

    @Test
    void dropsAbroadOnsite() {
        assertThat(india.accept("New York, NY", false)).isFalse();
        assertThat(india.accept("Dublin", false)).isFalse();
        assertThat(india.accept(null, false)).isFalse();
    }
}
