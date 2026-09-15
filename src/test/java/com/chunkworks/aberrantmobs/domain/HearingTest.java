/*
 * Aberrant Mobs - a protocol for monsters.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.aberrantmobs.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Nothing heard: no estimate. Far (300 blocks, a step): the
 * error is at least half the distance, never past it, and the bearing
 * still lies the right way; the same guess for six hundred ticks, another
 * after. Near (20): exact. Louder is more precise. Beyond the range:
 * unheard. Old: forgotten after the decay. Two sources: the louder for
 * its distance wins, and the newer sound of a source replaces the older.
 * Loud: a block broken within a hundred ticks. Bad sounds refused.
 */
final class HearingTest {
    private static final Vec EARS = new Vec(0, 0, 0);

    @Test
    void farOffItIsOnlyABearingThatDriftsNearItIsExact() {
        Hearing h = Hearing.seeded(42).heard(new Hearing.Sound(new Vec(300, 0, 0), 1.0, 10, "p"), EARS);
        Hearing.Estimate e = h.estimate(EARS, 20).orElseThrow();
        assertTrue(e.error() >= 150.0 && e.error() <= 300.0, "vague: " + e.error());
        assertTrue(e.bearing().x() > 0, "but the right way");
        assertTrue(Math.abs(e.bearing().x() - 300) < 1e-9, "moved sideways, not along: " + e.bearing());
        assertEquals(10, e.age());
        assertEquals(300.0, e.distance(), 1e-9);
        assertFalse(e.loud());
        Hearing.Estimate later = h.estimate(EARS, 500).orElseThrow();
        assertTrue(later.bearing().near(e.bearing(), 1e-9), "the same guess within the roll");
        Hearing.Estimate rolled = h.estimate(EARS, 700).orElseThrow();
        assertFalse(rolled.bearing().near(e.bearing(), 1e-6), "another after it");
        assertEquals(e.error(), rolled.error(), 1e-9, "as far off");
        Hearing near = Hearing.seeded(42).heard(new Hearing.Sound(new Vec(20, 0, 0), 1.0, 0, "p"), EARS);
        Hearing.Estimate n = near.estimate(EARS, 0).orElseThrow();
        assertEquals(0.0, n.error());
        assertTrue(n.bearing().near(new Vec(20, 0, 0), 1e-12), "exact");
        assertTrue(Hearing.SILENT.estimate(EARS, 0).isEmpty());
        assertTrue(Hearing.seeded(1).heard(new Hearing.Sound(new Vec(400, 0, 0), 20, 0, "p"), EARS).estimate(EARS, 0).isEmpty(), "beyond the range, unheard");
    }

    @Test
    void louderIsMorePreciseAndTheLouderForItsDistanceWins() {
        Vec at = new Vec(100, 0, 0);
        double step = Hearing.seeded(1).heard(new Hearing.Sound(at, 1, 0, "p"), EARS).estimate(EARS, 0).orElseThrow().error();
        double block = Hearing.seeded(1).heard(new Hearing.Sound(at, 6, 0, "p"), EARS).estimate(EARS, 0).orElseThrow().error();
        assertTrue(block < step, block + " < " + step);
        assertEquals(50.0, step, 1e-9, "half a block per block for a step");
        Hearing two = Hearing.seeded(1).heard(new Hearing.Sound(new Vec(200, 0, 0), 1, 0, "far"), EARS).heard(new Hearing.Sound(new Vec(60, 0, 0), 1, 0, "near"), EARS);
        assertEquals(2, two.remembered());
        assertEquals(60.0, two.estimate(EARS, 0).orElseThrow().distance(), 1e-9, "the nearer step");
        Hearing loudFar = two.heard(new Hearing.Sound(new Vec(200, 0, 0), 20, 5, "far"), EARS);
        assertEquals(200.0, loudFar.estimate(EARS, 5).orElseThrow().distance(), 1e-9, "a blast far off beats a step near");
        assertTrue(loudFar.estimate(EARS, 5).orElseThrow().loud());
        assertFalse(loudFar.estimate(EARS, 200).orElseThrow().loud(), "not loud any more");
        assertEquals(2, loudFar.remembered(), "the newer sound of a source replaced the older");
    }

    @Test
    void oldSoundsAreForgottenAndBadOnesRefused() {
        Hearing h = Hearing.seeded(1).heard(new Hearing.Sound(new Vec(50, 0, 0), 1, 0, "p"), EARS);
        assertTrue(h.estimate(EARS, Hearing.DECAY).isPresent());
        assertTrue(h.estimate(EARS, Hearing.DECAY + 1).isEmpty(), "too old to act on");
        assertEquals(0, h.forgotten(Hearing.DECAY + 1).remembered());
        assertTrue(h.forgotten(100) == h, "nothing to forget: the same ears");
        assertThrows(IllegalArgumentException.class, () -> new Hearing.Sound(Vec.ZERO, 0, 0, "p"));
        assertThrows(IllegalArgumentException.class, () -> new Hearing.Sound(Vec.ZERO, 1, -1, "p"));
        Optional<Hearing.Estimate> none = Hearing.seeded(1).estimate(EARS, 0);
        assertTrue(none.isEmpty());
    }

    @Test
    void oneSourceCanBeFollowedOnItsOwn() {
        // Two players: a near one stepping, a far one mining. The ears as a whole go by the loudest for its distance;
        // asked after the far one alone, they place it as its own sound says, erred for its range.
        Hearing h = Hearing.seeded(3)
                .heard(new Hearing.Sound(new Vec(10, 0, 0), 1, 40, "near"), EARS)
                .heard(new Hearing.Sound(new Vec(100, 0, 0), 6, 30, "far"), EARS);
        assertEquals(10.0, h.estimate(EARS, 40).orElseThrow().distance(), 1e-9, "the whole: the near step");
        Hearing.Estimate far = h.estimateFrom("far", EARS, 40).orElseThrow();
        assertEquals(100.0, far.distance(), 1e-9);
        assertEquals(10, far.age());
        assertEquals(100 * Hearing.ERROR_PER_BLOCK / 6, far.error(), 1e-9, "erred for its range and loudness");
        assertEquals(far.error(), far.bearing().minus(new Vec(100, 0, 0)).length(), 1e-9, "the bearing off by that much");
        assertTrue(far.loud(), "a block broken ten ticks ago is loud");
        assertEquals(0.0, h.estimateFrom("near", EARS, 40).orElseThrow().error(), 1e-9, "exact within twenty-four");
        assertTrue(h.estimateFrom("nobody", EARS, 40).isEmpty(), "never heard");
        assertTrue(h.estimateFrom("far", EARS, 30 + Hearing.DECAY + 1).isEmpty(), "too old");
        assertEquals(h.estimate(EARS, 40).orElseThrow().bearing(), h.estimateFrom("near", EARS, 40).orElseThrow().bearing(), "the same reckoning either way");
    }
}
