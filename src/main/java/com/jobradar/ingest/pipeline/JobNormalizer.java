package com.jobradar.ingest.pipeline;

import com.jobradar.ingest.job.Job;
import com.jobradar.ingest.source.RawJob;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Cleans raw jobs (HTML to text, length limits) and computes the dedupe hash. */
@Component
public class JobNormalizer {
    static final int MAX_DESCRIPTION = 20_000;

    /** Returns null when the raw job lacks the minimum fields (title, url). */
    public Job toJob(String sourceName, RawJob raw) {
        if (blank(raw.title()) || blank(raw.url())) return null;
        Job j = new Job();
        j.setSource(clip(raw.source() != null ? raw.source() : sourceName, 40));
        j.setExternalId(clip(raw.externalId(), 200));
        j.setCompany(clip(blank(raw.company()) ? "Unknown" : raw.company().strip(), 200));
        j.setTitle(clip(raw.title().strip(), 300));
        j.setLocation(clip(blank(raw.location()) ? null : raw.location().strip(), 300));
        j.setRemote(raw.remote());
        j.setUrl(clip(raw.url().strip(), 1000));
        j.setDescription(clip(toText(raw.description()), MAX_DESCRIPTION));
        j.setSalary(clip(raw.salary(), 120));
        j.setPostedAt(raw.postedAt());
        j.setDedupeHash(dedupeHash(j.getCompany(), j.getTitle(), j.getLocation()));
        return j;
    }

    /** sha256 of normalised company|title|location: the same posting on two boards collapses to one job. */
    public static String dedupeHash(String company, String title, String location) {
        String key = norm(company) + "|" + norm(title) + "|" + norm(location);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String toText(String maybeHtml) {
        if (blank(maybeHtml)) return null;
        return maybeHtml.contains("<") ? Jsoup.parse(maybeHtml).text() : maybeHtml.strip();
    }

    private static String norm(String v) {
        if (v == null) return "";
        return v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static String clip(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
