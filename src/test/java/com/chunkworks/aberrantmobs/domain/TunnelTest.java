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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. A section along an axis is three by three and leaves the
 * floor the head rides on; a diagonal takes more cells; the order is
 * fixed. Rock lists only the rock. Diggable: all air or rock yes; a hard
 * cell no; a fluid cell no; rock with fluid behind it no. A cell's
 * neighbours, centre and containing.
 */
final class TunnelTest {
    @Test
    void aSectionAlongAnAxisIsThreeByThreeOverTheFloor() {
        // The head at 1.46 over the floor's top (y = 0), heading +X, cut 2 ahead with radius 1.5.
        List<Cell> s = Tunnel.section(new Vec(3.0, 1.46, 0.5), Vec.X, 2.0, 1.5);
        assertFalse(s.isEmpty());
        for (Cell c : s) {
            assertTrue(c.y() >= 0 && c.y() <= 2, "three high, the floor kept: " + c);
            assertTrue(c.z() >= -1 && c.z() <= 1, "three wide: " + c);
            assertTrue(c.x() >= 1 && c.x() <= 6, "along the run: " + c);
        }
        assertTrue(s.contains(new Cell(4, 0, 0)) && s.contains(new Cell(4, 1, -1)) && s.contains(new Cell(4, 2, 1)), "the full three by three at x = 4");
        assertFalse(s.contains(new Cell(4, -1, 0)), "never the floor");
        assertFalse(s.contains(new Cell(4, 3, 0)), "nor a fourth row");
        List<Cell> diagonal = Tunnel.section(new Vec(3.0, 1.46, 0.5), new Vec(1, 0, 1).normalized(), 2.0, 1.5);
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Cell c : diagonal) {
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        assertTrue(maxZ - minZ >= 3, "a diagonal run spreads across z: " + minZ + ".." + maxZ);
        assertEquals(s, Tunnel.section(new Vec(3.0, 1.46, 0.5), Vec.X, 2.0, 1.5), "a fixed order");
        assertThrows(IllegalArgumentException.class, () -> Tunnel.section(Vec.ZERO, Vec.X, -1, 1));
    }

    @Test
    void rockIsListedAndDiggabilityJudged() {
        Cells floor = Cells.floor(-1);
        List<Cell> s = Tunnel.section(new Vec(3.0, 1.46, 0.5), Vec.X, 2.0, 1.5);
        assertTrue(Tunnel.rock(floor, s).isEmpty(), "air over the floor: nothing to cut");
        assertTrue(Tunnel.diggable(floor, s));
        Cells wall = (x, y, z) -> y <= -1 || x >= 4 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        List<Cell> rock = Tunnel.rock(wall, s);
        assertFalse(rock.isEmpty());
        for (Cell c : rock) {
            assertTrue(c.x() >= 4 && wall.at(c) == Cells.Kind.ROCK);
        }
        assertTrue(Tunnel.diggable(wall, s));
        Cells hard = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 4 ? (y == 1 && z == 0 ? Cells.Kind.HARD : Cells.Kind.ROCK) : Cells.Kind.AIR;
        assertFalse(Tunnel.diggable(hard, s), "a hard cell stops the dig");
        Cells water = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 4 ? (x >= 6 ? Cells.Kind.FLUID : Cells.Kind.ROCK) : Cells.Kind.AIR;
        assertFalse(Tunnel.diggable(water, s), "rock with water behind it is never breached");
        Cells pool = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 4 ? Cells.Kind.FLUID : Cells.Kind.AIR;
        assertFalse(Tunnel.diggable(pool, s), "nor is a fluid cell passed");
    }

    @Test
    void aCellKnowsItsPlace() {
        Cell c = Cell.containing(new Vec(-0.5, 2.9, 7.0));
        assertEquals(new Cell(-1, 2, 7), c);
        assertTrue(c.centre().near(new Vec(-0.5, 2.5, 7.5), 1e-12));
        assertEquals(6, c.neighbours().length);
        assertEquals(new Cell(0, 2, 7), c.neighbours()[0]);
        assertEquals(3, c.manhattan(new Cell(0, 3, 6)));
        assertEquals(0.0, Tunnel.distanceToSegment(new Vec(1, 0, 0), Vec.ZERO, new Vec(2, 0, 0)), 1e-12);
        assertEquals(1.0, Tunnel.distanceToSegment(new Vec(3, 0, 0), Vec.ZERO, new Vec(2, 0, 0)), 1e-12, "past the end");
        assertEquals(Math.sqrt(2.0), Tunnel.distanceToSegment(new Vec(1, 1, 0), Vec.ZERO, Vec.ZERO), 1e-12, "a point segment");
    }
}
