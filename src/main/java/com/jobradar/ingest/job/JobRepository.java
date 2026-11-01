package com.jobradar.ingest.job;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {
    boolean existsByDedupeHashOrUrl(String dedupeHash, String url);

    @Query("""
            select j from Job j
            where (:source = '' or j.source = :source)
              and (:q = '' or lower(j.title) like lower(concat('%', :q, '%'))
                   or lower(j.company) like lower(concat('%', :q, '%')))
            order by j.ingestedAt desc
            """)
    Page<Job> search(@Param("q") String q, @Param("source") String source, Pageable pageable);

    interface SourceCount {
        String getSource();
        long getTotal();
        long getLastWeek();
    }

    @Query("""
            select j.source as source, count(j) as total,
                   sum(case when j.ingestedAt >= :since then 1 else 0 end) as lastWeek
            from Job j group by j.source order by count(j) desc
            """)
    List<SourceCount> countBySource(@Param("since") Instant since);
}
