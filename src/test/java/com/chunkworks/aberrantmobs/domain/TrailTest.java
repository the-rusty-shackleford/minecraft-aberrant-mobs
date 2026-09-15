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
 * Partitions. Seeding: a straight body, its samples where the seed says,
 * a bad length or capacity refused. Sampling: at 0 the head; within the
 * path a point on the segment with its travel direction; beyond the
 * oldest, straight on; a negative distance refused. Pushing: a step adds
 * and the arc grows; a step under MIN_STEP moves the newest instead;
 * pushes round a quarter circle put the samples on the circle and turn
 * the travel direction with it; the up follows the pushes; a full ring
 * drops the oldest and keeps the newest.
 */
final class TrailTest {
    private static final Vec HEAD = new Vec(10, 5, 10);

    @Test
    void aSeedIsAStraightBody() {
        Trail t = Trail.seeded(HEAD, Vec.X, Vec.Y, 8.0, 16);
        assertEquals(2, t.size());
        assertEquals(8.0, t.length(), 1e-12);
        assertTrue(t.at(0).pos().near(HEAD, 1e-12));
        assertTrue(t.at(3).pos().near(new Vec(7, 5, 10), 1e-12));
        assertTrue(t.at(3).forward().near(Vec.X, 1e-12));
        assertTrue(t.at(3).up().near(Vec.Y, 1e-12));
        assertTrue(t.at(20).pos().near(new Vec(-10, 5, 10), 1e-12), "straight on past the oldest");
        assertThrows(IllegalArgumentException.class, () -> Trail.seeded(HEAD, Vec.X, Vec.Y, 0.0, 16));
        assertThrows(IllegalArgumentException.class, () -> Trail.seeded(HEAD, Vec.X, Vec.Y, 1.0, 1));
        assertThrows(IllegalArgumentException.class, () -> t.at(-1));
    }

    @Test
    void aStepAddsAndASmallOneMovesTheNewest() {
        Trail t = Trail.seeded(HEAD, Vec.X, Vec.Y, 2.0, 16);
        t.push(HEAD.plus(new Vec(1, 0, 0)), Vec.Y);
        assertEquals(3, t.size());
        assertEquals(3.0, t.length(), 1e-12);
        t.push(HEAD.plus(new Vec(1.02, 0, 0)), Vec.Y);
        assertEquals(3, t.size(), "a step under the minimum does not add");
        assertEquals(3.02, t.length(), 1e-12);
        assertTrue(t.at(0).pos().near(HEAD.plus(new Vec(1.02, 0, 0)), 1e-12));
    }

    @Test
    void pushesRoundACircleLieOnItAndTurnTheTravelDirection() {
        Trail t = Trail.seeded(new Vec(5, 0, 0), Vec.Z, Vec.Y, 1.0, 256);
        // A quarter circle of radius 5 about the origin, from (5, 0, 0) toward (0, 0, 5), in 90 steps.
        for (int i = 1; i <= 90; i++) {
            double a = Math.toRadians(i);
            t.push(new Vec(5 * Math.cos(a), 0, 5 * Math.sin(a)), Vec.Y);
        }
        double arc = Math.PI / 2 * 5;
        assertEquals(arc + 1.0, t.length(), 0.01);
        Trail.Sample mid = t.at(arc / 2);
        assertEquals(5.0, mid.pos().length(), 0.01, "on the circle");
        assertTrue(mid.pos().near(new Vec(5 * Math.cos(Math.PI / 4), 0, 5 * Math.sin(Math.PI / 4)), 0.05));
        // Travel at 45 degrees round is along (-sin, 0, cos) = (-0.707, 0, 0.707).
        assertTrue(mid.forward().near(new Vec(-Math.sqrt(0.5), 0, Math.sqrt(0.5)), 0.03), "travel " + mid.forward());
        assertTrue(t.at(0).forward().near(new Vec(-1, 0, 0), 0.03), "at the head, along the tangent");
    }

    @Test
    void theUpFollowsThePushes() {
        Trail t = Trail.seeded(Vec.ZERO, Vec.X, Vec.Y, 1.0, 16);
        t.push(new Vec(1, 0, 0), Vec.Y);
        t.push(new Vec(2, 0, 0), new Vec(0, 0, -1));   // onto a wall
        assertTrue(t.at(0).up().near(new Vec(0, 0, -1), 1e-12));
        assertTrue(t.at(1.5).up().near(Vec.Y, 1e-12));
        Vec between = t.at(0.5).up();
        assertEquals(1.0, between.length(), 1e-12, "interpolated ups are unit");
        assertTrue(between.y() > 0.5 && between.z() < -0.5);
    }

    @Test
    void aFullRingDropsTheOldestAndKeepsTheNewest() {
        Trail t = Trail.seeded(Vec.ZERO, Vec.X, Vec.Y, 1.0, 4);
        for (int i = 1; i <= 10; i++) {
            t.push(new Vec(i, 0, 0), Vec.Y);
        }
        assertEquals(4, t.size());
        assertEquals(3.0, t.length(), 1e-12);
        assertTrue(t.at(0).pos().near(new Vec(10, 0, 0), 1e-12));
        assertTrue(t.at(3).pos().near(new Vec(7, 0, 0), 1e-12));
        assertTrue(t.at(5).pos().near(new Vec(5, 0, 0), 1e-12), "straight on past the oldest kept");
    }
}
