package com.ourgiant.markdown.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PathValidator is the app's security boundary for opening a user-supplied file path — see
 *  issue #5's priority note. Covers the rejection cases as well as the happy path. */
class PathValidatorTest {

    @Test
    void rejectsNullPath() {
        assertThrows(SecurityException.class, () -> PathValidator.validateAndNormalizePath(null));
    }

    @Test
    void rejectsEmptyPath() {
        assertThrows(SecurityException.class, () -> PathValidator.validateAndNormalizePath(""));
    }

    @Test
    void rejectsBlankPath() {
        assertThrows(SecurityException.class, () -> PathValidator.validateAndNormalizePath("   "));
    }

    @Test
    void rejectsNonExistentPath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.md");
        assertThrows(IOException.class, () -> PathValidator.validateAndNormalizePath(missing.toString()));
    }

    @Test
    void rejectsDirectory(@TempDir Path tempDir) throws IOException {
        assertThrows(SecurityException.class, () -> PathValidator.validateAndNormalizePath(tempDir.toString()));
    }

    @Test
    void acceptsExistingRegularFileAndReturnsRealPath(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("doc.md");
        Files.writeString(file, "# Hello");

        String result = PathValidator.validateAndNormalizePath(file.toString());

        assertEquals(file.toRealPath().toString(), result);
    }

    @Test
    void resolvesRelativePathsToRealPath(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("doc.md");
        Files.writeString(file, "# Hello");
        Path relative = tempDir.resolve(".").resolve("doc.md");

        String result = PathValidator.validateAndNormalizePath(relative.toString());

        assertEquals(file.toRealPath().toString(), result);
    }
}
