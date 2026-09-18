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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Placement yaw: all frames, oblique/vertical looks, wall/ceiling fallback, invalid angles.
 * Partitions. Gravity: the nearest to a vector; toward a face; opposites.
 * Frame: every gravity's frame is right-handed with up against it; the
 * world's is the game's; local to world and back are inverse; the box on
 * the floor is the game's formula and on a wall the axis swap with the
 * feet on the wall; the eye is up from the feet; the rotation takes the
 * axes to the frame; codes round trip. Camera angles: compose then
 * decompose gives the angles back for ordinary looks and the gimbal
 * rows; a frame's rotation shows as a roll. Transition: into a wall
 * takes it when moving into it fast enough and the box fits, not when
 * creeping, not into its own floor or ceiling, not when the box does not
 * fit; over an edge wraps onto the ledge's face; release lands the least
 * way up that fits. Blend: from at the start, to at the end, between in
 * between, done after.
 */
final class FrameTest {
    @Test
    void placementYawProjectsTheLookAndHasAStableVerticalFallback() {
        for (Gravity gravity : Gravity.values()) {
            Frame frame = Frame.of(gravity);
            for (double yaw : new double[] {-135, -90, 0, 37, 90, 180}) {
                for (double pitch : new double[] {-90, -25, 0, 60, 90}) {
                    Vec look = frame.toWorld(CameraAngles.compose(new CameraAngles.Angles(yaw, pitch, 0)).rotate(Vec.Z));
                    Vec heading = Frame.headingOf(frame.placementYaw(yaw, pitch));
                    assertEquals(0, heading.y(), 1e-9);
                    assertEquals(1, heading.length(), 1e-9);
                    double length = Math.hypot(look.x(), look.z());
                    if (length > 1e-5) {
                        assertTrue(heading.near(new Vec(look.x() / length, 0, look.z() / length), 1e-7));
                    }
                }
            }
        }
        assertEquals(-90, Frame.of(Gravity.EAST).placementYaw(0, 0), 1e-9, "up the east wall falls back toward that wall");
        assertEquals(90, Frame.of(Gravity.WEST).placementYaw(0, 0), 1e-9);
        assertEquals(-37, Frame.of(Gravity.UP).placementYaw(37, 90), 1e-9, "ceiling keeps its horizontal heading");
        assertEquals(37, Frame.WORLD.placementYaw(37, -90), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Frame.WORLD.placementYaw(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> Frame.WORLD.placementYaw(0, Double.POSITIVE_INFINITY));
    }

    @Test
    void gravityIsAnAxisNearestTheVector() {
        assertEquals(Gravity.DOWN, Gravity.nearest(new Vec(0.1, -1, 0.2)));
        assertEquals(Gravity.WEST, Gravity.toward(Vec.X), "a face looking east pulls west");
        assertEquals(Gravity.UP, Gravity.toward(new Vec(0, -1, 0)), "a ceiling pulls up");
        assertEquals(Gravity.NORTH, Gravity.SOUTH.opposite());
        assertTrue(Gravity.DOWN.isDown() && !Gravity.EAST.isDown());
        assertThrows(IllegalArgumentException.class, () -> Gravity.nearest(Vec.ZERO));
    }

    @Test
    void everyFrameIsRightHandedWithUpAgainstItsGravityAndTheWorldsIsTheGames() {
        for (Gravity g : Gravity.values()) {
            Frame f = Frame.of(g);
            assertTrue(f.up().near(g.dir.times(-1), 1e-12), g + " up is against gravity");
            assertTrue(f.right().cross(f.up()).near(f.forward(), 1e-12), g + " is right-handed");
            Vec local = new Vec(0.3, 1.2, -0.7);
            assertTrue(f.toLocal(f.toWorld(local)).near(local, 1e-12), g + " round trips");
            assertEquals(g.ordinal(), f.code());
            assertEquals(f, Frame.decode(f.code()));
        }
        assertEquals(Frame.WORLD, Frame.of(Gravity.DOWN));
        assertTrue(Frame.WORLD.toWorld(new Vec(1, 2, 3)).near(new Vec(1, 2, 3), 1e-12));
        assertEquals(Frame.WORLD, Frame.decode(99), "a bad code is the world's");
        assertThrows(IllegalArgumentException.class, () -> new Frame(Gravity.DOWN, Vec.X, Vec.Y, Vec.Y));
        assertThrows(IllegalArgumentException.class, () -> new Frame(Gravity.UP, Vec.X, Vec.Y, Vec.Z), "up must be against the gravity");
    }

    @Test
    void theBoxIsTheGamesOnTheFloorAndTheAxisSwapOnAWall() {
        Vec feet = new Vec(10, 64, 10);
        Frame.Box floor = Frame.WORLD.box(feet, 0.6, 1.8);
        assertTrue(floor.lo().near(new Vec(9.7, 64, 9.7), 1e-12) && floor.hi().near(new Vec(10.3, 65.8, 10.3), 1e-12), "the game's own box: " + floor);
        Frame.Box wall = Frame.of(Gravity.WEST).box(feet, 0.6, 1.8);
        assertTrue(wall.lo().near(new Vec(10, 63.7, 9.7), 1e-12) && wall.hi().near(new Vec(11.8, 64.3, 10.3), 1e-12), "on a west wall the box runs east from the feet: " + wall);
        Frame.Box ceiling = Frame.of(Gravity.UP).box(feet, 0.6, 1.8);
        assertTrue(ceiling.lo().near(new Vec(9.7, 62.2, 9.7), 1e-12) && ceiling.hi().near(new Vec(10.3, 64, 10.3), 1e-12), "under the ceiling it hangs down: " + ceiling);
        assertTrue(Frame.of(Gravity.WEST).eye(feet, 1.62).near(new Vec(11.62, 64, 10), 1e-12), "the eyes are up along the frame");
        assertTrue(wall.size().near(new Vec(1.8, 0.6, 0.6), 1e-9), "1.8 along the wall's normal: " + wall.size());
        assertTrue(wall.contains(new Vec(11, 64, 10)) && !wall.contains(new Vec(9, 64, 10)));
        assertTrue(Frame.of(Gravity.WEST).rotation().rotate(Vec.Y).near(Vec.X, 1e-9), "the rotation stands Y up along the wall's normal");
        assertTrue(Frame.WORLD.rotation().near(Quat.IDENTITY, 1e-12));
    }

    @Test
    void cameraAnglesComposeAndDecomposeEitherWay() {
        for (double[] a : new double[][] {{0, 0, 0}, {45, 10, 0}, {-120, -30, 0}, {170, 60, 20}, {30, -80, -40}, {0, 89.9, 0}}) {
            CameraAngles.Angles in = new CameraAngles.Angles(a[0], a[1], a[2]);
            CameraAngles.Angles out = CameraAngles.decompose(CameraAngles.compose(in));
            assertEquals(a[0], out.yaw(), 1e-6, "yaw of " + in);
            assertEquals(a[1], out.pitch(), 1e-6, "pitch of " + in);
            assertEquals(a[2], out.roll(), 1e-6, "roll of " + in);
        }
        // The game's view vector: yaw 0 faces +Z, yaw 90 faces -X, pitch 90 looks down.
        assertTrue(CameraAngles.compose(new CameraAngles.Angles(0, 0, 0)).rotate(Vec.Z).near(Vec.Z, 1e-12));
        assertTrue(CameraAngles.compose(new CameraAngles.Angles(90, 0, 0)).rotate(Vec.Z).near(Vec.X.times(-1), 1e-9));
        assertTrue(CameraAngles.compose(new CameraAngles.Angles(0, 90, 0)).rotate(Vec.Z).near(Vec.Y.times(-1), 1e-9));
        // Straight down: the gimbal row: yaw carries it all, roll zero, and the composition still looks down.
        CameraAngles.Angles down = CameraAngles.decompose(CameraAngles.compose(new CameraAngles.Angles(40, 90, 0)));
        assertEquals(90.0, down.pitch(), 1e-6);
        assertEquals(0.0, down.roll(), 1e-6);
        assertTrue(CameraAngles.compose(down).rotate(Vec.Z).near(Vec.Y.times(-1), 1e-6));
        // A wearer on a west wall looking along its forward (the world's up) with no local turn: the camera rolls.
        Quat onWall = Frame.of(Gravity.WEST).rotation().times(CameraAngles.compose(new CameraAngles.Angles(0, 0, 0)));
        CameraAngles.Angles seen = CameraAngles.decompose(onWall);
        assertEquals(-90.0, seen.pitch(), 1e-6, "looking up the wall is looking up");
        assertEquals(0.0, CameraAngles.norm(360.0), 1e-12);
        assertEquals(-170.0, CameraAngles.norm(190.0), 1e-12);
    }

    @Test
    void walkingIntoAWallTakesItWhenPushingAndFitting() {
        Vec feet = new Vec(10.0, 64, 10.5);   // a wearer on the floor just west of a wall whose face is x = 10.3
        java.util.function.Predicate<Frame.Box> free = b -> true;
        Optional<Transition.Stance> s = Transition.intoWall(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.1, 0, 0), Vec.X.times(-1), free);
        assertTrue(s.isPresent(), "moving east into a west-looking face");
        assertEquals(Gravity.EAST, s.get().frame().gravity(), "the wall pulls east");
        assertEquals(10.3, s.get().feet().x(), 1e-9, "the feet on the face");
        assertEquals(64.9, s.get().feet().y(), 1e-9, "at the old box's middle");
        assertEquals(0.0, s.get().yaw(), 1e-9, "facing up the wall: the frame's forward");
        assertTrue(Transition.intoWall(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.005, 0, 0), Vec.X.times(-1), free).isEmpty(), "creeping does not take it");
        assertTrue(Transition.intoWall(Frame.WORLD, feet, 0.6, 1.8, new Vec(0, -0.1, 0), Vec.Y, free).isEmpty(), "its own floor is not a wall");
        assertTrue(Transition.intoWall(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.1, 0, 0), Vec.X.times(-1), b -> false).isEmpty(), "no room on the wall");
        Optional<Transition.Stance> ceiling = Transition.intoWall(Frame.of(Gravity.EAST), s.get().feet(), 0.6, 1.8, new Vec(0, 0.1, 0), Vec.Y.times(-1), free);
        assertTrue(ceiling.isPresent() && ceiling.get().frame().gravity() == Gravity.UP, "from the wall up into the ceiling");
        assertTrue(Frame.headingOf(ceiling.get().yaw()).near(Frame.of(Gravity.UP).toLocal(Vec.X.times(-1)), 1e-9), "and facing on along the wall's up, which was west");
        assertEquals(90.0, Frame.WORLD.yawToward(Vec.X.times(-1)), 1e-9, "the game's yaw: 90 faces -X");
        assertEquals(0.0, Frame.WORLD.yawToward(Vec.Y), 1e-9, "straight up has no floor part");
        assertTrue(Frame.headingOf(-90.0).near(Vec.X, 1e-9));
    }

    @Test
    void overAnEdgeWrapsOntoTheLedgeAndReleaseLandsTheLeastWayUp() {
        Vec feet = new Vec(10.0, 64, 10.5);
        Optional<Transition.Stance> s = Transition.overEdge(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.1, 0, 0), new Vec(9.7, 63.69, 10.5), b -> true);
        assertTrue(s.isPresent());
        assertEquals(Gravity.WEST, s.get().frame().gravity(), "the ledge's east face pulls west");
        assertTrue(s.get().feet().x() == 9.7 && s.get().feet().y() < 64.0, "just past and under the edge: " + s.get().feet());
        assertTrue(Frame.headingOf(s.get().yaw()).near(s.get().frame().toLocal(Vec.Y.times(-1)), 1e-9), "facing down the face");
        assertTrue(Transition.overEdge(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.001, 0, 0), new Vec(9.7, 63.69, 10.5), b -> true).isEmpty(), "standing still");
        assertTrue(Transition.overEdge(Frame.WORLD, feet, 0.6, 1.8, new Vec(0.1, 0, 0), new Vec(9.7, 63.69, 10.5), b -> false).isEmpty(), "no face to take");
        // On a wall to the east (its face at x = 10.3, gravity east, up west): letting go moves the feet west until the world's box clears the wall.
        Vec onWall = new Vec(10.3, 66, 10.5);
        Optional<Transition.Stance> r = Transition.release(Frame.of(Gravity.EAST), onWall, 0.6, 1.8, b -> b.hi().x() <= 10.3 + 1e-9);
        assertTrue(r.isPresent() && r.get().frame().equals(Frame.WORLD), "let go to the world's down");
        assertEquals(10.0, r.get().feet().x(), 1e-9, "moved along the old up until the box cleared the wall");
        assertTrue(Transition.release(Frame.of(Gravity.EAST), onWall, 0.6, 1.8, b -> false).isEmpty());
        assertEquals(feet, Transition.release(Frame.WORLD, feet, 0.6, 1.8, b -> true).get().feet(), "already the world's: unmoved");
        assertTrue(Double.isNaN(r.get().yaw()), "letting go keeps the yaw");
    }

    @Test
    void aBlendSwingsBetweenItsEnds() {
        Quat a = Quat.IDENTITY, b = Quat.fromAxisAngle(Vec.Z, Math.toRadians(90));
        Blend blend = new Blend(a, b, Blend.TICKS, 100);
        assertTrue(blend.at(100).near(a, 1e-12) && blend.at(90).near(a, 1e-12));
        assertTrue(blend.at(106).near(b, 1e-12) && blend.at(200).near(b, 1e-12));
        Quat mid = blend.at(103);
        assertTrue(mid.rotate(Vec.X).near(new Vec(Math.cos(Math.toRadians(45)), Math.sin(Math.toRadians(45)), 0), 1e-9), "half way, half the turn");
        assertTrue(blend.at(101).rotate(Vec.X).y() < mid.rotate(Vec.X).y(), "eased: slow to start");
        assertFalse(blend.done(105.9));
        assertTrue(blend.done(106));
        assertThrows(IllegalArgumentException.class, () -> new Blend(a, b, 0, 0));
    }

    @Test
    void releaseIncludesTheFullHeightWhenHangingFromTheCeiling() {
        // Minecraft's player height is a float, just below the double 1.8.
        // A 0.1 loop must still try its endpoint: only the full height clears.
        double height = 1.8f;
        Vec feet = new Vec(0, 14, 0);
        var stance = Transition.release(Frame.of(Gravity.UP), feet, 0.6, height, box -> box.hi().y() <= 14);
        assertTrue(stance.isPresent(), "the final candidate clears the ceiling");
        assertEquals(14 - height, stance.orElseThrow().feet().y(), 1e-9);
    }
}
