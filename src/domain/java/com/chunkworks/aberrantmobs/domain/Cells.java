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
