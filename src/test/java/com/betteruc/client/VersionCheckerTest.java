package com.betteruc.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCheckerTest {

    @Test
    void mc26ReleaseRangeAcceptsSupportedMinecraftVersions() {
        var requirement = JsonParser.parseString("\">=26.1.2 <26.3\"");

        assertTrue(VersionChecker.matchesMinecraftDependency(requirement, "26.1.2"));
        assertTrue(VersionChecker.matchesMinecraftDependency(requirement, "26.2"));
        assertFalse(VersionChecker.matchesMinecraftDependency(requirement, "26.1.1"));
        assertFalse(VersionChecker.matchesMinecraftDependency(requirement, "26.3"));
        assertFalse(VersionChecker.matchesMinecraftDependency(requirement, "1.21.11"));
    }

    @Test
    void minecraftDependencyAlternativesAndExactVersionsAreSupported() {
        var alternatives = JsonParser.parseString("[\"26.1.2\", \"26.2\"]");

        assertTrue(VersionChecker.matchesMinecraftDependency(alternatives, "26.2"));
        assertFalse(VersionChecker.matchesMinecraftDependency(alternatives, "26.2.1"));
        assertFalse(VersionChecker.matchesMinecraftDependency(JsonParser.parseString("\"1.21.11\""), "26.2"));
    }

    @Test
    void numericVersionComparisonHandlesOldAndCurrentModVersions() {
        assertTrue(VersionChecker.compareVersionNumbers("1.4.1", "1.3.7") > 0);
        assertTrue(VersionChecker.compareVersionNumbers("26.2", "26.1.2") > 0);
        assertTrue(VersionChecker.compareVersionNumbers("26.2", "26.2.0") == 0);
    }
}
