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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. At rest every leg stands. Walking: left and right of a pair
 * are half a cycle apart; adjacent pairs are the wave apart; the swing is
 * bounded by the stride and the lift by the lift, never below zero; a
 * full cycle of travel brings a leg back. Half the reference speed gives
 * half the stride. Going backward reverses the swing. Bad arguments refused.
 */
final class LegGaitTest {
    private static final LegGait G = LegGait.FACE_STEALER;

    @Test
    void atRestEveryLegStands() {
        for (LegGait.LegPose p : G.poses(13, 7.3, 0.0)) {
            assertEquals(0.0, p.swingDeg(), 1e-12);
            assertEquals(0.0, p.liftDeg(), 1e-12);
        }
    }

    @Test
    void leftAndRightAreHalfACycleApartAndPairsAWaveApart() {
        LegGait.LegPose[] p = G.poses(3, 0.37, 1.0);
        double phase = 2 * Math.PI * 0.37 / G.cycleBlocks();
        assertEquals(G.strideDeg() * Math.sin(phase), p[0].swingDeg(), 1e-9);
        assertEquals(G.strideDeg() * Math.sin(phase + Math.PI), p[1].swingDeg(), 1e-9);
        assertEquals(G.strideDeg() * Math.sin(phase + G.waveRad()), p[2].swingDeg(), 1e-9);
        assertEquals(G.liftDeg() * Math.max(0, Math.cos(phase + 2 * G.waveRad())), p[4].liftDeg(), 1e-9);
    }

    @Test
    void swingAndLiftStayInTheirBounds() {
        for (double d = 0; d < 20; d += 0.05) {
            for (LegGait.LegPose p : G.poses(13, d, 5.0)) {
                assertTrue(Math.abs(p.swingDeg()) <= G.strideDeg() + 1e-9);
                assertTrue(p.liftDeg() >= 0 && p.liftDeg() <= G.liftDeg() + 1e-9);
            }
        }
        LegGait.LegPose[] a = G.poses(2, 1.0, 1.0);
        LegGait.LegPose[] b = G.poses(2, 1.0 + G.cycleBlocks(), 1.0);
        assertEquals(a[0].swingDeg(), b[0].swingDeg(), 1e-9, "a cycle of travel brings the leg back");
    }

    @Test
    void speedScalesTheStrideAndBackwardReversesIt() {
        assertEquals(G.poses(1, 0.4, G.speedRef()).length, 2);
        assertEquals(G.poses(1, 0.4, G.speedRef())[0].swingDeg() / 2, G.poses(1, 0.4, G.speedRef() / 2)[0].swingDeg(), 1e-9);
        assertEquals(-G.poses(1, 0.4, 1.0)[0].swingDeg(), G.poses(1, -0.4, 1.0)[0].swingDeg(), 1e-9);
    }

    @Test
    void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LegGait(-1, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LegGait(1, 1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> G.poses(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> G.poses(1, 0, -1));
    }
}
