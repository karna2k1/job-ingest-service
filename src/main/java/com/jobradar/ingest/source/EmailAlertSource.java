package com.jobradar.ingest.source;

import com.jobradar.ingest.config.IngestProperties;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads the last 3 days of LinkedIn / Naukri job emails over IMAP (Gmail app password), strictly read-only.
 * Enabled only when IMAP_USERNAME / IMAP_PASSWORD are set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAlertSource implements JobSource {
    private static final int MAX_MESSAGES_PER_RUN = 100;
    private static final Duration LOOKBACK = Duration.ofDays(3);

    private final IngestProperties props;
    private final AlertEmailParser parser;

    @Override
    public String name() {
        return "EMAIL_ALERT";
    }

    @Override
    public boolean enabled() {
        return props.email() != null && props.email().enabled();
    }

    @Override
    public boolean trustedLocation() {
        return true;
    }

    @Override
    public List<RawJob> fetch() {
        var cfg = props.email();
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.connectiontimeout", "20000");
        p.put("mail.imaps.timeout", "30000");
        p.put("mail.imaps.peek", "true"); // BODY.PEEK: never mark mail as read
        List<RawJob> out = new ArrayList<>();
        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(cfg.host(), cfg.username(), cfg.password().replace(" ", ""));
            Folder folder = store.getFolder(cfg.folder());
            folder.open(Folder.READ_ONLY); // read-only: the user's inbox is never modified
            try {
                // recent alerts, read or not; already-ingested jobs are dropped by dedupe
                Date since = Date.from(Instant.now().minus(LOOKBACK));
                SearchTerm alerts = new AndTerm(
                        new ReceivedDateTerm(ComparisonTerm.GE, since),
                        new OrTerm(new SearchTerm[]{
                                new FromStringTerm("jobs-noreply@linkedin.com"),
                                new FromStringTerm("jobalerts-noreply@linkedin.com"),
                                new FromStringTerm("naukri.com")}));
                Message[] messages = folder.search(alerts);
                int n = Math.min(messages.length, MAX_MESSAGES_PER_RUN);
                int skipped = 0;
                for (int i = messages.length - 1; i >= messages.length - n; i--) { // newest first
                    try {
                        Message m = messages[i];
                        out.addAll(parser.parse(htmlOf(m), safeSubject(m), safeFromName(m)));
                    } catch (MessagingException | IOException | RuntimeException e) {
                        skipped++; // one malformed email must not sink the run
                        log.debug("Skipping email {}: {}", i, e.getMessage());
                    }
                }
                log.info("Parsed {} alert emails ({} skipped), {} job links", n, skipped, out.size());
            } finally {
                folder.close(false);
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("IMAP fetch failed: " + e.getMessage(), e);
        }
        return out;
    }

    private static String safeSubject(Message m) {
        try {
            return m.getSubject();
        } catch (MessagingException | RuntimeException e) {
            return null;
        }
    }

    private static String safeFromName(Message m) {
        try {
            var from = m.getFrom();
            return from != null && from.length > 0 && from[0] instanceof InternetAddress ia ? ia.getPersonal() : null;
        } catch (MessagingException | RuntimeException e) {
            return null;
        }
    }

    private static String htmlOf(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String plain = null;
            for (int i = 0; i < mp.getCount(); i++) {
                String html = htmlOf(mp.getBodyPart(i));
                if (html != null && html.contains("<")) return html;
                if (html != null) plain = html;
            }
            return plain;
        }
        if (part.isMimeType("text/plain")) return (String) part.getContent();
        return null;
    }
}
