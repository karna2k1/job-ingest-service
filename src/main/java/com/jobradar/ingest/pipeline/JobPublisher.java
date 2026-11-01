package com.jobradar.ingest.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.ingest.config.AppConfig;
import com.jobradar.ingest.job.Job;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes job.ingested (JSON string). Backs off for a minute when the broker is unreachable. */
@Slf4j
@Component
public class JobPublisher {
    private static final int MAX_DESCRIPTION_IN_EVENT = 8_000;

    public record JobIngestedEvent(Long id, String source, String company, String title, String location,
                                   boolean remote, String url, String description, String salary, Instant postedAt) {}

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private volatile Instant brokerDownUntil = Instant.EPOCH;

    public JobPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                        @Value("${app.kafka.enabled}") boolean enabled) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    /** @return true if handed to Kafka */
    public boolean publish(Job j) {
        if (!enabled || Instant.now().isBefore(brokerDownUntil)) return false;
        String desc = j.getDescription();
        if (desc != null && desc.length() > MAX_DESCRIPTION_IN_EVENT) desc = desc.substring(0, MAX_DESCRIPTION_IN_EVENT);
        var event = new JobIngestedEvent(j.getId(), j.getSource(), j.getCompany(), j.getTitle(), j.getLocation(),
                j.isRemote(), j.getUrl(), desc, j.getSalary(), j.getPostedAt());
        try {
            kafka.send(AppConfig.TOPIC_JOB_INGESTED, String.valueOf(j.getId()), mapper.writeValueAsString(event))
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.warn("job.ingested send failed for job {}: {}", j.getId(), ex.getMessage());
                    });
            return true;
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise job {}", j.getId());
            return false;
        } catch (RuntimeException e) {
            brokerDownUntil = Instant.now().plusSeconds(60);
            log.warn("Kafka unavailable, pausing publishes for 60s: {}", e.getMessage());
            return false;
        }
    }
}
