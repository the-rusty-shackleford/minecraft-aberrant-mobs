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

import java.util.ArrayList;
import java.util.List;

/**
 * Feet on the world. Each leg keeps its foot planted on a solid surface
 * (its anchor) until the body has carried its hip too far from it; then
 * the foot swings, over a few ticks and a lifted arc, to a new anchor found
 * on the nearest surface under and beside the leg's rest point -- floor,
 * wall or ceiling alike -- or hangs at rest when there is none within
 * reach. Most legs are down at all times: a leg may start a swing only
 * while its pair's other leg stands and fewer than a share of all legs
 * are swinging, the candidates taken in the gait's metachronal order, so
 * the steps run in a wave down the body. Standing still nothing swings.
 * The measures of a step -- how far an anchor drifts before the foot
 * steps, how far under the rest point ground is looked for, how high the
 * foot lifts, how far ahead it may land -- are the body's own, carried by
 * its {@link LegGait}.
 *
 * <p>All values immutable; the per-leg state is a {@link Foot} array the
 * caller owns and hands back each tick.
 */
public final class Legs {
    private Legs() {}

    /** A step takes this many ticks standing or crawling; fewer at speed, see {@link #swingTicks}. */
    public static final int SWING_TICKS = 4;
    /** At most this share of all legs swings at once. */
    public static final double MAX_SWINGING = 0.4;

    /**
     * effects: returns how many ticks a step takes at {@code speed}: four at a
     * crawl, three walking, two scurrying -- a fast body's feet fly
     */
    public static int swingTicks(double speed) {
        return speed < 0.1 ? SWING_TICKS : speed < 0.25 ? 3 : 2;
    }

    /**
     * effects: returns how far ahead of its rest point a step is aimed at
     * {@code speed} with {@code legs} legs of which {@code cap} may swing at
     * once: half the ground the body covers while a foot stands its turn
     * (every leg steps once per {@code legs / cap} swings), so a foot lands
     * as far ahead as it will be behind when it steps again; capped at
     * {@code maxLead}
     */
    public static double lead(double speed, int legs, int cap, double maxLead) {
        double stanceTicks = (double) legs / Math.max(1, cap) * swingTicks(speed);
        return Math.min(maxLead, speed * stanceTicks / 2.0);
    }

    /**
     * A leg as the body knows it: its chain segment, its side (+1 left, the
     * model's +X, -1 right), its hip's offset from the segment's pivot in
     * the segment's frame (blocks: +X left, +Y up, +Z forward), and where
     * its foot rests relative to the hip in that frame -- out to its side,
     * down, and swept forward or back as the file poses it.
     * RI: segment >= 0; side is +1 or -1; the rest foot is out to the leg's
     * own side ({@code side * rest.x() > 0}).
     */
    public record Leg(int segment, int side, Vec hip, Vec rest) {
        public Leg {
            if (segment < 0 || (side != 1 && side != -1) || hip == null || rest == null || !(side * rest.x() > 0)) {
                throw new IllegalArgumentException("a leg has a segment, a side, a hip, and a rest foot out to its side");
            }
        }

        /** effects: returns the length of the leg at rest, hip to foot, blocks */
        public double length() {
            return rest.length();
        }
    }

    /**
     * A foot's state. Planted: {@code anchor} set, not swinging. Swinging: from
     * {@code from} to {@code anchor}, {@code swingT} of the way. Hanging:
     * {@code anchor} null, not swinging. RI: 0 <= swingT <= 1; swinging implies
     * anchor and from non-null.
     */
    public record Foot(Vec anchor, Vec from, double swingT, boolean swinging) {
        public static final Foot HANGING = new Foot(null, null, 0.0, false);

        public Foot {
            if (!(swingT >= 0 && swingT <= 1) || (swinging && (anchor == null || from == null))) {
                throw new IllegalArgumentException("a foot swings between two points, or stands, or hangs");
            }
        }

        public boolean planted() {
            return anchor != null && !swinging;
        }

        /**
         * effects: returns this foot {@code dt} of a swing further on (at
         * most at its anchor), unchanged unless swinging -- a frame between
         * two ticks<br>
         * throws: {@link IllegalArgumentException} for a negative {@code dt}
         */
        public Foot advanced(double dt) {
            if (!(dt >= 0)) {
                throw new IllegalArgumentException("a frame is not before its tick: " + dt);
            }
            if (!swinging) {
                return this;
            }
            return new Foot(anchor, from, Math.min(1.0, swingT + dt), true);
        }

        /** effects: returns where the foot is now, given the leg's rest point for a hanging foot, a swinging one risen {@code lift} blocks at mid-step */
        public Vec at(Vec rest, Vec up, double lift) {
            if (anchor == null) {
                return rest;
            }
            if (!swinging) {
                return anchor;
            }
            return from.plus(anchor.minus(from).times(swingT)).plus(up.times(lift * Math.sin(Math.PI * swingT)));
        }
    }

    /** effects: returns every foot hanging: what a body has before its first tick */
    public static Foot[] hanging(int legs) {
        Foot[] out = new Foot[legs];
        java.util.Arrays.fill(out, Foot.HANGING);
        return out;
    }

    /** effects: returns the world position of leg {@code leg}'s hip on segment placement {@code segment} */
    public static Vec hip(Leg leg, Vec segmentPos, Quat segmentOrient) {
        return segmentPos.plus(segmentOrient.rotate(leg.hip()));
    }

    /** effects: returns the world position of leg {@code leg}'s rest foot: its rest offset from the hip, in the segment's frame */
    public static Vec rest(Leg leg, Vec segmentPos, Quat segmentOrient) {
        return segmentPos.plus(segmentOrient.rotate(leg.hip().plus(leg.rest())));
    }

    /** The surface is found to within this, blocks. */
    public static final double SURFACE_TOLERANCE = Cells.FACE_TOLERANCE;

    /** effects: returns {@link Cells#face}: the surface a cast from {@code from} along {@code dir} meets within {@code reach}, or null */
    public static Vec surface(Cells cells, Vec from, Vec dir, double reach) {
        return cells.face(from, dir, reach);
    }

    /**
     * requires: {@code feet.length == legs.length}; {@code chain} has a
     * placement for every leg's segment; {@code speed >= 0}
     * effects: returns the feet one tick on: a swinging foot advances and
     * plants at its anchor when done; a planted foot stays while its anchor
     * is within the stride (the larger of the gait's stride and the lead) of
     * its rest point and its supporting cell is still solid; a foot that
     * must step, or hangs with a surface now within the gait's reach, starts
     * a swing to the surface under the rest point led along {@code travel} by
     * {@link #lead} if its pair's other foot is not
     * swinging and fewer than {@link #MAX_SWINGING} of all legs are, legs
     * with no foot down first and then in the gait's order of phase; with
     * no surface in reach it hangs.
     * Standing still ({@code speed == 0}) no planted foot steps.
     */
    public static Foot[] step(Cells cells, ChainPose chain, Leg[] legs, Foot[] feet, LegGait gait, double distance, double speed, Vec travel) {
        if (feet.length != legs.length) {
            throw new IllegalArgumentException("a foot per leg");
        }
        if (!(speed >= 0)) {
            throw new IllegalArgumentException("speed is not negative");
        }
        Foot[] out = feet.clone();
        int swinging = 0;
        int cap = Math.max(1, (int) Math.floor(legs.length * MAX_SWINGING));
        double lead = speed > 0 ? lead(speed, legs.length, cap, gait.lead()) : 0.0;
        double stride = Math.max(gait.stride(), lead);
        // Advance the swings.
        for (int i = 0; i < legs.length; i++) {
            Foot f = feet[i];
            if (f.swinging()) {
                double t = Math.min(1.0, f.swingT() + 1.0 / swingTicks(speed));
                out[i] = t >= 1.0 ? new Foot(f.anchor(), null, 0.0, false) : new Foot(f.anchor(), f.from(), t, true);
                if (out[i].swinging()) {
                    swinging++;
                }
            }
        }
        // Who needs to step, in the gait's order.
        List<Integer> wanting = new ArrayList<>();
        Vec[] rests = new Vec[legs.length];
        Vec[] ups = new Vec[legs.length];
        for (int i = 0; i < legs.length; i++) {
            Leg leg = legs[i];
            Vec pos = chain.position(leg.segment());
            Quat orient = chain.orientation(leg.segment());
            rests[i] = rest(leg, pos, orient);
            ups[i] = orient.rotate(Vec.Y);
            Foot f = out[i];
            if (f.swinging()) {
                continue;
            }
            boolean needs;
            if (f.planted()) {
                boolean supported = cells.solidAt(f.anchor().minus(ups[i].times(0.15)));
                needs = !supported || (speed > 0 && f.anchor().minus(rests[i]).length() > stride);
            } else {
                needs = surface(cells, rests[i].plus(ups[i].times(0.5)), ups[i].times(-1), gait.reach() + 0.5) != null;
            }
            if (needs) {
                wanting.add(i);
            }
        }
        int pairs = legs.length / 2;
        double[] phase = new double[legs.length];
        for (int i = 0; i < legs.length; i++) {
            double base = 2 * Math.PI * distance / gait.cycleBlocks() + (i / 2) * gait.waveRad() + (i % 2 == 1 ? Math.PI : 0.0);
            phase[i] = ((base % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        }
        // A leg with no foot down at all steps before any that is merely overdue; then the gait's order.
        wanting.sort((a, b) -> {
            boolean ha = out[a].anchor() == null, hb = out[b].anchor() == null;
            if (ha != hb) {
                return ha ? -1 : 1;
            }
            return Double.compare(phase[a], phase[b]);
        });
        for (int i : wanting) {
            if (swinging >= cap) {
                break;
            }
            int other = i ^ 1;
            if (other < legs.length && out[other].swinging()) {
                continue;
            }
            Vec aim = rests[i].plus(travel.times(lead));
            Vec target = surface(cells, aim.plus(ups[i].times(0.5)), ups[i].times(-1), gait.reach() + 0.5);
            if (target == null) {
                out[i] = Foot.HANGING;
                continue;
            }
            Vec from = out[i].at(rests[i], ups[i], gait.lift());
            if (out[i].planted() && target.minus(out[i].anchor()).length() < 1e-6) {
                continue;
            }
            out[i] = new Foot(target, from, 0.0, true);
            swinging++;
        }
        return out;
    }

    /**
     * effects: returns the lever pose that points leg {@code leg} on segment
     * placement {@code (pos, orient)} at {@code foot}: the swing is how far
     * the hip-to-foot direction lies forward of the rest foot's, about the
     * segment's up; the lift how far it rises above the rest foot's, in
     * the leg's own vertical plane; both in degrees, so a foot at rest
     * gives zero
     */
    public static LegGait.LegPose aim(Leg leg, Vec pos, Quat orient, Vec foot) {
        Vec d = orient.conjugate().rotate(foot.minus(hip(leg, pos, orient)));
        return new LegGait.LegPose(yaw(leg, d) - yaw(leg, leg.rest()), pitch(leg, d) - pitch(leg, leg.rest()));
    }

    /** effects: returns the direction's angle forward of straight out to the leg's side, degrees */
    private static double yaw(Leg leg, Vec d) {
        return Math.toDegrees(Math.atan2(d.z(), Math.max(1e-6, leg.side() * d.x())));
    }

    /** effects: returns the direction's angle above the horizontal, degrees */
    private static double pitch(Leg leg, Vec d) {
        double lateral = leg.side() * d.x();
        double horizontal = Math.sqrt(lateral * lateral + d.z() * d.z());
        return Math.toDegrees(Math.atan2(d.y(), Math.max(1e-6, horizontal)));
    }
}
