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
import java.util.function.Predicate;

/**
 * When a wearer changes which way is down: walking into a sturdy wall
 * takes it, stepping off a ledge wraps onto the ledge's face, and losing
 * the armour, entering water, flying, riding or a jump on a wall lets go
 * to the world's own down. Pure: whether a box fits in the world comes in
 * as a predicate, so collision stays outside and the rules run under
 * JUnit.
 */
public final class Transition {
    private Transition() {}

    /** A wearer must be moving at least this fast into a wall to take it, blocks a tick. */
    public static final double INTO_WALL = 0.02;
    /** How far past the feet a ledge's face must lie to be wrapped onto, blocks. */
    public static final double EDGE_REACH = 0.3;

    /**
     * Where a wearer stands after a change: the new frame, the feet point
     * that fits, and the yaw it should face so that the way it was going
     * carries on (NaN: keep the yaw it has).
     */
    public record Stance(Frame frame, Vec feet, double yaw) {}

    /**
     * effects: returns the stance of a wearer of {@code width} by {@code height}
     * at {@code feet} in {@code frame}, moving {@code worldMove} (the move
     * it tried this tick) that was stopped by a wall whose outward normal is
     * {@code wallNormal} (a world axis): the wall becomes its floor, its
     * feet on the wall's face at the box's near edge, facing up the wall
     * (along the old up, so a wearer that walked into the wall keeps
     * going), if the new box {@code fits}; nothing when the wearer was not
     * pushing into the wall at {@link #INTO_WALL}, the wall is its own
     * floor or ceiling, or the box does not fit
     */
    public static Optional<Stance> intoWall(Frame frame, Vec feet, double width, double height, Vec worldMove, Vec wallNormal, Predicate<Frame.Box> fits) {
        Gravity next = Gravity.toward(wallNormal);
        if (next == frame.gravity() || next == frame.gravity().opposite()) {
            return Optional.empty();
        }
        if (worldMove.dot(wallNormal) > -INTO_WALL) {
            return Optional.empty();
        }
        Frame f = Frame.of(next);
        // The feet lie on the wall's face: move the feet point onto the plane the box's near side touched.
        Frame.Box old = frame.box(feet, width, height);
        double facePlane = wallNormal.dot(wallNormal.x() != 0 ? (wallNormal.x() > 0 ? old.lo() : old.hi())
                : wallNormal.y() != 0 ? (wallNormal.y() > 0 ? old.lo() : old.hi()) : (wallNormal.z() > 0 ? old.lo() : old.hi()));
        // The feet point along the new up sits on that plane; across it, the old box's centre.
        Vec centre = old.lo().plus(old.hi()).times(0.5);
        Vec feetNew = centre.minus(wallNormal.times(centre.dot(wallNormal) - facePlane)).minus(wallNormal.times(0.0));
        // The box's gravity-side face must lie on the plane: the feet point is on it already; the box grows along the new up.
        Frame.Box box = f.box(feetNew, width, height);
        if (!fits.test(box)) {
            return Optional.empty();
        }
        return Optional.of(new Stance(f, feetNew, f.yawToward(frame.up())));
    }

    /**
     * effects: returns the stance of a wearer in {@code frame} at {@code feet}
     * that has walked off its floor's edge along {@code worldMove}: it wraps
     * onto the ledge's face (the face looking along its move), its feet
     * just under the edge, facing down the face (along the old down, so it
     * keeps going), if that box {@code fits}; nothing when the wearer moved
     * too little, or the box does not fit
     */
    public static Optional<Stance> overEdge(Frame frame, Vec feet, double width, double height, Vec worldMove, Predicate<Frame.Box> fits) {
        Vec along = worldMove.minus(frame.up().times(worldMove.dot(frame.up())));
        if (along.length() < INTO_WALL) {
            return Optional.empty();
        }
        Gravity next = Gravity.nearest(along.times(-1));
        Frame f = Frame.of(next);
        // The ledge's face is a plane perpendicular to the move, just behind the feet; the feet go onto it, a little down.
        Vec faceNormal = f.up();
        Vec feetNew = feet.plus(faceNormal.times(width / 2.0 + 1e-3)).plus(frame.up().times(-EDGE_REACH));
        Frame.Box box = f.box(feetNew, width, height);
        if (!fits.test(box)) {
            return Optional.empty();
        }
        return Optional.of(new Stance(f, feetNew, f.yawToward(frame.up().times(-1))));
    }

    /**
     * effects: returns the stance of a wearer letting go of {@code frame} at
     * {@code feet}: the world's own frame with the feet moved by the least
     * along the old up that makes the box {@code fits}, the yaw kept (NaN);
     * the feet unmoved when it fits already; nothing when no place within
     * {@code height} fits
     */
    public static Optional<Stance> release(Frame frame, Vec feet, double width, double height, Predicate<Frame.Box> fits) {
        if (frame.gravity().isDown()) {
            return Optional.of(new Stance(Frame.WORLD, feet, Double.NaN));
        }
        for (double d = 0.0; d <= height; d += 0.1) {
            Vec candidate = feet.plus(frame.up().times(d));
            if (fits.test(Frame.WORLD.box(candidate, width, height))) {
                return Optional.of(new Stance(Frame.WORLD, candidate, Double.NaN));
            }
        }
        return Optional.empty();
    }
}
