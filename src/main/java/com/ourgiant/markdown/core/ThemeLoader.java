package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.RetroTheme;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads the app's retro theme palette from {@code themes.json} on the classpath, falling back
 *  to a single built-in default theme if the resource is missing or malformed. */
public final class ThemeLoader {

    private ThemeLoader() {}

    public static List<RetroTheme> loadThemes() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = ThemeLoader.class.getResourceAsStream("/themes.json")) {
            if (is == null) throw new IOException("themes.json not found");
            List<RetroTheme> themes = mapper.readValue(is, new TypeReference<List<RetroTheme>>() {});
            if (themes == null || themes.isEmpty()) throw new IOException("themes.json empty");
            themes.forEach(MarkdownHtmlRenderer::normalizeTheme);
            return themes;
        } catch (Exception e) {
            return List.of(MarkdownHtmlRenderer.defaultTheme());
        }
    }
}
