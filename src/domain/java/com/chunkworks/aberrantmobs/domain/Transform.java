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
 * An affine map of mesh space: a 3x3 matrix and a translation. Enough for
 * mirroring, scaling, translating and composing them; rotations of parts
 * are {@link Rotation}s applied at draw time, not here.
 *
 * <p>RI: the nine matrix entries and the translation are finite.
 */
public record Transform(double m00, double m01, double m02, double m10, double m11, double m12,
                        double m20, double m21, double m22, Vec translation) {
    public static final Transform IDENTITY = new Transform(1, 0, 0, 0, 1, 0, 0, 0, 1, Vec.ZERO);
    /** x -> -x: the map between a left-handed export and the game's right-handed frame. */
    public static final Transform MIRROR_X = new Transform(-1, 0, 0, 0, 1, 0, 0, 0, 1, Vec.ZERO);

    public Transform {
        for (double d : new double[] {m00, m01, m02, m10, m11, m12, m20, m21, m22}) {
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("a transform must be finite");
            }
        }
    }

    /** effects: returns the uniform scale by {@code s} */
    public static Transform scale(double s) {
        return new Transform(s, 0, 0, 0, s, 0, 0, 0, s, Vec.ZERO);
    }

    /** effects: returns the translation by {@code t} */
    public static Transform translate(Vec t) {
        return new Transform(1, 0, 0, 0, 1, 0, 0, 0, 1, t);
    }

    /** effects: returns the map that applies this, then {@code next} */
    public Transform andThen(Transform n) {
        return new Transform(
                n.m00 * m00 + n.m01 * m10 + n.m02 * m20, n.m00 * m01 + n.m01 * m11 + n.m02 * m21, n.m00 * m02 + n.m01 * m12 + n.m02 * m22,
                n.m10 * m00 + n.m11 * m10 + n.m12 * m20, n.m10 * m01 + n.m11 * m11 + n.m12 * m21, n.m10 * m02 + n.m11 * m12 + n.m12 * m22,
                n.m20 * m00 + n.m21 * m10 + n.m22 * m20, n.m20 * m01 + n.m21 * m11 + n.m22 * m21, n.m20 * m02 + n.m21 * m12 + n.m22 * m22,
                n.applyDirection(translation).plus(n.translation));
    }

    /** effects: returns {@code p} mapped, translation included */
    public Vec apply(Vec p) {
        return applyDirection(p).plus(translation);
    }

    /** effects: returns {@code d} mapped as a direction: no translation */
    public Vec applyDirection(Vec d) {
        return new Vec(m00 * d.x() + m01 * d.y() + m02 * d.z(),
                m10 * d.x() + m11 * d.y() + m12 * d.z(),
                m20 * d.x() + m21 * d.y() + m22 * d.z());
    }

    /** effects: returns the determinant of the matrix part */
    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
    }

    /** effects: returns whether the map turns a right hand into a left one */
    public boolean isReflection() {
        return determinant() < 0;
    }
}
