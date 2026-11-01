package com.jobradar.ingest.job;

import com.jobradar.ingest.pipeline.IngestService;
import com.jobradar.ingest.source.SourceHealth;
import com.jobradar.ingest.source.SourceHealthRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class JobController {
    private final JobRepository jobs;
    private final SourceHealthRepository health;
    private final IngestService ingest;

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record SourceStatus(String source, boolean enabled, SourceHealth health) {}

    @GetMapping("/jobs")
    public PageResponse<JobDto> search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String query = q == null ? "" : q.strip();
        String src = source == null ? "" : source.strip().toUpperCase();
        var result = jobs.search(query, src, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(result.map(JobDto::from));
    }

    @GetMapping("/jobs/{id}")
    public JobDto get(@PathVariable Long id) {
        return jobs.findById(id).map(JobDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    @PostMapping("/jobs/republish")
    public Map<String, Integer> republish(@RequestParam(defaultValue = "24") int hours) {
        return Map.of("sent", ingest.republish(Math.min(Math.max(hours, 1), 168)));
    }

    @GetMapping("/sources")
    public List<SourceStatus> sources() {
        Map<String, SourceHealth> byName = health.findAll().stream()
                .collect(Collectors.toMap(SourceHealth::getSource, Function.identity()));
        return ingest.sources().stream()
                .map(s -> new SourceStatus(s.name(), s.enabled(), byName.get(s.name())))
                .toList();
    }

    @PostMapping("/sources/{name}/run")
    public IngestService.RunResult runNow(@PathVariable String name) {
        var source = ingest.find(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown source " + name));
        return ingest.run(source);
    }

    /** Jobs per source (total and last 7 days), for insights-mfe. */
    @GetMapping("/stats/sources")
    public List<JobRepository.SourceCount> stats() {
        return jobs.countBySource(Instant.now().minus(7, ChronoUnit.DAYS));
    }
}
