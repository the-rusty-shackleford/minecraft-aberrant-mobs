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

    /**
     * requires: {@code |normal| = 1}, {@code ahead >= 0}, {@code radius > 0}, {@code 0 <= from <= way.size()}
     * effects: returns the cells whose centres lie within {@code radius} of
     * the polyline that starts at {@code centre} and runs through the
     * centres of {@code way[from..]} laid into the plane through
     * {@code centre} perpendicular to {@code normal} (each moved along the
     * normal onto it: a way's cells lie a row under the head, on the floor
     * side, or in its own row, and the bore is cut about the head), cut
     * off {@code ahead} along its length; no cell twice, in the order met
     * along the way -- the tube a strike cuts so that a bend in the way is
     * cut as a bend, where a section along the heading alone leaves the
     * bend's outer corner standing
     */
    public static List<Cell> along(Vec centre, Vec normal, List<Cell> way, int from, double ahead, double radius) {
        if (Math.abs(normal.length() - 1.0) > 1e-6 || !(ahead >= 0) || !(radius > 0) || from < 0 || from > way.size()) {
            throw new IllegalArgumentException("a unit normal, a reach, a radius and a place in the way");
        }
        List<Cell> out = new ArrayList<>();
        Vec at = centre;
        double left = ahead;
        for (int i = from; i < way.size() && left > 1e-9; i++) {
            Vec raw = way.get(i).centre();
            Vec next = raw.minus(normal.times(raw.minus(centre).dot(normal)));
            double d = next.minus(at).length();
            if (d < 1e-9) {
                continue;
            }
            if (d > left) {
                next = at.plus(next.minus(at).times(left / d));
                d = left;
            }
            for (Cell c : section(at, next.minus(at).times(1.0 / d), d, radius)) {
                if (!out.contains(c)) {
                    out.add(c);
                }
            }
            left -= d;
            at = next;
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

    /**
     * effects: returns the rock cells of {@code section} that may be cut,
     * in its order: those with no fluid on any face, so that cutting them
     * breaches nothing -- what a strike removes, when its section may hold
     * cells beyond the ones {@link #diggable} judged
     */
    public static List<Cell> cuttable(Cells cells, List<Cell> section) {
        List<Cell> out = new ArrayList<>();
        for (Cell c : section) {
            if (cells.at(c) != Cells.Kind.ROCK) {
                continue;
            }
            boolean wet = false;
            for (Cell n : c.neighbours()) {
                wet |= cells.at(n) == Cells.Kind.FLUID;
            }
            if (!wet) {
                out.add(c);
            }
        }
        return List.copyOf(out);
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
