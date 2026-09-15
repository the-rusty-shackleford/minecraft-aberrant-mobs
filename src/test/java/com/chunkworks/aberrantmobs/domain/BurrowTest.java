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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Through open air along a floor: the straight line, its
 * length the taxicab distance. A wall of rock in the way: through it when
 * digging is cheap enough, around it when it is dear and a way round
 * exists. A hard wall: always around; sealed in hard: nothing. Air with
 * no face costs more than air by one. The budget ends a hopeless search.
 * Bad arguments refused. A target deep in a hill under open sky is
 * planned within the budget, and the way is the cheapest: in through
 * the face of a hill too tall and wide to go round, over the top of a
 * low one. The rock depth is the taxicab distance to the nearest air,
 * capped. The heuristic leaves every way the cheapest a plain search of
 * every cell finds, on random rock, hunting and stalking. The table reads
 * each cell once, says what the world says, and survives growing; a key
 * packs a cell and unpacks to it across the world's range.
 */
final class BurrowTest {
    private static final Cells FLOOR = Cells.floor(-1);

    @Test
    void openAirIsTheStraightLine() {
        Optional<List<Cell>> p = Burrow.plan(FLOOR, new Cell(0, 0, 0), new Cell(6, 0, 0), Burrow.HUNT, Burrow.BUDGET);
        assertTrue(p.isPresent());
        assertEquals(7, p.get().size());
        assertEquals(new Cell(0, 0, 0), p.get().get(0));
        assertEquals(new Cell(6, 0, 0), p.get().get(6));
        for (int i = 1; i < 7; i++) {
            assertEquals(1, p.get().get(i).manhattan(p.get().get(i - 1)), "six-connected");
        }
    }

    @Test
    void aWallIsDugThroughWhenCheapAndGoneRoundWhenDear() {
        // A wall at x = 5, three high, from z = -3 to 3; open floor beyond.
        Cells wall = (x, y, z) -> y <= -1 || (x == 5 && y <= 2 && Math.abs(z) <= 3) ? Cells.Kind.ROCK : Cells.Kind.AIR;
        List<Cell> hunt = Burrow.plan(wall, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        assertTrue(hunt.contains(new Cell(5, 0, 0)), "hunting digs straight through: " + hunt);
        List<Cell> stalk = Burrow.plan(wall, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.STALK, Burrow.BUDGET).orElseThrow();
        boolean through = false;
        for (Cell c : stalk) {
            through |= wall.at(c) == Cells.Kind.ROCK;
        }
        assertFalse(through, "stalking goes round: " + stalk);
        assertTrue(stalk.size() > hunt.size());
    }

    @Test
    void hardRockIsNeverPassedAndASealedTargetIsNothing() {
        Cells hard = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : (x == 5 && y <= 2 && Math.abs(z) <= 3) ? Cells.Kind.HARD : Cells.Kind.AIR;
        List<Cell> p = Burrow.plan(hard, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        for (Cell c : p) {
            assertFalse(hard.at(c) == Cells.Kind.HARD, "round the hard wall: " + c);
        }
        // Rock beside bedrock is never cut: a way through rock keeps a cell clear of it.
        Cells seam = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : (x == 5 && y == 0 && z == 0) ? Cells.Kind.HARD : x >= 3 && x <= 7 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        List<Cell> q = Burrow.plan(seam, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        for (Cell c : q) {
            if (seam.at(c) == Cells.Kind.ROCK) {
                assertTrue(Burrow.clearAbout(seam, c), "dug rock is clear of the bedrock: " + c);
            }
        }
        Cells sealed = (x, y, z) -> (x == 10 && y == 0 && z == 0) ? Cells.Kind.AIR : (Math.abs(x - 10) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1) ? Cells.Kind.HARD : FLOOR.at(x, y, z);
        assertTrue(Burrow.plan(sealed, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).isEmpty());
    }

    @Test
    void airByAFaceIsPreferredToAirWithNone() {
        // From the floor to a point 4 up and 4 over: hugging the floor then a column of rock beats a diagonal through the void.
        Cells column = (x, y, z) -> y <= -1 || (x == 6 && y <= 8) ? Cells.Kind.ROCK : Cells.Kind.AIR;
        List<Cell> p = Burrow.plan(column, new Cell(0, 0, 0), new Cell(5, 4, 0), Burrow.STALK, Burrow.BUDGET).orElseThrow();
        int unsupported = 0;
        for (Cell c : p) {
            if (column.at(c) == Cells.Kind.AIR && !Burrow.supported(column, c)) {
                unsupported++;
            }
        }
        assertEquals(0, unsupported, "every cell of the way has a face: " + p);
    }

    @Test
    void cutOffItGoesAsNearAsItCan() {
        // A pool of water across the whole way at x = 5..6: the target beyond it cannot be reached, and rock beside it is never cut.
        Cells pool = (x, y, z) -> (x == 5 || x == 6) ? Cells.Kind.FLUID : y <= -1 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        assertTrue(Burrow.plan(pool, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).isEmpty(), "no way through");
        List<Cell> near = Burrow.planNearest(pool, new Cell(0, 0, 0), new Cell(10, 0, 0), Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        Cell end = near.get(near.size() - 1);
        assertEquals(new Cell(0, 0, 0), near.get(0));
        assertTrue(end.x() >= 3 && end.x() <= 4, "as near the pool as it may go: " + end);
        for (Cell c : near) {
            assertTrue(pool.at(c) != Cells.Kind.FLUID, "never through the water: " + c);
        }
        assertTrue(Burrow.planNearest(pool, new Cell(0, 0, 0), new Cell(0, 0, 0), Burrow.HUNT, Burrow.BUDGET).isPresent(), "already there");
        Cells sealed = (x, y, z) -> (x == 0 && y == 0 && z == 0) ? Cells.Kind.AIR : Cells.Kind.HARD;
        assertTrue(Burrow.planNearest(sealed, new Cell(0, 0, 0), new Cell(5, 0, 0), Burrow.HUNT, Burrow.BUDGET).isEmpty(), "sealed in: nowhere to go");
        List<Cell> budgeted = Burrow.planNearest(Cells.EMPTY, new Cell(0, 0, 0), new Cell(60, 0, 0), Burrow.HUNT, 50).orElseThrow();
        assertTrue(budgeted.get(budgeted.size() - 1).x() > 0, "the budget spent, the nearest seen: " + budgeted.get(budgeted.size() - 1));
    }

    @Test
    void theBudgetEndsAHopelessSearchAndBadArgumentsAreRefused() {
        assertTrue(Burrow.plan(Cells.EMPTY, new Cell(0, 0, 0), new Cell(60, 0, 0), Burrow.HUNT, 50).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Burrow.plan(Cells.EMPTY, new Cell(0, 0, 0), new Cell(1, 0, 0), Burrow.HUNT, 0));
        assertThrows(IllegalArgumentException.class, () -> new Burrow.Costs(1, 0, 1));
    }

    /** The booth's flat world: bedrock at -64, dirt to -62, grass at -61, air from -60; a hill of stone from x = 60, 15 deep, {@code high} tall, {@code halfWide} either side of z = 0. */
    private static Cells hill(int high, int halfWide) {
        return (x, y, z) -> y <= -64 ? Cells.Kind.HARD : y <= -61 ? Cells.Kind.ROCK
                : (x >= 60 && x <= 74 && y >= -60 && y < -60 + high && Math.abs(z) <= halfWide) ? Cells.Kind.ROCK : Cells.Kind.AIR;
    }

    @Test
    void aTargetDeepInAHillUnderOpenSkyIsPlannedInBudgetAndTheWayIsTheCheapest() {
        // The booth's dig: from six blocks short of the hill to a point ten and a half in, at head height. Before the
        // rock-depth heuristic this found nothing in budget (it needed 8000 in the open, 16000 under a ceiling).
        Cell from = new Cell(54, -59, 0), to = new Cell(70, -59, 0);
        List<Cell> in = Burrow.plan(hill(12, 8), from, to, Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        assertEquals(to, in.get(in.size() - 1));
        int rock = 0;
        for (Cell c : in) {
            assertEquals(0, c.z(), "straight in through the face: " + c);
            assertTrue(c.y() == -60 || c.y() == -59, "along the ground and at head height: " + c);
            if (hill(12, 8).at(c) == Cells.Kind.ROCK) {
                rock++;
            }
        }
        assertEquals(11, rock, "eleven cells of rock cut: " + in);
        // A low hill, six high and thirteen wide: over the top and four down costs less than eleven through, so the way goes over.
        List<Cell> over = Burrow.plan(hill(6, 6), from, to, Burrow.HUNT, Burrow.BUDGET).orElseThrow();
        int highest = Integer.MIN_VALUE;
        rock = 0;
        for (Cell c : over) {
            highest = Math.max(highest, c.y());
            if (hill(6, 6).at(c) == Cells.Kind.ROCK) {
                rock++;
            }
        }
        assertEquals(-54, highest, "over the top: " + over);
        assertTrue(rock < 11, "and less rock than through: " + rock);
        // The same hill under a ceiling three above the ground, as a cave would give.
        Cells cave = (x, y, z) -> y >= -56 ? Cells.Kind.ROCK : hill(12, 8).at(x, y, z);
        assertTrue(Burrow.plan(cave, from, to, Burrow.HUNT, Burrow.BUDGET).isPresent(), "planned under a ceiling too");
    }

    @Test
    void theRockDepthIsTheTaxicabToTheNearestAirCapped() {
        assertEquals(0, Burrow.rockDepth(FLOOR, new Cell(0, 0, 0), 8), "air is at no depth");
        assertEquals(1, Burrow.rockDepth(FLOOR, new Cell(0, -1, 0), 8), "the floor's top cell is one in");
        assertEquals(3, Burrow.rockDepth(FLOOR, new Cell(0, -3, 0), 8));
        Cells cube = (x, y, z) -> Math.abs(x) <= 3 && Math.abs(y) <= 3 && Math.abs(z) <= 3 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        assertEquals(4, Burrow.rockDepth(cube, new Cell(0, 0, 0), 8), "the centre of a seven-cube: three of rock about it, air at four");
        assertEquals(1, Burrow.rockDepth(cube, new Cell(3, 0, 0), 8), "its face");
        assertEquals(8, Burrow.rockDepth(FLOOR, new Cell(0, -30, 0), 8), "deeper than the look: the look");
        assertEquals(0, Burrow.rockDepth(FLOOR, new Cell(0, -30, 0), 0), "with no look, nothing is known");
        Cells hardAbout = (x, y, z) -> x == 0 && y == 0 && z == 0 ? Cells.Kind.ROCK : Cells.Kind.HARD;
        assertEquals(8, Burrow.rockDepth(hardAbout, new Cell(0, 0, 0), 8), "hard is not air");
        // The bound holds for every cell: nothing within the depth is air.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 4) {
                        assertTrue(cube.at(dx, dy, dz) != Cells.Kind.AIR);
                    }
                }
            }
        }
    }

    /** effects: returns the cheapest cost from {@code from} to {@code to} under {@code costs} by a plain search of every cell, or NaN when there is none */
    private static double cheapest(Cells cells, Cell from, Cell to, Burrow.Costs costs) {
        Map<Cell, Double> dist = new HashMap<>();
        Set<Cell> done = new HashSet<>();
        PriorityQueue<Cell> queue = new PriorityQueue<>((a, b) -> Double.compare(dist.get(a), dist.get(b)));
        dist.put(from, 0.0);
        queue.add(from);
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            if (!done.add(c)) {
                continue;
            }
            if (c.equals(to)) {
                return dist.get(c);
            }
            for (Cell n : c.neighbours()) {
                double cost = Burrow.passage(cells, n).cost(costs);
                if (Double.isInfinite(cost) || done.contains(n)) {
                    continue;
                }
                double d = dist.get(c) + cost;
                if (!dist.containsKey(n) || d < dist.get(n)) {
                    dist.put(n, d);
                    queue.add(n);
                }
            }
        }
        return Double.NaN;
    }

    /** effects: returns what {@code path} costs under {@code costs}, the start free */
    private static double costOf(Cells cells, List<Cell> path, Burrow.Costs costs) {
        double sum = 0;
        for (int i = 1; i < path.size(); i++) {
            sum += Burrow.passage(cells, path.get(i)).cost(costs);
        }
        return sum;
    }

    @Test
    void theHeuristicLeavesEveryWayTheCheapest() {
        // Forty worlds of random rock in a box walled with hard, the target buried in rock a cell thick at least,
        // hunting and stalking: the way found costs exactly what a plain search of every cell finds, or both find none.
        Random random = new Random(20260915);
        int found = 0;
        for (int world = 0; world < 40; world++) {
            boolean[][][] rock = new boolean[11][7][11];
            for (int x = 0; x < 11; x++) {
                for (int y = 0; y < 7; y++) {
                    for (int z = 0; z < 11; z++) {
                        rock[x][y][z] = random.nextDouble() < 0.3;
                    }
                }
            }
            Cell from = new Cell(1, 1, 1), to = new Cell(7, 3, 7);
            rock[1][1][1] = false;
            for (Cell c : to.neighbours()) {
                rock[c.x()][c.y()][c.z()] = true;
            }
            rock[7][3][7] = true;
            Cells cells = (x, y, z) -> (x < 0 || x > 10 || y < 0 || y > 6 || z < 0 || z > 10) ? Cells.Kind.HARD
                    : rock[x][y][z] ? Cells.Kind.ROCK : Cells.Kind.AIR;
            assertTrue(Burrow.rockDepth(cells, to, 8) >= 2, "buried");
            for (Burrow.Costs costs : List.of(Burrow.HUNT, Burrow.STALK)) {
                double plain = cheapest(cells, from, to, costs);
                Optional<List<Cell>> p = Burrow.plan(cells, from, to, costs, Burrow.BUDGET);
                assertEquals(!Double.isNaN(plain), p.isPresent(), "both find a way or neither, world " + world);
                if (p.isPresent()) {
                    assertEquals(plain, costOf(cells, p.get(), costs), 1e-9, "the cheapest, world " + world + ": " + p.get());
                    found++;
                }
            }
        }
        assertTrue(found > 20, "enough worlds had a way to mean anything: " + found);
    }

    private static long key(int x, int y, int z) {
        return Burrow.Table.pack(x, y, z);
    }

    @Test
    void theTableReadsEachCellOnceAndSaysWhatTheWorldSays() {
        int[] reads = {0};
        Cells counted = (x, y, z) -> {
            reads[0]++;
            return FLOOR.at(x, y, z);
        };
        Burrow.Table table = new Burrow.Table(counted);
        assertEquals(Cells.Kind.AIR, table.at(3, 0, 3));
        assertEquals(Cells.Kind.ROCK, table.at(3, -1, 3));
        assertEquals(Cells.Kind.AIR, table.at(3, 0, 3));
        assertEquals(2, reads[0], "two cells, two reads");
        assertEquals(2, table.reads());
        assertEquals(Burrow.Passage.AIR_BY_A_FACE, table.passage(key(3, 0, 3)));
        int afterOnce = reads[0];
        assertTrue(afterOnce > 2 && afterOnce <= 8, "some of the six about it read, the floor cell not again: " + afterOnce);
        assertEquals(Burrow.Passage.AIR_BY_A_FACE, table.passage(key(3, 0, 3)));
        assertEquals(afterOnce, reads[0], "asked again, nothing is read");
        assertEquals(Burrow.Passage.AIR_ALONE, table.passage(key(3, 5, 3)));
        assertEquals(Burrow.Passage.ROCK, table.passage(key(3, -1, 3)));
        Cells seam = (x, y, z) -> y == -5 ? Cells.Kind.HARD : FLOOR.at(x, y, z);
        assertEquals(Burrow.Passage.NEVER, new Burrow.Table(seam).passage(key(0, -4, 0)), "rock beside hard");
        assertEquals(Burrow.Passage.NEVER, new Burrow.Table(seam).passage(key(0, -5, 0)), "hard itself");
        // Far more cells than the first table holds, in every kind, with negative coordinates: the same answers after growing.
        Cells varied = (x, y, z) -> Cells.Kind.values()[Math.floorMod(x * 31 + y * 17 + z * 7, 4)];
        Burrow.Table big = new Burrow.Table(varied);
        int n = 0;
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                for (int y = -3; y < 3; y++) {
                    assertEquals(varied.at(x, y, z), big.at(x, y, z));
                    n++;
                }
            }
        }
        assertEquals(n, big.reads(), "each read once");
        for (int x = -39; x < 39; x += 7) {   // inside the read region by one, so a passage's neighbours were all read
            for (int z = -39; z < 39; z += 5) {
                assertEquals(varied.at(x, 0, z), big.at(x, 0, z));
                assertEquals(Burrow.passage(varied, new Cell(x, 0, z)), big.passage(key(x, 0, z)));
            }
        }
        assertEquals(n, big.reads(), "nothing read twice");
    }

    @Test
    void aKeyPacksACellAndUnpacksToIt() {
        int[] xs = {0, 1, -1, 7, -30000000, 29999999};
        int[] ys = {0, -64, 319, -2048, 2047};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : xs) {
                    long k = key(x, y, z);
                    assertEquals(x, Burrow.Table.x(k), "x of " + x + "," + y + "," + z);
                    assertEquals(y, Burrow.Table.y(k), "y of " + x + "," + y + "," + z);
                    assertEquals(z, Burrow.Table.z(k), "z of " + x + "," + y + "," + z);
                }
            }
        }
        assertTrue(key(1, 2, 3) != key(3, 2, 1) && key(-1, 0, 0) != key(0, 0, -1) && key(0, -1, 0) != key(0, 0, -1));
    }
}
