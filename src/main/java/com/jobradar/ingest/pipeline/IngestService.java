package com.jobradar.ingest.pipeline;

import com.jobradar.ingest.config.IngestProperties;
import com.jobradar.ingest.job.Job;
import com.jobradar.ingest.job.JobRepository;
import com.jobradar.ingest.source.JobSource;
import com.jobradar.ingest.source.RawJob;
import com.jobradar.ingest.source.SourceHealth;
import com.jobradar.ingest.source.SourceHealthRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Polls every enabled JobSource, filters, dedupes, stores and publishes new jobs. */
@Slf4j
@Service
public class IngestService {
    public record RunResult(String source, boolean enabled, int fetched, int created, String error) {}

    private final List<JobSource> sources;
    private final JobNormalizer normalizer;
    private final JobRepository jobs;
    private final SourceHealthRepository health;
    private final JobPublisher publisher;
    private final Pattern titleInclude;
    private final LocationFilter locationFilter;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public IngestService(List<JobSource> sources, JobNormalizer normalizer, JobRepository jobs,
                         SourceHealthRepository health, JobPublisher publisher, IngestProperties props,
                         LocationFilter locationFilter) {
        this.locationFilter = locationFilter;
        this.sources = sources;
        this.normalizer = normalizer;
        this.jobs = jobs;
        this.health = health;
        this.publisher = publisher;
        this.titleInclude = Pattern.compile(props.titleInclude());
    }

    @Scheduled(fixedDelayString = "${app.ingest.poll-interval}", initialDelayString = "${app.ingest.initial-delay}")
    public void runAll() {
        for (JobSource s : sources) {
            if (s.enabled() && dueForRun(s)) run(s);
        }
    }

    /** Respects minInterval (e.g. JSearch's monthly quota) using the last successful run. */
    private boolean dueForRun(JobSource s) {
        if (s.minInterval().isZero()) return true;
        return health.findById(s.name())
                .map(SourceHealth::getLastSuccessAt)
                .map(last -> last.plus(s.minInterval()).isBefore(Instant.now()))
                .orElse(true);
    }

    public List<JobSource> sources() {
        return sources;
    }

    public Optional<JobSource> find(String name) {
        return sources.stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
    }

    public RunResult run(JobSource source) {
        if (!source.enabled()) return new RunResult(source.name(), false, 0, 0, "not configured");
        ReentrantLock lock = locks.computeIfAbsent(source.name(), k -> new ReentrantLock());
        if (!lock.tryLock()) return new RunResult(source.name(), true, 0, 0, "already running");
        try {
            return doRun(source);
        } finally {
            lock.unlock();
        }
    }

    private RunResult doRun(JobSource source) {
        SourceHealth h = health.findById(source.name()).orElseGet(() -> new SourceHealth(source.name()));
        try {
            List<RawJob> raws = source.fetch();
            int created = 0;
            for (RawJob raw : raws) {
                if (raw.title() == null || !titleInclude.matcher(raw.title()).find()) continue;
                // alert emails are the user's own subscriptions: trust their location choice
                if (!source.trustedLocation() && !locationFilter.accept(raw.location(), raw.remote())) continue;
                Job job = normalizer.toJob(source.name(), raw);
                if (job == null || jobs.existsByDedupeHashOrUrl(job.getDedupeHash(), job.getUrl())) continue;
                try {
                    jobs.saveAndFlush(job);
                } catch (DataIntegrityViolationException race) {
                    continue;
                }
                created++;
                publisher.publish(job);
            }
            h.success(raws.size(), created);
            health.save(h);
            log.info("{}: fetched {}, new {}", source.name(), raws.size(), created);
            return new RunResult(source.name(), true, raws.size(), created, null);
        } catch (RuntimeException e) {
            h.failure(e.getMessage());
            health.save(h);
            log.warn("{} failed: {}", source.name(), e.getMessage());
            return new RunResult(source.name(), true, 0, 0, e.getMessage());
        }
    }

    /** Re-sends jobs ingested in the last N hours (e.g. after match-notify was down). */
    public int republish(int hours) {
        Instant since = Instant.now().minusSeconds(hours * 3600L);
        int sent = 0;
        for (Job j : jobs.search("", "", PageRequest.of(0, 2000)).getContent()) {
            if (j.getIngestedAt().isBefore(since)) break;
            if (publisher.publish(j)) sent++;
        }
        return sent;
    }
}
