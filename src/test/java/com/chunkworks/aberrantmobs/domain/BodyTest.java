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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Naming: the Face-Stealer's names resolve; a missing name is
 * refused; the arcs come off the file's pivots (0 for the head and s01,
 * 11/16 apart after); the axis height off the head's pivot. Legs: each
 * hangs from its segment, its hip off the pivots, its rest foot at the
 * tarsus tip as the file sweeps it (forward at the head, back at the
 * tail), out to its own side; a leg with no cubes is refused. Posing:
 * chain bones are placed absolutely at (position - origin) / scale, legs
 * lifted then swung with the sign by side; a pose of the wrong size
 * refused.
 */
final class BodyTest {
    private static final Path FACE_STEALER = Path.of("src/main/resources/assets/aberrantmobs/aberrantmobs/model/face_stealer.bbmodel");
    static final List<String> CHAIN = List.of("s01", "s02", "s03", "s04", "s05", "s06", "s07", "s08", "s09", "s10", "tail");
    static final List<String> LEGS = List.of("head_leg", "s01_leg", "s02_leg", "s03_leg", "s04_leg", "s05_leg", "s06_leg", "s07_leg", "s08_leg", "s09_leg", "s10_leg", "tail_leg1", "tail_leg2");

    static Rig rig() throws IOException {
        return BbRig.parse(Files.readString(FACE_STEALER, StandardCharsets.UTF_8));
    }

    @Test
    void theFaceStealersNamesResolveAndItsArcsComeOffThePivots() throws IOException {
        Rig rig = rig();
        Body b = Body.of(rig, "head", CHAIN, LEGS, "_l", "_r", 1.0 / 16.0);
        double[] arcs = b.arcBack();
        assertEquals(12, arcs.length);
        assertEquals(0.0, arcs[0], 1e-12);
        assertEquals(0.0, arcs[1], 1e-12, "s01 shares the head's pivot");
        assertEquals(11.0 / 16.0, arcs[2], 1e-9);
        assertEquals(110.0 / 16.0, arcs[11], 1e-9, "the tail");
        assertEquals(13, b.legPairs());
        assertEquals(23.3 / 16.0, b.axisHeight(), 1e-9);
        assertEquals(110.0 / 16.0, b.length(), 1e-9);
        assertEquals(0, b.chainIndexOf("head"));
        assertEquals(5, b.chainIndexOf("s05"));
        assertEquals(11, b.chainIndexOf("tail"));
        assertEquals(-1, b.chainIndexOf("s05_leg_l"), "a leg is no chain bone");
        assertEquals(rig.bone("s05").getAsInt(), b.chainBone(5));
        assertThrows(IllegalArgumentException.class, () -> Body.of(rig, "head", List.of("s99"), LEGS, "_l", "_r", 1.0 / 16.0));
        assertThrows(IllegalArgumentException.class, () -> Body.of(rig, "head", CHAIN, List.of("nope"), "_l", "_r", 1.0 / 16.0));
        assertThrows(IllegalArgumentException.class, () -> Body.of(rig, "head", List.of("tail", "s01"), LEGS, "_l", "_r", 1.0 / 16.0));
    }

    @Test
    void theLegsHangFromTheirSegmentsWithTheirFeetAsTheFileSweepsThem() throws IOException {
        Rig rig = rig();
        Body b = Body.of(rig, "head", CHAIN, LEGS, "_l", "_r", 1.0 / 16.0);
        assertEquals(26, b.legCount());
        Legs.Leg s05l = b.leg(10), s05r = b.leg(11);   // pair 5: s05_leg
        assertEquals(5, s05l.segment());
        assertEquals(+1, s05l.side());
        assertEquals(-1, s05r.side());
        assertTrue(s05l.hip().near(new Vec(7.68 / 16, (17.4 - 22.9) / 16, (-49.5 + 44.0) / 16), 1e-9), "the hip off the pivots: " + s05l.hip());
        assertTrue(s05r.hip().near(new Vec(-7.68 / 16, (17.4 - 22.9) / 16, (-49.5 + 44.0) / 16), 1e-9));
        Vec foot = s05l.rest();
        assertTrue(foot.x() > 1.3 && foot.x() < 1.7, "the tarsus tip a block and a half out: " + foot);
        assertTrue(foot.y() < -0.85 && foot.y() > -1.0, "and near a block down: " + foot);
        assertTrue(foot.z() < 0 && foot.z() > -0.4, "swept a little back by the file's ten degrees: " + foot);
        assertTrue(s05r.rest().near(new Vec(-foot.x(), foot.y(), foot.z()), 1e-9), "the right leg mirrors");
        assertTrue(b.leg(0).rest().z() > 0.4, "the head's legs reach forward: " + b.leg(0).rest());
        assertTrue(b.leg(25).rest().z() < -1.0, "the tail's last legs sweep back: " + b.leg(25).rest());
        assertEquals(11, b.leg(24).segment(), "the tail's legs hang from the tail");
        assertEquals(0, b.leg(0).segment(), "the head's from the head");
        assertTrue(b.legs() != b.legs(), "a fresh copy each time");
    }

    @Test
    void thePosePlacesTheChainAndTurnsTheLegsBySide() throws IOException {
        Rig rig = rig();
        double scale = 1.0 / 16.0;
        Body b = Body.of(rig, "head", CHAIN, LEGS, "_l", "_r", scale);
        Trail t = Trail.seeded(new Vec(100, 64 + b.axisHeight(), 100), Vec.X, Vec.Y, 10.0, 64);
        Undulation none = new Undulation(0, 0, 5.5, 0.35, 0.02, 0);
        ChainPose chain = ChainPose.of(t, b.arcBack(), none, none.rest());
        LegGait.LegPose[] legs = new LegGait.LegPose[26];
        for (int i = 0; i < 26; i++) {
            legs[i] = new LegGait.LegPose(i == 0 || i == 1 ? 20.0 : 0.0, i == 0 || i == 1 ? 10.0 : 0.0);
        }
        Vec origin = new Vec(100, 64, 100);
        Pose pose = b.pose(chain, legs, origin);
        int head = rig.bone("head").getAsInt();
        Xform placed = pose.absolute(head);
        assertTrue(placed != null, "the head is placed outright");
        assertTrue(placed.translation().near(new Vec(0, 23.3, 0), 1e-6), "at the axis height over the origin, in units: " + placed.translation());
        int s02 = rig.bone("s02").getAsInt();
        assertTrue(pose.absolute(s02).translation().near(new Vec(-11, 23.3, 0), 1e-6), "eleven units behind along -X: " + pose.absolute(s02).translation());
        assertTrue(pose.absolute(s02).rotation().rotate(Vec.Z).near(Vec.X, 1e-9), "facing +X");
        // The first pair: left lifts +10 about Z then swings -20 about Y; right the reverse.
        int left = rig.bone("head_leg_l").getAsInt(), right = rig.bone("head_leg_r").getAsInt();
        Quat ly = Quat.fromAxisAngle(Vec.Y, Math.toRadians(-20)), lz = Quat.fromAxisAngle(Vec.Z, Math.toRadians(10));
        assertTrue(pose.local(left).near(ly.times(lz), 1e-9));
        assertTrue(pose.local(right).near(ly.conjugate().times(lz.conjugate()), 1e-9));
        assertTrue(pose.local(left).rotate(new Vec(1, 0, 0)).y() > 0.17, "a left leg pointing out lifts its tip");
        assertTrue(pose.local(left).rotate(new Vec(1, 0, 0)).z() > 0.3, "and swings it forward");
        assertTrue(pose.local(right).rotate(new Vec(-1, 0, 0)).y() > 0.17, "a right leg pointing out lifts its tip");
        assertTrue(pose.local(right).rotate(new Vec(-1, 0, 0)).z() > 0.3, "and swings it forward");
        assertTrue(pose.local(rig.bone("s01_leg_l").getAsInt()).near(Quat.IDENTITY, 1e-12));
        assertThrows(IllegalArgumentException.class, () -> b.pose(chain, new LegGait.LegPose[3], origin));
    }
}
