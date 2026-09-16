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
import java.util.Optional;

/** The footprint of a vanilla contact shadow, reckoned in the wearer's frame. */
public final class ContactShadow {
    private ContactShadow() {}

    /**
     * Immutable rectangle on a support plane in local coordinates.
     * AF: [x0,x1] x [z0,z1] at local height y, with normal local +Y.
     * RI: finite coordinates, x0 < x1 and z0 < z1.
     */
    public record Patch(double x0, double x1, double z0, double z1, double y) {
        public Patch {
            if (!Double.isFinite(x0) || !Double.isFinite(x1) || !Double.isFinite(z0)
                    || !Double.isFinite(z1) || !Double.isFinite(y) || x0 >= x1 || z0 >= z1) {
                throw new IllegalArgumentException("a finite, nonempty support rectangle is required");
            }
        }
    }

    /**
     * requires: frame is an axis frame from Frame.of; feet and block coordinates
     * are finite; block has positive volume; radius and reach are finite and positive
     * effects: returns the block's local top face when it lies under the feet within
     * reach and overlaps the shadow footprint; otherwise empty. A 0.01 surface
     * tolerance accepts collision rounding. Texture coordinates may extend past the
     * footprint, as in vanilla; its texture clamps the visible circle.
     * throws: IllegalArgumentException for a nonpositive/nonfinite radius or reach
     */
    public static Optional<Patch> project(Frame frame, Vec feet, Frame.Box block, double radius, double reach) {
        if (!Double.isFinite(radius) || !Double.isFinite(reach) || radius <= 0 || reach <= 0) {
            throw new IllegalArgumentException("positive finite shadow dimensions required");
        }
        Vec a = frame.toLocal(block.lo().minus(feet));
        Vec b = frame.toLocal(block.hi().minus(feet));
        double x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
        double z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
        double y = Math.max(a.y(), b.y());
        if (y > 0.01 || y < -reach || x1 <= -radius || x0 >= radius || z1 <= -radius || z0 >= radius) {
            return Optional.empty();
        }
        return Optional.of(new Patch(x0, x1, z0, z1, y));
    }
}
