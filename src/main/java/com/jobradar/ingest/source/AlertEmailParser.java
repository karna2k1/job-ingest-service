package com.jobradar.ingest.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts jobs from emails the user subscribed to (no scraping):
 * LinkedIn job alerts, Naukri "Top Openings" / alert digests, and Naukri recruiter mails ("Job | title in city").
 */
@Component
public class AlertEmailParser {
    private static final Pattern LINKEDIN = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d+)");
    private static final Pattern NAUKRI = Pattern.compile(
            "(https?://(?:www\\.)?naukri\\.com/(?:jd/)?job-listings-([a-z0-9-]*?)-(\\d{6,}))", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMBITIONBOX = Pattern.compile("ambitionbox\\.com/reviews/([a-z0-9-]+?)-reviews");
    /** Recruiter mail subject, e.g. "✉️ Job | Senior Full Stack Engineer- 3+Yrs in Bengaluru | Pune". */
    private static final Pattern RECRUITER_SUBJECT = Pattern.compile("\\bJob\\s*\\|\\s*(.+?)\\s+in\\s+([^|]+?)\\s*(?:\\||$)");
    private static final Pattern NAUKRI_MAIL_ID = Pattern.compile("[?&](?:mid|f)=(\\d{6,})");
    private static final Set<String> NOISE = Set.of("view job", "apply", "apply now", "see all jobs", "view all jobs",
            "easy apply", "see more jobs", "view details", "view similar jobs");
    private static final List<String> CITIES = List.of("bengaluru", "bangalore", "hyderabad", "pune", "mumbai", "chennai",
            "gurugram", "gurgaon", "noida", "delhi", "kolkata", "ahmedabad", "kochi", "jaipur", "coimbatore",
            "chandigarh", "indore", "thiruvananthapuram", "trivandrum", "nashik", "mysuru", "vadodara", "remote");

    public List<RawJob> parse(String html) {
        return parse(html, null, null);
    }

    /** @param subject email subject, @param fromName sender display name (company for recruiter mails) */
    public List<RawJob> parse(String html, String subject, String fromName) {
        Map<String, RawJob> byUrl = new LinkedHashMap<>();
        Document doc = html == null ? null : Jsoup.parse(html);
        if (doc != null) {
            parseLinkedIn(doc, byUrl);
            for (Element a : doc.select("a[href]")) {
                String href = a.attr("href");
                String title = a.text().strip();
                if (title.length() < 3 || title.length() > 150 || NOISE.contains(title.toLowerCase(Locale.ROOT))) continue;
                Matcher nk = NAUKRI.matcher(href);
                if (nk.find()) {
                    String url = nk.group(1).replace("/jd/", "/");
                    if (byUrl.containsKey(url)) continue;
                    String slug = nk.group(2).toLowerCase(Locale.ROOT);
                    String[] cl = companyAndLocation(a);
                    String company = companyFromAmbitionBox(a);
                    String location = cityFromSlug(slug);
                    byUrl.put(url, new RawJob("nk-" + nk.group(3), company != null ? company : cl[0], title,
                            location != null ? location : cl[1], "Remote".equals(location), url, null, null, null, "NAUKRI_ALERT"));
                }
            }
        }
        if (byUrl.isEmpty()) {
            RawJob recruiter = recruiterMail(doc, subject, fromName);
            if (recruiter != null) byUrl.put(recruiter.url(), recruiter);
        }
        return new ArrayList<>(byUrl.values());
    }

    /**
     * A LinkedIn alert card links the same job several times: logo (img alt = company), the title, and a wrapper
     * around the whole card ("Title Company · City (On-site)"). Shortest text = title; the longest minus the title
     * gives "Company · Location".
     */
    private static void parseLinkedIn(Document doc, Map<String, RawJob> out) {
        Map<String, List<Element>> byId = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            Matcher li = LINKEDIN.matcher(a.attr("href"));
            if (li.find()) byId.computeIfAbsent(li.group(1), k -> new ArrayList<>()).add(a);
        }
        for (var e : byId.entrySet()) {
            List<String> texts = e.getValue().stream().map(a -> a.text().strip())
                    .filter(t -> t.length() >= 3 && !NOISE.contains(t.toLowerCase(Locale.ROOT))).toList();
            if (texts.isEmpty()) continue;
            String title = texts.stream().min((x, y) -> x.length() - y.length()).orElseThrow();
            String longest = texts.stream().max((x, y) -> x.length() - y.length()).orElseThrow();
            if (title.length() > 150 || title.toLowerCase(Locale.ROOT).startsWith("jobs similar to")) continue;

            String rest = longest.length() > title.length() ? longest.replace(title, " ").strip() : "";
            String company = null;
            String location = null;
            if (!rest.isEmpty()) {
                String[] parts = rest.split("\\s*·\\s*", 2);
                company = parts[0].strip();
                location = parts.length > 1 ? parts[1].strip() : null;
            } else {
                String[] cl = companyAndLocation(e.getValue().get(0));
                company = cl[0];
                location = cl[1];
            }
            if (company == null || company.isBlank() || company.equals("Unknown")) {
                company = e.getValue().stream().flatMap(a -> a.select("img[alt]").stream())
                        .map(img -> img.attr("alt").strip()).filter(s -> !s.isBlank()).findFirst().orElse("Unknown");
            }
            boolean remote = location != null && location.toLowerCase(Locale.ROOT).contains("remote");
            if (location != null) location = location.replaceAll("\\s*\\((On-site|Hybrid|Remote)\\)\\s*$", "").strip();
            String url = "https://www.linkedin.com/jobs/view/" + e.getKey();
            out.put(url, new RawJob("li-" + e.getKey(), clip(company, 100), clip(title, 150),
                    location == null || location.isBlank() ? null : clip(location, 120), remote, url, null, null, null,
                    "LINKEDIN_ALERT"));
        }
    }

    /** Naukri recruiter broadcast: the whole email is one job. */
    private static RawJob recruiterMail(Document doc, String subject, String fromName) {
        if (subject == null || doc == null) return null;
        Matcher s = RECRUITER_SUBJECT.matcher(subject);
        if (!s.find()) return null;
        String link = null;
        String id = null;
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (href.contains("naukri.com") && (href.contains("applyBroadcastMail") || href.contains("/durl") || href.contains("RMJ"))) {
                Matcher m = NAUKRI_MAIL_ID.matcher(href);
                if (m.find()) {
                    id = m.group(1);
                    link = href;
                    break;
                }
            }
        }
        if (link == null) return null;
        String title = s.group(1).replaceAll("(?i)\\s*-\\s*\\d+\\s*\\+?\\s*(yrs?|years?)\\b.*$", "").strip();
        String company = fromName == null || fromName.isBlank() ? "Unknown" : clip(fromName.replaceAll("[\"<].*$", "").strip(), 100);
        String location = clip(s.group(2).strip(), 120);
        return new RawJob("nk-mail-" + id, company, clip(title, 150), location, SourceHttp.looksRemote(location),
                link, null, null, null, "NAUKRI_ALERT");
    }

    /** Naukri cards link the company's AmbitionBox reviews: ".../reviews/nest-digital-reviews" -> "Nest Digital". */
    private static String companyFromAmbitionBox(Element a) {
        Element block = a.parent();
        for (int i = 0; i < 6 && block != null; i++, block = block.parent()) {
            for (Element link : block.select("a[href*=ambitionbox.com/reviews/]")) {
                Matcher m = AMBITIONBOX.matcher(link.attr("href"));
                if (m.find()) return titleCase(m.group(1).replace('-', ' '));
            }
        }
        return null;
    }

    private static String cityFromSlug(String slug) {
        List<String> found = new ArrayList<>();
        for (String c : CITIES) {
            if (("-" + slug + "-").contains("-" + c + "-")) found.add(titleCase(c));
        }
        return found.isEmpty() ? null : String.join(", ", found);
    }

    /** Alert cards render "Title" then "Company · Location" nearby; walk up until there is extra text. */
    private static String[] companyAndLocation(Element a) {
        String title = a.text().strip();
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

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String clip(String s, int max) {
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
