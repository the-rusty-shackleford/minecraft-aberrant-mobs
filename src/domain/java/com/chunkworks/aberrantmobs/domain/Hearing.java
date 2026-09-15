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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Freakishly long ears: what a creature believes about where a noise came
 * from. Every sound it hears (a position, a loudness, a tick, a source)
 * replaces what it last heard from that source; its estimate is the
 * source whose loudness over distance is greatest among sounds not yet
 * forgotten, reported with an error that grows with the distance and
 * shrinks with the loudness -- exact within {@link #SURE_RANGE}, at the
 * far end no more than a bearing: the reported point is the true one
 * moved sideways (never past it, never back) by the error along a
 * direction drawn from the seed and re-drawn every {@link #RE_ROLL} ticks,
 * so a guess drifts rather than jitters. Immutable.
 *
 * <p>RI: every sound's loudness > 0; ticks >= 0.
 * AF: AF(sounds, seed) = "the creature last heard, from each source, the
 *     sound recorded, and errs by {@code seed}".
 */
public final class Hearing {
    /** Beyond this nothing is heard, blocks. */
    public static final double RANGE = 320.0;
    /** Error grows this much per block of distance, per unit of loudness. */
    public static final double ERROR_PER_BLOCK = 0.5;
    /** Within this the estimate is exact, blocks. */
    public static final double SURE_RANGE = 24.0;
    /** The error's direction is drawn afresh this often, ticks. */
    public static final int RE_ROLL = 600;
    /** A sound older than this is forgotten, ticks. */
    public static final int DECAY = 2400;
    /** A sound at least this loud within {@link #LOUD_TICKS} is a loud one. */
    public static final double LOUD = 6.0;
    public static final int LOUD_TICKS = 100;

    /** A noise: where, how loud (a step 1, a block 6, a blast 20), when, from whom. */
    public record Sound(Vec pos, double loudness, int tick, String source) {
        public Sound {
            if (pos == null || !(loudness > 0) || tick < 0 || source == null) {
                throw new IllegalArgumentException("a sound has a place, a loudness, a tick and a source");
            }
        }
    }

    /** What the ears say: where the noise seems to be, how far that may be off, how old it is, whether a loud one was recent, and the true distance. */
    public record Estimate(Vec bearing, double error, int age, boolean loud, double distance) {}

    public static final Hearing SILENT = new Hearing(Map.of(), 0L);

    private final Map<String, Sound> sounds;
    private final long seed;

    private Hearing(Map<String, Sound> sounds, long seed) {
        this.sounds = Map.copyOf(sounds);
        this.seed = seed;
    }

    /** effects: returns ears that have heard nothing, erring by {@code seed} */
    public static Hearing seeded(long seed) {
        return new Hearing(Map.of(), seed);
    }

    /**
     * effects: returns these ears having heard {@code sound} from
     * {@code listener}: unchanged when it is beyond {@link #RANGE}, else
     * replacing what was last heard from its source
     */
    public Hearing heard(Sound sound, Vec listener) {
        if (sound.pos().minus(listener).length() > RANGE) {
            return this;
        }
        Map<String, Sound> s = new HashMap<>(sounds);
        s.put(sound.source(), sound);
        return new Hearing(s, seed);
    }

    /** effects: returns these ears with every sound older than {@link #DECAY} at {@code tick} forgotten */
    public Hearing forgotten(int tick) {
        Map<String, Sound> s = new HashMap<>();
        for (Map.Entry<String, Sound> e : sounds.entrySet()) {
            if (tick - e.getValue().tick() <= DECAY) {
                s.put(e.getKey(), e.getValue());
            }
        }
        return s.size() == sounds.size() ? this : new Hearing(s, seed);
    }

    /**
     * effects: returns the estimate at {@code tick} for a listener at
     * {@code listener}: of the sounds not yet forgotten, the one loudest
     * for its distance, its bearing erred as the class says; nothing when
     * nothing is remembered
     */
    public Optional<Estimate> estimate(Vec listener, int tick) {
        Sound best = null;
        double bestScore = -1;
        boolean loud = false;
        for (Sound s : sounds.values()) {
            int age = tick - s.tick();
            if (age > DECAY) {
                continue;
            }
            if (s.loudness() >= LOUD && age <= LOUD_TICKS) {
                loud = true;
            }
            double score = s.loudness() / (1.0 + s.pos().minus(listener).length());
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(estimate(best, listener, tick, loud));
    }

    /**
     * effects: returns the estimate at {@code tick} for a listener at
     * {@code listener} of the last sound from {@code source} alone, erred
     * as the class says, its {@code loud} whether that sound was loud and
     * recent; nothing when none from that source is remembered -- how a
     * creature that has lost sight of its prey follows its footsteps
     */
    public Optional<Estimate> estimateFrom(String source, Vec listener, int tick) {
        Sound s = sounds.get(source);
        if (s == null || tick - s.tick() > DECAY) {
            return Optional.empty();
        }
        return Optional.of(estimate(s, listener, tick, s.loudness() >= LOUD && tick - s.tick() <= LOUD_TICKS));
    }

    private Estimate estimate(Sound best, Vec listener, int tick, boolean loud) {
        Vec d = best.pos().minus(listener);
        double distance = d.length();
        double error = distance <= SURE_RANGE ? 0.0 : Math.min(distance, distance * ERROR_PER_BLOCK / best.loudness());
        Vec bearing = best.pos();
        if (error > 0 && distance > 1e-9) {
            bearing = best.pos().plus(sideways(d.times(1.0 / distance), tick / RE_ROLL).times(error));
        }
        return new Estimate(bearing, error, tick - best.tick(), loud, distance);
    }

    /** effects: returns a unit vector perpendicular to {@code dir}, turned about it by an angle drawn from the seed and {@code epoch} */
    private Vec sideways(Vec dir, int epoch) {
        Vec any = Math.abs(dir.y()) < 0.9 ? Vec.Y : Vec.X;
        Vec u = any.cross(dir).normalized();
        Vec v = dir.cross(u);
        double angle = 2 * Math.PI * unit(seed ^ (epoch * 0x9E3779B97F4A7C15L));
        return u.times(Math.cos(angle)).plus(v.times(Math.sin(angle)));
    }

    /** effects: returns a number in [0, 1) mixed from {@code x} */
    private static double unit(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    /** effects: returns how many sources are remembered */
    public int remembered() {
        return sounds.size();
    }
}
