package com.ourgiant.markdown.core;

import com.ourgiant.markdown.model.Candidate;
import com.ourgiant.markdown.model.TagType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationPlannerTest {

    @Test
    void upperBoundYFindsFirstIndexPastGivenY() {
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 100, TagType.P, false),
                new Candidate(2, 200, TagType.P, false)
        );

        assertEquals(2, PaginationPlanner.upperBoundY(candidates, 150));
        assertEquals(0, PaginationPlanner.upperBoundY(candidates, -1));
        assertEquals(3, PaginationPlanner.upperBoundY(candidates, 200));
    }

    @Test
    void dedupeByYDropsCandidatesWithinTolerance() {
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 1, TagType.P, false),
                new Candidate(2, 50, TagType.P, false)
        );

        List<Candidate> deduped = PaginationPlanner.dedupeByY(candidates, 2);

        assertEquals(2, deduped.size());
        assertEquals(0, deduped.get(0).y());
        assertEquals(50, deduped.get(1).y());
    }

    @Test
    void computeNextPageStartAdvancesToLastCandidateBeforePageLimit() {
        PaginationPlanner planner = new PaginationPlanner(0, 0, 0, 0);
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 100, TagType.P, false),
                new Candidate(2, 900, TagType.P, false)
        );

        int nextStart = planner.computeNextPageStart(candidates, 1000, 500, 0);

        assertEquals(100, nextStart);
    }

    @Test
    void computeNextPageStartReturnsNegativeWhenContentFits() {
        PaginationPlanner planner = new PaginationPlanner(0, 0, 0, 0);
        List<Candidate> candidates = List.of(new Candidate(0, 0, TagType.OTHER, false));

        int nextStart = planner.computeNextPageStart(candidates, 400, 1000, 0);

        assertTrue(nextStart < 0);
    }

    @Test
    void keepsHeadingWithFollowingTableWhenNearPageBottom() {
        // heading at y=480, table right after at y=500; page limit 500 -> table would be orphaned
        // from its heading with only 20px left, so the break should move back to the heading.
        PaginationPlanner planner = new PaginationPlanner(0, 0, 140, 90);
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 480, TagType.H2, false),
                new Candidate(2, 500, TagType.TABLE, false),
                new Candidate(3, 900, TagType.P, false)
        );

        int nextStart = planner.computeNextPageStart(candidates, 1000, 500, 0);

        assertEquals(480, nextStart);
    }

    @Test
    void keepsHeaderRowWithFollowingBodyRowWhenNearPageBottom() {
        // header row (TH) at y=470, first body row at y=490; page limit 500 leaves only 10px,
        // less than keepHeaderRowWithBodyPx (90), so the break moves back to the header row.
        PaginationPlanner planner = new PaginationPlanner(0, 0, 140, 90);
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 470, TagType.TR, true),
                new Candidate(2, 490, TagType.TR, false),
                new Candidate(3, 900, TagType.P, false)
        );

        int nextStart = planner.computeNextPageStart(candidates, 1000, 500, 0);

        assertEquals(470, nextStart);
    }

    @Test
    void makesForwardProgressEvenWhenNoGoodBreakpointExists() {
        PaginationPlanner planner = new PaginationPlanner(0, 0, 140, 90);
        List<Candidate> candidates = List.of(
                new Candidate(0, 0, TagType.OTHER, false),
                new Candidate(1, 900, TagType.P, false)
        );

        int nextStart = planner.computeNextPageStart(candidates, 1000, 500, 0);

        assertTrue(nextStart > 0);
    }
}
