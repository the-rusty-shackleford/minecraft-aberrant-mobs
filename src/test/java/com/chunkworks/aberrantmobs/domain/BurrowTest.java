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
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Through open air along a floor: the straight line, its
 * length the taxicab distance. A wall of rock in the way: through it when
 * digging is cheap enough, around it when it is dear and a way round
 * exists. A hard wall: always around; sealed in hard: nothing. Air with
 * no face costs more than air by one. The budget ends a hopeless search.
 * Bad arguments refused.
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
    void theBudgetEndsAHopelessSearchAndBadArgumentsAreRefused() {
        assertTrue(Burrow.plan(Cells.EMPTY, new Cell(0, 0, 0), new Cell(60, 0, 0), Burrow.HUNT, 50).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Burrow.plan(Cells.EMPTY, new Cell(0, 0, 0), new Cell(1, 0, 0), Burrow.HUNT, 0));
        assertThrows(IllegalArgumentException.class, () -> new Burrow.Costs(1, 0, 1));
    }
}
