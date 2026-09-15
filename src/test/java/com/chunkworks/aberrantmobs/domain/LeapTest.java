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

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Level: the flight lands on the target exactly, in the
 * fewest ticks within the speed, and the arc rises. Up a ledge: lands on
 * it. A moving target: lands where it will be. Out of range: nothing. The
 * arc agrees with the tick-by-tick integration. Bad arguments refused.
 */
final class LeapTest {
    private static Vec integrate(Vec from, Vec v, double g, int ticks) {
        Vec p = from;
        for (int t = 0; t < ticks; t++) {
            p = p.plus(v);
            v = Leap.fallen(v, g);
        }
        return p;
    }

    private static int ticksToLand(Vec from, Vec v, Vec to, double g) {
        for (int t = 1; t <= Leap.MAX_TICKS; t++) {
            if (Leap.at(from, v, g, t).near(to, 1e-9)) {
                return t;
            }
        }
        return -1;
    }

    @Test
    void levelItLandsExactlyWithinItsSpeed() {
        Vec from = new Vec(0, 0, 0), to = new Vec(6, 0, 0);
        Optional<Vec> v = Leap.velocity(from, to, Vec.ZERO, 1.2, Leap.GRAVITY);
        assertTrue(v.isPresent());
        assertTrue(v.get().length() <= 1.2);
        int t = ticksToLand(from, v.get(), to, Leap.GRAVITY);
        assertTrue(t > 0, "it lands: " + v.get());
        assertEquals(6, t, "in six ticks: five would need more than the speed");
        assertTrue(v.get().y() > 0, "thrown upward");
        assertTrue(integrate(from, v.get(), Leap.GRAVITY, t).near(to, 1e-9), "the arc is the game's integration");
    }

    @Test
    void upALedgeAndOntoAMovingTarget() {
        Vec from = new Vec(0, 0, 0), ledge = new Vec(4, 3, 0);
        Vec v = Leap.velocity(from, ledge, Vec.ZERO, 1.2, Leap.GRAVITY).orElseThrow();
        assertTrue(ticksToLand(from, v, ledge, Leap.GRAVITY) > 0);
        Vec target = new Vec(5, 0, 0), walking = new Vec(0, 0, 0.2);
        Vec w = Leap.velocity(from, target, walking, 1.2, Leap.GRAVITY).orElseThrow();
        int t = -1;
        for (int i = 1; i <= Leap.MAX_TICKS && t < 0; i++) {
            if (Leap.at(from, w, Leap.GRAVITY, i).near(target.plus(walking.times(i)), 1e-9)) {
                t = i;
            }
        }
        assertTrue(t > 0, "lands where the target will be");
    }

    @Test
    void outOfRangeIsNothingAndBadArgumentsRefused() {
        assertTrue(Leap.velocity(Vec.ZERO, new Vec(200, 0, 0), Vec.ZERO, 1.2, Leap.GRAVITY).isEmpty());
        assertTrue(Leap.velocity(Vec.ZERO, new Vec(3, 0, 0), Vec.ZERO, 1.2, 0.0).isPresent(), "no gravity: a straight throw");
        assertThrows(IllegalArgumentException.class, () -> Leap.velocity(Vec.ZERO, Vec.X, Vec.ZERO, 0, Leap.GRAVITY));
        assertThrows(IllegalArgumentException.class, () -> Leap.at(Vec.ZERO, Vec.X, Leap.GRAVITY, -1));
        assertTrue(Leap.at(Vec.ZERO, Vec.X, Leap.GRAVITY, 0).near(Vec.ZERO, 1e-12));
    }
}
