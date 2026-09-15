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
 * A rigid placement: a rotation, then a translation. Immutable. What a bone
 * is, in the space of the model or of its parent.
 *
 * <p>RI: both parts non-null (each keeps its own invariant).
 * AF: AF(rotation, translation) = "the map p -> rotation(p) + translation".
 */
public record Xform(Quat rotation, Vec translation) {
    public static final Xform IDENTITY = new Xform(Quat.IDENTITY, Vec.ZERO);

    public Xform {
        if (rotation == null || translation == null) {
            throw new IllegalArgumentException("a placement has a rotation and a translation");
        }
    }

    /** effects: returns {@code p} placed: turned, then moved */
    public Vec apply(Vec p) {
        return rotation.rotate(p).plus(translation);
    }

    /** effects: returns the direction {@code d} turned, not moved */
    public Vec applyDirection(Vec d) {
        return rotation.rotate(d);
    }

    /** effects: returns the placement that is {@code inner}, then this: {@code compose(inner).apply(p) == apply(inner.apply(p))} */
    public Xform compose(Xform inner) {
        return new Xform(rotation.times(inner.rotation), rotation.rotate(inner.translation).plus(translation));
    }
}
