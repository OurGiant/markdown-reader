package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.RetroTheme;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownHtmlRendererTest {

    private RetroTheme customTheme() {
        RetroTheme t = new RetroTheme();
        t.name = "Custom";
        t.bg = "#111111";
        t.text = "#eeeeee";
        t.accent = "#ff00ff";
        t.font = "Comic Sans MS";
        t.glow = true;
        return t;
    }

    @Test
    void rendersMarkdownBodyIntoHtml() {
        String html = MarkdownHtmlRenderer.buildHtml("# Hello World", customTheme(), false, "");

        assertTrue(html.contains("<h1>Hello World</h1>"));
        assertTrue(html.startsWith("<html><head><style>"));
    }

    @Test
    void screenModeUsesThemeColors() {
        String html = MarkdownHtmlRenderer.buildHtml("body", customTheme(), false, "");

        assertTrue(html.contains("background-color:#111111"));
        assertTrue(html.contains("color:#eeeeee"));
    }

    @Test
    void printModeOverridesToBlackOnWhiteAndFixesTableLayout() {
        String html = MarkdownHtmlRenderer.buildHtml("body", customTheme(), true, "");

        assertTrue(html.contains("background-color:#ffffff"));
        assertTrue(html.contains("color:#000000"));
        assertTrue(html.contains("table-layout:fixed"));
    }

    @Test
    void nullThemeFallsBackToDefaultTheme() {
        String html = MarkdownHtmlRenderer.buildHtml("body", null, false, "");

        RetroTheme fallback = MarkdownHtmlRenderer.defaultTheme();
        assertTrue(html.contains("background-color:" + fallback.bg));
    }

    @Test
    void headerHtmlIsIncludedVerbatimWhenProvided() {
        String html = MarkdownHtmlRenderer.buildHtml("body", customTheme(), false, "<div class='meta'>marker</div>");

        assertTrue(html.contains("<div class='meta'>marker</div>"));
    }

    @Test
    void nullHeaderHtmlProducesNoHeaderMarkup() {
        String html = MarkdownHtmlRenderer.buildHtml("body", customTheme(), false, null);

        assertFalse(html.contains("class='meta'"));
    }

    @Test
    void safeFontReturnsDefaultForNullOrBlank() {
        assertEquals(MarkdownHtmlRenderer.defaultTheme().font, MarkdownHtmlRenderer.safeFont(null));
        assertEquals(MarkdownHtmlRenderer.defaultTheme().font, MarkdownHtmlRenderer.safeFont("   "));
    }

    @Test
    void safeFontReplacesMonospacedWithDefault() {
        assertEquals(MarkdownHtmlRenderer.defaultTheme().font, MarkdownHtmlRenderer.safeFont("Monospaced"));
        assertEquals(MarkdownHtmlRenderer.defaultTheme().font, MarkdownHtmlRenderer.safeFont("monospaced"));
    }

    @Test
    void safeFontReplacesSerifWithGeorgiaStack() {
        assertEquals("Georgia, 'Times New Roman', Times, serif", MarkdownHtmlRenderer.safeFont("Serif"));
    }

    @Test
    void safeFontPassesThroughOtherFontsTrimmed() {
        assertEquals("Comic Sans MS", MarkdownHtmlRenderer.safeFont("  Comic Sans MS  "));
    }

    @Test
    void normalizeThemeFillsInMissingFieldsFromAccent() {
        RetroTheme t = new RetroTheme();
        t.accent = "#123456";

        MarkdownHtmlRenderer.normalizeTheme(t);

        assertEquals("#123456", t.border);
        assertEquals("#123456", t.link);
        assertEquals("#123456", t.linkHover);
        assertEquals("Theme", t.name);
        assertEquals("#ffffff", t.bg);
        assertEquals("#000000", t.text);
    }

    @Test
    void renderLastModifiedHeaderFormatsTimestampDeterministically() {
        Instant fixed = Instant.parse("2026-08-02T20:15:00Z");

        String header = MarkdownHtmlRenderer.renderLastModifiedHeader(fixed, ZoneOffset.UTC, false);

        assertTrue(header.contains("2026-08-02 20:15:00"));
        assertFalse(header.contains("print profile"));
    }

    @Test
    void renderLastModifiedHeaderNotesPrintProfileWhenForPrint() {
        Instant fixed = Instant.parse("2026-08-02T20:15:00Z");

        String header = MarkdownHtmlRenderer.renderLastModifiedHeader(fixed, ZoneOffset.UTC, true);

        assertTrue(header.contains("(print profile)"));
    }

    @Test
    void rawHtmlInMarkdownIsSuppressedNotPassedThrough() {
        String html = MarkdownHtmlRenderer.buildHtml(
                "before\n\n<script>alert(1)</script>\n\n<div>raw block</div> and <b>inline</b> after",
                customTheme(), false, "");

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<div>raw block</div>"));
        assertFalse(html.contains("<b>inline</b>"));
    }

    @Test
    void remoteMarkdownImageIsStrippedFromRenderedHtml() {
        String html = MarkdownHtmlRenderer.buildHtml(
                "![tracker](https://evil.example/pixel.png)", customTheme(), false, "");

        assertFalse(html.contains("evil.example"));
        assertFalse(html.contains("<img"));
    }

    @Test
    void dataUriMarkdownImageIsPreserved() {
        String html = MarkdownHtmlRenderer.buildHtml(
                "![dot](data:image/png;base64,iVBORw0KGgo=)", customTheme(), false, "");

        assertTrue(html.contains("<img"));
        assertTrue(html.contains("data:image/png;base64,iVBORw0KGgo="));
    }

    @Test
    void rawHtmlImgTagWithRemoteSrcIsStripped() {
        String html = MarkdownHtmlRenderer.buildHtml(
                "<img src=\"http://evil.example/pixel.png\">", customTheme(), false, "");

        assertFalse(html.contains("evil.example"));
        assertFalse(html.contains("<img"));
    }
}
