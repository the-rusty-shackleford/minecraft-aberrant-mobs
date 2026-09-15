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
 * The skitter: how each leg swings and lifts as the body travels. A
 * metachronal gait -- each pair a little behind the pair before it, the
 * right leg of a pair half a cycle from the left -- driven by distance
 * travelled, so legs step faster the faster the body goes and stand still
 * when it stops, eased in by speed so a start does not snap. Immutable
 * parameters.
 *
 * <p>RI: {@code strideDeg}, {@code liftDeg} not negative; {@code cycleBlocks}
 *     and {@code speedRef} positive; {@code waveRad} finite.
 * AF: "a leg swings {@code strideDeg} either way and lifts {@code liftDeg}
 *     once per {@code cycleBlocks} of travel, pair i lagging pair 0 by
 *     {@code i * waveRad}, at full stride from {@code speedRef} up".
 */
public record LegGait(double strideDeg, double liftDeg, double cycleBlocks, double waveRad, double speedRef) {
    /** The Face-Stealer's: twenty-two degrees of swing, twelve of lift, a step every 1.6 blocks, the pairs a fifth of a turn apart. */
    public static final LegGait FACE_STEALER = new LegGait(22.0, 12.0, 1.6, 0.35 * 2 * Math.PI, 0.3);

    /** One leg: its swing about the coxa (degrees, forward positive) and its lift (degrees, up positive). */
    public record LegPose(double swingDeg, double liftDeg) {}

    public LegGait {
        if (strideDeg < 0 || liftDeg < 0 || !(cycleBlocks > 0) || !(speedRef > 0) || !Double.isFinite(waveRad)) {
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
