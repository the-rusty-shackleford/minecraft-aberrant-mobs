/*
 * Aberrant Mobs - a protocol for monsters.
 * Copyright (C) 2026 Chunkworks
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
import java.util.Objects;
import java.util.Optional;

/**
 * What lies between the pincers and their prey (D-0025): the solid cells a
 * straight line from the head passes through. Nothing between, the pincers
 * close; one block of diggable rock, they burst through it first; anything
 * more, they cannot reach.
 */
public final class Reach {
    private Reach() {}

    /** How the pincers can reach a prey. */
    public enum Way {
        /** Nothing solid between. */
        CLEAR,
        /** One block of diggable, dry rock between: burst through it. */
        WALL,
        /** More than one solid block, or one that cannot be dug. */
        BLOCKED
    }

    /**
     * How a prey is reached, and through what.
     *
     * <p>RI: wall is present exactly when way is WALL. AF: "the pincers reach
     * the prey by way, breaking wall when it is a wall".
     */
    public record Verdict(Way way, Optional<Cell> wall) {
        public Verdict {
            Objects.requireNonNull(way);
            Objects.requireNonNull(wall);
            if (wall.isPresent() != (way == Way.WALL)) {
                throw new IllegalArgumentException("a wall exactly when the way is WALL: " + way + ", " + wall);
            }
        }

        public static final Verdict CLEAR = new Verdict(Way.CLEAR, Optional.empty());
        public static final Verdict BLOCKED = new Verdict(Way.BLOCKED, Optional.empty());
    }

    /**
     * requires: from and to finite; limit &gt;= 1
     * effects: returns the solid cells the segment from {@code from} to {@code to} passes
     * through, in the order it meets them, the cell holding {@code from} left out (the head's
     * own); stops once it has {@code limit}
     */
    public static List<Cell> solidAlong(Cells cells, Vec from, Vec to, int limit) {
        if (!finite(from) || !finite(to) || limit < 1) {
            throw new IllegalArgumentException("finite ends and a positive limit: " + from + ", " + to + ", " + limit);
        }
        List<Cell> out = new ArrayList<>();
        int x = floor(from.x()), y = floor(from.y()), z = floor(from.z());
        int ex = floor(to.x()), ey = floor(to.y()), ez = floor(to.z());
        double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
        double tx = first(from.x(), x, sx, dx), ty = first(from.y(), y, sy, dy), tz = first(from.z(), z, sz, dz);
        double ux = sx == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double uy = sy == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double uz = sz == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        // Each step crosses one face, so the cells between the ends number at most the faces between them.
        int steps = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z);
        for (int i = 0; i < steps; i++) {
            if (tx <= ty && tx <= tz) {
                if (tx > 1.0) break;
                x += sx;
                tx += ux;
            } else if (ty <= tz) {
                if (ty > 1.0) break;
                y += sy;
                ty += uy;
            } else {
                if (tz > 1.0) break;
                z += sz;
                tz += uz;
            }
            if (cells.at(x, y, z).solid()) {
                out.add(new Cell(x, y, z));
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    /**
     * requires: from and every target finite; targets not empty
     * effects: returns the best way from the head at {@code from} to any of {@code targets}
     * (points on the prey: its feet, middle and eyes): CLEAR when a line to one passes no solid
     * cell; else WALL, through the first such line's one cell, when a line passes exactly one
     * and that one is ROCK with no fluid beside it (what {@link Tunnel#cuttable} cuts); else
     * BLOCKED
     */
    public static Verdict between(Cells cells, Vec from, List<Vec> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("no target points");
        }
        Optional<Cell> wall = Optional.empty();
        for (Vec to : targets) {
            List<Cell> solid = solidAlong(cells, from, to, 2);
            if (solid.isEmpty()) {
                return Verdict.CLEAR;
            }
            if (wall.isEmpty() && solid.size() == 1 && !Tunnel.cuttable(cells, solid).isEmpty()) {
                wall = Optional.of(solid.get(0));
            }
        }
        return wall.map(c -> new Verdict(Way.WALL, Optional.of(c))).orElse(Verdict.BLOCKED);
    }

    /** effects: the segment's parameter at which it first leaves cell {@code c} along this axis, or infinity when it never moves on it */
    private static double first(double p, int c, int s, double d) {
        if (s == 0) return Double.POSITIVE_INFINITY;
        return (s > 0 ? c + 1 - p : p - c) / Math.abs(d);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static boolean finite(Vec v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}
