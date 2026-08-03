package com.ourgiant.markdown.gui;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AboutDialog.isTrustedReleaseUrl is the security boundary before a GitHub API response's
 *  URL is handed to Desktop.browse() -- covers the allowlist as well as spoofing attempts. */
class AboutDialogTest {

    @Test
    void acceptsGithubReleasePage() {
        assertTrue(AboutDialog.isTrustedReleaseUrl(URI.create("https://github.com/OurGiant/markdown-reader/releases/tag/v1.0.4")));
    }

    @Test
    void rejectsNonGithubHost() {
        assertFalse(AboutDialog.isTrustedReleaseUrl(URI.create("https://evil.example.com/releases/tag/v1.0.4")));
    }

    @Test
    void rejectsLookalikeHost() {
        assertFalse(AboutDialog.isTrustedReleaseUrl(URI.create("https://github.com.evil.example.com/releases")));
    }

    @Test
    void rejectsNonHttpsScheme() {
        assertFalse(AboutDialog.isTrustedReleaseUrl(URI.create("http://github.com/OurGiant/markdown-reader")));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertFalse(AboutDialog.isTrustedReleaseUrl(URI.create("file:///etc/passwd")));
    }
}
