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

import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.Objects;

/**
 * Immutable correction from a newly selected physical pose to the pose last shown.
 * Movement and mouse look remain live: only the discontinuity decays, not the target.
 * RI: non-null rotation/offset, finite start, finite positive duration.
 * AF: a world-space rotation and translation fading smoothly to identity/zero.
 */
public record PoseBlend(Quat correction, Vec offset, double start, double duration) {
    public static final double TICKS = 10;

    /** effects: constructs a correction; throws: NullPointerException for null values, IllegalArgumentException for invalid times. */
    public PoseBlend {
        Objects.requireNonNull(correction);
        Objects.requireNonNull(offset);
        if (!Double.isFinite(start) || !Double.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("a pose blend needs finite time and positive duration");
        }
    }

    /** effects: returns a blend whose start exactly preserves the shown pose, including a retarget during another blend. */
    public static PoseBlend between(Quat shown, Vec shownPosition, Quat target, Vec targetPosition, double time) {
        return new PoseBlend(shown.times(target.conjugate()), shownPosition.minus(targetPosition), time, TICKS);
    }

    /** effects: returns the remaining fraction at time; before/after the interval clamps to one/zero. */
    public double remaining(double time) {
        double t = Math.max(0, Math.min(1, (time - start) / duration));
        return 1 - t * t * (3 - 2 * t);
    }

    /** effects: returns the current orientation with the fading correction applied in world space. */
    public Quat rotation(Quat target, double time) {
        return Quat.IDENTITY.slerp(correction, remaining(time)).times(target);
    }

    /** effects: returns the current position with the fading discontinuity correction. */
    public Vec position(Vec target, double time) {
        return target.plus(offset.times(remaining(time)));
    }

    /** effects: returns whether the correction has reached identity. */
    public boolean done(double time) { return time >= start + duration; }
}
