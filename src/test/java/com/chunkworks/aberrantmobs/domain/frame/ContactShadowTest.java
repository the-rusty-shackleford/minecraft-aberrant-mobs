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

import com.chunkworks.aberrantmobs.domain.Vec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: all axes; support at/below/above feet; beyond reach; lateral miss;
 * footprint straddling block boundaries; invalid radius/reach/rectangle. */
final class ContactShadowTest {
    @Test
    void theSupportingFaceIsTheLocalTopOnEveryAxis() {
        Vec feet = new Vec(4, 8, 12);
        for (Gravity gravity : Gravity.values()) {
            Frame frame = Frame.of(gravity);
            // A cube centered half a block along gravity, hence ending exactly at the feet.
            Vec center = feet.plus(gravity.dir.times(0.5));
            Vec half = new Vec(0.5, 0.5, 0.5);
            var patch = ContactShadow.project(frame, feet, new Frame.Box(center.minus(half), center.plus(half)), 0.5, 1).orElseThrow();
            assertEquals(0, patch.y(), 1e-9);
            assertEquals(-0.5, patch.x0(), 1e-9);
            assertEquals(0.5, patch.x1(), 1e-9);
            assertEquals(-0.5, patch.z0(), 1e-9);
            assertEquals(0.5, patch.z1(), 1e-9);
        }
    }

    @Test
    void onlyFacesWithinTheFootprintAndReachReceiveTheShadow() {
        Frame.Box cube = new Frame.Box(new Vec(0, -1, 0), new Vec(1, 0, 1));
        assertTrue(ContactShadow.project(Frame.WORLD, new Vec(0, 0, 0), cube, 0.5, 1).isPresent(), "foot on four cubes' junction");
        assertTrue(ContactShadow.project(Frame.WORLD, new Vec(0.5, 0.5, 0.5), cube, 0.5, 1).isPresent(), "near support");
        assertTrue(ContactShadow.project(Frame.WORLD, new Vec(0.5, 2, 0.5), cube, 0.5, 1).isEmpty(), "too far below");
        assertTrue(ContactShadow.project(Frame.WORLD, new Vec(0.5, -0.5, 0.5), cube, 0.5, 1).isEmpty(), "above feet");
        assertTrue(ContactShadow.project(Frame.WORLD, new Vec(2, 0, 0), cube, 0.5, 1).isEmpty(), "sideways");
        assertThrows(IllegalArgumentException.class, () -> ContactShadow.project(Frame.WORLD, Vec.ZERO, cube, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ContactShadow.project(Frame.WORLD, Vec.ZERO, cube, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ContactShadow.Patch(1, 0, 0, 1, 0));
    }
}
