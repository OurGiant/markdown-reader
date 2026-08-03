package com.ourgiant.markdown.gui;

import com.ourgiant.markdown.core.MarkdownHtmlRenderer;
import com.ourgiant.markdown.core.PathValidator;
import com.ourgiant.markdown.core.ThemeLoader;
import com.ourgiant.markdown.model.RetroTheme;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * MarkDown Client main window.
 * Print optimizations:
 *  - Print Preview toggle (renders with print profile on screen)
 *  - Export PDF ALWAYS uses print profile automatically
 *  - Print CSS forces fixed table layout + wraps long tokens to prevent truncation
 *  - Print font sizes normalized (body/table/code)
 *  - Reflow to page width before printing
 */
public final class MainWindow extends JFrame {

    private final List<RetroTheme> availableThemes;
    private RetroTheme currentTheme;

    private JEditorPane editPane;

    private String currentOpenPath = null;
    private WatchKey currentWatchKey = null;

    // Print preview toggle state
    private boolean printPreview = false;

    public MainWindow() {
        super("MarkDown Client");
        availableThemes = ThemeLoader.loadThemes();
        currentTheme = availableThemes.getFirst();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setupUI();
    }

    private void setupUI() {
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

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        // Icon
        try (InputStream is = MainWindow.class.getResourceAsStream("/icon.png")) {
            if (is != null) setIconImage(ImageIO.read(is));
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

        JButton themeBtn = new JButton(currentTheme != null ? currentTheme.name : "Theme");
        JPopupMenu themeMenu = new JPopupMenu();
        for (RetroTheme theme : availableThemes) {
            JMenuItem item = new JMenuItem(theme.name);
            item.addActionListener(e -> {
                currentTheme = theme;
                MarkdownHtmlRenderer.normalizeTheme(currentTheme);
                themeBtn.setText(currentTheme.name);
                if (currentOpenPath != null) updateContent();
            });
            themeMenu.add(item);
        }
        themeBtn.addActionListener(e -> themeMenu.show(themeBtn, 0, themeBtn.getHeight()));

        JButton printBtn = new JButton("Export PDF");
        printBtn.addActionListener(e -> triggerPrint());

        bar.add(previewToggle);
        bar.add(new JLabel("Theme: "));
        bar.add(themeBtn);
        bar.add(printBtn);

        add(new JScrollPane(editPane), BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);

        setSize(1150, 850);
        setLocationRelativeTo(null);
    }

    public void promptOpenFile() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("Select Audit Report (Markdown)");
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown Files", "md", "markdown"));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    public void openFile(String path) {
        try {
            String validatedPath = PathValidator.validateAndNormalizePath(path);
            currentOpenPath = validatedPath;
            setTitle("MarkDown Client- " + validatedPath);
            updateContent();
            startWatcher(validatedPath);
        } catch (IOException | SecurityException e) {
            editPane.setText("<html><body><h2>Error Opening File</h2><p>" + e.getMessage() + "</p></body></html>");
            System.err.println("Failed to open file: " + e.getMessage());
        }
    }

    private void startWatcher(String filePath) {
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
                            SwingUtilities.invokeLater(this::updateContent);
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                System.err.println("Watcher Error: " + e.getMessage());
            }
        });
    }

    private void updateContent() {
        if (currentOpenPath == null) return;
        try {
            String markdown = Files.readString(Paths.get(currentOpenPath), StandardCharsets.UTF_8);
            editPane.setText(buildHtmlForCurrentFile(markdown, printPreview));
            editPane.setCaretPosition(0);
        } catch (IOException e) {
            editPane.setText("<html><body><h2>Error Loading File</h2></body></html>");
        }
    }

    private String buildHtmlForCurrentFile(String markdown, boolean forPrint) {
        String header = "";
        if (currentOpenPath != null) {
            try {
                FileTime ft = Files.getLastModifiedTime(Paths.get(currentOpenPath));
                header = MarkdownHtmlRenderer.renderLastModifiedHeader(
                        Instant.ofEpochMilli(ft.toMillis()), ZoneId.systemDefault(), forPrint);
            } catch (Exception ignored) {}
        }
        return MarkdownHtmlRenderer.buildHtml(markdown, currentTheme, forPrint, header);
    }

    private void playClick() {
        Thread.ofVirtual().start(() -> {
            try (InputStream is = MainWindow.class.getResourceAsStream("/click.wav")) {
                if (is == null) return;
                AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception ignored) {}
        });
    }

    private void triggerPrint() {
        if (currentOpenPath == null) return;

        // ALWAYS force print profile for export
        String originalHtml = editPane.getText();
        boolean originalPreview = printPreview;

        try {
            String markdown = Files.readString(Paths.get(currentOpenPath), StandardCharsets.UTF_8);
            String printHtml = buildHtmlForCurrentFile(markdown, true);
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
