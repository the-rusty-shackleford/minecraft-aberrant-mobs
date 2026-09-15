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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The surface cast: a floor under, a wall beside, nothing in
 * reach, starting inside rock. A straight crawl on a floor: the feet plant
 * on the floor, anchors trail the hips and step ahead, never more than the
 * share swings at once, a pair never swings together, a step takes its
 * ticks on a lifted arc and lands on the anchor. At rest: nothing steps.
 * A wall: anchors on the wall's face. A gap: legs over it hang, and plant
 * again when a surface comes. A dug-away anchor re-plants. The aim: a foot
 * straight out at rest gives zero angles; ahead gives a forward swing;
 * higher gives a lift. Bad arguments refused.
 */
final class LegsTest {
    /** The gait of a body a sixteenth a unit -- the size the synthetic bodies here are built at: feet 1.2 out and 1.3 down, at height 1.5. */
    private static final LegGait GAIT = new LegGait(22.0, 12.0, 1.6, 0.35 * 2 * Math.PI, 0.3, 0.55, 1.6, 0.35, 1.2);
    private static final Undulation NONE = new Undulation(0, 0, 5.5, 0.35, 0.02, 0);

    /** A straight body along +X at height 1.5 with n segments 0.7 apart, on a trail. */
    private static ChainPose straight(double headX, int segments) {
        Trail t = Trail.seeded(new Vec(headX, 1.5, 0), Vec.X, Vec.Y, 20.0, 64);
        double[] arcs = new double[segments];
        for (int k = 0; k < segments; k++) {
            arcs[k] = k * 0.7;
        }
        return ChainPose.of(t, arcs, NONE, NONE.rest());
    }

    /** Two pairs of legs on segments 0 and 1, hips a block out, feet 1.2 out and 1.3 down. */
    private static Legs.Leg[] legs() {
        return legs(2);
    }

    /** A pair of legs on each of {@code pairs} segments. */
    private static Legs.Leg[] legs(int pairs) {
        Legs.Leg[] out = new Legs.Leg[pairs * 2];
        for (int i = 0; i < pairs; i++) {
            out[2 * i] = new Legs.Leg(i, +1, new Vec(0.5, -0.2, 0), new Vec(1.2, -1.3, 0));
            out[2 * i + 1] = new Legs.Leg(i, -1, new Vec(-0.5, -0.2, 0), new Vec(-1.2, -1.3, 0));
        }
        return out;
    }

    @Test
    void theCastFindsAFloorAWallOrNothing() {
        Cells floor = Cells.floor(-1);   // rock at y <= -1: the surface is y = 0
        Vec s = Legs.surface(floor, new Vec(0.5, 1.0, 0.5), new Vec(0, -1, 0), 2.0);
        assertTrue(s != null && s.y() >= 0.0 && s.y() < Legs.SURFACE_TOLERANCE, "on the floor: " + s);
        assertNull(Legs.surface(floor, new Vec(0.5, 5.0, 0.5), new Vec(0, -1, 0), 2.0), "too far up");
        Cells wall = (x, y, z) -> x >= 3 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Vec w = Legs.surface(wall, new Vec(1.5, 0, 0), Vec.X, 3.0);
        assertTrue(w != null && w.x() < 3.0 && w.x() > 3.0 - Legs.SURFACE_TOLERANCE, "at the wall's face: " + w);
        Vec inside = Legs.surface(floor, new Vec(0.5, -0.5, 0.5), new Vec(0, -1, 0), 2.0);
        assertTrue(inside != null && inside.y() >= 0.0 && inside.y() < Legs.SURFACE_TOLERANCE, "from inside the rock, the surface over it: " + inside);
    }

    @Test
    void aCrawlOnAFloorPlantsAndStepsInTurnWithMostFeetDown() {
        Cells floor = Cells.floor(-1);
        int pairs = 6, n = pairs * 2;
        Legs.Leg[] legs = legs(pairs);
        Foot[] feet = wrap(Legs.hanging(n));
        double x = 0.0;
        int maxSwinging = 0;
        boolean everStepped = false;
        for (int tick = 0; tick < 200; tick++) {
            x += 0.3;
            ChainPose chain = straight(x, pairs);
            feet = wrap(Legs.step(floor, chain, legs, unwrap(feet), GAIT, x, 0.3, Vec.X));
            int swinging = 0;
            for (int i = 0; i < n; i++) {
                Legs.Foot f = feet[i].f;
                if (f.swinging()) {
                    swinging++;
                    everStepped = true;
                }
                if (f.anchor() != null) {
                    assertTrue(f.anchor().y() >= 0.0 && f.anchor().y() < Legs.SURFACE_TOLERANCE, "anchors are on the floor: " + f.anchor());
                }
                assertFalse(f.swinging() && feet[i ^ 1].f.swinging(), "a pair never swings together");
            }
            maxSwinging = Math.max(maxSwinging, swinging);
            if (tick > 40) {
                for (int i = 0; i < n; i++) {
                    Legs.Foot f = feet[i].f;
                    Vec rest = Legs.rest(legs[i], chain.position(legs[i].segment()), chain.orientation(legs[i].segment()));
                    assertTrue(f.anchor() != null, "once under way every foot has an anchor (tick " + tick + ", leg " + i + ")");
                    assertTrue(f.anchor().minus(rest).length() < 2.5, "anchors stay near the rest point: " + f.anchor().minus(rest).length());
                }
            }
        }
        assertTrue(everStepped);
        assertTrue(maxSwinging <= Math.max(1, (int) Math.floor(n * Legs.MAX_SWINGING)), "at most the share swings: " + maxSwinging);
    }

    @Test
    void aStepTakesItsTicksOnALiftedArcAndLands() {
        Cells floor = Cells.floor(-1);
        Legs.Leg[] legs = legs();
        ChainPose chain = straight(0.0, 2);
        Legs.Foot[] feet = Legs.step(floor, chain, legs, Legs.hanging(4), GAIT, 0.0, 0.3, Vec.X);
        int i = -1;
        for (int k = 0; k < 4; k++) {
            if (feet[k].swinging()) {
                i = k;
            }
        }
        assertTrue(i >= 0, "a hanging foot with a floor in reach starts a step");
        Vec rest = Legs.rest(legs[i], chain.position(legs[i].segment()), chain.orientation(legs[i].segment()));
        double peak = 0;
        int ticks = 0;
        while (feet[i].swinging()) {
            peak = Math.max(peak, feet[i].at(rest, Vec.Y, GAIT.lift()).y() - feet[i].anchor().y());
            feet = Legs.step(floor, chain, legs, feet, GAIT, 0.0, 0.3, Vec.X);
            ticks++;
        }
        assertEquals(Legs.swingTicks(0.3), ticks);
        assertTrue(peak > 0.2, "the foot lifted on the way: " + peak);
        assertTrue(feet[i].planted());
        assertEquals(feet[i], feet[i].advanced(0.5), "a planted foot has nothing to advance");
        Legs.Foot mid = new Legs.Foot(new Vec(1, 0, 0), Vec.ZERO, 0.75, true);
        assertEquals(1.0, mid.advanced(0.5).swingT(), 1e-12, "a swing advances to its end at most");
        assertEquals(0.85, mid.advanced(0.1).swingT(), 1e-12);
    }

    @Test
    void standingStillNothingSteps() {
        Cells floor = Cells.floor(-1);
        Legs.Leg[] legs = legs();
        ChainPose chain = straight(0.0, 2);
        Legs.Foot[] feet = Legs.hanging(4);
        for (int t = 0; t < 30; t++) {
            feet = Legs.step(floor, chain, legs, feet, GAIT, 0.0, 0.3, Vec.X);   // walk in place to plant
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(feet[i].planted());
        }
        Legs.Foot[] before = feet.clone();
        for (int t = 0; t < 30; t++) {
            feet = Legs.step(floor, chain, legs, feet, GAIT, 0.0, 0.0, Vec.X);
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(before[i], feet[i], "at rest every foot stays put");
        }
    }

    @Test
    void aGapHangsTheLegsAndADugAnchorReplants() {
        Legs.Leg[] legs = legs();
        ChainPose chain = straight(0.0, 2);
        Legs.Foot[] feet = Legs.step(Cells.EMPTY, chain, legs, Legs.hanging(4), GAIT, 0.0, 0.3, Vec.X);
        for (Legs.Foot f : feet) {
            assertFalse(f.planted() || f.swinging(), "nothing to stand on: hanging");
        }
        Cells floor = Cells.floor(-1);
        for (int t = 0; t < 20; t++) {
            feet = Legs.step(floor, chain, legs, feet, GAIT, 0.0, 0.3, Vec.X);
        }
        assertTrue(feet[0].planted(), "a floor come, it plants");
        // The floor goes under leg 0's anchor: it lifts to find another (none), and hangs.
        Vec anchor0 = feet[0].anchor();
        Cells dug = (x, y, z) -> (Math.floor(anchor0.x()) == x && Math.floor(anchor0.z()) == z) ? Cells.Kind.AIR : floor.at(x, y, z);
        Legs.Foot[] after = Legs.step(dug, chain, legs, feet, GAIT, 0.0, 0.0, Vec.X);
        assertFalse(after[0].planted() && after[0].anchor().equals(anchor0), "the dug anchor is given up");
    }

    @Test
    void theAimTurnsTheLegTowardItsFoot() {
        Legs.Leg leg = new Legs.Leg(0, +1, new Vec(0.5, 0, 0), new Vec(1.0, -1.0, 0));
        Vec pos = new Vec(10, 5, 10);
        Quat orient = Quat.IDENTITY;
        Vec rest = Legs.rest(leg, pos, orient);
        assertTrue(rest.near(new Vec(11.5, 4.0, 10.0), 1e-12));
        assertEquals(Math.sqrt(2.0), leg.length(), 1e-12);
        LegGait.LegPose atRest = Legs.aim(leg, pos, orient, rest);
        assertEquals(0.0, atRest.swingDeg(), 1e-9);
        assertEquals(0.0, atRest.liftDeg(), 1e-9);
        LegGait.LegPose ahead = Legs.aim(leg, pos, orient, rest.plus(new Vec(0, 0, 1.0)));
        assertEquals(45.0, ahead.swingDeg(), 1e-9, "a block forward of a block out is 45 degrees of swing");
        LegGait.LegPose higher = Legs.aim(leg, pos, orient, rest.plus(new Vec(0, 1.0, 0)));
        assertEquals(45.0, higher.liftDeg(), 1e-9, "level with the hip is 45 degrees up from the rest's 45 down");
        // A right leg mirrors.
        Legs.Leg right = new Legs.Leg(0, -1, new Vec(-0.5, 0, 0), new Vec(-1.0, -1.0, 0));
        Vec rrest = Legs.rest(right, pos, orient);
        assertEquals(45.0, Legs.aim(right, pos, orient, rrest.plus(new Vec(0, 0, 1.0))).swingDeg(), 1e-9);
        // A leg the file sweeps back: at its own rest zero, and a foot straight out is a forward swing.
        Legs.Leg swept = new Legs.Leg(0, +1, new Vec(0.5, 0, 0), new Vec(1.0, -1.0, -1.0));
        Vec srest = Legs.rest(swept, pos, orient);
        assertEquals(0.0, Legs.aim(swept, pos, orient, srest).swingDeg(), 1e-9);
        assertEquals(45.0, Legs.aim(swept, pos, orient, srest.plus(new Vec(0, 0, 1.0))).swingDeg(), 1e-9);
        // On a turned segment the same foot reads the same.
        Quat turned = Quat.fromAxisAngle(Vec.Y, Math.toRadians(90));
        Vec trest = Legs.rest(leg, pos, turned);
        assertEquals(0.0, Legs.aim(leg, pos, turned, trest).swingDeg(), 1e-9);
        assertEquals(45.0, Legs.aim(leg, pos, turned, trest.plus(turned.rotate(new Vec(0, 0, 1.0)))).swingDeg(), 1e-9);
    }

    @Test
    void theStepShortensAndLeadsWithSpeed() {
        assertEquals(4, Legs.swingTicks(0.0));
        assertEquals(3, Legs.swingTicks(0.2));
        assertEquals(2, Legs.swingTicks(0.45));
        assertEquals(0.0, Legs.lead(0.0, 26, 10, GAIT.lead()), 1e-12);
        // 26 legs, 10 swinging, 2-tick swings: a foot stands 5.2 ticks; at 0.3 a tick that is 1.56 blocks, half of it ahead.
        assertEquals(0.3 * 26.0 / 10 * 2 / 2, Legs.lead(0.3, 26, 10, GAIT.lead()), 1e-12);
        assertEquals(GAIT.lead(), Legs.lead(5.0, 26, 10, GAIT.lead()), 1e-12, "capped by the gait's lead");
        assertEquals(1.8, Legs.lead(5.0, 26, 10, 1.8), 1e-12, "a bigger body's cap");
    }

    @Test
    void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Legs.Leg(0, 2, Vec.ZERO, new Vec(1, -1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Legs.Leg(0, 1, Vec.ZERO, new Vec(-1, -1, 0)), "a rest foot on the wrong side");
        assertThrows(IllegalArgumentException.class, () -> new Legs.Foot(new Vec(1, 0, 0), Vec.ZERO, 0.5, true).advanced(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new Legs.Foot(null, null, 0.0, true));
        assertThrows(IllegalArgumentException.class, () -> Legs.step(Cells.EMPTY, straight(0, 2), legs(), Legs.hanging(3), GAIT, 0, 0, Vec.X));
        assertThrows(IllegalArgumentException.class, () -> Legs.step(Cells.EMPTY, straight(0, 2), legs(), Legs.hanging(4), GAIT, 0, -1, Vec.X));
    }

    // A tiny wrapper so the crawl test can read feet by index without clutter.
    private record Foot(Legs.Foot f) {}

    private static Foot[] wrap(Legs.Foot[] feet) {
        Foot[] out = new Foot[feet.length];
        for (int i = 0; i < feet.length; i++) {
            out[i] = new Foot(feet[i]);
        }
        return out;
    }

    private static Legs.Foot[] unwrap(Foot[] feet) {
        Legs.Foot[] out = new Legs.Foot[feet.length];
        for (int i = 0; i < feet.length; i++) {
            out[i] = feet[i].f;
        }
        return out;
    }
}
