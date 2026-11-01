package com.jobradar.ingest.source;

import com.jobradar.ingest.config.IngestProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads unread LinkedIn / Naukri job-alert emails over IMAP (Gmail app password) and marks them read.
 * Enabled only when IMAP_USERNAME / IMAP_PASSWORD are set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAlertSource implements JobSource {
    private static final int MAX_MESSAGES_PER_RUN = 50;

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
        List<RawJob> out = new ArrayList<>();
        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(cfg.host(), cfg.username(), cfg.password());
            Folder folder = store.getFolder(cfg.folder());
            folder.open(Folder.READ_WRITE);
            try {
                SearchTerm alerts = new AndTerm(
                        new FlagTerm(new Flags(Flags.Flag.SEEN), false),
                        new OrTerm(new FromStringTerm("linkedin"), new FromStringTerm("naukri")));
                Message[] messages = folder.search(alerts);
                int n = Math.min(messages.length, MAX_MESSAGES_PER_RUN);
                for (int i = 0; i < n; i++) {
                    Message m = messages[i];
                    out.addAll(parser.parse(htmlOf(m)));
                    m.setFlag(Flags.Flag.SEEN, true);
                }
                log.info("Parsed {} alert emails, {} job links", n, out.size());
            } finally {
                folder.close(false);
            }
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("IMAP fetch failed: " + e.getMessage(), e);
        }
        return out;
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
