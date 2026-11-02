/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Testing strategy.
 *
 * solidAlong: direction along one axis, diagonal, none (the same cell);
 * solid cells none, one, several (in order), in the start cell (left
 * out), at the end cell; the limit reached; a line exactly on a face;
 * non-finite ends and a bad limit refused.
 *
 * between: one target or several; for the best line nothing solid, one
 * ROCK, one ROCK beside a fluid, one HARD, two ROCK; a clear line among
 * blocked ones wins; the first one-wall line's cell is the wall; no
 * targets refused. Verdict's RI refused both ways.
 */
final class ReachTest {
    /** effects: a world of air but for the given cells, of the given kind */
    private static Cells with(Cells.Kind kind, Cell... solid) {
        Set<Cell> set = Set.of(solid);
        return (x, y, z) -> set.contains(new Cell(x, y, z)) ? kind : Cells.Kind.AIR;
    }

    private static final Cells AIR = (x, y, z) -> Cells.Kind.AIR;

    @Test
    void solidAlongFindsTheCellsInOrder() {
        Vec from = new Vec(0.5, 0.5, 0.5);
        assertEquals(List.of(), Reach.solidAlong(AIR, from, new Vec(6.5, 0.5, 0.5), 8), "open air");
        Cells two = with(Cells.Kind.ROCK, new Cell(4, 0, 0), new Cell(2, 0, 0));
        assertEquals(List.of(new Cell(2, 0, 0), new Cell(4, 0, 0)), Reach.solidAlong(two, from, new Vec(6.5, 0.5, 0.5), 8), "nearest first");
        assertEquals(List.of(new Cell(2, 0, 0)), Reach.solidAlong(two, from, new Vec(6.5, 0.5, 0.5), 1), "stops at the limit");
        assertEquals(List.of(new Cell(4, 0, 0), new Cell(2, 0, 0)), Reach.solidAlong(two, new Vec(6.5, 0.5, 0.5), from, 8), "either way along");
        assertEquals(List.of(), Reach.solidAlong(with(Cells.Kind.ROCK, new Cell(0, 0, 0)), from, new Vec(3.5, 0.5, 0.5), 8),
                "the head's own cell is left out");
        assertEquals(List.of(new Cell(3, 0, 0)), Reach.solidAlong(with(Cells.Kind.ROCK, new Cell(3, 0, 0)), from, new Vec(3.5, 0.5, 0.5), 8),
                "the end cell counts");
        assertEquals(List.of(), Reach.solidAlong(with(Cells.Kind.ROCK, new Cell(0, 0, 0)), from, new Vec(0.9, 0.1, 0.7), 8), "within one cell");
        assertEquals(List.of(), Reach.solidAlong(with(Cells.Kind.ROCK, new Cell(5, 0, 0)), from, new Vec(3.5, 0.5, 0.5), 8), "beyond the end");
    }

    @Test
    void solidAlongCrossesDiagonallyAndUpward() {
        // From under a floor at y = 3 up to a player standing on it: the floor's cell is crossed.
        Cells floor = (x, y, z) -> y == 3 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        assertEquals(List.of(new Cell(0, 3, 0)), Reach.solidAlong(floor, new Vec(0.5, 1.5, 0.5), new Vec(0.5, 4.9, 0.5), 8));
        Cells thick = (x, y, z) -> y >= 2 && y <= 4 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        assertEquals(3, Reach.solidAlong(thick, new Vec(0.5, 0.5, 0.5), new Vec(0.5, 5.5, 0.5), 8).size(), "three rows of floor");
        assertTrue(Reach.solidAlong(thick, new Vec(0.5, 0.5, 0.5), new Vec(1.7, 5.5, 2.2), 8).size() >= 3, "slantwise, at least as many");
        List<Cell> diag = Reach.solidAlong((x, y, z) -> Cells.Kind.ROCK, new Vec(0.5, 0.5, 0.5), new Vec(3.5, 2.5, 1.5), 99);
        assertEquals(3 + 2 + 1, diag.size(), "one cell for each face crossed");
        for (int i = 1; i < diag.size(); i++) {
            Cell a = diag.get(i - 1), b = diag.get(i);
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()), "face neighbours: " + a + ", " + b);
        }
        assertEquals(new Cell(3, 2, 1), diag.get(diag.size() - 1), "ending in the end's cell");
        assertEquals(List.of(new Cell(-1, 0, 0)), Reach.solidAlong(with(Cells.Kind.ROCK, new Cell(-1, 0, 0)), new Vec(0, 0.5, 0.5), new Vec(-0.5, 0.5, 0.5), 8),
                "starting on a face, going down it: the cell below is entered at once");
    }

    @Test
    void solidAlongRefusesBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Reach.solidAlong(AIR, new Vec(Double.NaN, 0, 0), Vec.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> Reach.solidAlong(AIR, Vec.ZERO, new Vec(0, Double.POSITIVE_INFINITY, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> Reach.solidAlong(AIR, Vec.ZERO, Vec.ZERO, 0));
    }

    @Test
    void betweenJudgesTheBestLine() {
        Vec head = new Vec(0.5, 0.5, 0.5);
        List<Vec> prey = List.of(new Vec(4.5, 0.5, 0.5));
        assertEquals(Reach.Verdict.CLEAR, Reach.between(AIR, head, prey), "nothing between");
        Cell w = new Cell(2, 0, 0);
        assertEquals(new Reach.Verdict(Reach.Way.WALL, Optional.of(w)), Reach.between(with(Cells.Kind.ROCK, w), head, prey), "one block of rock");
        assertEquals(Reach.Verdict.BLOCKED, Reach.between(with(Cells.Kind.HARD, w), head, prey), "obsidian is not burst");
        assertEquals(Reach.Verdict.BLOCKED, Reach.between(with(Cells.Kind.ROCK, w, new Cell(3, 0, 0)), head, prey), "two blocks");
        Cells wet = (x, y, z) -> x == 2 && y == 0 && z == 0 ? Cells.Kind.ROCK : x == 2 && y == 1 && z == 0 ? Cells.Kind.FLUID : Cells.Kind.AIR;
        assertEquals(Reach.Verdict.BLOCKED, Reach.between(wet, head, prey), "rock with water beside it is never breached");
        // A wall that covers the feet but not the eyes: the eyes' line is clear.
        Cells low = with(Cells.Kind.ROCK, new Cell(2, 0, 0));
        assertEquals(Reach.Verdict.CLEAR, Reach.between(low, head, List.of(new Vec(4.5, 0.5, 0.5), new Vec(4.5, 2.5, 0.5))), "one clear line is enough");
        // Two one-wall lines: the first's cell.
        Cells both = with(Cells.Kind.ROCK, new Cell(2, 0, 0), new Cell(2, 3, 0));
        assertEquals(new Reach.Verdict(Reach.Way.WALL, Optional.of(new Cell(2, 0, 0))),
                Reach.between(both, head, List.of(new Vec(4.5, 0.5, 0.5), new Vec(4.5, 6.5, 0.5))), "the first line's wall");
        assertThrows(IllegalArgumentException.class, () -> Reach.between(AIR, head, List.of()));
    }

    @Test
    void verdictKeepsItsWallWithItsWay() {
        assertThrows(IllegalArgumentException.class, () -> new Reach.Verdict(Reach.Way.WALL, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new Reach.Verdict(Reach.Way.CLEAR, Optional.of(new Cell(0, 0, 0))));
        assertTrue(new Reach.Verdict(Reach.Way.BLOCKED, Optional.empty()).wall().isEmpty());
    }
}
