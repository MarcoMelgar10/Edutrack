package com.edutrack.domain.shared;

import java.net.URI;
import java.net.URISyntaxException;

/** The rule for web links typed by the user (resources): optional, http(s) only, scheme added when missing. */
public final class WebLinks {

    public static final int MAX_LENGTH = 2048;

    private WebLinks() {
    }

    /** Accepts "example.com/page" as well as full http(s) links; blank becomes {@code null}; anything else is rejected. */
    public static String normalize(String raw, String field) {
        String url = Validation.optionalText(raw, field, MAX_LENGTH);
        if (url == null) {
            return null;
        }
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        if (url.length() > MAX_LENGTH) {
            throw new DomainValidationException("too-long", field + " must be at most " + MAX_LENGTH + " characters.", field, MAX_LENGTH);
        }
        try {
            URI uri = new URI(url);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (web && uri.getHost() != null) {
                return url;
            }
        } catch (URISyntaxException invalid) {
            // fall through to the user-facing message
        }
        throw new DomainValidationException("not-web-address",
                field + " must be a web address, for example https://example.com.", field);
    }
}
