package com.jobradar.ingest.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {
    public static final String TOPIC_JOB_INGESTED = "job.ingested";

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(30));
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader("User-Agent", "JobRadar/1.0 (portfolio project)")
                .build();
    }

    @Bean
    NewTopic jobIngestedTopic() {
        return TopicBuilder.name(TOPIC_JOB_INGESTED).partitions(1).replicas(1).build();
    }
}
