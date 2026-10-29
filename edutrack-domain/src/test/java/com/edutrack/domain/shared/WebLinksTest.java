package com.edutrack.domain.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebLinksTest {

    @Test
    void linkWithoutSchemeGetsHttps() {
        assertThat(WebLinks.normalize("bbc.co.uk/learningenglish", "Link")).isEqualTo("https://bbc.co.uk/learningenglish");
    }

    @Test
    void httpLinkIsKeptAsIs() {
        assertThat(WebLinks.normalize("http://example.com/x", "Link")).isEqualTo("http://example.com/x");
    }

    @Test
    void linkIsStripped() {
        assertThat(WebLinks.normalize("  example.com  ", "Link")).isEqualTo("https://example.com");
    }

    @Test
    void nonWebSchemeIsRejected() {
        assertThatThrownBy(() -> WebLinks.normalize("ftp://example.com", "Link"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Link must be a web address");
    }

    @Test
    void textThatIsNotALinkIsRejected() {
        assertThatThrownBy(() -> WebLinks.normalize("not a link", "Link"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Link must be a web address");
    }

    @Test
    void blankAndNullBecomeNull() {
        assertThat(WebLinks.normalize("   ", "Link")).isNull();
        assertThat(WebLinks.normalize(null, "Link")).isNull();
    }

    @Test
    void messageNamesTheField() {
        assertThatThrownBy(() -> WebLinks.normalize("ftp://x.com", "Resource link"))
                .hasMessageContaining("Resource link");
    }

    @Test
    void tooLongLinkIsRejected() {
        assertThatThrownBy(() -> WebLinks.normalize("example.com/" + "a".repeat(2048), "Link"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("at most 2048");
    }
}
