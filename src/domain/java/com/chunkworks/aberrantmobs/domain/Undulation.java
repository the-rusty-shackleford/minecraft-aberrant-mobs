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
 * The writhe: a wave that travels down the body from the head, as a snake's
 * does, so the creature undulates even going straight, and keeps writhing,
 * slowly and slightly, when it stands still. The wave is a state
 * ({@link Wave}: its phase and its present amplitude) advanced once a tick
 * by the head's speed; the lateral offset of any point of the body is read
 * from it by how far back along the body the point is. Applied once, to
 * the head's own step, the trail then carries it down the body; the rig
 * reads it again only for what the trail cannot carry.
 *
 * <p>Immutable parameters; RI: amplitudes not negative, {@code wavelength},
 * {@code speedRef} and {@code restSpeed} positive, {@code verticalRatio}
 * not negative.
 * AF: "a wave of length {@code wavelength} whose amplitude eases between
 *     {@code amplitudeRest} standing still and {@code amplitudeMoving} at
 *     {@code speedRef} and above, its phase advancing with distance
 *     travelled and never slower than {@code restSpeed} a tick, with a
 *     vertical ripple {@code verticalRatio} of the lateral at twice the
 *     frequency".
 */
public record Undulation(double amplitudeMoving, double amplitudeRest, double wavelength, double speedRef, double restSpeed, double verticalRatio) {
    /** The Face-Stealer's: a third of a block of weave at speed, a tenth at rest, five and a half blocks a wave, a ten-second idle. */
    public static final Undulation FACE_STEALER = new Undulation(0.35, 0.10, 5.5, 0.35, 0.025, 0.15);

    /** How much of the gap to the target amplitude closes each tick. */
    private static final double EASE = 0.1;

    public Undulation {
        if (amplitudeMoving < 0 || amplitudeRest < 0 || !(wavelength > 0) || !(speedRef > 0) || !(restSpeed > 0) || verticalRatio < 0) {
            throw new IllegalArgumentException("bad undulation parameters");
        }
    }

    /**
     * The wave's state. RI: {@code 0 <= phase < 2 pi}, {@code amplitude >= 0}.
     * AF: "the head's point of the wave is at {@code phase}, and the wave is {@code amplitude} blocks high".
     */
    public record Wave(double phase, double amplitude) {
        public Wave {
            if (!(phase >= 0 && phase < 2 * Math.PI) || amplitude < 0 || !Double.isFinite(amplitude)) {
                throw new IllegalArgumentException("phase in [0, 2 pi), amplitude >= 0: " + phase + ", " + amplitude);
            }
        }
    }

    /** effects: returns a wave at rest: phase 0, the resting amplitude */
    public Wave rest() {
        return new Wave(0.0, amplitudeRest);
    }

    /** effects: returns the amplitude the wave settles to at {@code speed} */
    public double amplitudeAt(double speed) {
        return amplitudeRest + (amplitudeMoving - amplitudeRest) * Math.min(1.0, Math.max(0.0, speed) / speedRef);
    }

    /**
     * requires: {@code speed >= 0}
     * effects: returns {@code w} one tick on at {@code speed}: the phase
     * advanced by the distance travelled (never less than
     * {@code restSpeed}), the amplitude eased a tenth of the way toward
     * {@link #amplitudeAt}
     */
    public Wave advance(Wave w, double speed) {
        if (!(speed >= 0)) {
            throw new IllegalArgumentException("speed is not negative: " + speed);
        }
        double phase = w.phase() + 2 * Math.PI * Math.max(speed, restSpeed) / wavelength;
        phase = phase % (2 * Math.PI);
        double amplitude = w.amplitude() + (amplitudeAt(speed) - w.amplitude()) * EASE;
        return new Wave(phase, amplitude);
    }

    /**
     * requires: {@code arcBack >= 0}
     * effects: returns the sideways offset of the body {@code arcBack} blocks
     * behind the head under {@code w}, blocks: {@code amplitude * sin(phase -
     * 2 pi arcBack / wavelength)}, so the wave travels tailward as the phase
     * advances
     */
    public double lateralAt(Wave w, double arcBack) {
        return w.amplitude() * Math.sin(w.phase() - 2 * Math.PI * arcBack / wavelength);
    }

    /** effects: returns the vertical ripple {@code arcBack} behind the head: {@code verticalRatio} of the amplitude at twice the wave's frequency */
    public double verticalAt(Wave w, double arcBack) {
        return w.amplitude() * verticalRatio * Math.sin(2 * (w.phase() - 2 * Math.PI * arcBack / wavelength));
    }

    /** effects: returns the sideways velocity of the head this tick: the change of its offset over one advance at {@code speed} */
    public double lateralVelocity(Wave w, double speed) {
        return lateralAt(advance(w, speed), 0.0) - lateralAt(w, 0.0);
    }
}
