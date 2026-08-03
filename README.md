# MD Print Pro

A Java Swing desktop application for reading, previewing, and printing Markdown files. Renders GitHub-flavored Markdown with live file watching, theming support, and print-optimised output.

## Features

- **GFM rendering**: Tables, strikethrough, and autolinks via Flexmark
- **Live file watching**: Automatically reloads when the open file changes on disk
- **Print preview**: Toggle a print-profile view on screen before printing
- **PDF export**: Export directly to PDF using the print profile
- **Retro themes**: Multiple colour themes configurable via JSON
- **Print optimisations**: Fixed table layout, long-token wrapping, normalised font sizes, reflow to page width

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

## Project Structure

```
src/main/java/com/ourgiant/markdown/
├── Main.java                       # Entry point
├── model/
│   ├── RetroTheme.java              # Theme data
│   ├── TagType.java                 # HTML tag classification for pagination
│   └── Candidate.java                # A candidate page-break point
├── core/                            # Pure logic, no javax.swing.* dependency
│   ├── PathValidator.java            # Validates/canonicalizes a user-supplied file path
│   ├── MarkdownHtmlRenderer.java      # Markdown -> print-aware HTML rendering
│   ├── ThemeLoader.java               # Loads themes.json from the classpath
│   └── PaginationPlanner.java         # Page-break placement math
└── gui/
    ├── MainWindow.java               # Main application window, file watcher, print trigger
    └── SmartHtmlPrintable.java        # Print-aware HTML renderer (Swing layout)
```

## Dependencies

- **Flexmark**: Markdown parsing and HTML rendering (tables, strikethrough, autolink extensions)
- **Jackson**: Theme configuration loading from JSON

## License

See LICENSE file for details.
