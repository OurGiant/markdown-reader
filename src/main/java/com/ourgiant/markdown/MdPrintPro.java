package com.ourgiant.markdown;

import com.ourgiant.markdown.SmartHtmlPrintable;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.awt.print.Paper;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MarkDown Client
 * Print optimizations:
 *  - Print Preview toggle (renders with print profile on screen)
 *  - Export PDF ALWAYS uses print profile automatically
 *  - Print CSS forces fixed table layout + wraps long tokens to prevent truncation
 *  - Print font sizes normalized (body/table/code)
 *  - Reflow to page width before printing
 */
public class MdPrintPro {
    private static JFrame frame;
    private static JEditorPane editPane;

    private static List<RetroTheme> availableThemes = new ArrayList<>();
    private static RetroTheme currentTheme;

    private static String currentOpenPath = null;
    private static WatchKey currentWatchKey = null;

    // Print preview toggle state
    private static boolean printPreview = false;

    // Cache flexmark objects (no need to rebuild on every update)
    private static final MutableDataSet MD_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    AutolinkExtension.create()
            ));
    private static final Parser MD_PARSER = Parser.builder(MD_OPTIONS).build();
    private static final HtmlRenderer MD_RENDERER = HtmlRenderer.builder(MD_OPTIONS).softBreak("<br />\n").build();

    public static class RetroTheme {
        public String name;
        public String bg;
        public String accent;
        public String text;
        public String font;

        // Optional, backward-compatible tokens
        public String panelBg;
        public String codeBg;
        public String border;
        public String link;
        public String linkHover;

        public boolean glow;

        @Override public String toString() { return name; }
    }

    public static void main(String[] args) throws Exception {
        availableThemes = loadThemes();
        currentTheme = availableThemes.getFirst();

        SwingUtilities.invokeLater(() -> {
            setupUI();
            if (args.length > 0) {
                openFile(args[0]);
            } else {
                promptOpenFile();
            }
        });
    }

    private static void setupUI() {
        frame = new JFrame("MarkDown Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open Markdown...");
        openItem.addActionListener(e -> promptOpenFile());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // Icon
        try (InputStream is = MdPrintPro.class.getResourceAsStream("/icon.png")) {
            if (is != null) frame.setIconImage(ImageIO.read(is));
        } catch (IOException ignored) {}

        editPane = new JEditorPane("text/html",
                "<html><body><h2 style='text-align:center;margin-top:50px;'>Select a File to Begin Audit</h2></body></html>");
        editPane.setEditable(false);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Print Preview toggle
        JCheckBox previewToggle = new JCheckBox("Print Preview");
        previewToggle.setSelected(printPreview);
        previewToggle.addActionListener(e -> {
            printPreview = previewToggle.isSelected();
            if (currentOpenPath != null) updateContent();
        });

        JComboBox<RetroTheme> themeBox = new JComboBox<>(availableThemes.toArray(new RetroTheme[0]));
        themeBox.setSelectedItem(currentTheme);
        themeBox.addActionListener(e -> {
            currentTheme = (RetroTheme) themeBox.getSelectedItem();
            if (currentTheme != null) normalizeTheme(currentTheme);
            if (currentOpenPath != null) updateContent();
        });

        JButton printBtn = new JButton("Export PDF");
        printBtn.addActionListener(e -> triggerPrint());

        bar.add(previewToggle);
        bar.add(new JLabel("Theme: "));
        bar.add(themeBox);
        bar.add(printBtn);

        frame.add(new JScrollPane(editPane), BorderLayout.CENTER);
        frame.add(bar, BorderLayout.SOUTH);

        frame.setSize(1150, 850);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void promptOpenFile() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("Select Audit Report (Markdown)");
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown Files", "md", "markdown"));

        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static String validateAndNormalizePath(String path) throws IOException, SecurityException {
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

    private static void openFile(String path) {
        try {
            String validatedPath = validateAndNormalizePath(path);
            currentOpenPath = validatedPath;
            frame.setTitle("MarkDown Client- " + validatedPath);
            updateContent();
            startWatcher(validatedPath);
        } catch (IOException | SecurityException e) {
            editPane.setText("<html><body><h2>Error Opening File</h2><p>" + e.getMessage() + "</p></body></html>");
            System.err.println("Failed to open file: " + e.getMessage());
        }
    }

    private static void startWatcher(String filePath) {
        if (currentWatchKey != null) {
            currentWatchKey.cancel();
        }

        Thread.ofVirtual().start(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                Path path = Paths.get(filePath).toAbsolutePath();
                currentWatchKey = path.getParent().register(ws, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals(path.getFileName().toString())) {
                            playClick();
                            SwingUtilities.invokeLater(MdPrintPro::updateContent);
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                System.err.println("Watcher Error: " + e.getMessage());
            }
        });
    }

    private static void updateContent() {
        if (currentOpenPath == null) return;
        try {
            String markdown = Files.readString(Paths.get(currentOpenPath), StandardCharsets.UTF_8);
            editPane.setText(buildHtml(markdown, currentTheme, printPreview));
            editPane.setCaretPosition(0);
        } catch (IOException e) {
            editPane.setText("<html><body><h2>Error Loading File</h2></body></html>");
        }
    }

    private static String buildHtml(String markdown, RetroTheme theme, boolean forPrint) {
        RetroTheme t = (theme == null) ? defaultTheme() : theme;
        normalizeTheme(t);

        // Render markdown -> HTML
        String htmlBody = MD_RENDERER.render(MD_PARSER.parse(markdown));

        // Header with last-modified timestamp
        String header = "";
        if (currentOpenPath != null) {
            try {
                FileTime ft = Files.getLastModifiedTime(Paths.get(currentOpenPath));
                String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(ft.toMillis()));
                header = "<div class='meta'>Last updated: " + ts + (forPrint ? " (print profile)" : "") + "</div>";
            } catch (Exception ignored) {}
        }

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

    private static String safeFont(String font) {
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

    private static void normalizeTheme(RetroTheme t) {
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

    private static List<RetroTheme> loadThemes() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = MdPrintPro.class.getResourceAsStream("/themes.json")) {
            if (is == null) throw new IOException("themes.json not found");
            List<RetroTheme> themes = mapper.readValue(is, new TypeReference<List<RetroTheme>>() {});
            if (themes == null || themes.isEmpty()) throw new IOException("themes.json empty");
            themes.forEach(MdPrintPro::normalizeTheme);
            return themes;
        } catch (Exception e) {
            return List.of(defaultTheme());
        }
    }

    private static RetroTheme defaultTheme() {
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

    private static void playClick() {
        Thread.ofVirtual().start(() -> {
            try (InputStream is = MdPrintPro.class.getResourceAsStream("/click.wav")) {
                if (is == null) return;
                AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception ignored) {}
        });
    }

    private static void triggerPrint() {
        if (currentOpenPath == null) return;

        // ALWAYS force print profile for export
        String originalHtml = editPane.getText();
        boolean originalPreview = printPreview;

        try {
            String markdown = Files.readString(Paths.get(currentOpenPath), StandardCharsets.UTF_8);
            String printHtml = buildHtml(markdown, currentTheme, true);
            editPane.setText(printHtml);

            PrinterJob job = PrinterJob.getPrinterJob();
            PageFormat pf = job.defaultPage();
            Paper paper = pf.getPaper();

            
            // Letter size: 8.5 × 11 inches = 612 × 792 points
            double margin = 36; // 0.5 inch (72 pts = 1 inch)
            paper.setImageableArea(
                margin,
                margin,
                paper.getWidth() - (2 * margin),
                paper.getHeight() - (2 * margin)
            );
            pf.setPaper(paper);

            // Reflow the editor to page width BEFORE creating printable
            int pageWidth = (int) pf.getImageableWidth();
            editPane.setSize(new Dimension(pageWidth, Integer.MAX_VALUE));

            // Printable printable = editPane.getPrintable(null, null);
            job.setPrintable(new SmartHtmlPrintable(editPane, pf), pf);

            if (job.printDialog()) {
                job.print();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // Restore UI state
            printPreview = originalPreview;
            editPane.setText(originalHtml);
            editPane.setCaretPosition(0);
        }
    }
}
