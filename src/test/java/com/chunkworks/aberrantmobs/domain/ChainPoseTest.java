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
 * Partitions. A straight trail, no wave: segments exactly spaced along
 * it, all facing the travel direction, up up. A turning trail: still
 * exactly spaced (consecutive distances equal the spacing), facing the
 * segment before, tangents turning with the path. A wave on a straight
 * trail: the offset is along the body's left, symmetric about the path,
 * the head's own offset the wave's at 0. A trail up a wall: each segment's
 * up is its own surface's. A short trail extends straight behind. Bad
 * arcs refused.
 */
final class ChainPoseTest {
    private static final Undulation NONE = new Undulation(0.0, 0.0, 5.5, 0.35, 0.02, 0.0);
    private static final double[] ARCS = {0.0, 0.0, 0.6875, 1.375, 2.0625, 2.75};

    @Test
    void aStraightTrailSpacesTheSegmentsExactly() {
        Trail t = Trail.seeded(new Vec(20, 3, 0), Vec.X, Vec.Y, 10.0, 64);
        ChainPose c = ChainPose.of(t, ARCS, NONE, NONE.rest());
        assertEquals(6, c.size());
        assertTrue(c.position(0).near(new Vec(20, 3, 0), 1e-12));
        assertTrue(c.position(1).near(new Vec(20, 3, 0), 1e-12), "the first segment shares the head's pivot");
        assertTrue(c.position(2).near(new Vec(20 - 0.6875, 3, 0), 1e-12));
        assertTrue(c.position(5).near(new Vec(20 - 2.75, 3, 0), 1e-12));
        for (int k = 0; k < 6; k++) {
            assertTrue(c.orientation(k).rotate(Vec.Z).near(Vec.X, 1e-9), "faces along the travel");
            assertTrue(c.orientation(k).rotate(Vec.Y).near(Vec.Y, 1e-9));
        }
    }

    @Test
    void aTurningTrailKeepsTheSpacingAndTurnsTheSegments() {
        Trail t = Trail.seeded(new Vec(5, 0, 0), Vec.Z, Vec.Y, 1.0, 256);
        for (int i = 1; i <= 90; i++) {
            double a = Math.toRadians(i);
            t.push(new Vec(5 * Math.cos(a), 0, 5 * Math.sin(a)), Vec.Y);
        }
        ChainPose c = ChainPose.of(t, ARCS, NONE, NONE.rest());
        for (int k = 2; k < 6; k++) {
            double d = c.position(k).minus(c.position(k - 1)).length();
            assertEquals(0.6875, d, 0.02, "spacing along a bend, segment " + k);
            Vec facing = c.orientation(k).rotate(Vec.Z);
            assertTrue(facing.near(c.position(k - 1).minus(c.position(k)).normalized(), 1e-9), "faces the segment before");
        }
        Vec headFacing = c.orientation(0).rotate(Vec.Z);
        Vec tailFacing = c.orientation(5).rotate(Vec.Z);
        assertTrue(headFacing.dot(tailFacing) < 0.95, "the body bends round the path");
    }

    @Test
    void theWaveSetsSegmentsAsideAlongTheBodysLeft() {
        Trail t = Trail.seeded(new Vec(20, 3, 0), Vec.X, Vec.Y, 10.0, 64);
        Undulation u = Undulation.FACE_STEALER;
        Undulation.Wave w = new Undulation.Wave(1.2, 0.3);
        ChainPose c = ChainPose.of(t, ARCS, u, w);
        // Left of a body travelling +X with up +Y is up x forward = (0, 1, 0) x (1, 0, 0) = (0, 0, -1).
        for (int k = 0; k < 6; k++) {
            double expectedZ = -u.lateralAt(w, ARCS[k]);
            assertEquals(expectedZ, c.position(k).z(), 1e-9, "segment " + k + " offset along the left");
            assertEquals(3 + u.verticalAt(w, ARCS[k]), c.position(k).y(), 1e-9);
        }
        assertEquals(-u.lateralAt(w, 0.0), c.position(0).z(), 1e-9, "the head carries the wave's own offset");
    }

    @Test
    void eachSegmentStandsOnItsOwnSurface() {
        // The head went 2 blocks along the floor then 2 up a wall (up becomes -Z).
        Trail t = Trail.seeded(new Vec(0, 0, 0), Vec.X, Vec.Y, 4.0, 64);
        for (int i = 1; i <= 20; i++) {
            t.push(new Vec(i * 0.1, 0, 0), Vec.Y);
        }
        for (int i = 1; i <= 20; i++) {
            t.push(new Vec(2.0, i * 0.1, 0), new Vec(0, 0, -1));
        }
        ChainPose c = ChainPose.of(t, new double[] {0.0, 1.0, 3.0, 3.5}, NONE, NONE.rest());
        assertTrue(c.orientation(0).rotate(Vec.Y).near(new Vec(0, 0, -1), 1e-6), "the head is on the wall");
        // Three back it stands on the floor: its up is the floor's, tilted only to stay square to its
        // facing (which points up the wall's foot at the segment ahead), never toward the wall's up.
        Vec up2 = c.orientation(2).rotate(Vec.Y);
        assertEquals(0.0, up2.z(), 1e-6, "no part of the wall's up");
        assertTrue(up2.y() > 0.5, "the floor's up, tilted: " + up2);
        assertTrue(c.orientation(3).rotate(Vec.Y).near(Vec.Y, 1e-6), "further back, facing along the floor, exactly the floor's up");
        assertTrue(t.at(3.0).up().near(Vec.Y, 1e-9), "the trail itself says floor there");
    }

    @Test
    void aShortTrailExtendsStraightBehind() {
        Trail t = Trail.seeded(new Vec(0, 0, 0), Vec.X, Vec.Y, 1.0, 16);
        ChainPose c = ChainPose.of(t, new double[] {0.0, 4.0}, NONE, NONE.rest());
        assertTrue(c.position(1).near(new Vec(-4, 0, 0), 1e-12));
    }

    @Test
    void arcsMustStartAtTheHeadAndRunTailward() {
        Trail t = Trail.seeded(Vec.ZERO, Vec.X, Vec.Y, 1.0, 16);
        assertThrows(IllegalArgumentException.class, () -> ChainPose.of(t, new double[] {1.0, 2.0}, NONE, NONE.rest()));
        assertThrows(IllegalArgumentException.class, () -> ChainPose.of(t, new double[] {0.0, 2.0, 1.0}, NONE, NONE.rest()));
    }

    @Test
    void aCornerSampleWhoseUpLiesAlongTheForwardStillPoses() {
        // A head that walked +X on a floor, then straight up a wall: the corner sample's up (the floor's) lies along the climb.
        Trail t = Trail.seeded(new Vec(0, 1.5, 0), Vec.X, Vec.Y, 4.0, 64);
        t.push(new Vec(0, 2.5, 0), new Vec(-1, 0, 0));
        t.push(new Vec(0, 3.5, 0), new Vec(-1, 0, 0));
        Undulation none = new Undulation(0, 0, 5.5, 0.35, 0.02, 0);
        ChainPose c = ChainPose.of(t, new double[] {0.0, 1.0, 2.0, 3.0}, none, none.rest());
        assertEquals(4, c.size());
        assertTrue(c.orientation(1).rotate(Vec.Z).near(Vec.Y, 1e-6), "the segment on the wall faces up");
        assertTrue(c.orientation(2).rotate(Vec.Y).length() > 0.99, "the corner segment has an up");
    }
}
