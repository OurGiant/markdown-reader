package com.ourgiant.markdown.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Checks GitHub's releases API for a newer MD Print Pro version. */
public final class UpdateChecker {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);
    private static final String RELEASES_URL = "https://api.github.com/repos/OurGiant/markdown-reader/releases/latest";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ReleaseInfo(String version, String htmlUrl) {
    }

    private UpdateChecker() {
    }

    /**
     * Does a real network call — run this off the EDT (e.g. from a SwingWorker).
     * Never throws: any failure (offline, rate-limited, TLS handshake, no releases yet, ...)
     * is logged at WARN and reported back as an empty result.
     */
    public static Optional<ReleaseInfo> fetchLatestRelease() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "md-print-pro")
                .timeout(REQUEST_TIMEOUT)
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(response.body());
            String tagName = root.path("tag_name").asString(null);
            String htmlUrl = root.path("html_url").asString(null);
            if (tagName == null || htmlUrl == null) {
                return Optional.empty();
            }
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            return Optional.of(new ReleaseInfo(version, htmlUrl));
        } catch (SSLHandshakeException e) {
            logger.warn("TLS handshake failed fetching latest release from GitHub (possible corporate network proxy)", e);
            return Optional.empty();
        } catch (Exception e) {
            // WARN, not debug: root logger is INFO by default (logback.xml).
            logger.warn("Failed to fetch latest release from GitHub", e);
            return Optional.empty();
        }
    }

    /** @return true if {@code latest} is a strictly newer dotted-numeric version than {@code current}; false (not an exception) on any non-numeric segment. */
    public static boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int len = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < len; i++) {
                int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (l > c) {
                    return true;
                }
                if (l < c) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            logger.debug("Could not compare versions: {} vs {}", latest, current);
        }
        return false;
    }
}
