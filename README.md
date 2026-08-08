# MD Print Pro

[![Build](https://github.com/OurGiant/markdown-reader/actions/workflows/build.yml/badge.svg)](https://github.com/OurGiant/markdown-reader/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/OurGiant/markdown-reader?label=Release)](https://github.com/OurGiant/markdown-reader/releases/latest)
[![License: MIT](https://img.shields.io/github/license/OurGiant/markdown-reader)](LICENSE)
[![Java 24](https://img.shields.io/badge/Java-24-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platforms](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-blue)](#releases)

A Java Swing desktop application for reading, previewing, and printing Markdown files. Renders GitHub-flavored Markdown with live file watching, theming support, and print-optimised output.

## Features

- **GFM rendering**: Tables, strikethrough, and autolinks via Flexmark
- **Live file watching**: Automatically reloads when the open file changes on disk
- **Print preview**: Toggle a print-profile view on screen before printing
- **PDF export**: Export directly to PDF using the print profile
- **Retro themes**: Multiple colour themes configurable via JSON (content styling)
- **Look & feel**: FlatLaf-based UI theming, switchable from View > Look & Feel (application chrome, separate from retro themes)
- **Print optimisations**: Fixed table layout, long-token wrapping, normalised font sizes, reflow to page width
- **Update checking**: Help > About shows the current version and checks GitHub Releases for a newer one; a non-blocking silent check also runs on startup and only notifies once per new version

## Security

Markdown files are treated as untrusted input, since they may come from anywhere
the user chooses to open. `core/MarkdownHtmlRenderer.java` hardens the render
pipeline before HTML ever reaches the `JEditorPane`:

- **Raw HTML is suppressed, not passed through.** Flexmark's `SUPPRESS_HTML_BLOCKS`
  and `SUPPRESS_INLINE_HTML` options are enabled, so HTML embedded directly in a
  `.md` file (`<script>`, `<iframe>`, raw `<img>`, etc.) is dropped rather than
  rendered.
- **Remote images are stripped.** `Swing`'s `HTMLEditorKit` fetches `<img>` URLs
  at render time, so a markdown image referencing a remote host would otherwise
  trigger an outbound network request just from opening the file (a tracking-pixel
  risk). Only `data:` URI images survive rendering — every other markdown image
  (`![alt](https://...)`) is stripped.

There is no `HyperlinkListener` registered on the `JEditorPane`, so link clicks
are inert regardless of the above.

## Prerequisites

- Java 24 or higher

## Build

```bash
mvn clean package
```

Produces `target/md-print-pro-all.jar`.

## Run

```bash
java -jar target/md-print-pro-all.jar
```

## Test

```bash
mvn test
```

Covers the `core/` package (markdown/HTML rendering, path validation, pagination
placement math) — pure logic with no `javax.swing.*` dependency, so it's directly
unit-testable without a live UI — plus `util/` (version resolution, update checking)
and the pure logic pulled out of `gui/` (`AboutDialog.isTrustedReleaseUrl`, the
release-URL allowlist check).

## Releases

Pushing a `v*` tag triggers `.github/workflows/build.yml`, which runs the test
suite, then builds and attaches native installers to a GitHub Release: a
Windows app-image zip, a macOS `.dmg` for both `arm64` and `x64`, and a Linux
`.deb`, all via `jpackage`. Icon assets live in `src/packaging/`.

## Project Structure

```
src/main/java/com/ourgiant/markdown/
├── Main.java                       # Entry point
├── AppPreferences.java              # java.util.prefs wrapper (update-check dedup, etc.)
├── ThemeManager.java                # FlatLaf look-and-feel selection
├── model/
│   ├── RetroTheme.java              # Theme data
│   ├── TagType.java                 # HTML tag classification for pagination
│   └── Candidate.java                # A candidate page-break point
├── core/                            # Pure logic, no javax.swing.* dependency
│   ├── PathValidator.java            # Validates/canonicalizes a user-supplied file path
│   ├── MarkdownHtmlRenderer.java      # Markdown -> print-aware HTML rendering
│   ├── ThemeLoader.java               # Loads themes.json from the classpath
│   └── PaginationPlanner.java         # Page-break placement math
├── util/                            # Shared helpers with no business meaning of their own
│   ├── AppVersion.java               # Resolves the running app's version (manifest/properties fallback)
│   └── UpdateChecker.java            # Checks GitHub Releases for a newer version
└── gui/
    ├── MainWindow.java               # Main application window, file watcher, print trigger
    ├── AboutDialog.java              # Help > About: version, manual + silent update checks
    └── SmartHtmlPrintable.java        # Print-aware HTML renderer (Swing layout)
```

## Dependencies

- **Flexmark**: Markdown parsing and HTML rendering (tables, strikethrough, autolink extensions)
- **Jackson**: Theme configuration loading from JSON
- **FlatLaf**: Modern look-and-feel and IntelliJ theme palette for the application chrome
- **SLF4J + Logback**: Logging, to console and `~/.md-print-pro/logs/`

## License

See LICENSE file for details.
