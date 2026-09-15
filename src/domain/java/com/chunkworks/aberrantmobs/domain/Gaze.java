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
 * Whether a target is looking at the creature, judged over the last few
 * ticks rather than the last one, so a glance that flicks across it is
 * not eye contact but a stare is. Immutable: a bit per tick of the last
 * {@link #WINDOW}, the newest lowest.
 *
 * <p>RI: only the low WINDOW bits are set.
 * AF: AF(bits) = "bit i is whether the target met the creature's eyes i
 *     ticks ago".
 */
public record Gaze(int bits) {
    /** Ticks remembered, and how many of them must meet the eyes to count. */
    public static final int WINDOW = 10;
    public static final int NEEDED = 8;
    private static final int MASK = (1 << WINDOW) - 1;

    public static final Gaze NONE = new Gaze(0);

    public Gaze {
        if ((bits & ~MASK) != 0) {
            throw new IllegalArgumentException("a gaze remembers " + WINDOW + " ticks");
        }
    }

    /** effects: returns this gaze one tick on, the newest tick meeting the eyes or not as {@code met} says */
    public Gaze noting(boolean met) {
        return new Gaze(((bits << 1) | (met ? 1 : 0)) & MASK);
    }

    /** effects: returns how many of the remembered ticks met the eyes */
    public int count() {
        return Integer.bitCount(bits);
    }

    /** effects: returns whether the target is staring: at least {@link #NEEDED} of the last {@link #WINDOW} ticks met the eyes */
    public boolean locked() {
        return count() >= NEEDED;
    }
}
