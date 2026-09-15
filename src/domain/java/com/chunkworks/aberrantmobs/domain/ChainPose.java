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
 * Where a chain of body segments stands: a position and an orientation per
 * segment, laid along a {@link Trail} at fixed distances behind the head
 * and set aside by the {@link Undulation}. Immutable; the arrays are copied
 * in and read out one value at a time.
 *
 * <p>Segment k sits at the trail's point {@code arcBack[k]} behind the head,
 * moved sideways (along the body's own left, {@code up x forward}) by the
 * wave's lateral offset there and up by its vertical ripple; it faces the
 * segment before it (the head, for the first), so consecutive segments are
 * exactly their spacing apart however the path bends, and its up is the
 * trail's up there -- the surface it clings to at its own place, not the
 * head's, so a body over an edge has segments on the floor and segments on
 * the wall at once.
 *
 * <p>RI: positions and orientations are the same length, at least one.
 * AF: AF(position, orientation) = "segment k's centre is position[k] and
 *     it faces along orientation[k] (model +Z forward, +Y up)".
 */
public final class ChainPose {
    private final Vec[] position;
    private final Quat[] orientation;

    private ChainPose(Vec[] position, Quat[] orientation) {
        if (position.length == 0 || position.length != orientation.length) {
            throw new IllegalArgumentException("a chain has a position and an orientation per segment");
        }
        this.position = position.clone();
        this.orientation = orientation.clone();
    }

    /**
     * requires: {@code arcBack} non-decreasing, its first entry 0 (the head)
     * effects: returns the chain laid along {@code trail}: segment k at
     * {@code arcBack[k]} behind the head, offset by {@code undulation}'s
     * wave {@code wave} there, facing the segment before it
     */
    public static ChainPose of(Trail trail, double[] arcBack, Undulation undulation, Undulation.Wave wave) {
        return of(trail, arcBack, 0.0, undulation, wave);
    }

    /**
     * requires: {@code arcBack} non-decreasing, its first entry 0 (the head); {@code lag >= 0}
     * effects: returns the chain as {@link #of(Trail, double[], Undulation, Undulation.Wave)}
     * lays it, but with the head {@code lag} blocks behind the trail's newest
     * sample and every segment as far behind that -- how a frame between
     * two ticks is drawn, the head where the game interpolates it
     */
    public static ChainPose of(Trail trail, double[] arcBack, double lag, Undulation undulation, Undulation.Wave wave) {
        if (arcBack.length == 0 || arcBack[0] != 0.0) {
            throw new IllegalArgumentException("the chain starts at the head, arc 0");
        }
        if (!(lag >= 0)) {
            throw new IllegalArgumentException("the lag is not negative: " + lag);
        }
        Vec[] pos = new Vec[arcBack.length];
        Vec[] ups = new Vec[arcBack.length];
        Vec[] travel = new Vec[arcBack.length];
        for (int k = 0; k < arcBack.length; k++) {
            if (k > 0 && arcBack[k] < arcBack[k - 1]) {
                throw new IllegalArgumentException("arcs run tailward: " + arcBack[k - 1] + " then " + arcBack[k]);
            }
            Trail.Sample s = trail.at(arcBack[k] + lag);
            Vec left = s.up().cross(s.forward());
            double lateral = undulation.lateralAt(wave, arcBack[k]);
            double vertical = undulation.verticalAt(wave, arcBack[k]);
            pos[k] = s.pos().plus(left.times(lateral)).plus(s.up().times(vertical));
            ups[k] = s.up();
            travel[k] = s.forward();
        }
        Quat[] orient = new Quat[arcBack.length];
        for (int k = 0; k < arcBack.length; k++) {
            Vec forward = travel[k];
            if (k > 0) {
                Vec toPrev = pos[k - 1].minus(pos[k]);
                if (toPrev.length() > 1e-6) {
                    forward = toPrev.normalized();
                }
            }
            orient[k] = Quat.lookAlong(forward, ups[k]);
        }
        return new ChainPose(pos, orient);
    }

    public int size() {
        return position.length;
    }

    public Vec position(int k) {
        return position[k];
    }

    public Quat orientation(int k) {
        return orientation[k];
    }
}
