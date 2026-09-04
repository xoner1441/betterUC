package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ClipSettingsTest {
    @ParameterizedTest
    @CsvSource({"720,30,1280,8000000", "720,60,1280,12000000",
            "1080,30,1920,16000000", "1080,60,1920,25000000"})
    void presetsSetDimensionsFrameRateAndBitrate(int height, int fps, int width, int bitrate) {
        var settings = ClipSettings.forViewport(3840, 2160, 60, height, fps);
        assertEquals(new ClipSettings(width, height, fps, 60, bitrate), settings);
        assertTrue(settings.maxBufferBytes() <= ClipSettings.MAX_VIDEO_BUFFER_BYTES);
    }

    @Test void smallerViewportIsNeverUpscaledAndDimensionsStayEven() {
        for (int height : new int[]{720, 1080}) {
            var settings = ClipSettings.forViewport(961, 541, 15, height, 30);
            assertEquals(960, settings.width());
            assertEquals(540, settings.height());
            assertEquals(30, settings.fps());
        }
    }

    @Test void wideAndTallViewportsFitInsideTheSelectedBounds() {
        var wide = ClipSettings.forViewport(3440, 1440, 30, 720, 60);
        assertEquals(1280, wide.width());
        assertEquals(534, wide.height());
        var tall = ClipSettings.forViewport(1200, 1920, 30, 720, 30);
        assertEquals(450, tall.width());
        assertEquals(720, tall.height());
    }

    @Test void unsupportedValuesFallBackToTheExistingDefaults() {
        var defaults = ClipSettings.forViewport(1920, 1080, 30);
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 144, 2160, Integer.MAX_VALUE}) {
            assertEquals(defaults, ClipSettings.forViewport(1920, 1080, 30, invalid, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> ClipSettings.forViewport(1, 1080, 30, 720, 30));
        assertThrows(IllegalArgumentException.class, () -> ClipSettings.forViewport(1920, 0, 30, 720, 30));
    }

    @Test void lowerPresetsReduceTheCompressedBufferBudget() {
        long previous = 0;
        for (int[] preset : new int[][]{{720, 30}, {720, 60}, {1080, 30}, {1080, 60}}) {
            long budget = ClipSettings.forViewport(1920, 1080, 30, preset[0], preset[1]).maxBufferBytes(8L * 1024 * 1024 * 1024);
            assertTrue(budget > previous);
            previous = budget;
        }
    }

    @Test void arbitraryWholeSecondsAndHigherPresetsAreSupported() {
        for (int seconds : new int[]{5, 15, 30, 37, 60, 90, 120, 144, 180, 239, 300}) {
            assertEquals(seconds, ClipSettings.forViewport(1920, 1080, seconds).seconds());
            assertEquals(seconds, ClipSettings.parseSeconds(" " + seconds + " "));
        }
        int value = 15;
        for (int expected : new int[]{30, 60, 90, 120, 180, 300, 15}) {
            value = ClipSettings.nextDurationPreset(value);
            assertEquals(expected, value);
        }
        assertEquals(60, ClipSettings.nextDurationPreset(37));
    }

    @Test void invalidUserInputIsRejectedAndBadConfigIsBounded() {
        for (String input : new String[]{null, "", "4", "0", "-5", "301", "9999999999", "1:30", "30.5", "abc"}) {
            assertEquals(-1, ClipSettings.parseSeconds(input));
        }
        assertEquals(30, ClipSettings.normalizeSeconds(Integer.MIN_VALUE));
        assertEquals(300, ClipSettings.normalizeSeconds(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new ClipSettings(320, 180, 60, 301, 2_000_000));
    }

    @Test void longBufferBudgetIsCappedByBothHeapShareAndOneGiB() {
        var settings = ClipSettings.forViewport(1920, 1080, 300);
        long gib = 1024L * 1024 * 1024;
        assertEquals(gib / 2, settings.maxBufferBytes(4 * gib));
        assertEquals(gib, settings.maxBufferBytes(8 * gib));
        assertEquals(gib, settings.maxBufferBytes(32 * gib));
        assertTrue(settings.maxBufferBytes(8 * gib) > settings.bitrate() / 8L * 300,
                "Five minutes at the nominal 1080p60 bitrate must fit with sufficient heap");
    }
}
