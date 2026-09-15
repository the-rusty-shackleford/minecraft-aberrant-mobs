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
 * A block cell by its lowest corner. Immutable.
 * AF: AF(x, y, z) = "the cell [x, x + 1) x [y, y + 1) x [z, z + 1)".
 */
public record Cell(int x, int y, int z) {
    /** effects: returns the cell containing {@code p} */
    public static Cell containing(Vec p) {
        return new Cell((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
    }

    /** effects: returns the cell's centre */
    public Vec centre() {
        return new Vec(x + 0.5, y + 0.5, z + 0.5);
    }

    public Cell plus(int dx, int dy, int dz) {
        return new Cell(x + dx, y + dy, z + dz);
    }

    /** effects: returns the six face neighbours, +X, -X, +Y, -Y, +Z, -Z */
    public Cell[] neighbours() {
        return new Cell[] {plus(1, 0, 0), plus(-1, 0, 0), plus(0, 1, 0), plus(0, -1, 0), plus(0, 0, 1), plus(0, 0, -1)};
    }

    /** effects: returns the taxicab distance to {@code o}, cells */
    public int manhattan(Cell o) {
        return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
    }
}
