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
 * The skitter: how each leg swings and lifts as the body travels, and how
 * its foot is planted. A metachronal gait -- each pair a little behind the
 * pair before it, the right leg of a pair half a cycle from the left --
 * driven by distance travelled, so legs step faster the faster the body
 * goes and stand still when it stops, eased in by speed so a start does
 * not snap. The planting ({@link Legs}) is measured here too, in blocks,
 * since they are the body's own measures and a bigger body takes bigger
 * steps: how far a planted foot's anchor may drift from its rest point
 * before it steps, how far under the rest point a surface is looked for,
 * how high a step lifts, and how far ahead of the rest point a step may
 * be aimed. Immutable parameters.
 *
 * <p>RI: {@code strideDeg}, {@code liftDeg} not negative; {@code cycleBlocks}
 *     and {@code speedRef} positive; {@code waveRad} finite; {@code stride}
 *     and {@code reach} positive; {@code lift} and {@code lead} not negative.
 * AF: "a leg swings {@code strideDeg} either way and lifts {@code liftDeg}
 *     once per {@code cycleBlocks} of travel, pair i lagging pair 0 by
 *     {@code i * waveRad}, at full stride from {@code speedRef} up; its
 *     foot stays planted until its anchor is {@code stride} from its rest
 *     point, finds its ground within {@code reach} under that point, rises
 *     {@code lift} mid-step and lands at most {@code lead} ahead".
 */
public record LegGait(double strideDeg, double liftDeg, double cycleBlocks, double waveRad, double speedRef,
                      double stride, double reach, double lift, double lead) {
    /**
     * The Face-Stealer's, at its size (a model unit is a sixteenth and a
     * half): twenty-two degrees of swing, twelve of lift, a step every 2.4
     * blocks, the pairs a fifth of a turn apart; a foot drifts 0.825 before
     * it steps, looks 2.4 under its rest point for ground, lifts 0.525 and
     * lands at most 1.8 ahead.
     */
    public static final LegGait FACE_STEALER = new LegGait(22.0, 12.0, 2.4, 0.35 * 2 * Math.PI, 0.3, 0.825, 2.4, 0.525, 1.8);

    /** One leg: its swing about the coxa (degrees, forward positive) and its lift (degrees, up positive). */
    public record LegPose(double swingDeg, double liftDeg) {}

    public LegGait {
        if (strideDeg < 0 || liftDeg < 0 || !(cycleBlocks > 0) || !(speedRef > 0) || !Double.isFinite(waveRad)
                || !(stride > 0) || !(reach > 0) || lift < 0 || lead < 0) {
            throw new IllegalArgumentException("bad gait parameters");
        }
    }

    /**
     * requires: {@code pairs >= 0}, {@code speed >= 0}
     * effects: returns the pose of every leg, pair by pair, left then right,
     * with the body {@code distance} blocks along its path at {@code speed}:
     * pair i's left leg at phase {@code 2 pi distance / cycleBlocks + i *
     * waveRad}, its right half a turn on; swing {@code strideDeg * sin(phase)},
     * lift {@code liftDeg * max(0, cos(phase))}, both scaled by
     * {@code min(1, speed / speedRef)}
     */
    public LegPose[] poses(int pairs, double distance, double speed) {
        if (pairs < 0 || !(speed >= 0)) {
            throw new IllegalArgumentException("pairs >= 0 and speed >= 0");
        }
        double f = Math.min(1.0, speed / speedRef);
        LegPose[] out = new LegPose[pairs * 2];
        for (int i = 0; i < pairs; i++) {
            double phase = 2 * Math.PI * distance / cycleBlocks + i * waveRad;
            out[2 * i] = at(phase, f);
            out[2 * i + 1] = at(phase + Math.PI, f);
        }
        return out;
    }

    private LegPose at(double phase, double f) {
        return new LegPose(strideDeg * Math.sin(phase) * f, liftDeg * Math.max(0.0, Math.cos(phase)) * f);
    }
}
