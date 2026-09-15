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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Freshness: the crack lands on a candidate by the roll, on
 * any segment without candidates, never out of range. Routing: a blow on
 * the crack is whole with a flinch; on another segment a clang whatever
 * the cause but explosion; an explosion anywhere is its share with a
 * flinch; the body's own box (-1) is a clang; a zero blow flinches nothing;
 * a capped carapace takes no more than the cap from a blow on the crack or
 * from an explosion, and a blow under the cap whole. Aim: the segment
 * nearest a blow's ray, ahead of its origin and within reach, of equals
 * the one met first along it; none behind or beyond reach; a blast's
 * nearest segment.
 * Moulting: the crack moves when it can. Bad values refused.
 */
final class CarapaceTest {
    private static final int[] CANDIDATES = {2, 3, 4, 5};

    @Test
    void theCrackLandsOnACandidateByTheRoll() {
        for (long roll = 0; roll < 12; roll++) {
            Carapace c = Carapace.fresh(12, CANDIDATES, roll, Carapace.UNCAPPED);
            assertEquals(CANDIDATES[(int) (roll % 4)], c.weak());
        }
        Carapace any = Carapace.fresh(12, null, 13, Carapace.UNCAPPED);
        assertEquals(1, any.weak());
        assertEquals(11, Carapace.fresh(12, new int[0], -1, Carapace.UNCAPPED).weak(), "a negative roll still lands inside");
    }

    @Test
    void onlyTheCrackTakesABlowSaveAnExplosion() {
        Carapace c = new Carapace(12, 4, 0.5, Carapace.UNCAPPED);
        assertEquals(new Carapace.Verdict(7.0, true), c.route(new Carapace.Hit(4, 7.0, Carapace.Cause.MELEE)));
        assertTrue(c.route(new Carapace.Hit(3, 7.0, Carapace.Cause.MELEE)).isClang());
        assertTrue(c.route(new Carapace.Hit(0, 7.0, Carapace.Cause.PROJECTILE)).isClang());
        assertTrue(c.route(new Carapace.Hit(-1, 7.0, Carapace.Cause.OTHER)).isClang(), "the body's own box is plating");
        assertEquals(new Carapace.Verdict(10.0, true), c.route(new Carapace.Hit(0, 20.0, Carapace.Cause.EXPLOSION)));
        assertEquals(new Carapace.Verdict(20.0, true), c.route(new Carapace.Hit(4, 20.0, Carapace.Cause.EXPLOSION)), "on the crack an explosion is whole");
        assertFalse(c.route(new Carapace.Hit(4, 0.0, Carapace.Cause.MELEE)).flinch(), "nothing to flinch at");
    }

    @Test
    void noBlowTakesMoreThanTheCap() {
        // A creature of 84 that five blows at the least must kill: a cap of 16.8 a blow.
        Carapace c = new Carapace(12, 4, 0.5, 16.8);
        assertEquals(new Carapace.Verdict(16.8, true), c.route(new Carapace.Hit(4, 1.0e6, Carapace.Cause.MELEE)), "a million on the crack is the cap");
        assertEquals(new Carapace.Verdict(16.8, true), c.route(new Carapace.Hit(4, 17.0, Carapace.Cause.MELEE)), "a hair over the cap is the cap");
        assertEquals(new Carapace.Verdict(7.0, true), c.route(new Carapace.Hit(4, 7.0, Carapace.Cause.MELEE)), "under it, whole");
        assertEquals(new Carapace.Verdict(16.8, true), c.route(new Carapace.Hit(1, 100.0, Carapace.Cause.EXPLOSION)), "a blast's half, capped");
        assertEquals(new Carapace.Verdict(10.0, true), c.route(new Carapace.Hit(1, 20.0, Carapace.Cause.EXPLOSION)), "a small blast's half, whole");
        assertTrue(c.route(new Carapace.Hit(1, 1.0e6, Carapace.Cause.MELEE)).isClang(), "plating is still plating");
        assertEquals(16.8, c.moulted(CANDIDATES, 1).blowCap(), 1e-12, "the cap survives a moult");
        assertThrows(IllegalArgumentException.class, () -> new Carapace(12, 4, 0.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Carapace.fresh(12, CANDIDATES, 0, -1.0));
    }

    @Test
    void theSegmentAimedAtIsTheOneNearestTheBlowsRay() {
        // Four segments along +X a block apart at height 2, their boxes far wider than the spacing.
        Vec[] centres = {new Vec(0.5, 2, 0.5), new Vec(1.5, 2, 0.5), new Vec(2.5, 2, 0.5), new Vec(3.5, 2, 0.5)};
        Vec eye = new Vec(2.5, 2.5, -4.0);
        assertEquals(2, Carapace.aimed(eye, new Vec(2.5, 2, 0.5).minus(eye), centres, 3.3), "aimed at the third's centre from the side");
        assertEquals(1, Carapace.aimed(eye, new Vec(1.8, 2, 0.5).minus(eye), centres, 3.3), "aimed between the second and third, nearer the second");
        assertEquals(3, Carapace.aimed(new Vec(9.0, 2, 0.5), new Vec(-1, 0, 0), centres, 3.3), "along the body from beyond its tail, all on the ray: the tail, met first");
        assertEquals(-1, Carapace.aimed(eye, new Vec(0, 0, -1), centres, 3.3), "looking away: every segment behind the eye");
        assertEquals(-1, Carapace.aimed(eye, new Vec(0, 1, 0), centres, 3.3), "a ray missing all of them by more than the reach");
        assertEquals(0, Carapace.aimed(new Vec(-5, 2, 0.5), new Vec(1, 0, 0), centres, 3.3), "along the body from before its head: the head, met first");
        assertThrows(IllegalArgumentException.class, () -> Carapace.aimed(eye, Vec.ZERO, centres, 3.3));
        assertEquals(3, Carapace.nearest(new Vec(3.2, 0, 0.5), centres), "a blast's centre: the nearest");
        assertEquals(-1, Carapace.nearest(Vec.ZERO, new Vec[0]));
    }

    @Test
    void moultingMovesTheCrack() {
        Carapace c = new Carapace(12, 4, 0.5, Carapace.UNCAPPED);
        for (long roll = 0; roll < 8; roll++) {
            Carapace m = c.moulted(CANDIDATES, roll);
            assertNotEquals(4, m.weak(), "roll " + roll);
            assertTrue(m.weak() >= 2 && m.weak() <= 5);
        }
        assertEquals(0, new Carapace(1, 0, 0.5, Carapace.UNCAPPED).moulted(new int[] {0}, 3).weak(), "one choice stays");
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Carapace(0, 0, 0.5, Carapace.UNCAPPED));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 3, 0.5, Carapace.UNCAPPED));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 1, 1.5, Carapace.UNCAPPED));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 1, 0.5, Carapace.UNCAPPED).route(new Carapace.Hit(1, -1.0, Carapace.Cause.MELEE)));
        assertThrows(IllegalArgumentException.class, () -> new Carapace.Verdict(-1.0, false));
    }
}
