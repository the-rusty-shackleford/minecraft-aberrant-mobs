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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Construction: a mesh per bone, bone 0 a root, a parent must
 * come first. Placement: rest reproduces the chain; a local turn turns a
 * bone and its children about the bone's pivot and nothing above it; an
 * absolute placement replaces the chain for that bone and its children
 * hang from it; a pose naming a bone the rig lacks is refused. Pose: a bone
 * cannot be posed both ways at once.
 */
final class RigTest {
    private static Mesh box(Vec pivot) {
        // A unit cube from (2, 0, 0) to (3, 1, 1) in model space, expressed relative to the pivot.
        List<Vec> p = List.of(new Vec(2, 0, 0), new Vec(3, 0, 0), new Vec(3, 1, 0), new Vec(2, 1, 0), new Vec(2, 0, 1), new Vec(3, 0, 1), new Vec(3, 1, 1), new Vec(2, 1, 1));
        List<Vec> rel = p.stream().map(v -> v.minus(pivot)).toList();
        List<Uv> uv = List.of(new Uv(0, 0), new Uv(1, 0), new Uv(1, 1), new Uv(0, 1));
        List<Face> faces = List.of(new Face("m", "box", List.of(new Corner(0, 0), new Corner(3, 1), new Corner(2, 2), new Corner(1, 3)), Vec.Y));
        return Mesh.of(rel, uv, faces);
    }

    private static Rig chain() {
        Bone root = new Bone(BbRig.ROOT, "", -1, Vec.ZERO, Quat.IDENTITY);
        Bone a = new Bone("a", "a", 0, new Vec(1, 0, 0), Quat.IDENTITY);
        Bone b = new Bone("b", "a/b", 1, new Vec(2, 0, 0), Quat.IDENTITY);
        return new Rig(List.of(root, a, b), List.of(Mesh.of(List.of(), List.of(), List.of()), Mesh.of(List.of(), List.of(), List.of()), box(new Vec(2, 0, 0))),
                Optional.empty(), 0, 0, List.of());
    }

    @Test
    void aRigNeedsAMeshPerBoneAndParentsFirst() {
        Bone root = new Bone(BbRig.ROOT, "", -1, Vec.ZERO, Quat.IDENTITY);
        Mesh empty = Mesh.of(List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new Rig(List.of(root), List.of(), Optional.empty(), 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Rig(List.of(new Bone("x", "x", 0, Vec.ZERO, Quat.IDENTITY)), List.of(empty), Optional.empty(), 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Rig(List.of(root, new Bone("x", "x", 5, Vec.ZERO, Quat.IDENTITY)), List.of(empty, empty), Optional.empty(), 0, 0, List.of()));
    }

    @Test
    void restReproducesTheChain() {
        Rig rig = chain();
        Xform[] w = rig.place(Pose.REST);
        assertTrue(w[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(3, 1, 1), 1e-12));
    }

    @Test
    void aLocalTurnTurnsTheBoneAboutItsPivotAndItsChildrenWithIt() {
        Rig rig = chain();
        // Turn a (pivot (1, 0, 0)) a quarter about Y: the corner (3, 1, 1) of b's cube goes to (1, 1, -1) + ... :
        // relative to a's pivot it is (2, 1, 1); Y 90 sends (x, z) to (z, -x): (1, 1, -2); plus the pivot: (2, 1, -2).
        Pose pose = Pose.REST.withLocal(1, Quat.fromEulerXYZDegrees(0, 90, 0));
        Xform[] w = rig.place(pose);
        assertTrue(w[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(2, 1, -2), 1e-9));
        // Turning b instead turns about b's own pivot (2, 0, 0): (3, 1, 1) -> (1, 1, -1) + (2, 0, 0) = (3, 1, -1).
        Xform[] w2 = rig.place(Pose.REST.withLocal(2, Quat.fromEulerXYZDegrees(0, 90, 0)));
        assertTrue(w2[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(3, 1, -1), 1e-9));
    }

    @Test
    void anAbsolutePlacementReplacesTheChain() {
        Rig rig = chain();
        Pose pose = Pose.REST.withLocal(1, Quat.fromEulerXYZDegrees(0, 90, 0)).withAbsolute(2, new Xform(Quat.IDENTITY, new Vec(10, 10, 10)));
        Xform[] w = rig.place(pose);
        // b ignores a's turn: its cube's corner (3, 1, 1) is (1, 1, 1) from b's pivot, placed at (11, 11, 11).
        assertTrue(w[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(11, 11, 11), 1e-12));
    }

    @Test
    void aShiftMovesTheBoneAlongItsOwnRestAxesLocalOrPlaced() {
        Rig rig = chain();
        // b shifted up 5 at rest: its cube's corner (3, 1, 1) is at (3, 6, 1).
        Xform[] w = rig.place(Pose.REST.withShift(2, new Vec(0, 5, 0)));
        assertTrue(w[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(3, 6, 1), 1e-12));
        // Under a's quarter turn about Y, b's own X is the world's -Z: a shift of (1, 0, 0) on b moves the corner from (2, 1, -2) to (2, 1, -3).
        Pose turned = Pose.REST.withLocal(1, Quat.fromEulerXYZDegrees(0, 90, 0)).withShift(2, new Vec(1, 0, 0));
        assertTrue(rig.place(turned)[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(2, 1, -3), 1e-9));
        // A placed bone shifts along its placement's axes: placed turned a quarter about Y at (10, 10, 10), shifted (1, 0, 0),
        // the corner (1, 1, 1) from its pivot lands at (11, 11, 9) - (0, 0, 1) = (11, 11, 8).
        Pose placed = Pose.REST.withAbsolute(2, new Xform(Quat.fromEulerXYZDegrees(0, 90, 0), new Vec(10, 10, 10))).withShift(2, new Vec(1, 0, 0));
        assertTrue(rig.place(placed)[2].apply(rig.mesh(2).positions().get(6)).near(new Vec(11, 11, 8), 1e-9));
        // A shift survives a later local turn or placement of the bone; a zero shift is the default.
        assertTrue(placed.withLocal(2, Quat.IDENTITY).shift(2).near(new Vec(1, 0, 0), 1e-12));
        assertTrue(Pose.REST.shift(2).equals(Vec.ZERO));
        assertEquals(2, Pose.REST.withShift(2, Vec.Y).highestBone());
    }

    @Test
    void aPoseNamingAMissingBoneIsRefusedAndABoneIsPosedOneWay() {
        Rig rig = chain();
        assertThrows(IllegalArgumentException.class, () -> rig.place(Pose.REST.withLocal(7, Quat.IDENTITY)));
        Pose both = Pose.REST.withLocal(1, Quat.IDENTITY).withAbsolute(1, Xform.IDENTITY);
        assertTrue(both.absolute(1) != null && both.local(1).near(Quat.IDENTITY, 1e-12), "the later posing wins");
    }
}
