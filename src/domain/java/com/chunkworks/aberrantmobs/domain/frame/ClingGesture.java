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

/**
 * Immutable history of the deliberate attach/detach gesture.
 * AF: held is consecutive Jump samples, cooldown is remaining input ticks,
 * waiting requires a key release, detach is an unconsumed release request.
 * RI: held in [0,HOLD_TICKS], cooldown in [0,GUARD_TICKS].
 */
public record ClingGesture(boolean jump, int held, int cooldown, boolean waiting, boolean detach) {
    public static final int HOLD_TICKS = 4;
    public static final int GUARD_TICKS = 8;
    public static final ClingGesture IDLE = new ClingGesture(false, 0, 0, false, false);

    /** requires: bounded counters; effects: captures history; throws: IllegalArgumentException for invalid counters. */
    public ClingGesture {
        if (held < 0 || held > HOLD_TICKS || cooldown < 0 || cooldown > GUARD_TICKS)
            throw new IllegalArgumentException("invalid gesture counters");
    }

    /** effects: advances one manual input sample; a new press while attached requests release. */
    public ClingGesture next(boolean down, boolean attached) {
        boolean release = detach || (attached && down && !jump && !waiting);
        boolean wait = waiting && down;
        int guard = Math.max(0, cooldown - 1);
        int hold = down && !wait && guard == 0 ? Math.min(HOLD_TICKS, held + 1) : 0;
        return new ClingGesture(down, hold, guard, wait, release);
    }

    /** effects: whether a sustained fresh gesture permits entry; world geometry still decides attachment. */
    public boolean ready() { return held == HOLD_TICKS && cooldown == 0 && !waiting && !detach; }

    /** effects: consumes a release and guards against sticking again until time and key-release conditions both pass. */
    public ClingGesture released() { return new ClingGesture(jump, 0, GUARD_TICKS, jump, false); }
}
