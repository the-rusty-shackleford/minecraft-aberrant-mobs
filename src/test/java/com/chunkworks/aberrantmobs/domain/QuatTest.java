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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Construction: unit accepted, non-unit refused, zero refused
 * by normalized, non-finite refused. Euler: each single axis at 90 degrees
 * turns the right axis the right way; a triple matches Blockbench's X then
 * Y then Z order as {@link BbRig#rotate} applies it, over several angle
 * sets including negatives and those past 90. Composition: times is
 * right-to-left; conjugate undoes. Slerp: the endpoints exact, the middle
 * of a quarter turn an eighth turn, the short way round taken.
 */
final class QuatTest {
    private static final double EPS = 1e-9;

    @Test
    void aUnitIsAcceptedAndOthersRefused() {
        new Quat(1, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new Quat(1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Quat.normalized(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Quat(Double.NaN, 0, 0, 0));
        assertTrue(Quat.normalized(2, 0, 0, 0).near(Quat.IDENTITY, EPS));
    }

    @Test
    void aQuarterTurnAboutEachAxisTurnsTheRightWay() {
        // Right-handed: +Z about Y goes to +X; +X about Z goes to +Y; +Y about X goes to +Z.
        assertTrue(Quat.fromEulerXYZDegrees(0, 90, 0).rotate(Vec.Z).near(Vec.X, EPS));
        assertTrue(Quat.fromEulerXYZDegrees(0, 0, 90).rotate(Vec.X).near(Vec.Y, EPS));
        assertTrue(Quat.fromEulerXYZDegrees(90, 0, 0).rotate(Vec.Y).near(Vec.Z, EPS));
    }

    @Test
    void eulerAnglesMatchBlockbenchsOrder() {
        double[][] sets = {{0, 0, 0}, {30, 0, 0}, {0, 45, 0}, {0, 0, -37.867}, {12, -70, 154}, {124.3, 154.3, 66.6}, {-90, 90, 0}};
        Vec[] points = {new Vec(1, 2, 3), new Vec(-4, 0.5, 7), new Vec(0, 0, 1)};
        double[] origin = {0, 0, 0};
        for (double[] r : sets) {
            Quat q = Quat.fromEulerXYZDegrees(r[0], r[1], r[2]);
            for (Vec p : points) {
                Vec expected = BbRig.rotate(p, origin, r);
                assertTrue(q.rotate(p).near(expected, 1e-9), "angles " + r[0] + "," + r[1] + "," + r[2] + " on " + p + ": " + q.rotate(p) + " vs " + expected);
            }
        }
    }

    @Test
    void timesComposesRightToLeftAndConjugateUndoes() {
        Quat a = Quat.fromEulerXYZDegrees(0, 90, 0);
        Quat b = Quat.fromEulerXYZDegrees(0, 0, 90);
        Vec p = new Vec(1, 2, 3);
        assertTrue(a.times(b).rotate(p).near(a.rotate(b.rotate(p)), EPS));
        assertTrue(a.conjugate().rotate(a.rotate(p)).near(p, EPS));
        assertTrue(a.times(a.conjugate()).near(Quat.IDENTITY, EPS));
    }

    @Test
    void lookAlongTakesForwardToForwardAndUpToUpRightHanded() {
        Quat q = Quat.lookAlong(Vec.X, Vec.Y);
        assertTrue(q.rotate(Vec.Z).near(Vec.X, EPS));
        assertTrue(q.rotate(Vec.Y).near(Vec.Y, EPS));
        assertTrue(q.rotate(Vec.X).near(new Vec(0, 0, -1), EPS), "the model's +X goes to up x forward");
        // A slanted up is made perpendicular; a wall's up is honoured; parallel refused.
        Quat slant = Quat.lookAlong(Vec.X, new Vec(1, 1, 0));
        assertTrue(slant.rotate(Vec.Y).near(Vec.Y, EPS));
        Quat wall = Quat.lookAlong(Vec.Y, new Vec(0, 0, -1));
        assertTrue(wall.rotate(Vec.Z).near(Vec.Y, EPS) && wall.rotate(Vec.Y).near(new Vec(0, 0, -1), EPS));
        assertThrows(IllegalArgumentException.class, () -> Quat.lookAlong(Vec.X, Vec.X));
        // Every basis round-trips through fromBasis.
        for (Quat r : new Quat[] {Quat.fromEulerXYZDegrees(30, 40, 50), Quat.fromEulerXYZDegrees(-170, 10, 95), Quat.fromEulerXYZDegrees(0, 180, 0)}) {
            Quat back = Quat.fromBasis(r.rotate(Vec.X), r.rotate(Vec.Y), r.rotate(Vec.Z));
            assertTrue(back.near(r, 1e-9), r + " vs " + back);
        }
    }

    @Test
    void slerpHoldsItsEndpointsAndHalvesAQuarterTurn() {
        Quat a = Quat.IDENTITY;
        Quat b = Quat.fromAxisAngle(Vec.Y, Math.PI / 2);
        assertTrue(a.slerp(b, 0).near(a, EPS));
        assertTrue(a.slerp(b, 1).near(b, EPS));
        assertTrue(a.slerp(b, 0.5).near(Quat.fromAxisAngle(Vec.Y, Math.PI / 4), 1e-9));
        // The short way: toward -b (the same rotation) still lands on b.
        Quat minusB = new Quat(-b.w(), -b.x(), -b.y(), -b.z());
        assertTrue(a.slerp(minusB, 0.5).near(Quat.fromAxisAngle(Vec.Y, Math.PI / 4), 1e-9));
        assertEquals(1.0, Math.sqrt(a.slerp(b, 0.3).w() * a.slerp(b, 0.3).w() + a.slerp(b, 0.3).y() * a.slerp(b, 0.3).y()), 1e-9);
    }
}
