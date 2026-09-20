/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Partitions: silent/one/repeated sources; 64/256/512 blocks and just beyond the boundaries;
 * quiet/loud, duplicate/out-of-order ticks; sustained/interrupted/relocated noise; approach;
 * stable bearing and vertical habitat; source competition/expiry; invalid inputs.
 */
final class HearingTest {
    private static final Vec EARS = Vec.ZERO;

    private static Hearing one(double distance, double loudness) {
        return Hearing.seeded(42).heard(new Hearing.Sound(new Vec(distance, 0, 0), loudness, 0, "miner"), EARS);
    }

    @Test
    void rangeIncludes512AndExcludesAnythingBeyondIt() {
        assertTrue(one(512, 6).estimate(EARS, 0).isPresent());
        assertTrue(one(512.001, 20).estimate(EARS, 0).isEmpty());
        assertTrue(Hearing.SILENT.estimate(EARS, 0).isEmpty());
    }

    @Test
    void aSingleBlockAt512GivesOnlyABroadDirection() {
        var far = one(512, 6).estimate(EARS, 0).orElseThrow();
        assertTrue(far.error() >= 200 && far.error() <= 384, "long-range bearing remains very vague");
        assertEquals(512, far.bearing().x(), 1e-9, "error never points behind the listener");
        assertEquals(0, far.bearing().y(), 1e-9, "uncertainty must not send a deep hunter above its habitat");
        assertTrue(far.loud());
    }

    @Test
    void middleDistanceIsUsefulAndWithin64TheSoundIsExact() {
        var middle = one(256, 6).estimate(EARS, 0).orElseThrow();
        assertTrue(middle.error() >= 20 && middle.error() <= 64, "general area at 256 blocks");
        for (double range : new double[] {0, 20, 63.99, 64}) {
            var exact = one(range, 1).estimate(EARS, 0).orElseThrow();
            assertEquals(0, exact.error());
            assertEquals(new Vec(range, 0, 0), exact.bearing());
        }
        assertTrue(one(64.1, 6).estimate(EARS, 0).orElseThrow().error() > 0);
    }

    @Test
    void repeatedMiningGraduallySharpensButNeverPinpointsFromFarAway() {
        Hearing h = one(512, 6);
        double initial = h.estimate(EARS, 0).orElseThrow().error();
        double previous = initial;
        for (int tick = 20; tick <= 1000; tick += 20) {
            h = h.heard(new Hearing.Sound(new Vec(512, 0, 0), 6, tick, "miner"), EARS);
            double error = h.estimate(EARS, tick).orElseThrow().error();
            assertTrue(error < previous && error > previous * .95, "each block only slightly improves the bearing");
            previous = error;
        }
        assertTrue(previous <= initial * .6 && previous >= 100, "sustained noise helps but range still limits precision");
        assertTrue(h.estimate(new Vec(256, 0, 0), 1000).orElseThrow().error() < previous,
                "closing the distance improves the estimate further");
        assertEquals(0, h.estimate(new Vec(448, 0, 0), 1000).orElseThrow().error());
        assertEquals(1, h.remembered(), "events do not accumulate unbounded history");
    }

    @Test
    void silenceErasesConfidenceBeforeTheSoundIsEventuallyForgotten() {
        Hearing h = one(256, 6);
        for (int tick = 20; tick <= 1000; tick += 20)
            h = h.heard(new Hearing.Sound(new Vec(256, 0, 0), 6, tick, "miner"), EARS);
        double focused = h.estimate(EARS, 1000).orElseThrow().error();
        double faded = h.estimate(EARS, 2400).orElseThrow().error();
        assertTrue(faded > focused);
        assertEquals(one(256, 6).estimate(EARS, 0).orElseThrow().error(), faded, 1e-9);
        assertTrue(h.estimate(EARS, 1000 + Hearing.DECAY).isPresent());
        assertTrue(h.estimate(EARS, 1001 + Hearing.DECAY).isEmpty());
        assertEquals(0, h.forgotten(1001 + Hearing.DECAY).remembered());
    }

    @Test
    void duplicateTicksAndAnotherSourceDoNotInstantlyBuildConfidence() {
        Hearing h = one(256, 6);
        double first = h.estimateFrom("miner", EARS, 0).orElseThrow().error();
        for (int i = 0; i < 100; i++)
            h = h.heard(new Hearing.Sound(new Vec(256, 0, 0), 6, 0, "miner"), EARS);
        assertEquals(first, h.estimateFrom("miner", EARS, 0).orElseThrow().error());
        for (int tick = 20; tick <= 400; tick += 20)
            h = h.heard(new Hearing.Sound(new Vec(256, 0, 0), 6, tick, "other"), EARS);
        assertEquals(first, h.estimateFrom("miner", EARS, 400).orElseThrow().error());
        assertTrue(h.estimateFrom("other", EARS, 400).orElseThrow().error() < first);
        Hearing current = h;
        assertSame(current, current.heard(new Hearing.Sound(Vec.ZERO, 6, 1, "other"), EARS));
    }

    @Test
    void movingFarFromTheOldNoiseLosesTheAccumulatedBearing() {
        Hearing h = one(256, 6);
        for (int tick = 20; tick <= 400; tick += 20)
            h = h.heard(new Hearing.Sound(new Vec(256, 0, 0), 6, tick, "miner"), EARS);
        Vec relocated = new Vec(128, 0, 128);
        h = h.heard(new Hearing.Sound(relocated, 6, 420, "miner"), EARS);
        Hearing fresh = Hearing.seeded(42).heard(new Hearing.Sound(relocated, 6, 420, "miner"), EARS);
        assertEquals(fresh.estimate(EARS, 420), h.estimate(EARS, 420));
    }

    @Test
    void louderSoundsHelpWithoutTurningAnExplosionIntoLongRangeOmniscience() {
        double quiet = one(512, 1).estimate(EARS, 0).orElseThrow().error();
        double block = one(512, 6).estimate(EARS, 0).orElseThrow().error();
        double blast = one(512, 20).estimate(EARS, 0).orElseThrow().error();
        assertTrue(quiet > block && block > blast && blast > 150);
        Hearing two = one(200, 1).heard(new Hearing.Sound(new Vec(20, 0, 0), 1, 0, "near"), EARS);
        assertEquals(20, two.estimate(EARS, 0).orElseThrow().distance());
        two = two.heard(new Hearing.Sound(new Vec(200, 0, 0), 20, 1, "miner"), EARS);
        assertEquals(200, two.estimate(EARS, 1).orElseThrow().distance());
        assertFalse(two.estimate(EARS, 200).orElseThrow().loud());
    }

    @Test
    void invalidSoundsAreRefusedAndUnheardSourcesHaveNoEstimate() {
        for (double loudness : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new Hearing.Sound(Vec.ZERO, loudness, 0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new Hearing.Sound(Vec.ZERO, 1, -1, "p"));
        assertTrue(one(256, 6).estimateFrom("absent", EARS, 0).isEmpty());
    }
}
