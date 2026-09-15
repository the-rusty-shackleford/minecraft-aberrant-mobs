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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Nothing noted: not locked. A flick (a few ticks): not
 * locked. A stare (eight of ten): locked; ten of ten: locked; looking
 * away again for three ticks: unlocked. Only the window is remembered.
 * Bad bits refused.
 */
final class GazeTest {
    @Test
    void aStareLocksAFlickDoesNot() {
        Gaze g = Gaze.NONE;
        assertFalse(g.locked());
        for (int i = 0; i < 3; i++) {
            g = g.noting(true);
        }
        assertFalse(g.locked(), "a flick of three");
        assertEquals(3, g.count());
        for (int i = 0; i < 5; i++) {
            g = g.noting(true);
        }
        assertTrue(g.locked(), "eight of the last ten");
        for (int i = 0; i < 4; i++) {
            g = g.noting(true);
        }
        assertEquals(Gaze.WINDOW, g.count(), "only the window remembered");
        assertTrue(g.locked());
        g = g.noting(false).noting(false).noting(false);
        assertFalse(g.locked(), "looked away: three misses in the window");
        assertEquals(7, g.count());
        assertThrows(IllegalArgumentException.class, () -> new Gaze(1 << Gaze.WINDOW));
    }
}
