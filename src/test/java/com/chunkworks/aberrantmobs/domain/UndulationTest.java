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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. At rest: the offset is not zero, changes with time, and
 * stays within the resting amplitude. Straight at speed: the wave travels
 * (the offset at d after moving s equals the old offset at d - s); the
 * amplitude eases to the moving amplitude and no further. Between:
 * amplitude by speed. Continuity: neighbours differ by no more than the
 * wave's slope allows; a speed step moves the amplitude a tenth at a time.
 * The vertical ripple is a fixed ratio at twice the frequency. Bad
 * parameters and a negative speed refused.
 */
final class UndulationTest {
    private static final Undulation U = Undulation.FACE_STEALER;

    @Test
    void atRestItStillWrithesSlowlyAndSlightly() {
        Undulation.Wave w = U.rest();
        double before = U.lateralAt(w, 2.0);
        boolean moved = false;
        for (int i = 0; i < 400; i++) {
            w = U.advance(w, 0.0);
            double now = U.lateralAt(w, 2.0);
            assertTrue(Math.abs(now) <= U.amplitudeRest() + 1e-9, "within the resting amplitude");
            if (Math.abs(now - before) > 1e-6) {
                moved = true;
            }
        }
        assertTrue(moved, "the body writhes at rest");
        // The idle takes its ten seconds and more to go round: fewer than one cycle in 200 ticks.
        double perTick = 2 * Math.PI * U.restSpeed() / U.wavelength();
        assertTrue(perTick * 200 < 2 * Math.PI);
    }

    @Test
    void atSpeedTheWaveTravelsDownTheBody() {
        Undulation.Wave w = new Undulation.Wave(1.0, U.amplitudeMoving());
        double speed = 0.45;
        Undulation.Wave next = U.advance(w, speed);
        // Having moved `speed` blocks, the point that was d behind is now d + speed behind and carries the same offset.
        for (double d : new double[] {0.0, 1.0, 2.7, 6.5}) {
            assertEquals(U.lateralAt(w, d), U.lateralAt(next, d + speed), 1e-9, "d = " + d);
        }
        assertTrue(Math.abs(U.lateralVelocity(w, speed)) > 0.0);
    }

    @Test
    void theAmplitudeEasesTowardTheSpeedsAmplitudeAndNoFurther() {
        Undulation.Wave w = U.rest();
        for (int i = 0; i < 200; i++) {
            w = U.advance(w, 1.0);
            assertTrue(w.amplitude() <= U.amplitudeMoving() + 1e-9);
        }
        assertEquals(U.amplitudeMoving(), w.amplitude(), 1e-6, "full amplitude at full speed");
        assertEquals(U.amplitudeRest() + (U.amplitudeMoving() - U.amplitudeRest()) * 0.5, U.amplitudeAt(U.speedRef() / 2), 1e-12, "half way at half the reference");
        for (int i = 0; i < 200; i++) {
            w = U.advance(w, 0.0);
        }
        assertEquals(U.amplitudeRest(), w.amplitude(), 1e-6, "back to rest");
        Undulation.Wave one = U.advance(U.rest(), 1.0);
        assertEquals(U.amplitudeRest() + (U.amplitudeMoving() - U.amplitudeRest()) * 0.1, one.amplitude(), 1e-12, "a tenth of the gap a tick");
    }

    @Test
    void neighboursNeverKink() {
        Undulation.Wave w = new Undulation.Wave(2.0, U.amplitudeMoving());
        double spacing = 0.6875;
        double bound = U.amplitudeMoving() * 2 * Math.PI * spacing / U.wavelength();
        for (double d = 0; d < 10; d += 0.1) {
            assertTrue(Math.abs(U.lateralAt(w, d) - U.lateralAt(w, d + spacing)) <= bound + 1e-9);
        }
    }

    @Test
    void theVerticalRippleIsAFixedRatioAtTwiceTheFrequency() {
        Undulation.Wave w = new Undulation.Wave(0.3, 0.2);
        assertEquals(0.2 * U.verticalRatio() * Math.sin(2 * 0.3), U.verticalAt(w, 0.0), 1e-12);
        assertEquals(U.verticalAt(w, 0.0), U.verticalAt(w, U.wavelength() / 2), 1e-9, "half a wavelength back it repeats");
    }

    @Test
    void badParametersAndANegativeSpeedAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Undulation(0.3, 0.1, 0.0, 0.3, 0.02, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Undulation(-0.3, 0.1, 5.0, 0.3, 0.02, 0.1));
        assertThrows(IllegalArgumentException.class, () -> U.advance(U.rest(), -1.0));
        assertThrows(IllegalArgumentException.class, () -> new Undulation.Wave(7.0, 0.1));
    }
}
