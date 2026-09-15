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
package com.chunkworks.aberrantmobs.domain.mind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Parsing: a flag; a comparison with each operator; and, or,
 * not, parentheses, precedence (and binds tighter than or); a timer name;
 * a bad token, a missing number, an unbalanced parenthesis refused.
 * Holding: a flag present and absent; a number compared and absent
 * (false); a timer running, run out, never set. Names listed.
 */
final class CondTest {
    private static final Senses S = Senses.builder().flag("hurt", true).number("target.distance", 3.5).number("y", -20).build();
    private static final Memory M = Memory.fresh("roam", 1).withTimer("patience", 5).withTimer("done", 0);

    @Test
    void itParsesFlagsComparisonsAndTheConnectives() {
        assertTrue(Cond.parse("hurt").holds(S, M));
        assertFalse(Cond.parse("hurt_hard").holds(S, M), "an absent flag is false");
        assertTrue(Cond.parse("target.distance < 4").holds(S, M));
        assertTrue(Cond.parse("target.distance <= 3.5").holds(S, M));
        assertTrue(Cond.parse("target.distance > 3").holds(S, M));
        assertTrue(Cond.parse("target.distance >= 3.5").holds(S, M));
        assertTrue(Cond.parse("target.distance == 3.5").holds(S, M));
        assertTrue(Cond.parse("target.distance != 3").holds(S, M));
        assertTrue(Cond.parse("y > -30").holds(S, M), "a negative number");
        assertFalse(Cond.parse("health < 1").holds(S, M), "a comparison on an absent number is false");
        assertFalse(Cond.parse("health >= 0").holds(S, M), "however it is compared");
        assertTrue(Cond.parse("!health").holds(S, M), "an absent flag, negated");
        assertTrue(Cond.parse("hurt && target.distance < 4").holds(S, M));
        assertFalse(Cond.parse("hurt && target.distance > 4").holds(S, M));
        assertTrue(Cond.parse("hurt_hard || target.distance < 4").holds(S, M));
        assertTrue(Cond.parse("hurt_hard || hurt && y < 0").holds(S, M), "and binds tighter than or");
        assertFalse(Cond.parse("(hurt_hard || hurt) && y > 0").holds(S, M), "parentheses group");
        assertTrue(Cond.parse("  !( hurt_hard )  ").holds(S, M), "spaces are nothing");
        assertEquals(Set.of("hurt", "target.distance", "y"), Cond.parse("hurt && (target.distance < 4 || y > 0)").names());
    }

    @Test
    void timersAreReadFromTheMemory() {
        assertTrue(Cond.parse("timer.patience > 0").holds(S, M), "running");
        assertTrue(Cond.parse("timer.done == 0").holds(S, M), "run out");
        assertTrue(Cond.parse("timer.never == 0").holds(S, M), "never set reads as zero");
        assertTrue(Senses.readable("timer.x") && !Senses.readable("timer.") && Senses.readable("hurt") && !Senses.readable("nope"));
    }

    @Test
    void mistakesAreRefusedWithTheirOffset() {
        assertThrows(IllegalArgumentException.class, () -> Cond.parse("hurt &&"));
        assertThrows(IllegalArgumentException.class, () -> Cond.parse("target.distance <"));
        assertThrows(IllegalArgumentException.class, () -> Cond.parse("(hurt"));
        assertThrows(IllegalArgumentException.class, () -> Cond.parse("hurt hurt"));
        assertThrows(IllegalArgumentException.class, () -> Cond.parse("3 < 4"));
        assertThrows(IllegalArgumentException.class, () -> Cond.parse(""));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Cond.parse("hurt || ?"));
        assertTrue(e.getMessage().contains("at 8"), e.getMessage());
    }
}
