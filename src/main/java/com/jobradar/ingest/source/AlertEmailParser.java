package com.jobradar.ingest.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts job links from LinkedIn / Naukri job-alert emails (HTML). We only read emails the user
 * subscribed to; no scraping of either site.
 */
@Component
public class AlertEmailParser {
    private static final Pattern LINKEDIN = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d+)");
    private static final Pattern NAUKRI = Pattern.compile("(https?://(?:www\\.)?naukri\\.com/job-listings-[^?#\\s\"]*?(\\d{6,}))");
    private static final Set<String> NOISE = Set.of("view job", "apply", "apply now", "see all jobs", "view all jobs",
            "easy apply", "see more jobs", "view details");

    public List<RawJob> parse(String html) {
        if (html == null || html.isBlank()) return List.of();
        Map<String, RawJob> byUrl = new LinkedHashMap<>();
        for (Element a : Jsoup.parse(html).select("a[href]")) {
            String href = a.attr("href");
            String title = a.text().strip();
            if (title.length() < 3 || title.length() > 150 || NOISE.contains(title.toLowerCase())) continue;

            String url;
            String externalId;
            String source;
            Matcher li = LINKEDIN.matcher(href);
            Matcher nk = NAUKRI.matcher(href);
            if (li.find()) {
                url = "https://www.linkedin.com/jobs/view/" + li.group(1);
                externalId = "li-" + li.group(1);
                source = "LINKEDIN_ALERT";
            } else if (nk.find()) {
                url = nk.group(1);
                externalId = "nk-" + nk.group(2);
                source = "NAUKRI_ALERT";
            } else {
                continue;
            }
            if (byUrl.containsKey(url)) continue;

            String[] companyAndLocation = companyAndLocation(a, title);
            byUrl.put(url, new RawJob(externalId, companyAndLocation[0], title, companyAndLocation[1],
                    SourceHttp.looksRemote(companyAndLocation[1]), url, null, null, null, source));
        }
        return new ArrayList<>(byUrl.values());
    }

    /** Alert cards render "Title" then "Company · Location" nearby; walk up until there is extra text. */
    private static String[] companyAndLocation(Element a, String title) {
        Element block = a.parent();
        for (int i = 0; i < 4 && block != null && block.text().length() <= title.length() + 3; i++) {
            block = block.parent();
        }
        String rest = "";
        if (block != null) {
            Element copy = block.clone();
            copy.select("a").remove(); // drop the title and "View job"-style links
            rest = copy.text().strip();
        }
        String[] parts = rest.split("\\s*[·|•–]\\s*|\\s{2,}");
        String company = parts.length > 0 && !parts[0].isBlank() ? clip(parts[0], 100) : "Unknown";
        String location = parts.length > 1 && !parts[1].isBlank() ? clip(parts[1], 120) : null;
        return new String[]{company, location};
    }

    private static String clip(String s, int max) {
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
