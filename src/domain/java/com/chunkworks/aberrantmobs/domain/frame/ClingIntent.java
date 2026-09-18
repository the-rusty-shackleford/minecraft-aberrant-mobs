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

/**
 * Immutable manual input, never a position or a requested gravity.
 * AF: jump/forward describe held controls and yaw/pitch the local view.
 * RI: finite angles; pitch is in [-90,90].
 */
public record ClingIntent(boolean jump, boolean forward, float yaw, float pitch) {
    public static final ClingIntent NONE = new ClingIntent(false, false, 0, 0);

    /** requires: finite angles and a valid pitch; effects: captures input; throws: IllegalArgumentException otherwise. */
    public ClingIntent {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90)
            throw new IllegalArgumentException("invalid cling view");
    }

    /** requires: unit outward wall normal; effects: whether forward movement and view both address that wall. */
    public boolean faces(Frame frame, Vec normal) {
        Vec heading = frame.toWorld(Frame.headingOf(yaw));
        double angle = Math.toRadians(pitch);
        Vec look = heading.times(Math.cos(angle)).minus(frame.up().times(Math.sin(angle)));
        return forward && heading.dot(normal) < -0.5 && look.dot(normal) < -0.55;
    }
}
