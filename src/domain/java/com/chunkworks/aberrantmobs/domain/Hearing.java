/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable sound memory and uncertain localisation. AF: each source has a last heard location,
 * accumulated confidence and the time of its last loud noise; seed chooses a stable sideways
 * error. RI: finite positive loudness, nonnegative sound ticks, confidence in [0,1], one bounded
 * record per source. No player position is read independently of a sound.
 *
 * A single block break at 512 blocks has roughly 256 blocks of lateral error, around 47 at 256,
 * and none within 64. Repeated nearby sounds from the same source slowly halve that uncertainty;
 * approaching improves it further. Silence dissipates confidence and eventually the memory.
 * Error stays horizontal so it cannot invent a destination outside the underground depth band.
 */
public final class Hearing {
    public static final double RANGE = 512.0;
    public static final double SURE_RANGE = 64.0;
    public static final int DECAY = 2400;
    public static final double LOUD = 6.0;
    public static final int LOUD_TICKS = 100;
    private static final double FAR_ERROR = 256.0;
    private static final double CONFIDENCE_PER_SECOND = 0.02;
    private static final int CONFIDENCE_GRACE = 80;
    private static final int CONFIDENCE_FADE = 1200;
    private static final double RELOCATED = 64.0;
    private static final int TICKS_PER_SECOND = 20;

    /** AF: a source made a noise at pos on tick. RI: finite positive loudness and nonnegative tick. */
    public record Sound(Vec pos, double loudness, int tick, String source) {
        /** requires: nothing; effects: creates a sound value; throws: IllegalArgumentException for invalid fields. */
        public Sound {
            if (pos == null || !Double.isFinite(loudness) || loudness <= 0 || tick < 0 || source == null)
                throw new IllegalArgumentException("a sound has a place, finite positive loudness, a tick and a source");
        }
    }

    /**
     * AF: apparent source location, its uncertainty, age, recent loudness and distance to the last
     * actual sound. RI: finite nonnegative error/distance, nonnegative age, non-null bearing.
     */
    public record Estimate(Vec bearing, double error, int age, boolean loud, double distance) {}

    /** AF: last noise and accumulated localisation evidence. RI: confidence in [0,1]. */
    private record Track(Sound sound, double confidence, int loudTick) {}

    public static final Hearing SILENT = new Hearing(Map.of(), 0L);
    private final Map<String, Track> sounds;
    private final long seed;

    private Hearing(Map<String, Track> sounds, long seed) {
        this.sounds = Map.copyOf(sounds);
        this.seed = seed;
    }

    /** requires: nothing; effects: returns empty sound memory with the given bearing seed; throws: nothing. */
    public static Hearing seeded(long seed) {
        return new Hearing(Map.of(), seed);
    }

    /**
     * requires: non-null sound/listener; effects: records an in-range sound, gradually improving
     * confidence for continuing nearby noise; ignores out-of-order and out-of-range sounds.
     * Duplicate-tick events cannot increase confidence. throws: nothing.
     */
    public Hearing heard(Sound sound, Vec listener) {
        if (sound.pos().minus(listener).length() > RANGE) return this;
        Track previous = sounds.get(sound.source());
        if (previous != null && sound.tick() < previous.sound().tick()) return this;
        double confidence = 0;
        int loudTick = -LOUD_TICKS - 1;
        if (previous != null && sound.tick() - previous.sound().tick() <= DECAY
                && sound.pos().minus(previous.sound().pos()).length() <= RELOCATED) {
            int elapsed = sound.tick() - previous.sound().tick();
            double evidence = CONFIDENCE_PER_SECOND * Math.min(1.0, sound.loudness() / LOUD)
                    * Math.min(elapsed, TICKS_PER_SECOND) / TICKS_PER_SECOND;
            confidence = Math.min(1.0, confidence(previous, sound.tick()) + evidence);
            loudTick = previous.loudTick();
        }
        if (sound.loudness() >= LOUD) loudTick = sound.tick();
        Map<String, Track> updated = new HashMap<>(sounds);
        updated.put(sound.source(), new Track(sound, confidence, loudTick));
        return new Hearing(updated, seed);
    }

    /** requires: nonnegative tick; effects: forgets expired sound records; throws: nothing. */
    public Hearing forgotten(int tick) {
        Map<String, Track> retained = new HashMap<>();
        for (var entry : sounds.entrySet())
            if (tick - entry.getValue().sound().tick() <= DECAY) retained.put(entry.getKey(), entry.getValue());
        return retained.size() == sounds.size() ? this : new Hearing(retained, seed);
    }

    /**
     * requires: listener and nonnegative tick; effects: estimates the strongest remembered sound
     * for its distance, using only sound locations and accumulated evidence; throws: nothing.
     */
    public Optional<Estimate> estimate(Vec listener, int tick) {
        Track best = null;
        double bestScore = -1;
        boolean loud = false;
        for (Track track : sounds.values()) {
            Sound sound = track.sound();
            int age = tick - sound.tick();
            if (age < 0 || age > DECAY) continue;
            if (tick - track.loudTick() <= LOUD_TICKS) loud = true;
            double score = sound.loudness() / (1.0 + sound.pos().minus(listener).length());
            if (score > bestScore) { best = track; bestScore = score; }
        }
        return best == null ? Optional.empty() : Optional.of(estimate(best, listener, tick, loud));
    }

    /**
     * requires: source, listener and nonnegative tick; effects: estimates only this source's last
     * heard position, or nothing for an absent/expired sound; throws: nothing.
     */
    public Optional<Estimate> estimateFrom(String source, Vec listener, int tick) {
        Track track = sounds.get(source);
        if (track == null || tick < track.sound().tick() || tick - track.sound().tick() > DECAY)
            return Optional.empty();
        return Optional.of(estimate(track, listener, tick, tick - track.loudTick() <= LOUD_TICKS));
    }

    private static double confidence(Track track, int tick) {
        double lost = Math.max(0, tick - track.sound().tick() - CONFIDENCE_GRACE) / (double) CONFIDENCE_FADE;
        return Math.max(0, track.confidence() - lost);
    }

    private Estimate estimate(Track track, Vec listener, int tick, boolean loud) {
        Sound sound = track.sound();
        Vec delta = sound.pos().minus(listener);
        double distance = delta.length();
        double progress = Math.clamp((distance - SURE_RANGE) / (RANGE - SURE_RANGE), 0, 1);
        double loudness = Math.pow(Math.clamp(sound.loudness(), 1, 20) / LOUD, -0.15);
        double error = Math.min(distance * .75, FAR_ERROR * progress * progress * loudness)
                * (1 - .5 * confidence(track, tick));
        Vec bearing = sound.pos();
        if (error > 0) {
            double horizontal = Math.hypot(delta.x(), delta.z());
            Vec sideways = horizontal > 1e-9 ? new Vec(-delta.z() / horizontal, 0, delta.x() / horizontal) : Vec.X;
            double sign = unit(seed ^ (sound.source().hashCode() * 0x9E3779B97F4A7C15L)) < .5 ? -1 : 1;
            bearing = bearing.plus(sideways.times(sign * error));
        }
        return new Estimate(bearing, error, tick - sound.tick(), loud, distance);
    }

    private static double unit(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    /** requires: nothing; effects: returns the number of remembered sources; throws: nothing. */
    public int remembered() { return sounds.size(); }
}
