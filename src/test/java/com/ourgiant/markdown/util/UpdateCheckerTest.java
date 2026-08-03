package com.ourgiant.markdown.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void detectsNewerPatchVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.4", "1.0.3"));
    }

    @Test
    void detectsNewerMinorVersion() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.9"));
    }

    @Test
    void detectsNewerMajorVersion() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.9"));
    }

    @Test
    void sameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.3", "1.0.3"));
    }

    @Test
    void olderVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.2", "1.0.3"));
    }

    @Test
    void differingSegmentCountsCompareByImplicitZero() {
        assertTrue(UpdateChecker.isNewerVersion("1.1", "1.0.9"));
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.1"));
    }

    @Test
    void unparseableVersionFailsSafeToNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("dev", "1.0.3"));
        assertFalse(UpdateChecker.isNewerVersion("1.0.3", "dev"));
    }
}
