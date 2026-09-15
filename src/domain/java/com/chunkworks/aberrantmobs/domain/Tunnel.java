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


import java.util.ArrayList;
import java.util.List;

/**
 * The bore a head cuts as it moves: the cells within the body's radius of
 * the head's segment of travel, and whether they may be cut. A section on
 * an axis is three by three; a diagonal takes more. Since the head weaves,
 * a straight run bores a gently snaking tunnel. Rock only is ever cut:
 * hard cells and fluid stop the dig, and so does rock with fluid on the
 * other side of it, so a tunnel never breaches water or lava.
 */
public final class Tunnel {
    private Tunnel() {}

    /**
     * requires: {@code |heading| = 1}, {@code ahead >= 0}, {@code radius > 0}
     * effects: returns the cells whose centres lie within {@code radius} of
     * the segment from {@code centre} to {@code centre + heading * ahead},
     * in a fixed order (x, then y, then z ascending)
     */
    public static List<Cell> section(Vec centre, Vec heading, double ahead, double radius) {
        if (!(ahead >= 0) || !(radius > 0)) {
            throw new IllegalArgumentException("a section reaches ahead and has a radius");
        }
        Vec a = centre, b = centre.plus(heading.times(ahead));
        int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - radius), x1 = (int) Math.floor(Math.max(a.x(), b.x()) + radius);
        int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - radius), y1 = (int) Math.floor(Math.max(a.y(), b.y()) + radius);
        int z0 = (int) Math.floor(Math.min(a.z(), b.z()) - radius), z1 = (int) Math.floor(Math.max(a.z(), b.z()) + radius);
        List<Cell> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Cell c = new Cell(x, y, z);
                    if (distanceToSegment(c.centre(), a, b) <= radius) {
                        out.add(c);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** effects: returns the distance from {@code p} to the segment {@code ab} */
    static double distanceToSegment(Vec p, Vec a, Vec b) {
        Vec ab = b.minus(a);
        double len2 = ab.dot(ab);
        double t = len2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, p.minus(a).dot(ab) / len2));
        return p.minus(a.plus(ab.times(t))).length();
    }

    /**
     * effects: returns whether every cell of {@code section} may be cut or
     * passed: none is hard or fluid, and no rock cell has fluid on any face
     */
    public static boolean diggable(Cells cells, List<Cell> section) {
        for (Cell c : section) {
            Cells.Kind k = cells.at(c);
            if (k == Cells.Kind.HARD || k == Cells.Kind.FLUID) {
                return false;
            }
            if (k == Cells.Kind.ROCK) {
                for (Cell n : c.neighbours()) {
                    if (cells.at(n) == Cells.Kind.FLUID) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** effects: returns the rock cells of {@code section}, in its order: what a dig removes */
    public static List<Cell> rock(Cells cells, List<Cell> section) {
        List<Cell> out = new ArrayList<>();
        for (Cell c : section) {
            if (cells.at(c) == Cells.Kind.ROCK) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }
}
