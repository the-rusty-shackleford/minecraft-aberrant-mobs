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
 * neighbours, centre and containing. A tube along a way is laid into the
 * head's face, follows a bend round its outer corner where the heading's
 * section does not, stops at its reach, is the same for the way given in
 * the head's row or the floor's, and refuses bad arguments. Cuttable is
 * the rock with no fluid on a face, the hard left standing.
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

    @Test
    void aTubeAlongTheWayIsLaidIntoTheFaceAndCutsABendAsABend() {
        // The head 1.46 over the floor's top (y = 0), on the floor; its way runs two cells on along +X in the row
        // under it (the floor-side air) and then two toward -Z: an L. The tube follows the L at the head's height.
        Vec centre = new Vec(3.0, 1.46, 0.5);
        List<Cell> way = List.of(new Cell(3, 0, 0), new Cell(4, 0, 0), new Cell(5, 0, 0), new Cell(5, 0, -1), new Cell(5, 0, -2));
        List<Cell> tube = Tunnel.along(centre, Vec.Y, way, 1, 6.0, 1.5);
        assertFalse(tube.isEmpty());
        for (Cell c : tube) {
            assertTrue(c.y() >= 0 && c.y() <= 2, "laid into the head's row, three high, the floor kept: " + c);
        }
        assertTrue(tube.contains(new Cell(4, 1, 1)) && tube.contains(new Cell(4, 1, -1)), "the first leg's sides");
        assertTrue(tube.contains(new Cell(4, 1, -2)) && tube.contains(new Cell(6, 1, -2)), "the second leg's sides");
        assertTrue(tube.contains(new Cell(6, 1, 0)) && tube.contains(new Cell(6, 1, -1)) && tube.contains(new Cell(6, 1, 1)), "the bend's outer corner: " + tube);
        List<Cell> heading = Tunnel.section(centre, Vec.X, 2.0, 1.5);
        assertFalse(heading.contains(new Cell(6, 1, -1)) || heading.contains(new Cell(4, 1, -2)), "which a section along the heading leaves standing");
        assertEquals(tube.size(), new java.util.HashSet<>(tube).size(), "no cell twice");
        // Cut off two along its length: the first leg and a step, none of the far end.
        List<Cell> near = Tunnel.along(centre, Vec.Y, way, 1, 2.0, 1.5);
        assertTrue(near.contains(new Cell(4, 1, 0)));
        assertFalse(near.contains(new Cell(5, 1, -2)) || near.contains(new Cell(6, 1, -1)), "beyond the reach: " + near);
        // The same way given in the head's own row is the same tube: the projection along the normal.
        List<Cell> raised = List.of(new Cell(3, 1, 0), new Cell(4, 1, 0), new Cell(5, 1, 0), new Cell(5, 1, -1), new Cell(5, 1, -2));
        assertEquals(new java.util.HashSet<>(tube), new java.util.HashSet<>(Tunnel.along(centre, Vec.Y, raised, 1, 6.0, 1.5)));
        // On a wall whose face looks west, the head 1.46 off it: the way's cells in the wall come to the head's plane.
        List<Cell> wallTube = Tunnel.along(new Vec(3.0, 5.0, 0.5), new Vec(-1, 0, 0), List.of(new Cell(4, 6, 0), new Cell(4, 7, 0)), 0, 6.0, 1.5);
        for (Cell c : wallTube) {
            assertTrue(c.x() >= 1 && c.x() <= 4, "about the head's plane at x = 3: " + c);
        }
        assertTrue(Tunnel.along(centre, Vec.Y, way, way.size(), 6.0, 1.5).isEmpty(), "the way done: nothing");
        assertThrows(IllegalArgumentException.class, () -> Tunnel.along(centre, new Vec(0, 2, 0), way, 0, 6.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> Tunnel.along(centre, Vec.Y, way, way.size() + 1, 6.0, 1.5));
    }

    @Test
    void cuttableIsTheRockWithNoFluidOnAFace() {
        List<Cell> s = Tunnel.section(new Vec(3.0, 1.46, 0.5), Vec.X, 2.0, 1.5);
        Cells water = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 4 ? (x >= 6 ? Cells.Kind.FLUID : Cells.Kind.ROCK) : Cells.Kind.AIR;
        List<Cell> cut = Tunnel.cuttable(water, s);
        assertFalse(cut.isEmpty());
        for (Cell c : cut) {
            assertEquals(4, c.x(), "the rock by the water, at x = 5, is left; the rest is cut: " + c);
        }
        Cells hard = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 4 ? (y == 1 && z == 0 ? Cells.Kind.HARD : Cells.Kind.ROCK) : Cells.Kind.AIR;
        List<Cell> aboutHard = Tunnel.cuttable(hard, s);
        assertFalse(aboutHard.isEmpty());
        for (Cell c : aboutHard) {
            assertTrue(hard.at(c) == Cells.Kind.ROCK, "rock only, the hard cell left: " + c);
        }
        assertEquals(Tunnel.rock(Cells.floor(-1), s), Tunnel.cuttable(Cells.floor(-1), s), "over the floor, nothing either way");
    }
}
