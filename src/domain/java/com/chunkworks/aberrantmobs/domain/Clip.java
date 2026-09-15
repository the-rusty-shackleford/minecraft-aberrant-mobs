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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An authored animation: for each named bone, a few keyed turns about its
 * pivot and offsets along its own axes (Blockbench's units) over the
 * clip's ticks, interpolated between keys; and cues -- a sound, a strike
 * -- at their ticks. Laid over whatever pose the body has of its own, a
 * clip moves only the bones it names, so the coil rears and lifts the
 * front while the legs keep their planting. Immutable; built with the
 * {@link Builder}.
 *
 * <p>RI: {@code ticks > 0}; every track's keys have strictly increasing
 *     ticks within [0, ticks]; every cue's tick is within [0, ticks].
 * AF: AF(name, ticks, tracks, cues) = "the animation {@code name}, lasting
 *     {@code ticks} ticks, that at tick t turns bone b by the slerp and
 *     moves it by the lerp of its two keys about t (its first key before
 *     its first tick, its last key after its last) and sounds each cue
 *     once at its tick".
 */
public final class Clip {
    /** A key: at {@code tick}, the bone stands turned by {@code turn} about its pivot and moved by {@code offset} along its own axes, model units. */
    public record Key(int tick, Quat turn, Vec offset) {}

    /** A cue at a tick: its name is for whoever listens (a sound, the strike that removes blocks). */
    public record Cue(int tick, String name) {}

    private final String name;
    private final int ticks;
    private final Map<String, List<Key>> tracks;
    private final List<Cue> cues;

    private Clip(String name, int ticks, Map<String, List<Key>> tracks, List<Cue> cues) {
        this.name = name;
        this.ticks = ticks;
        Map<String, List<Key>> copy = new HashMap<>();
        tracks.forEach((bone, keys) -> copy.put(bone, List.copyOf(keys)));
        this.tracks = Map.copyOf(copy);
        this.cues = List.copyOf(cues);
        checkRep();
    }

    private void checkRep() {
        if (ticks <= 0) {
            throw new IllegalArgumentException("a clip lasts: " + ticks);
        }
        for (Map.Entry<String, List<Key>> e : tracks.entrySet()) {
            int last = -1;
            for (Key k : e.getValue()) {
                if (k.tick() <= last || k.tick() > ticks) {
                    throw new IllegalArgumentException("keys of " + e.getKey() + " run in order within the clip: " + k.tick());
                }
                last = k.tick();
            }
        }
        for (Cue c : cues) {
            if (c.tick() < 0 || c.tick() > ticks) {
                throw new IllegalArgumentException("a cue within the clip: " + c);
            }
        }
    }

    public String name() {
        return name;
    }

    public int ticks() {
        return ticks;
    }

    /** effects: returns the names of the bones this clip turns */
    public java.util.Set<String> bones() {
        return tracks.keySet();
    }

    /**
     * requires: {@code 0 <= t <= ticks}
     * effects: returns the turn of bone {@code bone} at time {@code t}
     * (ticks, fractional between ticks): the slerp between the keys about
     * {@code t}, the first key before it, the last after it; the identity
     * for a bone the clip does not name
     */
    public Quat turn(String bone, double t) {
        Key[] about = about(bone, t);
        if (about == null) {
            return Quat.IDENTITY;
        }
        return about[0].turn().slerp(about[1].turn(), fraction(about, t));
    }

    /**
     * requires: {@code 0 <= t <= ticks}
     * effects: returns the offset of bone {@code bone} at time {@code t}
     * along its own axes, model units: the lerp between the keys about
     * {@code t}, the first key before it, the last after it; zero for a
     * bone the clip does not name
     */
    public Vec offset(String bone, double t) {
        Key[] about = about(bone, t);
        if (about == null) {
            return Vec.ZERO;
        }
        Vec a = about[0].offset(), b = about[1].offset();
        return a.plus(b.minus(a).times(fraction(about, t)));
    }

    /** effects: returns the keys before and after {@code t} (the same key at either end), or null for a bone not named */
    private Key[] about(String bone, double t) {
        List<Key> keys = tracks.get(bone);
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        if (t <= keys.get(0).tick()) {
            return new Key[] {keys.get(0), keys.get(0)};
        }
        for (int i = 1; i < keys.size(); i++) {
            if (t <= keys.get(i).tick()) {
                return new Key[] {keys.get(i - 1), keys.get(i)};
            }
        }
        Key last = keys.get(keys.size() - 1);
        return new Key[] {last, last};
    }

    /** effects: returns how far {@code t} lies from the first key toward the second, 0 when they are one */
    private static double fraction(Key[] about, double t) {
        return about[0] == about[1] ? 0.0 : (t - about[0].tick()) / (double) (about[1].tick() - about[0].tick());
    }

    /** effects: returns the cues whose tick is {@code tick} exactly */
    public List<String> cuesAt(int tick) {
        List<String> out = new ArrayList<>();
        for (Cue c : cues) {
            if (c.tick() == tick) {
                out.add(c.name());
            }
        }
        return out;
    }

    public static Builder builder(String name, int ticks) {
        return new Builder(name, ticks);
    }

    /** Builds a clip one key at a time; keys of a bone must be added in order of tick. */
    public static final class Builder {
        private final String name;
        private final int ticks;
        private final Map<String, List<Key>> tracks = new HashMap<>();
        private final List<Cue> cues = new ArrayList<>();

        private Builder(String name, int ticks) {
            this.name = name;
            this.ticks = ticks;
        }

        /** effects: keys bone {@code bone} at {@code tick} to Blockbench's X, Y, Z degrees, unmoved */
        public Builder key(String bone, int tick, double rx, double ry, double rz) {
            return key(bone, tick, rx, ry, rz, 0.0, 0.0, 0.0);
        }

        /** effects: keys bone {@code bone} at {@code tick} to Blockbench's X, Y, Z degrees, moved by {@code (ox, oy, oz)} along its own axes, model units */
        public Builder key(String bone, int tick, double rx, double ry, double rz, double ox, double oy, double oz) {
            tracks.computeIfAbsent(bone, b -> new ArrayList<>()).add(new Key(tick, Quat.fromEulerXYZDegrees(rx, ry, rz), new Vec(ox, oy, oz)));
            return this;
        }

        /** effects: keys several bones alike at {@code tick}, unmoved */
        public Builder keys(int tick, double rx, double ry, double rz, String... bones) {
            for (String b : bones) {
                key(b, tick, rx, ry, rz);
            }
            return this;
        }

        public Builder cue(int tick, String cue) {
            cues.add(new Cue(tick, cue));
            return this;
        }

        public Clip build() {
            return new Clip(name, ticks, tracks, cues);
        }
    }
}
