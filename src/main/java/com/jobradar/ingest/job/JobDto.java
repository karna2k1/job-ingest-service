package com.jobradar.ingest.job;

import java.time.Instant;

public record JobDto(Long id, String source, String company, String title, String location, boolean remote,
                     String url, String description, String salary, Instant postedAt, Instant ingestedAt) {

    public static JobDto from(Job j) {
        return new JobDto(j.getId(), j.getSource(), j.getCompany(), j.getTitle(), j.getLocation(), j.isRemote(),
                j.getUrl(), j.getDescription(), j.getSalary(), j.getPostedAt(), j.getIngestedAt());
    }
}
