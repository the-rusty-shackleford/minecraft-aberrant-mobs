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

import java.util.Optional;

/**
 * Where a creature may come into the world: deep, in the dark, and in
 * the rock beside a cave, so that the first sign of it is digging. Pure
 * over {@link Cells}; the level's light and depth are handed in.
 */
public final class Habitat {
    private Habitat() {}

    /**
     * A creature's habitat: the depths it keeps to, the most light it will
     * bear (sky or block), how far from another of its kind it spawns, and
     * how many a level holds. RI: yMin <= yMax; 0 <= maxLight <= 15;
     * exclusion, cap >= 0.
     */
    public record Rules(int yMin, int yMax, int maxLight, int exclusion, int cap) {
        public Rules {
            if (yMin > yMax || maxLight < 0 || maxLight > 15 || exclusion < 0 || cap < 0) {
                throw new IllegalArgumentException("a habitat has a depth range, a light bound, an exclusion and a cap");
            }
        }
    }

    /** The Face-Stealer's: between -58 and 0, pitch dark, none within 128 blocks, six to a level. */
    public static final Rules FACE_STEALER = new Rules(-58, 0, 0, 128, 6);
    /** A pocket reaches this far from its centre in every direction: five cells across, room for a head 3.75 wide. */
    public static final int POCKET_RADIUS = 2;
    /** A site is bored this deep into the wall, blocks: the pocket's far side, leaving four of rock between the cave and the pocket. */
    public static final int SITE_DEPTH = 7;
    /** A cave wider than this is not crossed to reach its wall, blocks. */
    public static final int MAX_CAVE = 12;

    /** effects: returns whether a site at {@code y} with sky light {@code sky} and block light {@code block} is deep and dark enough under {@code rules} */
    public static boolean deepAndDark(Rules rules, int y, int sky, int block) {
        return y >= rules.yMin() && y <= rules.yMax() && sky <= rules.maxLight() && block <= rules.maxLight();
    }

    /** effects: returns whether every cell within {@link #POCKET_RADIUS} of {@code centre} in every direction is rock: a pocket may be bored there */
    public static boolean pocketOfRock(Cells cells, Cell centre) {
        for (int dx = -POCKET_RADIUS; dx <= POCKET_RADIUS; dx++) {
            for (int dy = -POCKET_RADIUS; dy <= POCKET_RADIUS; dy++) {
                for (int dz = -POCKET_RADIUS; dz <= POCKET_RADIUS; dz++) {
                    if (cells.at(centre.x() + dx, centre.y() + dy, centre.z() + dz) != Cells.Kind.ROCK) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * effects: returns a cell {@code depth} blocks into the rock of the
     * cave's wall from {@code floor} (a cell of the cave, one over its
     * floor) along the first of +X, -X, +Z, -Z: the cave's air is crossed
     * (at most {@link #MAX_CAVE} cells of it), then every cell of the next
     * {@code depth} must be rock, and the last the centre of a pocket of
     * rock ({@link #pocketOfRock}); nothing when no wall is thick enough
     */
    public static Optional<Cell> siteInWall(Cells cells, Cell floor, int depth) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int i = 1;
            while (i <= MAX_CAVE && cells.at(floor.x() + d[0] * i, floor.y(), floor.z() + d[1] * i) == Cells.Kind.AIR) {
                i++;
            }
            if (i > MAX_CAVE) {
                continue;
            }
            boolean solid = true;
            for (int k = 0; k < depth && solid; k++) {
                solid = cells.at(floor.x() + d[0] * (i + k), floor.y(), floor.z() + d[1] * (i + k)) == Cells.Kind.ROCK;
            }
            Cell end = new Cell(floor.x() + d[0] * (i + depth - 1), floor.y(), floor.z() + d[1] * (i + depth - 1));
            if (solid && pocketOfRock(cells, end)) {
                return Optional.of(end);
            }
        }
        return Optional.empty();
    }
}
