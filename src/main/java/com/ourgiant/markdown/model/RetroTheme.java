package com.ourgiant.markdown.model;

public class RetroTheme {
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
