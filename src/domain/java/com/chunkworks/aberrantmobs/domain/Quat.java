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
 * A rotation, as a unit quaternion. Immutable. The one rotation type the
 * domain has: bones turn by it, the camera rolls by it, the body's segments
 * face by it; the client turns it into the game's own quaternion at the
 * last moment.
 *
 * <p>RI: every component finite and w^2 + x^2 + y^2 + z^2 = 1 within 1e-6.
 * AF: AF(w, x, y, z) = "the rotation by 2*acos(w) about the axis (x, y, z)
 *     normalised"; q and -q are the same rotation.
 *
 * <p>Composition reads right to left: {@code a.times(b)} is b, then a --
 * {@code a.times(b).rotate(v) == a.rotate(b.rotate(v))}.
 */
public record Quat(double w, double x, double y, double z) {
    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    public Quat {
        if (!Double.isFinite(w) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("a quaternion must be finite");
        }
        double n = w * w + x * x + y * y + z * z;
        if (Math.abs(n - 1.0) > 1e-6) {
            throw new IllegalArgumentException("a rotation is a unit quaternion: |q|^2 = " + n);
        }
    }

    /** effects: returns the unit quaternion in the direction of (w, x, y, z)<br>throws: {@link IllegalArgumentException} for zero */
    public static Quat normalized(double w, double x, double y, double z) {
        double n = Math.sqrt(w * w + x * x + y * y + z * z);
        if (n < 1e-12) {
            throw new IllegalArgumentException("the zero quaternion is no rotation");
        }
        return new Quat(w / n, x / n, y / n, z / n);
    }

    /**
     * requires: |axis| = 1
     * effects: returns the rotation by {@code radians} about {@code axis}, right-handed
     */
    public static Quat fromAxisAngle(Vec axis, double radians) {
        double h = radians / 2;
        double s = Math.sin(h);
        return normalized(Math.cos(h), axis.x() * s, axis.y() * s, axis.z() * s);
    }

    /**
     * effects: returns Blockbench's rotation: about X by {@code rx}, then about
     * Y by {@code ry}, then about Z by {@code rz}, degrees, right-handed --
     * the order a cube's and a group's rotation is applied in a project
     */
    public static Quat fromEulerXYZDegrees(double rx, double ry, double rz) {
        Quat qx = fromAxisAngle(Vec.X, Math.toRadians(rx));
        Quat qy = fromAxisAngle(Vec.Y, Math.toRadians(ry));
        Quat qz = fromAxisAngle(Vec.Z, Math.toRadians(rz));
        return qz.times(qy).times(qx);
    }

    /** effects: returns the rotation that is {@code o}, then this */
    public Quat times(Quat o) {
        return normalized(
                w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }

    /** effects: returns the inverse rotation */
    public Quat conjugate() {
        return new Quat(w, -x, -y, -z);
    }

    /** effects: returns {@code v} turned by this rotation */
    public Vec rotate(Vec v) {
        // v' = v + 2w (q x v) + 2 q x (q x v), with q the vector part.
        double cx = y * v.z() - z * v.y();
        double cy = z * v.x() - x * v.z();
        double cz = x * v.y() - y * v.x();
        double ccx = y * cz - z * cy;
        double ccy = z * cx - x * cz;
        double ccz = x * cy - y * cx;
        return new Vec(v.x() + 2 * (w * cx + ccx), v.y() + 2 * (w * cy + ccy), v.z() + 2 * (w * cz + ccz));
    }

    /**
     * requires: 0 <= t <= 1
     * effects: returns the rotation {@code t} of the way from this to
     * {@code o} along the shortest arc; this at 0, {@code o} at 1
     */
    public Quat slerp(Quat o, double t) {
        double dot = w * o.w + x * o.x + y * o.y + z * o.z;
        double sign = dot < 0 ? -1 : 1;   // take the short way round
        dot = Math.abs(dot);
        if (dot > 0.9995) {
            return normalized(w + (sign * o.w - w) * t, x + (sign * o.x - x) * t, y + (sign * o.y - y) * t, z + (sign * o.z - z) * t);
        }
        double theta = Math.acos(dot);
        double a = Math.sin((1 - t) * theta) / Math.sin(theta);
        double b = sign * Math.sin(t * theta) / Math.sin(theta);
        return normalized(a * w + b * o.w, a * x + b * o.x, a * y + b * o.y, a * z + b * o.z);
    }

    /** effects: returns whether this and {@code o} are the same rotation within {@code eps} per component (q and -q alike) */
    public boolean near(Quat o, double eps) {
        boolean same = Math.abs(w - o.w) <= eps && Math.abs(x - o.x) <= eps && Math.abs(y - o.y) <= eps && Math.abs(z - o.z) <= eps;
        boolean flipped = Math.abs(w + o.w) <= eps && Math.abs(x + o.x) <= eps && Math.abs(y + o.y) <= eps && Math.abs(z + o.z) <= eps;
        return same || flipped;
    }
}
