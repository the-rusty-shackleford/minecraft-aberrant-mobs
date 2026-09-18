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
package com.chunkworks.aberrantmobs.domain.frame;

import static org.junit.jupiter.api.Assertions.*;
import com.chunkworks.aberrantmobs.domain.Vec;
import org.junit.jupiter.api.Test;

/** Partitions: idle/tap/held; initial hold/release/new press; guard expiry/key release in either order;
 * view front/side/back/up/down; movement forward/absent; finite/invalid input. */
final class ClingGestureTest {
    @Test void entryNeedsAHoldAndDetachNeedsANewPress() {
        var gesture = ClingGesture.IDLE;
        for (int i=1; i<ClingGesture.HOLD_TICKS; i++) {
            gesture = gesture.next(true, false);
            assertFalse(gesture.ready());
        }
        assertFalse(gesture.next(false, false).ready(), "a short tap is just a jump");
        gesture = gesture.next(true, false);
        assertTrue(gesture.ready());
        for (int i=0; i<20; i++) {
            gesture = gesture.next(true, true);
            assertFalse(gesture.detach(), "continuing the entry hold stays attached");
        }
        gesture = gesture.next(false, true);
        assertFalse(gesture.detach());
        gesture = gesture.next(true, true);
        assertTrue(gesture.detach(), "fresh press detaches");
    }

    @Test void releasedGuardRequiresBothTimeAndKeyRelease() {
        for (boolean earlyRelease : new boolean[] {true, false}) {
            var gesture = new ClingGesture(true, 4, 0, false, true).released();
            for (int i=0; i<ClingGesture.GUARD_TICKS; i++) {
                gesture = gesture.next(!earlyRelease, false);
                assertFalse(gesture.ready());
            }
            for (int i=0; !earlyRelease && i<30; i++) {
                gesture = gesture.next(true, false);
                assertFalse(gesture.ready(), "holding through the guard never sticks again");
            }
            gesture = gesture.next(false, false);
            for (int i=0; i<ClingGesture.HOLD_TICKS; i++) gesture = gesture.next(true, false);
            assertTrue(gesture.ready());
        }
    }

    @Test void inputMustAddressTheWallInBothViewAndMovement() {
        Vec outward = Vec.X.times(-1);
        assertTrue(new ClingIntent(true, true, -90, 0).faces(Frame.WORLD, outward));
        for (float yaw : new float[] {0, 90, 180})
            assertFalse(new ClingIntent(true, true, yaw, 0).faces(Frame.WORLD, outward));
        for (float pitch : new float[] {-90, 90})
            assertFalse(new ClingIntent(true, true, -90, pitch).faces(Frame.WORLD, outward));
        assertFalse(new ClingIntent(true, false, -90, 0).faces(Frame.WORLD, outward));
        assertThrows(IllegalArgumentException.class, () -> new ClingIntent(true, true, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClingIntent(true, true, 0, 91));
    }
}
