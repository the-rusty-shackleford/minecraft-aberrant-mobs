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
import java.util.Set;

/**
 * What a creature knows of the world this tick: numbers, flags and points
 * by name, from a fixed vocabulary so a tree cannot name a sense that is
 * never read. An absent number is NaN, so any comparison on it is false;
 * an absent flag is false; an absent point is null. Immutable.
 *
 * <p>RI: every name is in its kind's vocabulary; every number is finite or
 *     NaN; no value is null.
 * AF: AF(numbers, flags, points) = "the creature senses each named number,
 *     flag and point as given, and nothing else".
 */
public final class Senses {
    /** The numbers a tree may compare. */
    public static final Set<String> NUMBERS = Set.of(
            "target.distance", "health", "y", "light", "heard.error", "heard.age", "heard.distance", "random", "speed");
    /** The flags a tree may test. */
    public static final Set<String> FLAGS = Set.of(
            "target.seen", "target.known", "target.in_sight", "target.eye_contact", "target.underground", "target.blessed",
            "hurt", "hurt_hard", "on_wall", "underground", "grab.held", "grab.survived", "heard.any", "heard.loud", "blocked", "airborne");
    /** The points a verb may be sent to. */
    public static final Set<String> POINTS = Set.of("target.pos", "target.last_pos", "target.look", "heard.bearing", "home");
    /** A timer's value, from the memory, is read as a number under this prefix. */
    public static final String TIMER = "timer.";

    public static final Senses NONE = new Senses(Map.of(), Map.of(), Map.of());

    private final Map<String, Double> numbers;
    private final Map<String, Boolean> flags;
    private final Map<String, Vec> points;

    private Senses(Map<String, Double> numbers, Map<String, Boolean> flags, Map<String, Vec> points) {
        this.numbers = Map.copyOf(numbers);
        this.flags = Map.copyOf(flags);
        this.points = Map.copyOf(points);
        for (Map.Entry<String, Double> e : this.numbers.entrySet()) {
            if (!NUMBERS.contains(e.getKey()) || Double.isInfinite(e.getValue())) {
                throw new IllegalArgumentException("not a number sense: " + e.getKey() + " = " + e.getValue());
            }
        }
        for (String f : this.flags.keySet()) {
            if (!FLAGS.contains(f)) {
                throw new IllegalArgumentException("not a flag sense: " + f);
            }
        }
        for (String p : this.points.keySet()) {
            if (!POINTS.contains(p)) {
                throw new IllegalArgumentException("not a point sense: " + p);
            }
        }
    }

    /** effects: returns the number named, NaN when the creature has no such sense this tick */
    public double number(String name) {
        return numbers.getOrDefault(name, Double.NaN);
    }

    /** effects: returns the flag named, false when absent */
    public boolean flag(String name) {
        return flags.getOrDefault(name, false);
    }

    /** effects: returns the point named, or null when absent */
    public Vec point(String name) {
        return points.get(name);
    }

    /** effects: returns whether {@code name} is a sense a condition may read: a number, a flag, or a timer */
    public static boolean readable(String name) {
        return NUMBERS.contains(name) || FLAGS.contains(name) || name.startsWith(TIMER) && name.length() > TIMER.length();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Gathers a tick's senses; refuses a name outside the vocabulary at once. */
    public static final class Builder {
        private final Map<String, Double> numbers = new HashMap<>();
        private final Map<String, Boolean> flags = new HashMap<>();
        private final Map<String, Vec> points = new HashMap<>();

        public Builder number(String name, double value) {
            if (!NUMBERS.contains(name)) {
                throw new IllegalArgumentException("not a number sense: " + name);
            }
            numbers.put(name, value);
            return this;
        }

        public Builder flag(String name, boolean value) {
            if (!FLAGS.contains(name)) {
                throw new IllegalArgumentException("not a flag sense: " + name);
            }
            flags.put(name, value);
            return this;
        }

        public Builder point(String name, Vec value) {
            if (!POINTS.contains(name)) {
                throw new IllegalArgumentException("not a point sense: " + name);
            }
            if (value != null) {
                points.put(name, value);
            }
            return this;
        }

        public Senses build() {
            return new Senses(numbers, flags, points);
        }
    }
}
