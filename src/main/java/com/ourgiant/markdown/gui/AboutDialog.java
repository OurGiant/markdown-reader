package com.ourgiant.markdown.gui;

import com.ourgiant.markdown.util.AppVersion;
import com.ourgiant.markdown.util.UpdateChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

/** App name, version, and license blurb. Also checks GitHub for a newer release. */
final class AboutDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(AboutDialog.class);

    /** Help > About: does its own live check, same as always. */
    AboutDialog(Frame parent) {
        this(parent, null);
    }

    /**
     * Used only by the silent startup path, which already knows {@code knownNewerRelease} --
     * skips a second, redundant network call and shows it immediately instead of flashing
     * "Checking...". Pass {@code null} to check live instead, same as the single-arg
     * constructor.
     * <p>
     * Non-modal exactly when {@code knownNewerRelease} is non-null: the auto-shown case must
     * never block the main window. Help > About (a deliberate click) stays modal.
     */
    AboutDialog(Frame parent, UpdateChecker.ReleaseInfo knownNewerRelease) {
        super(parent, "About MD Print Pro", knownNewerRelease == null);
        String currentVersion = AppVersion.resolve();

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel iconLabel = new JLabel(loadAppIcon(48));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        add(iconLabel, BorderLayout.WEST);

        JEditorPane note = new JEditorPane("text/html", buildHtml(currentVersion));
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);
        add(note, BorderLayout.CENTER);

        JLabel updateLabel = new JLabel("Checking for updates...");
        updateLabel.setForeground(Color.GRAY);
        if (knownNewerRelease != null) {
            applyNewerReleaseAvailable(updateLabel, knownNewerRelease);
        } else {
            startUpdateCheck(updateLabel, currentVersion);
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(updateLabel, BorderLayout.WEST);
        southPanel.add(buttonPanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(parent);
    }

    private static ImageIcon loadAppIcon(int size) {
        URL iconUrl = AboutDialog.class.getResource("/icon.png");
        if (iconUrl == null) {
            return new ImageIcon();
        }
        Image scaled = new ImageIcon(iconUrl).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static String buildHtml(String currentVersion) {
        return """
            <html><body style="font-family: sans-serif;">
            <h2 style="margin-top: 0;">MD Print Pro</h2>
            <p>Version %s</p>
            <p>A Swing application for rendering and printing Markdown documents.</p>
            <p>&copy; OurGiant</p>
            </body></html>
            """.formatted(currentVersion);
    }

    private void startUpdateCheck(JLabel updateLabel, String currentVersion) {
        SwingWorker<Optional<UpdateChecker.ReleaseInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<UpdateChecker.ReleaseInfo> doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                Optional<UpdateChecker.ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    logger.warn("Update check failed", e);
                    updateLabel.setText("Could not check for updates");
                    return;
                }
                if (release.isEmpty()) {
                    updateLabel.setText("Could not check for updates");
                    return;
                }
                UpdateChecker.ReleaseInfo info = release.get();
                if (!UpdateChecker.isNewerVersion(info.version(), currentVersion)) {
                    updateLabel.setText("Up to date");
                    updateLabel.setForeground(new Color(0, 128, 0));
                    return;
                }
                applyNewerReleaseAvailable(updateLabel, info);
            }
        };
        worker.execute();
    }

    private void applyNewerReleaseAvailable(JLabel updateLabel, UpdateChecker.ReleaseInfo info) {
        updateLabel.setText("<html><a href=''>Version " + info.version() + " available</a></html>");
        updateLabel.setForeground(new Color(0, 102, 204));
        updateLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    URI uri = new URI(info.htmlUrl());
                    if (!isTrustedReleaseUrl(uri)) {
                        logger.warn("Refusing to open untrusted release URL: {}", info.htmlUrl());
                        return;
                    }
                    Desktop.getDesktop().browse(uri);
                } catch (Exception ex) {
                    logger.warn("Could not open release URL in browser", ex);
                }
            }
        });
    }

    /**
     * Defense in depth: {@code htmlUrl} comes straight from GitHub's releases API response, so
     * a tampered response could otherwise point this at an arbitrary URI/scheme. Restrict to
     * exactly the host the API is expected to point back to.
     */
    static boolean isTrustedReleaseUrl(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost());
    }
}
