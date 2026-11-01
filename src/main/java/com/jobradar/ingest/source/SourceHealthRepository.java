package com.jobradar.ingest.source;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceHealthRepository extends JpaRepository<SourceHealth, String> {
}
