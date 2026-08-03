package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.RetroTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeLoaderTest {

    @Test
    void loadsThemesFromClasspathResourceAndNormalizesThem() {
        List<RetroTheme> themes = ThemeLoader.loadThemes();

        assertFalse(themes.isEmpty());
        for (RetroTheme theme : themes) {
            assertNotNull(theme.name);
            assertNotNull(theme.bg);
            assertNotNull(theme.text);
            assertNotNull(theme.accent);
            assertNotNull(theme.font);
        }
    }
}
