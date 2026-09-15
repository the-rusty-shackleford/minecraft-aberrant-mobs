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
 * Partitions. Deep and dark: within the depths and dark yes; too high,
 * too deep, sky-lit, block-lit no. A pocket of rock: all rock yes; one
 * cell of air or bedrock no. A site in the wall: a wall thick enough on
 * one side gives the cell that deep; the first direction that works
 * wins; the cave's own air is crossed first, but not a cavern; a thin
 * wall, or bedrock in the way, gives nothing. Bad rules refused.
 */
final class HabitatTest {
    @Test
    void deepAndDarkIsDepthAndNoLight() {
        Habitat.Rules r = Habitat.FACE_STEALER;
        assertTrue(Habitat.deepAndDark(r, -30, 0, 0));
        assertTrue(Habitat.deepAndDark(r, 0, 0, 0) && Habitat.deepAndDark(r, -58, 0, 0), "the ends included");
        assertFalse(Habitat.deepAndDark(r, 1, 0, 0), "too high");
        assertFalse(Habitat.deepAndDark(r, -59, 0, 0), "too deep");
        assertFalse(Habitat.deepAndDark(r, -30, 1, 0), "a glimmer of sky");
        assertFalse(Habitat.deepAndDark(r, -30, 0, 1), "a torch");
        assertTrue(Habitat.deepAndDark(new Habitat.Rules(-58, 0, 7, 0, 0), -30, 7, 3), "a laxer rule bears some light");
        assertThrows(IllegalArgumentException.class, () -> new Habitat.Rules(0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Habitat.Rules(-1, 0, 16, 0, 0));
    }

    @Test
    void aPocketIsRockAllRound() {
        Cells rock = (x, y, z) -> Cells.Kind.ROCK;
        assertTrue(Habitat.pocketOfRock(rock, new Cell(0, 0, 0)));
        Cells holed = (x, y, z) -> x == 1 && y == 1 && z == 1 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertFalse(Habitat.pocketOfRock(holed, new Cell(0, 0, 0)), "a corner of air");
        Cells farHole = (x, y, z) -> x == 2 && y == -2 && z == 2 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertFalse(Habitat.pocketOfRock(farHole, new Cell(0, 0, 0)), "the pocket reaches two each way: air at its far corner");
        Cells beyond = (x, y, z) -> x == 3 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertTrue(Habitat.pocketOfRock(beyond, new Cell(0, 0, 0)), "air three away is outside it");
        Cells bedrock = (x, y, z) -> y == -1 ? Cells.Kind.HARD : Cells.Kind.ROCK;
        assertFalse(Habitat.pocketOfRock(bedrock, new Cell(0, 0, 0)), "bedrock below");
    }

    @Test
    void aSiteLiesInTheFirstWallThickEnough() {
        // A cave one cell wide at x = 0, rock to the east from x = 1 on, and to the west only three cells.
        Cells cave = (x, y, z) -> x == 0 || x < -3 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        Optional<Cell> site = Habitat.siteInWall(cave, new Cell(0, 5, 0), Habitat.SITE_DEPTH);
        assertEquals(Optional.of(new Cell(7, 5, 0)), site, "seven into the east wall: four of rock, then the pocket's five");
        Cells thin = (x, y, z) -> x == 0 || Math.abs(x) > 6 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertTrue(Habitat.siteInWall(thin, new Cell(0, 5, 0), Habitat.SITE_DEPTH).isEmpty(), "a wall six thick each way is too thin");
        Cells seam = (x, y, z) -> x == 0 && z == 0 ? Cells.Kind.AIR : Math.abs(x) == 3 || Math.abs(z) == 3 ? Cells.Kind.HARD : Cells.Kind.ROCK;
        assertTrue(Habitat.siteInWall(seam, new Cell(0, 5, 0), Habitat.SITE_DEPTH).isEmpty(), "bedrock in every way");
        Cells north = (x, y, z) -> z < 0 ? Cells.Kind.ROCK : Cells.Kind.AIR;   // rock only to the north
        assertEquals(Optional.of(new Cell(0, 5, -7)), Habitat.siteInWall(north, new Cell(0, 5, 0), Habitat.SITE_DEPTH), "the last direction tried, north");
        // A cave four wide is crossed first; the wall past it is what counts.
        Cells wide = (x, y, z) -> x >= 0 && x <= 3 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertEquals(Optional.of(new Cell(10, 5, 0)), Habitat.siteInWall(wide, new Cell(0, 5, 0), Habitat.SITE_DEPTH), "seven into the wall beyond the cave");
        Cells cavern = (x, y, z) -> Math.abs(x) <= 20 && Math.abs(z) <= 20 ? Cells.Kind.AIR : Cells.Kind.ROCK;
        assertTrue(Habitat.siteInWall(cavern, new Cell(0, 5, 0), Habitat.SITE_DEPTH).isEmpty(), "a cavern too wide to cross");
    }
}
