package com.ourgiant.markdown.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Validates and canonicalizes a user-supplied file path before it's opened. Security boundary:
 *  rejects paths that don't resolve to an existing regular file. */
public final class PathValidator {

    private PathValidator() {}

    public static String validateAndNormalizePath(String path) throws IOException, SecurityException {
        if (path == null || path.trim().isEmpty()) {
            throw new SecurityException("Path cannot be null or empty");
        }

        Path filePath = Paths.get(path);
        Path realPath = filePath.toRealPath();

        if (!Files.exists(realPath)) {
            throw new SecurityException("File does not exist: " + path);
        }

        if (!Files.isRegularFile(realPath)) {
            throw new SecurityException("Path is not a regular file: " + path);
        }

        return realPath.toString();
    }
}
