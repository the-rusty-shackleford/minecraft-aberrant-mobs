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
 * Partitions. On a floor: straight, the head moves its speed and rides
 * at its clearance; a turn is rate-limited and reaches the wish; holding
 * stays. A wall ahead: without digging it climbs (the wall its face, up
 * its heading, its clearance off the wall); with digging it holds for the
 * dig, or is blocked when the rock is hard. A ceiling met on a wall: it
 * takes the ceiling. An edge: it wraps over onto the ledge's face heading
 * down. Nothing under it and nothing in reach: airborne, falling, landing
 * on a floor that comes. A wish through the floor: bores with digging,
 * blocked without; a wish away from the face: the nearest other face the
 * wish lies along if one is in reach (a wall-clinger steps onto the
 * floor), else blocked. Bad arguments refused; the normal nearest a
 * direction.
 */
final class CrawlTest {
    private static final Crawl.Rules R = new Crawl.Rules(1.5, 1.5, 1.6);
    private static final Cells FLOOR = Cells.floor(-1);   // the floor's top is y = 0

    private static Crawl.Pose onFloor(double x) {
        return new Crawl.Pose(new Vec(x, 1.5, 0.5), Vec.X, Crawl.Normal.UP);
    }

    @Test
    void straightOnAFloorItMovesItsSpeedAtItsClearance() {
        Crawl.Pose p = onFloor(0.0);
        for (int i = 0; i < 10; i++) {
            Crawl.Step s = Crawl.step(FLOOR, p, Vec.X, 0.4, R, false);
            assertFalse(s.digNeeded() || s.blocked() || s.turned());
            p = s.pose();
        }
        assertTrue(p.centre().near(new Vec(4.0, 1.5, 0.5), 1e-6), "four blocks on, at its clearance: " + p.centre());
        assertEquals(Crawl.Normal.UP, p.normal());
        // Started too high or too low, the snap sets it right.
        Crawl.Pose high = new Crawl.Pose(new Vec(0, 2.0, 0.5), Vec.X, Crawl.Normal.UP);
        assertEquals(1.5, Crawl.step(FLOOR, high, Vec.X, 0.0, R, false).pose().centre().y(), 0.02);
        Crawl.Pose low = new Crawl.Pose(new Vec(0, 1.0, 0.5), Vec.X, Crawl.Normal.UP);
        assertEquals(1.5, Crawl.step(FLOOR, low, Vec.X, 0.0, R, false).pose().centre().y(), 0.02);
        // Holding stays put.
        Crawl.Step held = Crawl.step(FLOOR, onFloor(3.0), Vec.ZERO, 0.4, R, false);
        assertTrue(held.pose().centre().near(new Vec(3.0, 1.5, 0.5), 1e-6));
    }

    @Test
    void aTurnIsRateLimitedAndReachesTheWish() {
        Crawl.Pose p = onFloor(0.0);
        Crawl.Step s = Crawl.step(FLOOR, p, Vec.Z, 0.1, R, false);
        double angle = Math.toDegrees(Math.acos(s.pose().heading().dot(Vec.X)));
        assertEquals(25.0, angle, 1e-6, "a tick turns twenty-five degrees");
        assertTrue(s.pose().heading().z() > 0, "toward the wish");
        for (int i = 0; i < 5; i++) {
            s = Crawl.step(FLOOR, s.pose(), Vec.Z, 0.1, R, false);
        }
        assertTrue(s.pose().heading().near(Vec.Z, 1e-9), "and is there in time");
        // Straight back turns the body's left first.
        Crawl.Step back = Crawl.step(FLOOR, onFloor(0.0), Vec.X.times(-1), 0.1, R, false);
        assertTrue(back.pose().heading().x() > 0.8 && back.pose().heading().z() < -0.3, "turning: " + back.pose().heading());
        assertTrue(Crawl.turnToward(Vec.X, Vec.Z, Vec.Y, Math.PI).near(Vec.Z, 1e-12));
    }

    @Test
    void aWallAheadIsClimbedOrDug() {
        Cells wall = (x, y, z) -> y <= -1 || x >= 8 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Crawl.Pose p = onFloor(5.0);
        Crawl.Step s = null;
        for (int i = 0; i < 12 && (s == null || s.pose().normal() == Crawl.Normal.UP); i++) {
            s = Crawl.step(wall, s == null ? p : s.pose(), Vec.X, 0.3, R, false);
        }
        assertEquals(Crawl.Normal.WEST, s.pose().normal(), "the wall's face, which looks west, is its face");
        assertTrue(s.pose().heading().near(Vec.Y, 1e-9), "and up is its heading");
        assertEquals(8.0 - 1.5, s.pose().centre().x(), 0.02, "its clearance off the wall: " + s.pose().centre());
        assertTrue(s.turned());
        double climbY = s.pose().centre().y();
        s = Crawl.step(wall, s.pose(), Vec.Y, 0.3, R, false);
        assertEquals(climbY + 0.3, s.pose().centre().y(), 1e-6, "and it climbs");
        // Digging instead: it holds for the dig, the section ahead is rock.
        Crawl.Step dig = Crawl.step(wall, onFloor(6.5), Vec.X, 0.3, R, true);
        assertTrue(dig.digNeeded() && !dig.blocked());
        assertTrue(dig.pose().centre().near(onFloor(6.5).centre(), 1e-9), "holding");
        assertFalse(Tunnel.rock(wall, Crawl.section(dig.pose(), R)).isEmpty());
        // Hard rock: blocked either way.
        Cells hard = (x, y, z) -> y <= -1 ? Cells.Kind.ROCK : x >= 8 ? Cells.Kind.HARD : Cells.Kind.AIR;
        assertTrue(Crawl.step(hard, onFloor(6.5), Vec.X, 0.3, R, true).blocked());
        // Rock ahead and rock above: a dead end.
        Cells box = (x, y, z) -> y <= -1 || x >= 8 || y >= 3 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        assertTrue(Crawl.step(box, onFloor(6.5), Vec.X, 0.3, R, false).blocked());
    }

    @Test
    void aCeilingMetOnAWallBecomesItsFace() {
        Cells room = (x, y, z) -> y <= -1 || x >= 8 || y >= 10 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Crawl.Pose p = new Crawl.Pose(new Vec(6.5, 6.0, 0.5), Vec.Y, Crawl.Normal.WEST);
        Crawl.Step s = null;
        for (int i = 0; i < 20 && (s == null || s.pose().normal() == Crawl.Normal.WEST); i++) {
            s = Crawl.step(room, s == null ? p : s.pose(), Vec.Y, 0.3, R, false);
        }
        assertEquals(Crawl.Normal.DOWN, s.pose().normal(), "the ceiling's face looks down");
        assertTrue(s.pose().heading().near(Vec.X.times(-1), 1e-9), "heading back along it, the old up");
        assertEquals(10.0 - 1.5, s.pose().centre().y(), 0.02, "hanging its clearance under it");
    }

    @Test
    void anEdgeIsWrappedOverOntoTheLedgesFace() {
        Cells ledge = (x, y, z) -> y <= -1 && y >= -6 && x < 5 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Crawl.Pose p = onFloor(3.0);
        Crawl.Step s = null;
        for (int i = 0; i < 12 && (s == null || s.pose().normal() == Crawl.Normal.UP); i++) {
            s = Crawl.step(ledge, s == null ? p : s.pose(), Vec.X, 0.3, R, false);
        }
        assertEquals(Crawl.Normal.EAST, s.pose().normal(), "the ledge's face looks east");
        assertTrue(s.pose().heading().near(new Vec(0, -1, 0), 1e-9), "heading down it");
        assertEquals(5.0 + 1.5, s.pose().centre().x(), 0.02, "its clearance off the face: " + s.pose().centre());
        assertTrue(s.pose().centre().y() < 0.0 && s.pose().centre().y() > -2.0, "just under the edge: " + s.pose().centre());
        double edgeY = s.pose().centre().y();
        s = Crawl.step(ledge, s.pose(), new Vec(0, -1, 0), 0.3, R, false);
        assertEquals(edgeY - 0.3, s.pose().centre().y(), 1e-6, "and walks down");
    }

    @Test
    void withNothingUnderItAndNothingInReachItFallsAndLandsWhenAFloorComes() {
        Crawl.Pose over = new Crawl.Pose(new Vec(0.5, 1.5, 0.5), Vec.X, Crawl.Normal.UP);
        Crawl.Step s = Crawl.step(Cells.EMPTY, over, Vec.X, 0.3, R, false);
        assertTrue(s.pose().airborne(), "no floor, no ledge, nothing in reach: airborne");
        Vec c = s.pose().centre();
        s = Crawl.step(Cells.EMPTY, s.pose(), Vec.X, 0.3, R, false);
        assertEquals(c.y() - Crawl.FALL, s.pose().centre().y(), 1e-9, "falling");
        assertTrue(s.pose().airborne());
        Cells deep = Cells.floor(-4);   // top at -3
        Crawl.Pose falling = new Crawl.Pose(new Vec(0.5, -0.9, 0.5), Vec.X, Crawl.Normal.NONE);
        s = Crawl.step(deep, falling, Vec.X, 0.3, R, false);
        assertEquals(Crawl.Normal.UP, s.pose().normal(), "the floor within reach: landed");
        assertEquals(-3.0 + 1.5, s.pose().centre().y(), 0.02);
        assertTrue(s.pose().heading().near(Vec.X, 1e-9), "keeping its heading");
        // Attaching straight down keeps the heading; attaching to a wall when heading at it picks a way along it.
        Cells wallOnly = (x, y, z) -> x >= 3 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Crawl.Pose at = Crawl.attach(wallOnly, new Vec(1.8, 5, 0.5), Vec.X, R, 2.0);
        assertEquals(Crawl.Normal.WEST, at.normal());
        assertEquals(0.0, at.heading().x(), 1e-9, "a heading in the face");
        assertNull(Crawl.attach(Cells.EMPTY, Vec.ZERO, Vec.X, R, 2.0));
    }

    @Test
    void aWishThroughTheFloorBoresWithDiggingAndIsBlockedWithout() {
        Vec down = new Vec(0, -1, 0);
        Crawl.Step bore = Crawl.step(FLOOR, onFloor(3.0), down, 0.3, R, true);
        assertTrue(bore.digNeeded() && bore.turned() && !bore.blocked());
        assertTrue(bore.pose().heading().near(down, 1e-9), "heading into the floor");
        assertEquals(Crawl.Normal.WEST, bore.pose().normal(), "its feet on the shaft's wall ahead, which looks back west");
        assertTrue(Tunnel.rock(FLOOR, Crawl.section(bore.pose(), R)).size() >= 9, "a shaft's worth of rock to cut");
        Crawl.Step no = Crawl.step(FLOOR, onFloor(3.0), down, 0.3, R, false);
        assertTrue(no.blocked() && !no.digNeeded());
        assertTrue(no.pose().centre().near(onFloor(3.0).centre(), 1e-6), "and it stays");
        Crawl.Step up = Crawl.step(FLOOR, onFloor(3.0), Vec.Y, 0.3, R, true);
        assertTrue(up.blocked(), "it cannot leave its face upward with nothing else to cling to");
    }

    @Test
    void aWishOffItsFaceTakesTheFaceTheWishLiesAlong() {
        // On a wall to the west (its face looks east) at floor level, wanting east across the floor: it steps onto the floor.
        Cells corner = (x, y, z) -> y <= -1 || x <= -1 ? Cells.Kind.ROCK : Cells.Kind.AIR;
        Crawl.Pose onWall = new Crawl.Pose(new Vec(1.5, 1.0, 0.5), Vec.Y, Crawl.Normal.EAST);
        Crawl.Step s = Crawl.step(corner, onWall, Vec.X, 0.3, R, true);
        assertEquals(Crawl.Normal.UP, s.pose().normal(), "the floor, along which east lies");
        assertTrue(s.pose().heading().near(Vec.X, 1e-9), "heading east on it");
        assertEquals(1.5, s.pose().centre().y(), 0.02, "at its clearance over the floor");
        assertTrue(s.turned() && !s.blocked());
        // Wanting straight out from the wall with no floor in reach: blocked, as before.
        Crawl.Pose high = new Crawl.Pose(new Vec(1.5, 8.0, 0.5), Vec.Y, Crawl.Normal.EAST);
        assertTrue(Crawl.step(corner, high, Vec.X, 0.3, R, false).blocked(), "nothing else to cling to");
        assertEquals(Crawl.Normal.EAST, Crawl.step(corner, high, Vec.X, 0.3, R, false).pose().normal());
        // On the floor wanting up with a wall in reach: takes the wall, heading up it.
        Crawl.Pose byWall = new Crawl.Pose(new Vec(1.0, 1.5, 0.5), Vec.X.times(-1), Crawl.Normal.UP);
        Crawl.Step up = Crawl.step(corner, byWall, Vec.Y, 0.3, R, false);
        assertEquals(Crawl.Normal.EAST, up.pose().normal(), "the wall, along which up lies");
        assertTrue(up.pose().heading().near(Vec.Y, 1e-9));
        assertTrue(Crawl.attachAlong(Cells.EMPTY, Vec.ZERO, Vec.Y, Crawl.Normal.UP, R, 2.0) == null);
    }

    @Test
    void badArgumentsAreRefusedAndTheNearestNormalFound() {
        assertThrows(IllegalArgumentException.class, () -> new Crawl.Pose(Vec.ZERO, new Vec(0, 0, 2), Crawl.Normal.UP));
        assertThrows(IllegalArgumentException.class, () -> new Crawl.Pose(Vec.ZERO, Vec.Y, Crawl.Normal.UP));
        assertThrows(IllegalArgumentException.class, () -> new Crawl.Rules(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Crawl.step(FLOOR, onFloor(0), Vec.X, -1, R, false));
        assertThrows(IllegalArgumentException.class, () -> Crawl.Normal.nearest(Vec.ZERO));
        assertEquals(Crawl.Normal.EAST, Crawl.Normal.nearest(new Vec(0.9, 0.1, 0.3)));
        assertEquals(Crawl.Normal.DOWN, Crawl.Normal.nearest(new Vec(0.1, -2, 0)));
        assertEquals(Crawl.Normal.NORTH, Crawl.Normal.SOUTH.opposite());
        assertTrue(new Crawl.Pose(Vec.ZERO, Vec.Y, Crawl.Normal.NONE).airborne(), "airborne, any heading");
    }
}
