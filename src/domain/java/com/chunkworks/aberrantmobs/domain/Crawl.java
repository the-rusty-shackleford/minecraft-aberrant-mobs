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

        /**
         * effects: returns how many cells out from the head's own, along an
         * axis, the bore reaches: the cells a way must keep between itself
         * and anything hard or wet, so the bore that follows it never
         * touches them -- one for a bore of one and a half, two for two
         * and a quarter
         */
        public int margin() {
            return (int) Math.ceil(bore - 0.5 - 1e-9);
        }

        /**
         * effects: returns how many cells out from the head's own, along an
         * axis, a face may lie for the head to be riding it: the whole
         * cells of the clearance and one more -- two for an axis a block
         * and a half over its feet (the floor's cell is the second below),
         * three for one over two -- so a way may run through any cell that
         * near a face, where the head can be, and a bore wider than three
         * is not planned along its walls
         */
        public int hold() {
            return (int) Math.floor(clearance) + 1;
        }
    }

    /**
     * The Face-Stealer's, at its size (a model unit is a sixteenth and a
     * half): its axis 23.3 units over its feet, a bore of 2.25 (six tenths
     * of its width of 3.75), looking 2.4 ahead.
     */
    public static final Rules FACE_STEALER = new Rules(23.3 * 1.5 / 16.0, 2.25, 2.4);

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
    /** A point may lie this far under the face the head stands on and still be underfoot, blocks: a sound in the floor's own block. */
    public static final double UNDERFOOT = 1.0;
    /** A wish mostly along the normal (less than this share of it in the face) is a wish to leave the face. */
    private static final double IN_FACE_SHARE = 0.25;
    /** A wish into the face that cannot be taken is still followed along the face while at least this share of it lies there; under that it is straight in, and blocked. */
    private static final double ALONG_SHARE = 0.05;

    /**
     * effects: returns whether the head at {@code pose} reaches
     * {@code point} within {@code reach}: within it outright; or, the head
     * on a face, within it across the face while the point lies under the
     * head, between its axis and {@link #UNDERFOOT} below the face -- a
     * target on the surface the head stands on (a player's feet, a sound
     * in the floor) is as reached as a head riding its clearance can reach
     * it, and a way's cell in the rows under the axis is passed when the
     * head is over it, while a cell below a ledge the head has not gone
     * over is not
     */
    public static boolean reaches(Pose pose, Rules rules, Vec point, double reach) {
        Vec d = point.minus(pose.centre());
        if (d.length() < reach) {
            return true;
        }
        if (pose.airborne()) {
            return false;
        }
        Vec n = pose.normal().dir;
        double under = -d.dot(n);
        if (under < 0 || under > rules.clearance() + UNDERFOOT) {
            return false;
        }
        return d.plus(n.times(under)).length() < reach;
    }

    /**
     * effects: returns how many cells a strike cuts at most under
     * {@code rules}: the straight section of a head at its clearance over
     * the middle of a cell, heading along an axis from the cell's boundary
     * -- 32 at the model's own size, the number the first release cut, 82
     * at one and a half -- so a straight bore is cleared a section a
     * strike (a few cells more when the head rides off the cell's middle,
     * and the tube round a bend in the way, take a second) and the world
     * never changes by more in a tick
     */
    public static int strikeBudget(Rules rules) {
        return section(new Pose(new Vec(0.0, rules.clearance(), 0.5), Vec.X, Normal.UP), rules).size();
    }

    /** effects: as {@link #step(Cells, Pose, Vec, double, Rules, boolean, boolean)} with climbing allowed: a walk with no way climbs whatever it meets */
    public static Step step(Cells cells, Pose pose, Vec desired, double speed, Rules rules, boolean mayDig) {
        return step(cells, pose, desired, speed, rules, mayDig, true);
    }

    /**
     * requires: {@code speed >= 0}
     * effects: returns the head one tick on from {@code pose}, wanting to go
     * along {@code desired} (any length; zero to hold) at {@code speed}
     * blocks a tick under {@code rules}, digging when {@code mayDig},
     * climbing what is ahead when {@code mayClimb}:
     * <ul>
     * <li>airborne: falls {@link #FALL} and lands on a floor it has come
     *     down to -- one within its clearance and the fall of it -- keeping
     *     its heading laid flat; never a wall on the way down, so a head
     *     that let go of one is not back on it next tick;
     * <li>holding (no speed or no wish): settles on its face;
     * <li>a wish mostly along the normal: into the face with digging, turns
     *     to bore straight in (heading along -normal, its feet on the face
     *     nearest behind it) and holds for the dig if the section is
     *     diggable; away from the face, or into it without digging, takes
     *     the nearest other face within {@link #ATTACH_REACH} the wish
     *     lies along (half of it in that face at least) -- a creature on a
     *     wall wanting what is out on the floor steps down onto the floor;
     *     with none: away from a floor, blocked, settled (it cannot fly);
     *     away from a wall or a ceiling, lets go -- airborne, to fall to
     *     what is below, rather than hanging refused on the far side of a
     *     post or under a canopy wanting the ground just out of reach;
     *     into the face, with {@link #ALONG_SHARE} of the wish in the face
     *     at all, crawls on along that part, as below -- toward the point's
     *     column, or to the edge that leads down to it, since a head near
     *     the top of a wall whose way goes over the edge would otherwise
     *     stand blocked half a block short of it -- else blocked, settled;
     * <li>otherwise turns toward the wish within the face by at most
     *     {@link #TURN_RATE}; with rock within the lookahead ahead: digging,
     *     holds for the dig (blocked if the section is not diggable); not
     *     climbing, turns where it stands -- what is ahead is something the
     *     way goes round -- and is blocked only once it faces the wish with
     *     the rock still ahead; else climbs -- the wall ahead becomes its
     *     face, its heading the old up -- or is blocked when rock is above
     *     too; else moves {@code speed} scaled by how far round it has come
     *     (the cosine of what is left of the turn, none for a wish beside
     *     or behind it: it pivots, and never orbits a point inside its
     *     turning circle) and settles: snapped to its clearance; over an
     *     edge, wrapped onto the ledge's face heading down it; with nothing
     *     under it, attached to any face in reach, or airborne.
     * </ul>
     */
    public static Step step(Cells cells, Pose pose, Vec desired, double speed, Rules rules, boolean mayDig, boolean mayClimb) {
        if (!(speed >= 0)) {
            throw new IllegalArgumentException("speed is not negative");
        }
        if (pose.airborne()) {
            Vec fallen = pose.centre().plus(new Vec(0, -FALL, 0));
            Vec floor = cells.face(fallen, Vec.Y.times(-1), rules.clearance() + FALL);
            if (floor == null) {
                return new Step(new Pose(fallen, pose.heading(), Normal.NONE), false, false, false);
            }
            Vec flat = pose.heading().minus(Vec.Y.times(pose.heading().y()));
            if (flat.length() < 1e-6) {
                flat = Vec.X;
            }
            return new Step(new Pose(floor.plus(Vec.Y.times(rules.clearance())), flat.normalized(), Normal.UP), false, false, true);
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
                if (Tunnel.diggable(cells, section(bore, rules))) {
                    return new Step(bore, true, false, true);
                }
            }
            Pose other = attachAlong(cells, pose.centre(), desired, pose.normal(), rules, ATTACH_REACH);
            if (other != null) {
                return new Step(snapOr(cells, other, rules), false, false, true);
            }
            if (desired.dot(n) > 0) {
                // Away from the face with nothing along the wish in reach: a floor holds it, since it cannot fly; a wall
                // or a ceiling is let go of, to fall to what is below.
                return pose.normal() == Normal.UP ? settle(cells, pose, rules, true)
                        : new Step(new Pose(pose.centre(), h, Normal.NONE), false, false, true);
            }
            if (inFace.length() < ALONG_SHARE * wish) {
                return settle(cells, pose, rules, true);
            }
            // Into the face, no digging, no other face in reach: on along what little of the wish lies in the face.
        }
        Vec h2 = turnToward(h, inFace.normalized(), n, TURN_RATE);
        Pose turned = new Pose(pose.centre(), h2, pose.normal());
        if (cells.solidAt(pose.centre().plus(h2.times(rules.lookahead())))) {
            if (mayDig) {
                return Tunnel.diggable(cells, section(turned, rules)) ? new Step(turned, true, false, false) : new Step(turned, false, true, false);
            }
            if (!mayClimb) {
                // Something the way goes round: turn toward the wish where it stands; blocked only facing the wish with rock still ahead.
                boolean facing = h2.minus(inFace.normalized()).length() < 1e-9;
                return settle(cells, turned, rules, facing);
            }
            if (cells.solidAt(pose.centre().plus(n.times(rules.lookahead())))) {
                return new Step(turned, false, true, false);
            }
            Pose climbed = snap(cells, new Pose(pose.centre(), n, Normal.nearest(h2.times(-1))), rules);
            return climbed == null ? new Step(turned, false, true, false) : new Step(climbed, false, false, true);
        }
        double along = Math.max(0.0, h2.dot(inFace.normalized()));
        return settle(cells, new Pose(pose.centre().plus(h2.times(speed * along)), h2, pose.normal()), rules, false);
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
     * effects: returns the pose clinging to the face within {@code reach} of
     * {@code centre}, other than {@code except}, along which {@code wish}
     * lies best (at least half of it in the face), heading along the wish
     * laid into that face; null when no such face is in reach
     */
    public static Pose attachAlong(Cells cells, Vec centre, Vec wish, Normal except, Rules rules, double reach) {
        double length = wish.length();
        if (length < 1e-9) {
            return null;
        }
        Normal best = null;
        Vec bestFace = null;
        double bestShare = 0.5;
        for (Normal n : Normal.values()) {
            if (n == Normal.NONE || n == except) {
                continue;
            }
            Vec inFace = wish.minus(n.dir.times(wish.dot(n.dir)));
            double share = inFace.length() / length;
            if (share < bestShare) {
                continue;
            }
            Vec face = cells.face(centre, n.dir.times(-1), reach);
            if (face != null) {
                bestShare = share;
                best = n;
                bestFace = face;
            }
        }
        if (best == null) {
            return null;
        }
        Vec h = wish.minus(best.dir.times(wish.dot(best.dir))).normalized();
        return new Pose(bestFace.plus(best.dir.times(rules.clearance())), h, best);
    }

    /** effects: returns {@code pose} snapped to its face, or as it is when the face is not found from there */
    private static Pose snapOr(Cells cells, Pose pose, Rules rules) {
        Pose snapped = snap(cells, pose, rules);
        return snapped == null ? pose : snapped;
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
