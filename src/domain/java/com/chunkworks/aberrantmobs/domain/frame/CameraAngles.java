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

/**
 * The game's look angles and their quaternion, both ways: the camera's
 * rotation is yaw about Y then pitch about X then roll about Z, as the
 * renderer composes it; a wearer's camera is the frame's rotation
 * composed with the local look, and the renderer wants it back as three
 * angles. Degrees; yaw as the game has it (0 faces +Z, 90 faces -X).
 */
public final class CameraAngles {
    private CameraAngles() {}

    /** Yaw, pitch and roll, degrees, the game's signs. */
    public record Angles(double yaw, double pitch, double roll) {}

    /** effects: returns the rotation of the given look: the view vector for {@code yaw}, {@code pitch} is what the game computes */
    public static Quat compose(Angles a) {
        Quat yaw = Quat.fromAxisAngle(Vec.Y, Math.toRadians(-a.yaw()));
        Quat pitch = Quat.fromAxisAngle(Vec.X, Math.toRadians(a.pitch()));   // a positive pitch looks down, as the game's does
        Quat roll = Quat.fromAxisAngle(Vec.Z, Math.toRadians(a.roll()));
        return yaw.times(pitch).times(roll);
    }

    /**
     * effects: returns the angles whose {@link #compose} is {@code q} (within
     * rounding), pitch in [-90, 90]; at a pitch of +-90 (looking straight
     * up or down) yaw and roll are one turn and the yaw takes it all
     */
    public static Angles decompose(Quat q) {
        // The look direction is the rotated +Z; the up is the rotated +Y.
        Vec look = q.rotate(Vec.Z);
        Vec up = q.rotate(Vec.Y);
        double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -look.y()))));
        double yaw;
        if (Math.abs(look.y()) > 1.0 - 1e-9) {
            // Gimbal: the yaw is the up's heading (looking down, up points forward).
            yaw = Math.toDegrees(Math.atan2(-up.x(), up.z()));
            if (look.y() > 0) {
                yaw = Math.toDegrees(Math.atan2(up.x(), -up.z()));
            }
            return new Angles(norm(yaw), pitch, 0.0);
        }
        yaw = Math.toDegrees(Math.atan2(-look.x(), look.z()));
        // Remove yaw and pitch; what is left about the look is the roll.
        Quat unrolled = Quat.fromAxisAngle(Vec.Y, Math.toRadians(-yaw)).times(Quat.fromAxisAngle(Vec.X, Math.toRadians(pitch)));
        Vec upNoRoll = unrolled.rotate(Vec.Y);
        Vec rightNoRoll = unrolled.rotate(Vec.X);
        double roll = Math.toDegrees(Math.atan2(up.dot(rightNoRoll.times(-1)), up.dot(upNoRoll)));
        return new Angles(norm(yaw), pitch, norm(roll));
    }

    /** effects: returns {@code deg} wrapped into (-180, 180] */
    public static double norm(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        }
        if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }
}
