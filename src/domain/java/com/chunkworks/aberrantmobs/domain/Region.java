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
 * A box in mesh space that selects faces by where their centres lie; an
 * open side is the largest value.
 *
 * <p>RI: min <= max on every axis.
 */
public record Region(Vec min, Vec max) {
    public static final double OPEN = 1e9;
    public static final Region ALL = new Region(new Vec(-OPEN, -OPEN, -OPEN), new Vec(OPEN, OPEN, OPEN));

    public Region {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("min must not exceed max: " + min + " .. " + max);
        }
    }

    /** effects: returns the region of everything with x <= {@code x} */
    public static Region xAtMost(double x) {
        return new Region(new Vec(-OPEN, -OPEN, -OPEN), new Vec(x, OPEN, OPEN));
    }

    /** effects: returns the region of everything with x >= {@code x} */
    public static Region xAtLeast(double x) {
        return new Region(new Vec(x, -OPEN, -OPEN), new Vec(OPEN, OPEN, OPEN));
    }

    /** effects: returns whether {@code p} is inside, edges included */
    public boolean contains(Vec p) {
        return p.x() >= min.x() && p.x() <= max.x() && p.y() >= min.y() && p.y() <= max.y() && p.z() >= min.z() && p.z() <= max.z();
    }
}
