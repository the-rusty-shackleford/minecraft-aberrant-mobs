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

/**
 * The camera's turn from one frame to another, spread over a few ticks so
 * a change of down is a swing, not a snap. Immutable: a blend is a value
 * with a start, an end and a tick count; the current rotation is read for
 * any time.
 *
 * <p>RI: ticks > 0; from and to are rotations.
 * AF: AF(from, to, ticks, startTick) = "the camera turns from {@code from}
 *     to {@code to} between {@code startTick} and {@code startTick + ticks}".
 */
public record Blend(Quat from, Quat to, int ticks, int startTick) {
    /** A change of down takes this long, ticks. */
    public static final int TICKS = 6;

    public Blend {
        if (ticks <= 0) {
            throw new IllegalArgumentException("a blend takes time");
        }
    }

    /** effects: returns the rotation at {@code time} (ticks, fractional): {@code from} before the start, {@code to} after the end, the slerp between */
    public Quat at(double time) {
        double t = (time - startTick) / ticks;
        if (t <= 0) {
            return from;
        }
        if (t >= 1) {
            return to;
        }
        // Ease in and out, so the swing starts and ends gently.
        double eased = t * t * (3 - 2 * t);
        return from.slerp(to, eased);
    }

    /** effects: returns whether the blend is over at {@code time} */
    public boolean done(double time) {
        return time >= startTick + ticks;
    }
}
