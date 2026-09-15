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
 * file's own pivots), which bones are legs and on which side, and how
 * high the body's axis runs above the feet. Built once per profile from
 * names the profile gives; then it turns a {@link ChainPose} and the legs'
 * poses into a {@link Pose} every frame. Immutable.
 *
 * <p>RI: chain[0] is the head; arcBack is the same length as chain, starts
 *     at 0 and never decreases; every bone index names a bone of the rig
 *     it was built from; scale positive.
 * AF: AF(chain, arcBack, legs, axisHeight) = "the body whose spine is the
 *     bones chain[0..n) at arcBack[k] blocks behind the head, with legs
 *     {@code legs} (pair i left at 2i, right at 2i + 1), its axis
 *     {@code axisHeight} blocks over its feet".
 */
public final class Body {
    /** A leg bone and its side: +1 for the left (the model's +X), -1 for the right. */
    public record Leg(int bone, int side) {}

    private final int[] chain;
    private final String[] boneNames;
    private final double[] arcBack;
    private final Leg[] legs;
    private final double axisHeight;
    private final double scale;

    private Body(int[] chain, String[] boneNames, double[] arcBack, Leg[] legs, double axisHeight, double scale) {
        this.chain = chain;
        this.boneNames = boneNames;
        this.arcBack = arcBack;
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
     * bone's, times {@code scale}<br>
     * throws: {@link IllegalArgumentException} when a name is not a bone of
     * the rig, or the chain's pivots do not run tailward
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
        Leg[] legs = new Leg[legPairs.size() * 2];
        for (int i = 0; i < legPairs.size(); i++) {
            legs[2 * i] = new Leg(bone(rig, legPairs.get(i) + left), +1);
            legs[2 * i + 1] = new Leg(bone(rig, legPairs.get(i) + right), -1);
        }
        String[] names = new String[chain.length];
        for (int k = 0; k < chain.length; k++) {
            names[k] = rig.bone(chain[k]).name();
        }
        return new Body(chain, names, arcBack, legs, rig.bone(chain[0]).pivot().y() * scale, scale);
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
     * turns each leg about its coxa by its pose: a left leg's swing forward
     * is a turn about -Y and its lift a turn about +Z, a right leg's the
     * reverse, so both swing toward the head and lift their tips
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
            Leg leg = legs[i];
            LegGait.LegPose lp = legPoses[i];
            p = p.withLocal(leg.bone(), Quat.fromEulerXYZDegrees(0.0, -leg.side() * lp.swingDeg(), leg.side() * lp.liftDeg()));
        }
        return p;
    }
}
