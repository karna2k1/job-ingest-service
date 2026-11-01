CREATE TABLE jobs (
  id          BIGSERIAL PRIMARY KEY,
  source      VARCHAR(40)   NOT NULL,
  external_id VARCHAR(200),
  company     VARCHAR(200)  NOT NULL,
  title       VARCHAR(300)  NOT NULL,
  location    VARCHAR(300),
  remote      BOOLEAN       NOT NULL DEFAULT false,
  url         VARCHAR(1000) NOT NULL,
  description TEXT,
  salary      VARCHAR(120),
  posted_at   TIMESTAMPTZ,
  dedupe_hash VARCHAR(64)   NOT NULL UNIQUE,
  ingested_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_jobs_url ON jobs(url);
CREATE INDEX idx_jobs_ingested ON jobs(ingested_at DESC);
CREATE INDEX idx_jobs_source ON jobs(source);

CREATE TABLE source_health (
  source               VARCHAR(40) PRIMARY KEY,
  last_run_at          TIMESTAMPTZ,
  last_success_at      TIMESTAMPTZ,
  last_error           VARCHAR(500),
  last_fetched         INT    NOT NULL DEFAULT 0,
  last_new             INT    NOT NULL DEFAULT 0,
  total_new            BIGINT NOT NULL DEFAULT 0,
  consecutive_failures INT    NOT NULL DEFAULT 0
);
