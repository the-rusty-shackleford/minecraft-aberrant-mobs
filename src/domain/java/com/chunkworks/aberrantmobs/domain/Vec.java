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

import java.util.Collection;

/**
 * A point or direction in three dimensions. Immutable; finite.
 *
 * <p>RI: every component finite.
 */
public record Vec(double x, double y, double z) {
    public static final Vec ZERO = new Vec(0, 0, 0);
    public static final Vec X = new Vec(1, 0, 0);
    public static final Vec Y = new Vec(0, 1, 0);
    public static final Vec Z = new Vec(0, 0, 1);

    public Vec {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("a vector must be finite: " + x + ", " + y + ", " + z);
        }
    }

    public Vec plus(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    public Vec minus(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    public Vec times(double s) {
        return new Vec(x * s, y * s, z * s);
    }

    public double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec cross(Vec o) {
        return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    /** effects: returns this at unit length<br>throws: {@link IllegalArgumentException} for the zero vector */
    public Vec normalized() {
        double l = length();
        if (l < 1e-12) {
            throw new IllegalArgumentException("the zero vector has no direction");
        }
        return times(1.0 / l);
    }

    /** effects: returns the mean of {@code points}<br>throws: {@link IllegalArgumentException} if there are none */
    public static Vec centroid(Collection<Vec> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("no points");
        }
        double sx = 0, sy = 0, sz = 0;
        for (Vec p : points) {
            sx += p.x;
            sy += p.y;
            sz += p.z;
        }
        int n = points.size();
        return new Vec(sx / n, sy / n, sz / n);
    }

    /** effects: returns whether this is within {@code eps} of {@code o} on every axis */
    public boolean near(Vec o, double eps) {
        return Math.abs(x - o.x) <= eps && Math.abs(y - o.y) <= eps && Math.abs(z - o.z) <= eps;
    }
}
