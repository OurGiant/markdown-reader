package com.ourgiant.markdown;

import com.ourgiant.markdown.gui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            if (!ThemeManager.applyTheme("Flat Light")) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Fall through to whatever the platform default is.
                }
            }

            MainWindow window = new MainWindow();
            window.setVisible(true);
            if (args.length > 0) {
                window.openFile(args[0]);
            } else {
                window.promptOpenFile();
            }
        });
    }
}
