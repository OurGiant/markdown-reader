package com.ourgiant.markdown.model;

/** A candidate page-break point: a Y offset in rendered document coordinates, tagged with the
 *  HTML element type it came from, for {@code core.PaginationPlanner}'s keep-with-next rules. */
public record Candidate(int offset, int y, TagType type, boolean isHeaderRow) {}
