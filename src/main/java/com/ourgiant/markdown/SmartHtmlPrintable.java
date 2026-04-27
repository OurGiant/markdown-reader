package com.ourgiant.markdown;


import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.*;
import java.util.List;

public class SmartHtmlPrintable implements Printable {
    private final JEditorPane pane;
    private final PageFormat pf;

    // Computed page start Y positions (in pane coordinates)
    private final List<Integer> pageStarts = new ArrayList<>();

    // Candidate breakpoints sorted by Y
    private List<Candidate> candidates = List.of();
    private int contentHeight = -1;

    // Heuristics (tune as desired)
    private final int topPadPx = 0;                 // you already have margins via Paper/imageable area
    private final int bottomPadPx = 0;
    private final int keepHeadingWithNextPx = 140;  // if a heading is within last ~140px of a page, push it
    private final int keepHeaderRowWithBodyPx = 90; // if header row would be orphaned, push it too

    SmartHtmlPrintable(JEditorPane pane, PageFormat pf) {
        this.pane = pane;
        this.pf = pf;
    }

    @Override
    public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
        ensureLaidOutAndCandidates();

        // Ensure pageStarts computed up to this page
        while (pageStarts.size() <= pageIndex) {
            int nextStart = computeNextPageStart(pageStarts.isEmpty() ? 0 : pageStarts.getLast());
            if (nextStart < 0) break; // no more pages
            pageStarts.add(nextStart);
        }

        if (pageIndex >= pageStarts.size()) return NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            double ix = pageFormat.getImageableX();
            double iy = pageFormat.getImageableY();
            double iw = pageFormat.getImageableWidth();
            double ih = pageFormat.getImageableHeight();

            int yStart = pageStarts.get(pageIndex);

            // Translate so that yStart is at top of imageable area
            g2.translate(ix, iy - yStart + topPadPx);

            // Clip to the imageable area (prevents drawing outside page)
            g2.setClip(new Rectangle2D.Double(0, yStart, iw, ih - topPadPx - bottomPadPx));

            // Paint the whole pane; clip+translate show only one page slice
            pane.printAll(g2);

            return PAGE_EXISTS;
        } finally {
            g2.dispose();
        }
    }

    private void ensureLaidOutAndCandidates() throws PrinterException {
        if (candidates != null && !candidates.isEmpty() && contentHeight > 0) return;

        try {
            // Must have HTMLDocument
            Document doc = pane.getDocument();
            if (!(doc instanceof HTMLDocument htmlDoc)) {
                // fallback: just use pane preferred height
                pane.validate();
                contentHeight = pane.getPreferredSize().height;
                candidates = List.of(new Candidate(0, 0, TagType.OTHER, false));
                return;
            }

            pane.validate();
            contentHeight = pane.getPreferredSize().height;

            ArrayList<Candidate> list = new ArrayList<>();
            collectCandidates(htmlDoc.getDefaultRootElement(), list, htmlDoc);

            // Always include y=0
            list.add(new Candidate(0, 0, TagType.OTHER, false));

            // Sort by Y then by model offset
            list.sort(Comparator.<Candidate>comparingInt(c -> c.y).thenComparingInt(c -> c.offset));

            // De-dup near-identical Ys (optional but nice)
            list = dedupeByY(list, 2);

            candidates = list;
        } catch (Exception e) {
            throw new PrinterException("Failed to prepare pagination: " + e.getMessage());
        }
    }

    private int computeNextPageStart(int currentStartY) {
        double pageH = pf.getImageableHeight() - topPadPx - bottomPadPx;
        int limitY = (int) Math.floor(currentStartY + pageH);

        if (currentStartY >= contentHeight - 5) return -1;

        // Find the last candidate that begins before limitY
        int idx = upperBoundY(candidates, limitY) - 1;

        // If we can’t find a better break, advance at least one pixel to avoid infinite loop
        if (idx < 0) return -1;

        Candidate chosen = candidates.get(idx);

        // If chosen.y <= currentStartY, force forward progress
        if (chosen.y <= currentStartY + 1) {
            // Find next candidate after currentStartY
            int nextIdx = upperBoundY(candidates, currentStartY + 2);
            if (nextIdx >= candidates.size()) return -1;
            chosen = candidates.get(nextIdx);
        }

        // Apply "keep-with-next" heuristics
        Candidate adjusted = applyKeepRules(currentStartY, limitY, chosen, idx);

        // If adjustment would stall, keep forward progress
        if (adjusted.y <= currentStartY + 1) return chosen.y;

        return adjusted.y;
    }

    private Candidate applyKeepRules(int pageStartY, int pageLimitY, Candidate chosen, int chosenIdx) {
        // Rule 1: Avoid breaking between heading and a table that follows.
        // If the break we chose is at a TABLE start, and immediately before it is a heading,
        // and that heading would be stuck near the bottom => move break to heading instead.
        if (chosen.type == TagType.TABLE && chosenIdx > 0) {
            Candidate prev = candidates.get(chosenIdx - 1);
            if (prev.type == TagType.H1 || prev.type == TagType.H2 || prev.type == TagType.H3) {
                int headingBottomOnPage = prev.y - pageStartY;
                if ((pageLimitY - prev.y) < keepHeadingWithNextPx) {
                    return prev; // push heading + table to next page together
                }
            }
        }

        // Rule 2: Avoid orphaning THEAD row.
        // If chosen is the first BODY row (TR not in THEAD) and previous TR is a header row,
        // and header row would end up at page bottom => push header row too.
        if (chosen.type == TagType.TR && !chosen.isHeaderRow && chosenIdx > 0) {
            Candidate prev = candidates.get(chosenIdx - 1);
            if (prev.type == TagType.TR && prev.isHeaderRow) {
                if ((pageLimitY - prev.y) < keepHeaderRowWithBodyPx) {
                    return prev; // move the break to header row start
                }
            }
        }

        // Rule 3 (optional): If chosen is any heading, avoid leaving it at bottom with little following space.
        if (chosen.type == TagType.H1 || chosen.type == TagType.H2 || chosen.type == TagType.H3) {
            if ((pageLimitY - chosen.y) < keepHeadingWithNextPx) {
                // move heading to next page by breaking at itself (already) -> no-op
                return chosen;
            }
        }

        return chosen;
    }

    private static int upperBoundY(List<Candidate> list, int y) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).y <= y) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private void collectCandidates(Element element, List<Candidate> out, HTMLDocument doc) throws BadLocationException {
        TagType type = tagTypeOf(element);
        boolean isHeaderRow = (type == TagType.TR) && rowContainsTh(element);

        // Only store break candidates for the tag types we care about
        if (type != TagType.OTHER) {
            int offset = element.getStartOffset();
            Rectangle2D r = pane.modelToView2D(offset);
            if (r != null) {
                out.add(new Candidate(offset, (int) Math.floor(r.getY()), type, isHeaderRow));
            }
        }

        for (int i = 0; i < element.getElementCount(); i++) {
            collectCandidates(element.getElement(i), out, doc);
        }
    }

    private boolean rowContainsTh(Element tr) {
        // Walk descendants of the TR and see if any are TH
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(tr);

        while (!stack.isEmpty()) {
            Element el = stack.pop();
            AttributeSet a = el.getAttributes();
            Object nameAttr = a.getAttribute(StyleConstants.NameAttribute);

            if (nameAttr == HTML.Tag.TH) {
                return true;
            }

            for (int i = 0; i < el.getElementCount(); i++) {
                stack.push(el.getElement(i));
            }
        }
        return false;
    }

    private TagType tagTypeOf(Element el) {
        AttributeSet a = el.getAttributes();
        Object nameAttr = a.getAttribute(StyleConstants.NameAttribute);

        if (nameAttr instanceof HTML.Tag tag) {
            if (tag == HTML.Tag.H1) return TagType.H1;
            if (tag == HTML.Tag.H2) return TagType.H2;
            if (tag == HTML.Tag.H3) return TagType.H3;
            if (tag == HTML.Tag.P)  return TagType.P;
            if (tag == HTML.Tag.PRE) return TagType.PRE;
            if (tag == HTML.Tag.BLOCKQUOTE) return TagType.BLOCKQUOTE;
            if (tag == HTML.Tag.TABLE) return TagType.TABLE;
            if (tag == HTML.Tag.TR) return TagType.TR;
            // you can add UL/OL/LI if desired
        }
        return TagType.OTHER;
    }

    private boolean hasAncestorTag(Element el, HTML.Tag tag) {
        Element p = el.getParentElement();
        while (p != null) {
            AttributeSet a = p.getAttributes();
            Object nameAttr = a.getAttribute(StyleConstants.NameAttribute);
            if (nameAttr == tag) return true;
            p = p.getParentElement();
        }
        return false;
    }

    private static ArrayList<Candidate> dedupeByY(ArrayList<Candidate> list, int tolerancePx) {
        ArrayList<Candidate> out = new ArrayList<>();
        Candidate last = null;
        for (Candidate c : list) {
            if (last == null || Math.abs(c.y - last.y) > tolerancePx) {
                out.add(c);
                last = c;
            }
        }
        return out;
    }

    enum TagType { H1, H2, H3, P, PRE, BLOCKQUOTE, TABLE, TR, OTHER }

    static class Candidate {
        final int offset;
        final int y;
        final TagType type;
        final boolean isHeaderRow;

        Candidate(int offset, int y, TagType type, boolean isHeaderRow) {
            this.offset = offset;
            this.y = y;
            this.type = type;
            this.isHeaderRow = isHeaderRow;
        }
    }
}
