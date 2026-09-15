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

import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Vec;

/**
 * A wearer's local axes in world coordinates: up is against the gravity,
 * forward and right are two world axes chosen so that a wearer standing
 * on the floor has the world's own frame (right +X, up +Y, forward +Z)
 * and a wearer on a wall keeps as much of that as the wall allows. Local
 * coordinates are (right, up, forward); a wearer's motion, look and box
 * are reckoned in them and turned to the world here. Immutable.
 *
 * <p>RI: right, up, forward are unit, mutually perpendicular, right-handed
 *     ({@code right x up = forward}), and {@code up = -gravity.dir}.
 * AF: AF(gravity, right, up, forward) = "the wearer falls along gravity,
 *     stands along up, faces along forward at zero yaw, with right to its
 *     right".
 */
public record Frame(Gravity gravity, Vec right, Vec up, Vec forward) {
    /** The world's own frame: the one every non-wearer has. */
    public static final Frame WORLD = new Frame(Gravity.DOWN, Vec.X, Vec.Y, Vec.Z);

    public Frame {
        if (!near(right.length(), 1) || !near(up.length(), 1) || !near(forward.length(), 1)
                || Math.abs(right.dot(up)) > 1e-9 || Math.abs(up.dot(forward)) > 1e-9 || Math.abs(right.dot(forward)) > 1e-9
                || !right.cross(up).near(forward, 1e-9) || !up.near(gravity.dir.times(-1), 1e-9)) {
            throw new IllegalArgumentException("a frame is a right-handed orthonormal basis with up against its gravity");
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    /**
     * effects: returns the frame of {@code gravity}: up against it; for the
     * world's down the world's axes; on a wall the forward is the world's
     * up (a wearer that walked into the wall keeps going the way it went,
     * which is now up the wall) and right is what makes the frame
     * right-handed; under the ceiling forward stays the world's +Z and
     * right is the world's -X
     */
    public static Frame of(Gravity gravity) {
        return switch (gravity) {
            case DOWN -> WORLD;
            case UP -> new Frame(gravity, new Vec(-1, 0, 0), Vec.Y.times(-1), Vec.Z);
            case WEST -> new Frame(gravity, new Vec(0, 0, 1), Vec.X, Vec.Y);
            case EAST -> new Frame(gravity, new Vec(0, 0, -1), Vec.X.times(-1), Vec.Y);
            case NORTH -> new Frame(gravity, new Vec(-1, 0, 0), Vec.Z, Vec.Y);
            case SOUTH -> new Frame(gravity, new Vec(1, 0, 0), Vec.Z.times(-1), Vec.Y);
        };
    }

    /** effects: returns local {@code (x, y, z)} = {@code (right, up, forward)} components as a world vector */
    public Vec toWorld(Vec local) {
        return right.times(local.x()).plus(up.times(local.y())).plus(forward.times(local.z()));
    }

    /** effects: returns {@code world} as local components */
    public Vec toLocal(Vec world) {
        return new Vec(world.dot(right), world.dot(up), world.dot(forward));
    }

    /**
     * effects: returns the box of a wearer whose feet point is {@code feet},
     * {@code width} across and {@code height} tall along up: the face on
     * the gravity side contains the feet; for the world's down this is the
     * game's own box
     */
    public Box box(Vec feet, double width, double height) {
        double h = width / 2.0;
        Vec lo = null, hi = null;
        // Corners in local coordinates: x, z in [-h, h], y in [0, height].
        double[][] corners = {{-h, 0, -h}, {h, 0, -h}, {-h, 0, h}, {h, 0, h}, {-h, height, -h}, {h, height, -h}, {-h, height, h}, {h, height, h}};
        for (double[] c : corners) {
            Vec w = feet.plus(toWorld(new Vec(c[0], c[1], c[2])));
            lo = lo == null ? w : new Vec(Math.min(lo.x(), w.x()), Math.min(lo.y(), w.y()), Math.min(lo.z(), w.z()));
            hi = hi == null ? w : new Vec(Math.max(hi.x(), w.x()), Math.max(hi.y(), w.y()), Math.max(hi.z(), w.z()));
        }
        return new Box(lo, hi);
    }

    /**
     * effects: returns the yaw (degrees, the game's: 0 faces local forward,
     * 90 faces local -right) that faces along the world direction
     * {@code world} laid into this frame's floor; 0 when it has no part in
     * the floor
     */
    public double yawToward(Vec world) {
        Vec local = toLocal(world);
        if (local.x() * local.x() + local.z() * local.z() < 1e-12) {
            return 0.0;
        }
        return Math.toDegrees(Math.atan2(-local.x(), local.z()));
    }

    /** effects: returns the unit local floor direction the game's yaw {@code yawDeg} faces: (-sin, 0, cos) */
    public static Vec headingOf(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec(-Math.sin(r), 0.0, Math.cos(r));
    }

    /** effects: returns where the eyes are: {@code eyeHeight} up from the feet */
    public Vec eye(Vec feet, double eyeHeight) {
        return feet.plus(up.times(eyeHeight));
    }

    /** effects: returns the rotation that takes the world's axes to this frame's: X to right, Y to up, Z to forward */
    public Quat rotation() {
        return Quat.fromBasis(right, up, forward);
    }

    /** effects: returns the gravity's index in {@link Gravity#values()}, a byte for the wire */
    public byte code() {
        return (byte) gravity.ordinal();
    }

    /** effects: returns the frame coded {@code code}, the world's for a code out of range */
    public static Frame decode(int code) {
        Gravity[] all = Gravity.values();
        return code >= 0 && code < all.length ? of(all[code]) : WORLD;
    }

    /** An axis-aligned box in world coordinates. RI: lo <= hi in each axis. */
    public record Box(Vec lo, Vec hi) {
        public Box {
            if (lo.x() > hi.x() || lo.y() > hi.y() || lo.z() > hi.z()) {
                throw new IllegalArgumentException("a box's low corner is below its high one");
            }
        }

        public Vec size() {
            return hi.minus(lo);
        }

        public Box moved(Vec by) {
            return new Box(lo.plus(by), hi.plus(by));
        }

        public boolean contains(Vec p) {
            return p.x() >= lo.x() && p.x() <= hi.x() && p.y() >= lo.y() && p.y() <= hi.y() && p.z() >= lo.z() && p.z() <= hi.z();
        }
    }
}
