# job-ingest-service

JobRadar ingestion microservice (Spring Boot, port 8081). Polls job sources every 30 minutes, normalises and dedupes jobs, stores them and publishes `job.ingested` to Kafka.

- **Pluggable sources** (`JobSource`): Greenhouse, Lever and Ashby public board APIs, Remotive, Adzuna (free key), **JSearch** (RapidAPI, Google for Jobs: LinkedIn / Naukri / Indeed listings, tagged per publisher, quota-aware every 12h), and **LinkedIn / Naukri job-alert emails over IMAP** (no scraping).
- **Resilience**: Resilience4j retry with exponential backoff per HTTP call; one failing board doesn't sink the others; per-source health (`GET /sources`).
- **Dedupe**: sha256 of normalised company|title|location plus a unique URL.
- **API**: `GET /jobs`, `GET /jobs/{id}`, `GET /sources`, `POST /sources/{name}/run`, `POST /jobs/republish`, `GET /stats/sources`.

Config (env): `GREENHOUSE_BOARDS`, `LEVER_COMPANIES`, `ASHBY_BOARDS`, `ADZUNA_APP_ID`/`ADZUNA_APP_KEY`, `JSEARCH_API_KEY`/`JSEARCH_QUERIES`, `IMAP_USERNAME`/`IMAP_PASSWORD` (Gmail app password); `JWT_SECRET` in `.env`.

Run: `mvn spring-boot:run` (infra from `jobradar-core-api/infra`). Architecture: see jobradar-core-api.
