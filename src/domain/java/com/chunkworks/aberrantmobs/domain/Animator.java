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

import java.util.List;

/**
 * Plays one {@link Clip} at a time over a body's own pose: each tick it
 * advances and reports the clip's cues for that tick; each frame it lays
 * the clip's turns over the pose the body computed, composing with a
 * bone's own turn or placement so the coil rears segments the chain has
 * already placed. A clip that ends is done; a new one replaces the
 * playing one at once. Mutable, owned by one creature, never handed out;
 * everything it returns is a value.
 *
 * <p>RI: {@code tick} is within [0, playing.ticks()] while a clip plays.
 * AF: "the clip {@code playing}, {@code tick} ticks in; or nothing".
 */
public final class Animator {
    private Clip playing;
    private int tick;

    /** effects: starts {@code clip} from its first tick, dropping whatever played */
    public void play(Clip clip) {
        playing = clip;
        tick = 0;
    }

    /** effects: returns the clip playing, or null */
    public Clip playing() {
        return playing;
    }

    /** effects: returns how far into the clip the animator is, ticks; 0 when nothing plays */
    public int tick() {
        return tick;
    }

    /** effects: returns whether a clip is under way */
    public boolean busy() {
        return playing != null;
    }

    /**
     * effects: advances one tick; returns the cues of the tick just reached
     * (the first tick's cues at the first advance); the clip is dropped once
     * its last tick has been reached and reported
     */
    public List<String> advance() {
        if (playing == null) {
            return List.of();
        }
        tick++;
        List<String> cues = playing.cuesAt(tick);
        if (tick >= playing.ticks()) {
            playing = null;
            tick = 0;
        }
        return cues;
    }

    /**
     * effects: returns {@code base} with the playing clip's turns and
     * offsets laid over it at {@code partialTick} of the way from the last
     * tick to the next: a bone the clip names gets its clip turn composed
     * after its own (about its pivot, on top of its rest, its local turn
     * or its placement's rotation) and its clip offset added to its shift;
     * other bones are untouched; {@code base} itself when nothing plays
     */
    public Pose overlay(Rig rig, Pose base, double partialTick) {
        if (playing == null) {
            return base;
        }
        double t = Math.min(playing.ticks(), tick + partialTick);
        Pose out = base;
        for (String name : playing.bones()) {
            java.util.OptionalInt b = rig.bone(name);
            if (b.isEmpty()) {
                continue;
            }
            int bone = b.getAsInt();
            Quat turn = playing.turn(name, t);
            Xform placed = base.absolute(bone);
            if (placed != null) {
                out = out.withAbsolute(bone, new Xform(placed.rotation().times(turn), placed.translation()));
            } else {
                out = out.withLocal(bone, base.local(bone).times(turn));
            }
            Vec offset = playing.offset(name, t);
            if (!offset.equals(Vec.ZERO)) {
                out = out.withShift(bone, base.shift(bone).plus(offset));
            }
        }
        return out;
    }
}
