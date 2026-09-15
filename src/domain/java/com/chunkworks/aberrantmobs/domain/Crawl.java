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


import java.util.List;

/**
 * How a creature keeps to the world's surfaces: its head is a point held
 * a fixed clearance off the face it clings to (floor, wall or ceiling,
 * an axis face), turning within that face toward where it wants to go,
 * climbing a wall it meets, wrapping over an edge it walks off, boring
 * into rock when it may dig, and falling to the nearest surface when it
 * has none. Axis normals only: exact, six cases, enumerable; a slope reads
 * as corners, as it does to a centipede. Every rule is a pure function of
 * a {@link Cells} view, so it runs under JUnit on synthetic rock.
 */
public final class Crawl {
    private Crawl() {}

    /** The outward normal of the face the head clings to: its up. NONE is airborne. */
    public enum Normal {
        UP(0, 1, 0), DOWN(0, -1, 0), EAST(1, 0, 0), WEST(-1, 0, 0), SOUTH(0, 0, 1), NORTH(0, 0, -1), NONE(0, 0, 0);

        public final Vec dir;

        Normal(double x, double y, double z) {
            dir = new Vec(x, y, z);
        }

        /**
         * requires: {@code v} non-zero
         * effects: returns the axis normal nearest {@code v}'s direction
         */
        public static Normal nearest(Vec v) {
            if (!(v.length() > 0)) {
                throw new IllegalArgumentException("no direction");
            }
            Normal best = UP;
            double bestDot = -2;
            for (Normal n : values()) {
                double d = n.dir.dot(v);
                if (n != NONE && d > bestDot) {
                    bestDot = d;
                    best = n;
                }
            }
            return best;
        }

        public Normal opposite() {
            return switch (this) {
                case UP -> DOWN;
                case DOWN -> UP;
                case EAST -> WEST;
                case WEST -> EAST;
                case SOUTH -> NORTH;
                case NORTH -> SOUTH;
                case NONE -> NONE;
            };
        }
    }

    /**
     * A body's measures for the crawl: how far off its surface the head's
     * axis rides, the bore's radius when it digs, and how far ahead of the
     * head the crawl looks for rock or an edge. Blocks. RI: all positive.
     */
    public record Rules(double clearance, double bore, double lookahead) {
        public Rules {
            if (!(clearance > 0) || !(bore > 0) || !(lookahead > 0)) {
                throw new IllegalArgumentException("clearance, bore and lookahead are positive");
            }
        }
    }

    /** The Face-Stealer's: its axis 23.3 units over its feet, a bore of one and a half, looking 1.6 ahead. */
    public static final Rules FACE_STEALER = new Rules(23.3 / 16.0, 1.5, 1.6);

    /**
     * Where the head is: its axis point, the way it faces (unit) and the
     * face it clings to. RI: {@code |heading| = 1}; unless airborne,
     * {@code heading} is perpendicular to the normal.
     */
    public record Pose(Vec centre, Vec heading, Normal normal) {
        public Pose {
            if (centre == null || heading == null || normal == null || Math.abs(heading.length() - 1.0) > 1e-6) {
                throw new IllegalArgumentException("a pose has a centre, a unit heading and a normal");
            }
            if (normal != Normal.NONE && Math.abs(heading.dot(normal.dir)) > 1e-6) {
                throw new IllegalArgumentException("the heading lies in the face: " + heading + " on " + normal);
            }
        }

        public boolean airborne() {
            return normal == Normal.NONE;
        }
    }

    /**
     * One tick's outcome: the pose after it; whether the head holds for a
     * dig of the section ahead; whether it is blocked (wants a way it
     * cannot take); whether it turned a corner or landed this tick.
     */
    public record Step(Pose pose, boolean digNeeded, boolean blocked, boolean turned) {}

    /** The heading turns at most this much a tick, radians. */
    public static final double TURN_RATE = Math.toRadians(25);
    /** An airborne head falls this much a tick, blocks. */
    public static final double FALL = 0.3;
    /** A head with no surface attaches to one within this, blocks. */
    public static final double ATTACH_REACH = 2.0;
    /** The snap looks this far under the clearance for the surface, blocks. */
    public static final double SNAP_REACH = 0.75;
    /** A dig cuts the section this far ahead of the head, blocks. */
    public static final double DIG_AHEAD = 2.0;
    /** A wish mostly along the normal (less than this share of it in the face) is a wish to leave the face. */
    private static final double IN_FACE_SHARE = 0.25;

    /**
     * requires: {@code speed >= 0}
     * effects: returns the head one tick on from {@code pose}, wanting to go
     * along {@code desired} (any length; zero to hold) at {@code speed}
     * blocks a tick under {@code rules}, digging when {@code mayDig}:
     * <ul>
     * <li>airborne: falls {@link #FALL} and attaches to the nearest face
     *     within {@link #ATTACH_REACH} if any;
     * <li>holding (no speed or no wish): settles on its face;
     * <li>a wish mostly along the normal: into the face with digging, turns
     *     to bore straight in (heading along -normal, its feet on the face
     *     nearest behind it) and holds for the dig if the section is
     *     diggable; otherwise blocked, settled;
     * <li>otherwise turns toward the wish within the face by at most
     *     {@link #TURN_RATE}; with rock within the lookahead ahead: digging,
     *     holds for the dig (blocked if the section is not diggable); else
     *     climbs -- the wall ahead becomes its face, its heading the old up
     *     -- or is blocked when rock is above too; else moves {@code speed}
     *     and settles: snapped to its clearance; over an edge, wrapped onto
     *     the ledge's face heading down it; with nothing under it, attached
     *     to any face in reach, or airborne.
     * </ul>
     */
    public static Step step(Cells cells, Pose pose, Vec desired, double speed, Rules rules, boolean mayDig) {
        if (!(speed >= 0)) {
            throw new IllegalArgumentException("speed is not negative");
        }
        if (pose.airborne()) {
            Vec fallen = pose.centre().plus(new Vec(0, -FALL, 0));
            Pose landed = attach(cells, fallen, pose.heading(), rules, ATTACH_REACH);
            return landed == null ? new Step(new Pose(fallen, pose.heading(), Normal.NONE), false, false, false)
                    : new Step(landed, false, false, true);
        }
        Vec n = pose.normal().dir;
        Vec h = pose.heading();
        double wish = desired.length();
        if (speed == 0 || wish < 1e-9) {
            return settle(cells, pose, rules, false);
        }
        Vec inFace = desired.minus(n.times(desired.dot(n)));
        if (inFace.length() < IN_FACE_SHARE * wish) {
            if (desired.dot(n) < 0 && mayDig) {
                Pose bore = new Pose(pose.centre(), n.times(-1), Normal.nearest(h.times(-1)));
                return Tunnel.diggable(cells, section(bore, rules)) ? new Step(bore, true, false, true) : new Step(pose, false, true, false);
            }
            return settle(cells, pose, rules, true);
        }
        Vec h2 = turnToward(h, inFace.normalized(), n, TURN_RATE);
        Pose turned = new Pose(pose.centre(), h2, pose.normal());
        if (cells.solidAt(pose.centre().plus(h2.times(rules.lookahead())))) {
            if (mayDig) {
                return Tunnel.diggable(cells, section(turned, rules)) ? new Step(turned, true, false, false) : new Step(turned, false, true, false);
            }
            if (cells.solidAt(pose.centre().plus(n.times(rules.lookahead())))) {
                return new Step(turned, false, true, false);
            }
            Pose climbed = snap(cells, new Pose(pose.centre(), n, Normal.nearest(h2.times(-1))), rules);
            return climbed == null ? new Step(turned, false, true, false) : new Step(climbed, false, false, true);
        }
        return settle(cells, new Pose(pose.centre().plus(h2.times(speed)), h2, pose.normal()), rules, false);
    }

    /**
     * effects: returns {@code pose} kept on its face: snapped to its
     * clearance; failing that (the face ended under it), wrapped over the
     * edge onto the ledge's face, heading down it; failing that, attached
     * to any face within reach, or airborne; {@code blocked} passed through
     */
    private static Step settle(Cells cells, Pose pose, Rules rules, boolean blocked) {
        Pose snapped = snap(cells, pose, rules);
        if (snapped != null) {
            return new Step(snapped, false, blocked, false);
        }
        Vec n = pose.normal().dir, h = pose.heading();
        Vec under = pose.centre().minus(n.times(rules.clearance() + 0.5));
        Vec face = cells.face(under, h.times(-1), rules.lookahead() + 1.0);
        if (face != null) {
            Normal n2 = Normal.nearest(h);
            return new Step(new Pose(face.plus(n2.dir.times(rules.clearance())), n.times(-1), n2), false, blocked, true);
        }
        Pose held = attach(cells, pose.centre(), h, rules, ATTACH_REACH);
        return new Step(held == null ? new Pose(pose.centre(), h, Normal.NONE) : held, false, blocked, true);
    }

    /**
     * requires: {@code pose} not airborne
     * effects: returns {@code pose} with its centre exactly the clearance
     * off its face, found by a cast toward the face of the clearance plus
     * {@link #SNAP_REACH}; null when no face is there
     */
    public static Pose snap(Cells cells, Pose pose, Rules rules) {
        Vec n = pose.normal().dir;
        Vec face = cells.face(pose.centre(), n.times(-1), rules.clearance() + SNAP_REACH);
        return face == null ? null : new Pose(face.plus(n.times(rules.clearance())), pose.heading(), pose.normal());
    }

    /**
     * effects: returns the pose clinging to the nearest axis face within
     * {@code reach} of {@code centre}, the clearance off it, heading along
     * {@code heading} laid into the face (or any way in it when heading is
     * straight at it); null when no face is within reach
     */
    public static Pose attach(Cells cells, Vec centre, Vec heading, Rules rules, double reach) {
        Normal best = null;
        Vec bestFace = null;
        double bestDistance = Double.MAX_VALUE;
        for (Normal n : Normal.values()) {
            if (n == Normal.NONE) {
                continue;
            }
            Vec face = cells.face(centre, n.dir.times(-1), reach);
            if (face != null && face.minus(centre).length() < bestDistance) {
                bestDistance = face.minus(centre).length();
                bestFace = face;
                best = n;
            }
        }
        if (best == null) {
            return null;
        }
        Vec h = heading.minus(best.dir.times(heading.dot(best.dir)));
        if (h.length() < 1e-6) {
            h = Math.abs(best.dir.x()) < 0.5 ? Vec.X : Vec.Z;
        }
        return new Pose(bestFace.plus(best.dir.times(rules.clearance())), h.normalized(), best);
    }

    /**
     * requires: {@code heading}, {@code want} unit and perpendicular to {@code axis}
     * effects: returns {@code heading} turned toward {@code want} by at most
     * {@code maxRad}, within the plane perpendicular to {@code axis}; a
     * wish straight back turns toward the left ({@code axis x heading})
     */
    public static Vec turnToward(Vec heading, Vec want, Vec axis, double maxRad) {
        double c = Math.max(-1.0, Math.min(1.0, heading.dot(want)));
        if (Math.acos(c) <= maxRad) {
            return want;
        }
        Vec p = want.minus(heading.times(c));
        if (p.length() < 1e-9) {
            p = axis.cross(heading);
        }
        p = p.normalized();
        return heading.times(Math.cos(maxRad)).plus(p.times(Math.sin(maxRad))).normalized();
    }

    /** effects: returns the section a dig from {@code pose} cuts: the bore {@link #DIG_AHEAD} along its heading */
    public static List<Cell> section(Pose pose, Rules rules) {
        return Tunnel.section(pose.centre(), pose.heading(), DIG_AHEAD, rules.bore());
    }
}
