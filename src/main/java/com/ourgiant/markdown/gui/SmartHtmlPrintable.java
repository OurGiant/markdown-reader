package com.ourgiant.markdown.gui;

import com.ourgiant.markdown.core.PaginationPlanner;
import com.ourgiant.markdown.model.Candidate;
import com.ourgiant.markdown.model.TagType;

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
    private static final int TOP_PAD_PX = 0;                 // you already have margins via Paper/imageable area
    private static final int BOTTOM_PAD_PX = 0;
    private static final int KEEP_HEADING_WITH_NEXT_PX = 140;  // if a heading is within last ~140px of a page, push it
    private static final int KEEP_HEADER_ROW_WITH_BODY_PX = 90; // if header row would be orphaned, push it too

    private final PaginationPlanner planner =
            new PaginationPlanner(TOP_PAD_PX, BOTTOM_PAD_PX, KEEP_HEADING_WITH_NEXT_PX, KEEP_HEADER_ROW_WITH_BODY_PX);

    SmartHtmlPrintable(JEditorPane pane, PageFormat pf) {
        this.pane = pane;
        this.pf = pf;
    }

    @Override
    public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
        ensureLaidOutAndCandidates();

        // Ensure pageStarts computed up to this page
        while (pageStarts.size() <= pageIndex) {
            int nextStart = planner.computeNextPageStart(candidates, contentHeight, pf.getImageableHeight(),
                    pageStarts.isEmpty() ? 0 : pageStarts.getLast());
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
            g2.translate(ix, iy - yStart + TOP_PAD_PX);

            // Clip to the imageable area (prevents drawing outside page)
            g2.setClip(new Rectangle2D.Double(0, yStart, iw, ih - TOP_PAD_PX - BOTTOM_PAD_PX));

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
            list.sort(Comparator.<Candidate>comparingInt(Candidate::y).thenComparingInt(Candidate::offset));

            // De-dup near-identical Ys (optional but nice)
            candidates = PaginationPlanner.dedupeByY(list, 2);
        } catch (Exception e) {
            throw new PrinterException("Failed to prepare pagination: " + e.getMessage());
        }
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
}
