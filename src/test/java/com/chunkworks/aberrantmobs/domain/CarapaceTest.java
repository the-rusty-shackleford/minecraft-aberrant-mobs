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
 * flinch; the body's own box (-1) is a clang; a zero blow flinches nothing.
 * Moulting: the crack moves when it can. Bad values refused.
 */
final class CarapaceTest {
    private static final int[] CANDIDATES = {2, 3, 4, 5};

    @Test
    void theCrackLandsOnACandidateByTheRoll() {
        for (long roll = 0; roll < 12; roll++) {
            Carapace c = Carapace.fresh(12, CANDIDATES, roll);
            assertEquals(CANDIDATES[(int) (roll % 4)], c.weak());
        }
        Carapace any = Carapace.fresh(12, null, 13);
        assertEquals(1, any.weak());
        assertEquals(11, Carapace.fresh(12, new int[0], -1).weak(), "a negative roll still lands inside");
    }

    @Test
    void onlyTheCrackTakesABlowSaveAnExplosion() {
        Carapace c = new Carapace(12, 4, 0.5);
        assertEquals(new Carapace.Verdict(7.0, true), c.route(new Carapace.Hit(4, 7.0, Carapace.Cause.MELEE)));
        assertTrue(c.route(new Carapace.Hit(3, 7.0, Carapace.Cause.MELEE)).isClang());
        assertTrue(c.route(new Carapace.Hit(0, 7.0, Carapace.Cause.PROJECTILE)).isClang());
        assertTrue(c.route(new Carapace.Hit(-1, 7.0, Carapace.Cause.OTHER)).isClang(), "the body's own box is plating");
        assertEquals(new Carapace.Verdict(10.0, true), c.route(new Carapace.Hit(0, 20.0, Carapace.Cause.EXPLOSION)));
        assertEquals(new Carapace.Verdict(20.0, true), c.route(new Carapace.Hit(4, 20.0, Carapace.Cause.EXPLOSION)), "on the crack an explosion is whole");
        assertFalse(c.route(new Carapace.Hit(4, 0.0, Carapace.Cause.MELEE)).flinch(), "nothing to flinch at");
    }

    @Test
    void moultingMovesTheCrack() {
        Carapace c = new Carapace(12, 4, 0.5);
        for (long roll = 0; roll < 8; roll++) {
            Carapace m = c.moulted(CANDIDATES, roll);
            assertNotEquals(4, m.weak(), "roll " + roll);
            assertTrue(m.weak() >= 2 && m.weak() <= 5);
        }
        assertEquals(0, new Carapace(1, 0, 0.5).moulted(new int[] {0}, 3).weak(), "one choice stays");
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Carapace(0, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 3, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 1, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new Carapace(3, 1, 0.5).route(new Carapace.Hit(1, -1.0, Carapace.Cause.MELEE)));
        assertThrows(IllegalArgumentException.class, () -> new Carapace.Verdict(-1.0, false));
    }
}
