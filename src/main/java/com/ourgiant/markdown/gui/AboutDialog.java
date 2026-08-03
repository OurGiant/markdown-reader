package com.ourgiant.markdown.gui;

import com.ourgiant.markdown.util.AppVersion;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/** App name, version, and license blurb. */
final class AboutDialog extends JDialog {

    AboutDialog(Frame parent) {
        super(parent, "About MD Print Pro", true);
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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
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
}
