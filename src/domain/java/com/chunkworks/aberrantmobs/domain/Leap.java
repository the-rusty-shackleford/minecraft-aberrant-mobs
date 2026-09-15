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


import java.util.Optional;

/**
 * A pounce's flight: a launch velocity that lands the head on a target,
 * moving or still, under the game's gravity as the body integrates it
 * (position, then velocity, each tick), within a top speed; and the arc
 * that velocity makes. Pure.
 */
public final class Leap {
    private Leap() {}

    /** The game's fall, blocks a tick each tick. */
    public static final double GRAVITY = 0.08;
    /** The longest flight considered, ticks. */
    public static final int MAX_TICKS = 80;

    /**
     * requires: {@code speed > 0}, {@code gravity >= 0}
     * effects: returns the launch velocity (blocks a tick) that lands a
     * head leaving {@code from} exactly on {@code to} as it will be after
     * the flight (moving at {@code targetVelocity}), taking the fewest
     * ticks whose velocity is within {@code speed}; nothing when no flight
     * of up to {@link #MAX_TICKS} ticks is
     */
    public static Optional<Vec> velocity(Vec from, Vec to, Vec targetVelocity, double speed, double gravity) {
        if (!(speed > 0) || !(gravity >= 0)) {
            throw new IllegalArgumentException("a speed and a gravity");
        }
        for (int t = 1; t <= MAX_TICKS; t++) {
            Vec aim = to.plus(targetVelocity.times(t));
            Vec d = aim.minus(from);
            Vec v = d.plus(new Vec(0, gravity * t * (t - 1) / 2.0, 0)).times(1.0 / t);
            if (v.length() <= speed) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    /**
     * requires: {@code tick >= 0}
     * effects: returns where a head launched from {@code from} at
     * {@code velocity} is after {@code tick} ticks of moving then falling
     * by {@code gravity}: {@code from + v t - g t (t - 1) / 2} up
     */
    public static Vec at(Vec from, Vec velocity, double gravity, int tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("a tick");
        }
        return from.plus(velocity.times(tick)).minus(new Vec(0, gravity * tick * (tick - 1) / 2.0, 0));
    }

    /** effects: returns {@code velocity} after one tick of {@code gravity} */
    public static Vec fallen(Vec velocity, double gravity) {
        return velocity.minus(new Vec(0, gravity, 0));
    }
}
