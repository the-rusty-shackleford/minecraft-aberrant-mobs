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
import java.util.OptionalInt;

/**
 * A rig read as a body: which bones are the head and the chain behind it,
 * how far behind the head each chain bone sits along the body (from the
 * file's own pivots), which bones are legs, on which side and on which
 * segment, where each leg's hip and rest foot are in its segment's frame
 * (from the file's pivots and the farthest vertex of the leg's cubes, as
 * it stands in the file), and how high the body's axis runs above the
 * feet. Built once per profile from names the profile gives; then it turns
 * a {@link ChainPose} and the legs' poses into a {@link Pose} every frame.
 * Immutable.
 *
 * <p>RI: chain[0] is the head; arcBack is the same length as chain, starts
 *     at 0 and never decreases; legBones and legs are the same length, even;
 *     every bone index names a bone of the rig it was built from; every
 *     leg's segment is an index into chain; scale positive.
 * AF: AF(chain, arcBack, legBones, legs, axisHeight) = "the body whose
 *     spine is the bones chain[0..n) at arcBack[k] blocks behind the head,
 *     with leg i the bone legBones[i] standing as legs[i] says (pair i
 *     left at 2i, right at 2i + 1), its axis {@code axisHeight} blocks
 *     over its feet".
 */
public final class Body {
    private final int[] chain;
    private final String[] boneNames;
    private final double[] arcBack;
    private final int[] legBones;
    private final Legs.Leg[] legs;
    private final double axisHeight;
    private final double scale;

    private Body(int[] chain, String[] boneNames, double[] arcBack, int[] legBones, Legs.Leg[] legs, double axisHeight, double scale) {
        this.chain = chain;
        this.boneNames = boneNames;
        this.arcBack = arcBack;
        this.legBones = legBones;
        this.legs = legs;
        this.axisHeight = axisHeight;
        this.scale = scale;
    }

    /**
     * requires: {@code scale > 0}
     * effects: returns the body of {@code rig} with the named bones: the
     * head, the chain bones tail-ward in order, and the leg pairs by their
     * common prefix with {@code left} and {@code right} appended; the arc
     * behind the head of each chain bone is the head pivot's Z less the
     * bone's, times {@code scale}; each leg hangs from the nearest chain
     * bone above it, its hip the leg pivot's offset from that bone's pivot
     * and its rest foot the vertex of its cubes (its own and its
     * descendants') farthest from its pivot, both as the file stands and
     * in the segment's frame, blocks<br>
     * throws: {@link IllegalArgumentException} when a name is not a bone of
     * the rig, the chain's pivots do not run tailward, a leg hangs from no
     * chain bone, has no cubes, or its foot is not out to its side
     */
    public static Body of(Rig rig, String head, List<String> chainNames, List<String> legPairs, String left, String right, double scale) {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("scale is positive");
        }
        int[] chain = new int[chainNames.size() + 1];
        chain[0] = bone(rig, head);
        for (int i = 0; i < chainNames.size(); i++) {
            chain[i + 1] = bone(rig, chainNames.get(i));
        }
        double[] arcBack = new double[chain.length];
        double headZ = rig.bone(chain[0]).pivot().z();
        for (int k = 0; k < chain.length; k++) {
            arcBack[k] = (headZ - rig.bone(chain[k]).pivot().z()) * scale;
            if (k > 0 && arcBack[k] < arcBack[k - 1] - 1e-9) {
                throw new IllegalArgumentException("chain bone " + rig.bone(chain[k]).name() + " sits ahead of the one before it");
            }
        }
        arcBack[0] = 0.0;
        int[] legBones = new int[legPairs.size() * 2];
        for (int i = 0; i < legPairs.size(); i++) {
            legBones[2 * i] = bone(rig, legPairs.get(i) + left);
            legBones[2 * i + 1] = bone(rig, legPairs.get(i) + right);
        }
        Xform[] rest = rig.place(Pose.REST);
        Legs.Leg[] legs = new Legs.Leg[legBones.length];
        for (int i = 0; i < legBones.length; i++) {
            legs[i] = leg(rig, rest, chain, legBones[i], i % 2 == 0 ? +1 : -1, scale);
        }
        String[] names = new String[chain.length];
        for (int k = 0; k < chain.length; k++) {
            names[k] = rig.bone(chain[k]).name();
        }
        return new Body(chain, names, arcBack, legBones, legs, rig.bone(chain[0]).pivot().y() * scale, scale);
    }

    /** effects: returns leg bone {@code bone} as a leg of the chain, see {@link #of} */
    private static Legs.Leg leg(Rig rig, Xform[] rest, int[] chain, int bone, int side, double scale) {
        int segment = -1;
        for (int b = rig.bone(bone).parent(); b >= 0 && segment < 0; b = rig.bone(b).parent()) {
            for (int k = 0; k < chain.length; k++) {
                if (chain[k] == b) {
                    segment = k;
                }
            }
        }
        if (segment < 0) {
            throw new IllegalArgumentException("leg " + rig.bone(bone).name() + " hangs from no chain bone");
        }
        // The segment's frame as the file stands: its placement's rotation about its pivot.
        Xform seg = rest[chain[segment]];
        Quat unturn = seg.rotation().conjugate();
        Vec hipModel = rest[bone].translation();
        Vec hip = unturn.rotate(hipModel.minus(seg.translation())).times(scale);
        Vec tip = null;
        double farthest = -1.0;
        for (int b = 0; b < rig.boneCount(); b++) {
            if (!descends(rig, b, bone)) {
                continue;
            }
            for (Vec v : rig.mesh(b).positions()) {
                Vec p = rest[b].apply(v);
                double d = p.minus(hipModel).length();
                if (d > farthest) {
                    farthest = d;
                    tip = p;
                }
            }
        }
        if (tip == null) {
            throw new IllegalArgumentException("leg " + rig.bone(bone).name() + " has no cubes to stand on");
        }
        Vec foot = unturn.rotate(tip.minus(hipModel)).times(scale);
        if (!(side * foot.x() > 0)) {
            throw new IllegalArgumentException("leg " + rig.bone(bone).name() + "'s foot is not out to its side: " + foot);
        }
        return new Legs.Leg(segment, side, hip, foot);
    }

    /** effects: returns whether {@code b} is {@code ancestor} or hangs from it */
    private static boolean descends(Rig rig, int b, int ancestor) {
        for (int i = b; i >= 0; i = rig.bone(i).parent()) {
            if (i == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static int bone(Rig rig, String name) {
        OptionalInt i = rig.bone(name);
        if (i.isEmpty()) {
            throw new IllegalArgumentException("no bone named " + name + " in the rig");
        }
        return i.getAsInt();
    }

    /** effects: returns how far behind the head each chain bone sits, blocks; a fresh copy */
    public double[] arcBack() {
        return arcBack.clone();
    }

    /** effects: returns the chain's bone indices, the head first; a fresh copy */
    public int[] chain() {
        return chain.clone();
    }

    public int legPairs() {
        return legs.length / 2;
    }

    /** effects: returns how many legs the body has, left and right of each pair */
    public int legCount() {
        return legs.length;
    }

    /** effects: returns leg {@code i} (pair i / 2, left when even) as the feet need it */
    public Legs.Leg leg(int i) {
        return legs[i];
    }

    /** effects: returns every leg, pair by pair, left then right; a fresh copy */
    public Legs.Leg[] legs() {
        return legs.clone();
    }

    /** effects: returns the bone index of chain segment {@code k} (the head 0) */
    public int chainBone(int k) {
        return chain[k];
    }

    /** effects: returns the chain index of the bone named {@code name}, or -1 when no chain bone has that name */
    public int chainIndexOf(String name) {
        for (int k = 0; k < chain.length; k++) {
            if (boneNames[k].equals(name)) {
                return k;
            }
        }
        return -1;
    }

    /** effects: returns how high the body's axis (the chain's pivots) runs over the feet, blocks */
    public double axisHeight() {
        return axisHeight;
    }

    /** effects: returns the distance from the head's pivot to the last chain bone's, blocks */
    public double length() {
        return arcBack[arcBack.length - 1];
    }

    /**
     * requires: {@code chain.size() == this chain's length}, {@code legPoses.length == 2 * legPairs()}
     * effects: returns the pose that places every chain bone where
     * {@code chain} puts it, relative to {@code origin} (the point the rig
     * is drawn from, blocks) and in model units, facing as it says, and
     * turns each leg about its coxa by its pose: first the lift, about the
     * leg's own Z (its hinge, since its cubes run along its X), a left
     * leg's by +Z and a right leg's by -Z so both raise their tips; then
     * the swing about Y, a left leg's by -Y and a right leg's by +Y so both
     * swing toward the head
     */
    public Pose pose(ChainPose chain, LegGait.LegPose[] legPoses, Vec origin) {
        if (chain.size() != this.chain.length || legPoses.length != legs.length) {
            throw new IllegalArgumentException("a pose for every chain bone and every leg");
        }
        Pose p = Pose.REST;
        for (int k = 0; k < this.chain.length; k++) {
            Vec at = chain.position(k).minus(origin).times(1.0 / scale);
            p = p.withAbsolute(this.chain[k], new Xform(chain.orientation(k), at));
        }
        for (int i = 0; i < legs.length; i++) {
            int side = legs[i].side();
            LegGait.LegPose lp = legPoses[i];
            Quat swing = Quat.fromAxisAngle(Vec.Y, Math.toRadians(-side * lp.swingDeg()));
            Quat lift = Quat.fromAxisAngle(Vec.Z, Math.toRadians(side * lp.liftDeg()));
            p = p.withLocal(legBones[i], swing.times(lift));
        }
        return p;
    }
}
