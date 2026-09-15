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
package com.chunkworks.aberrantmobs.domain.frame;

import com.chunkworks.aberrantmobs.domain.Vec;

/**
 * Which way is down for a wearer of the chitin: one of six axis
 * directions, never an arbitrary vector -- the game's collision is boxes
 * against voxel shapes, block faces are the only surfaces, and a box on
 * a wall is an axis swap, which an arbitrary vector cannot be. DOWN is
 * the world's own.
 */
public enum Gravity {
    DOWN(0, -1, 0), UP(0, 1, 0), WEST(-1, 0, 0), EAST(1, 0, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1);

    /** The direction things fall, unit. */
    public final Vec dir;

    Gravity(double x, double y, double z) {
        dir = new Vec(x, y, z);
    }

    /** effects: returns the gravity whose direction is nearest {@code v}; requires v non-zero */
    public static Gravity nearest(Vec v) {
        if (!(v.length() > 0)) {
            throw new IllegalArgumentException("no direction");
        }
        Gravity best = DOWN;
        double bestDot = -2;
        for (Gravity g : values()) {
            double d = g.dir.dot(v);
            if (d > bestDot) {
                bestDot = d;
                best = g;
            }
        }
        return best;
    }

    /** effects: returns the gravity pulling toward the face with outward normal {@code n}: the opposite way */
    public static Gravity toward(Vec n) {
        return nearest(n.times(-1));
    }

    public Gravity opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case WEST -> EAST;
            case EAST -> WEST;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
        };
    }

    /** effects: returns whether this is the world's own down */
    public boolean isDown() {
        return this == DOWN;
    }
}
