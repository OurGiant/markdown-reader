package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.Candidate;
import com.ourgiant.markdown.model.TagType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure page-break placement math for {@code gui.SmartHtmlPrintable}: given a sorted list of
 * candidate break points and page geometry, decides where the next page should start. No
 * javax.swing.* dependency, so the pagination heuristics are directly unit-testable without a
 * live JEditorPane.
 */
public final class PaginationPlanner {
    private final int topPadPx;
    private final int bottomPadPx;
    private final int keepHeadingWithNextPx;
    private final int keepHeaderRowWithBodyPx;

    public PaginationPlanner(int topPadPx, int bottomPadPx, int keepHeadingWithNextPx, int keepHeaderRowWithBodyPx) {
        this.topPadPx = topPadPx;
        this.bottomPadPx = bottomPadPx;
        this.keepHeadingWithNextPx = keepHeadingWithNextPx;
        this.keepHeaderRowWithBodyPx = keepHeaderRowWithBodyPx;
    }

    public int computeNextPageStart(List<Candidate> candidates, int contentHeight, double imageableHeight, int currentStartY) {
        double pageH = imageableHeight - topPadPx - bottomPadPx;
        int limitY = (int) Math.floor(currentStartY + pageH);

        if (currentStartY >= contentHeight - 5) return -1;

        // Find the last candidate that begins before limitY
        int idx = upperBoundY(candidates, limitY) - 1;

        // If we can't find a better break, advance at least one pixel to avoid infinite loop
        if (idx < 0) return -1;

        Candidate chosen = candidates.get(idx);

        // If chosen.y <= currentStartY, force forward progress
        if (chosen.y() <= currentStartY + 1) {
            // Find next candidate after currentStartY
            int nextIdx = upperBoundY(candidates, currentStartY + 2);
            if (nextIdx >= candidates.size()) return -1;
            chosen = candidates.get(nextIdx);
        }

        // Apply "keep-with-next" heuristics
        Candidate adjusted = applyKeepRules(candidates, currentStartY, limitY, chosen, idx);

        // If adjustment would stall, keep forward progress
        if (adjusted.y() <= currentStartY + 1) return chosen.y();

        return adjusted.y();
    }

    private Candidate applyKeepRules(List<Candidate> candidates, int pageStartY, int pageLimitY, Candidate chosen, int chosenIdx) {
        // Rule 1: Avoid breaking between heading and a table that follows.
        // If the break we chose is at a TABLE start, and immediately before it is a heading,
        // and that heading would be stuck near the bottom => move break to heading instead.
        if (chosen.type() == TagType.TABLE && chosenIdx > 0) {
            Candidate prev = candidates.get(chosenIdx - 1);
            if (prev.type() == TagType.H1 || prev.type() == TagType.H2 || prev.type() == TagType.H3) {
                if ((pageLimitY - prev.y()) < keepHeadingWithNextPx) {
                    return prev; // push heading + table to next page together
                }
            }
        }

        // Rule 2: Avoid orphaning THEAD row.
        // If chosen is the first BODY row (TR not in THEAD) and previous TR is a header row,
        // and header row would end up at page bottom => push header row too.
        if (chosen.type() == TagType.TR && !chosen.isHeaderRow() && chosenIdx > 0) {
            Candidate prev = candidates.get(chosenIdx - 1);
            if (prev.type() == TagType.TR && prev.isHeaderRow()) {
                if ((pageLimitY - prev.y()) < keepHeaderRowWithBodyPx) {
                    return prev; // move the break to header row start
                }
            }
        }

        // Rule 3 (optional): If chosen is any heading, avoid leaving it at bottom with little following space.
        if (chosen.type() == TagType.H1 || chosen.type() == TagType.H2 || chosen.type() == TagType.H3) {
            if ((pageLimitY - chosen.y()) < keepHeadingWithNextPx) {
                // move heading to next page by breaking at itself (already) -> no-op
                return chosen;
            }
        }

        return chosen;
    }

    public static int upperBoundY(List<Candidate> list, int y) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).y() <= y) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    public static List<Candidate> dedupeByY(List<Candidate> list, int tolerancePx) {
        List<Candidate> out = new ArrayList<>();
        Candidate last = null;
        for (Candidate c : list) {
            if (last == null || Math.abs(c.y() - last.y()) > tolerancePx) {
                out.add(c);
                last = c;
            }
        }
        return out;
    }
}
