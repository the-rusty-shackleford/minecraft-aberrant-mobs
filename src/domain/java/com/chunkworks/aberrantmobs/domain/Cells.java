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

/**
 * The world as a creature senses it, one cell at a time: the one way any
 * rule of this layer reads blocks, so every rule runs under JUnit on
 * synthetic rock. The adapter that reads the level classifies each block
 * once; the rules never see a block.
 */
@FunctionalInterface
public interface Cells {
    /** What a cell is to a creature. */
    enum Kind {
        /** Nothing to stand on or dig. */
        AIR,
        /** Solid, and diggable. */
        ROCK,
        /** Solid, and not diggable: bedrock, obsidian, a block with a tile entity, the profile's own list. */
        HARD,
        /** A fluid, or a cell a fluid would pour from if opened: never breached. */
        FLUID;

        public boolean solid() {
            return this == ROCK || this == HARD;
        }
    }

    /** effects: returns the kind of the cell at {@code (x, y, z)}; pure, and cheap enough to ask thousands of times a tick */
    Kind at(int x, int y, int z);

    /** effects: returns the kind of the cell containing {@code p} */
    default Kind at(Vec p) {
        return at((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
    }

    /** effects: returns the kind of {@code c} */
    default Kind at(Cell c) {
        return at(c.x(), c.y(), c.z());
    }

    /** A cast samples the world this finely, blocks. */
    double CAST_STEP = 0.2;
    /** A face is found to within this, blocks. */
    double FACE_TOLERANCE = 0.01;

    /**
     * effects: returns the point where a cast from {@code from} along {@code dir}
     * (unit) first meets a solid cell within {@code reach} blocks -- on the
     * surface, just outside the cell, within {@link #FACE_TOLERANCE} -- or
     * null when it meets none; a cast that starts inside a solid cell looks
     * back the other way for the surface it is under
     */
    default Vec face(Vec from, Vec dir, double reach) {
        if (solidAt(from)) {
            for (double d = CAST_STEP; d <= reach; d += CAST_STEP) {
                if (!solidAt(from.minus(dir.times(d)))) {
                    return bisect(from, dir.times(-1), d, d - CAST_STEP);
                }
            }
            return null;
        }
        for (double d = CAST_STEP; d <= reach; d += CAST_STEP) {
            if (solidAt(from.plus(dir.times(d)))) {
                return bisect(from, dir, d - CAST_STEP, d);
            }
        }
        return null;
    }

    /**
     * requires: {@code from + dir * clear} is not solid, {@code from + dir * solid} is, either order
     * effects: returns the clear point nearest the solid one along the cast, within {@link #FACE_TOLERANCE}
     */
    private Vec bisect(Vec from, Vec dir, double clear, double solid) {
        while (Math.abs(solid - clear) > FACE_TOLERANCE) {
            double mid = (clear + solid) / 2;
            if (solidAt(from.plus(dir.times(mid)))) {
                solid = mid;
            } else {
                clear = mid;
            }
        }
        return from.plus(dir.times(clear));
    }

    /** effects: returns whether the cell containing {@code p} is solid */
    default boolean solidAt(Vec p) {
        return at(p).solid();
    }

    /** A world of nothing: air everywhere. */
    Cells EMPTY = (x, y, z) -> Kind.AIR;

    /** effects: returns a world with a flat floor: every cell at or under {@code floorY} is rock, the rest air */
    static Cells floor(int floorY) {
        return (x, y, z) -> y <= floorY ? Kind.ROCK : Kind.AIR;
    }
}
