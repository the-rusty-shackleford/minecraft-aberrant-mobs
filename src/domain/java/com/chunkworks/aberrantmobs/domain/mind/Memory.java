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
package com.chunkworks.aberrantmobs.domain.mind;

import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.HashMap;
import java.util.Map;

/**
 * What a creature carries from tick to tick: the mode it is in, its
 * timers (ticks left, counting down to zero and staying there), the
 * points it remembers, and a seed for whatever it must decide at random
 * and stick to. Immutable; saved and restored whole, so a reload does not
 * forget a hunt.
 *
 * <p>RI: mode non-null; every timer is >= 0.
 * AF: AF(mode, timers, points, seed) = "the creature is in {@code mode},
 *     with timers[t] ticks left on timer t (a timer not in the map has
 *     never been set, one at zero has run out), remembering points, seeded
 *     by {@code seed}".
 */
public record Memory(String mode, Map<String, Integer> timers, Map<String, Vec> points, long seed) {
    public Memory {
        if (mode == null) {
            throw new IllegalArgumentException("a memory has a mode");
        }
        timers = Map.copyOf(timers);
        points = Map.copyOf(points);
        for (Map.Entry<String, Integer> e : timers.entrySet()) {
            if (e.getValue() < 0) {
                throw new IllegalArgumentException("a timer is not negative: " + e.getKey());
            }
        }
    }

    /** effects: returns a fresh memory in {@code mode} with {@code seed} */
    public static Memory fresh(String mode, long seed) {
        return new Memory(mode, Map.of(), Map.of(), seed);
    }

    /** effects: returns this memory one tick on: every running timer one less, a timer at zero staying */
    public Memory aged() {
        if (timers.isEmpty()) {
            return this;
        }
        Map<String, Integer> t = new HashMap<>(timers);
        t.replaceAll((k, v) -> Math.max(0, v - 1));
        return new Memory(mode, t, points, seed);
    }

    /** effects: returns the ticks left on {@code name}, 0 when it has run out or was never set */
    public int timer(String name) {
        return timers.getOrDefault(name, 0);
    }

    /** effects: returns whether {@code name} has been set at some time and not since cleared */
    public boolean hasTimer(String name) {
        return timers.containsKey(name);
    }

    public Memory withMode(String newMode) {
        return new Memory(newMode, timers, points, seed);
    }

    /** requires: {@code ticks >= 0}; effects: returns this memory with {@code name} set to {@code ticks} */
    public Memory withTimer(String name, int ticks) {
        Map<String, Integer> t = new HashMap<>(timers);
        t.put(name, ticks);
        return new Memory(mode, t, points, seed);
    }

    /** effects: returns this memory with {@code name} never set */
    public Memory withoutTimer(String name) {
        if (!timers.containsKey(name)) {
            return this;
        }
        Map<String, Integer> t = new HashMap<>(timers);
        t.remove(name);
        return new Memory(mode, t, points, seed);
    }

    public Memory withPoint(String name, Vec point) {
        Map<String, Vec> p = new HashMap<>(points);
        if (point == null) {
            p.remove(name);
        } else {
            p.put(name, point);
        }
        return new Memory(mode, timers, p, seed);
    }

    /** effects: returns the remembered point, or null */
    public Vec point(String name) {
        return points.get(name);
    }
}
