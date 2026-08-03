package com.ourgiant.markdown;

import com.ourgiant.markdown.gui.MainWindow;

import javax.swing.SwingUtilities;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
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
