package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.RetroTheme;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Markdown -> print-aware HTML rendering. No javax.swing.* dependency, so this is directly
 *  unit-testable without a live Swing component. */
public final class MarkdownHtmlRenderer {

    // Cache flexmark objects (no need to rebuild on every update)
    private static final MutableDataSet MD_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    AutolinkExtension.create()
            ));
    private static final Parser MD_PARSER = Parser.builder(MD_OPTIONS).build();
    private static final HtmlRenderer MD_RENDERER = HtmlRenderer.builder(MD_OPTIONS).softBreak("<br />\n").build();

    private MarkdownHtmlRenderer() {}

    public static String renderLastModifiedHeader(Instant lastModified, ZoneId zone, boolean forPrint) {
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(zone)
                .format(lastModified);
        return "<div class='meta'>Last updated: " + ts + (forPrint ? " (print profile)" : "") + "</div>";
    }

    public static String buildHtml(String markdown, RetroTheme theme, boolean forPrint, String headerHtml) {
        RetroTheme t = (theme == null) ? defaultTheme() : theme;
        normalizeTheme(t);

        // Render markdown -> HTML
        String htmlBody = MD_RENDERER.render(MD_PARSER.parse(markdown));
        String header = headerHtml == null ? "" : headerHtml;

        // Print profile overrides
        String bg = forPrint ? "#ffffff" : t.bg;
        String text = forPrint ? "#000000" : t.text;
        String accent = forPrint ? "#000000" : t.accent;
        String border = forPrint ? "#cccccc" : t.border;
        String panelBg = forPrint ? "#f6f6f6" : t.panelBg;
        String codeBg = forPrint ? "#f3f3f3" : t.codeBg;
        String link = forPrint ? "#000000" : t.link;
        String linkHover = forPrint ? "#000000" : t.linkHover;
        String bodyPadding = forPrint ? "10px" : "45px";

        // Normalize fonts + sizes for print
        String bodyFontSize = forPrint ? "10pt" : "14px";
        String tableFontSize = forPrint ? "9pt" : "inherit";
        String codeFontSize = forPrint ? "9pt" : "inherit";

        String headingGlow = (!forPrint && t.glow)
                ? "text-shadow: 0 0 8px " + accent + ";"
                : "";

        // Print-safe table rules prevent truncation
        String tablePrintRules = forPrint
                ? "table{table-layout:fixed;} th,td{overflow-wrap:anywhere;word-break:break-word;word-wrap:break-word;font-size:" + tableFontSize + ";}"
                : "";

        String css = ""
                + "body{background-color:" + bg + ";color:" + text + ";font-family:" + safeFont(t.font) + ";padding:" + bodyPadding + ";line-height:1.6;font-size:" + bodyFontSize + ";}"
                + ".meta{opacity:0.75;font-size:6px;margin-bottom:10px;}"
                + "h1,h2,h3{color:" + accent + ";" + headingGlow + "}"
                + "h2{border-bottom:2px solid " + accent + ";padding-bottom:6px;}"
                + "hr{border:0;border-top:1px solid " + border + ";margin:18px 0;}"
                + "a{color:" + link + ";text-decoration:underline;}"
                + "a:hover{color:" + linkHover + ";}"
                + "blockquote{margin:16px 0;padding:10px 14px;background:" + panelBg + ";border-left:6px solid " + accent + ";}"
                + "table{border-collapse:collapse;width:100%;margin:20px 0;border:1px solid " + border + ";}"
                + "th{background:" + accent + ";color:" + bg + ";padding:12px;text-align:left;}"
                + "td{border:1px solid " + border + ";padding:10px;}"
                + "tr:nth-child(even) td{background:rgba(128,128,128,0.10);}"
                + "tr:hover td{background:rgba(128,128,128,0.12);}"
                + "code{background:" + codeBg + ";padding:2px 6px;border-radius:4px;color:" + accent + ";font-size:" + codeFontSize + ";}"
                + "pre{background:" + codeBg + ";padding:16px;border-left:6px solid " + accent + ";overflow-x:auto;font-size:" + codeFontSize + ";}"
                + "img{max-width:100%;height:auto;}"
                + tablePrintRules;

        return "<html><head><style>" + css + "</style></head><body>" + header + htmlBody + "</body></html>";
    }

    public static String safeFont(String font) {
        if (font == null || font.isBlank()) {
            return defaultTheme().font;
        }
        String f = font.trim();
        if (Objects.equals(f, "Monospaced") || Objects.equals(f, "monospaced")) {
            return defaultTheme().font;
        }
        if (Objects.equals(f, "Serif") || Objects.equals(f, "serif")) {
            return "Georgia, 'Times New Roman', Times, serif";
        }
        return f;
    }

    public static void normalizeTheme(RetroTheme t) {
        if (t.name == null) t.name = "Theme";
        if (t.bg == null) t.bg = "#ffffff";
        if (t.text == null) t.text = "#000000";
        if (t.accent == null) t.accent = "#2980b9";
        if (t.font == null) t.font = defaultTheme().font;

        if (t.panelBg == null) t.panelBg = "rgba(128,128,128,0.08)";
        if (t.codeBg == null) t.codeBg = "rgba(0,0,0,0.30)";
        if (t.border == null) t.border = t.accent;
        if (t.link == null) t.link = t.accent;
        if (t.linkHover == null) t.linkHover = t.accent;

        t.font = safeFont(t.font);
    }

    public static RetroTheme defaultTheme() {
        RetroTheme t = new RetroTheme();
        t.name = "Modern Blue";
        t.bg = "#ffffff";
        t.accent = "#2980b9";
        t.text = "#333333";
        t.font = "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace";
        t.panelBg = "#f6f6f6";
        t.codeBg = "#f2f2f2";
        t.border = "#dddddd";
        t.link = "#1a73e8";
        t.linkHover = "#0b57d0";
        t.glow = false;
        return t;
    }
}
