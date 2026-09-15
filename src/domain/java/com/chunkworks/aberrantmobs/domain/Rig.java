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
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A creature's skeleton and skin: bones in an order that puts every parent
 * before its children, and for each bone the mesh of its own cubes in the
 * bone's space (model units, relative to the bone's pivot, the bone's own
 * rest rotation not yet applied), with the project's first texture.
 * Immutable.
 *
 * <p>Placing bone b in model space: {@code W(b) = W(parent) * T(pivot_b -
 * pivot_parent) * R(rest_b) * R(pose_b)}, the root's parent the identity
 * at the origin; a cube's vertex v (bone space) stands at {@code W(b)(v)}.
 * With every pose turn the identity this reproduces the project exactly as
 * Blockbench shows it, group rotations included -- the reader's test
 * proves it against the saved file.
 *
 * <p>RI: bones and meshes are the same length, at least one; bone 0 is the
 *     root and has parent -1; every other bone's parent index is smaller
 *     than its own; textureWidth and textureHeight are positive when a
 *     texture is present.
 * AF: AF(bones, meshes, texture) = "the jointed model whose joint i is
 *     bones[i] carrying meshes[i], skinned with texture".
 */
public final class Rig {
    private final List<Bone> bones;
    private final List<Mesh> meshes;
    private final Optional<byte[]> texture;
    private final int textureWidth;
    private final int textureHeight;
    private final List<String> warnings;

    /**
     * effects: makes the rig<br>
     * throws: {@link IllegalArgumentException} unless the lists match, bone
     * 0 is a root, and every other bone hangs from an earlier one
     */
    public Rig(List<Bone> bones, List<Mesh> meshes, Optional<byte[]> texture, int textureWidth, int textureHeight, List<String> warnings) {
        this.bones = List.copyOf(bones);
        this.meshes = List.copyOf(meshes);
        this.texture = texture.map(byte[]::clone);
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.warnings = List.copyOf(warnings);
        if (this.bones.isEmpty() || this.bones.size() != this.meshes.size()) {
            throw new IllegalArgumentException("a rig has a mesh per bone and at least one bone");
        }
        if (!this.bones.get(0).isRoot()) {
            throw new IllegalArgumentException("bone 0 is the root");
        }
        for (int i = 1; i < this.bones.size(); i++) {
            int parent = this.bones.get(i).parent();
            if (parent < 0 || parent >= i) {
                throw new IllegalArgumentException("bone " + i + " (" + this.bones.get(i).name() + ") must hang from an earlier bone, not " + parent);
            }
        }
        if (this.texture.isPresent() && (textureWidth <= 0 || textureHeight <= 0)) {
            throw new IllegalArgumentException("a texture has a size");
        }
    }

    public List<Bone> bones() {
        return bones;
    }

    public int boneCount() {
        return bones.size();
    }

    public Bone bone(int i) {
        return bones.get(i);
    }

    /** effects: returns the mesh of bone {@code i}'s own cubes, in its space */
    public Mesh mesh(int i) {
        return meshes.get(i);
    }

    /** effects: returns the index of the first bone named {@code name}, if any */
    public OptionalInt bone(String name) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name().equals(name)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /** effects: returns the first texture's PNG bytes, if the project embedded one */
    public Optional<byte[]> texture() {
        return texture.map(byte[]::clone);
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    /** effects: returns what the reader skipped, for the log */
    public List<String> warnings() {
        return warnings;
    }

    /**
     * effects: returns the placement of every bone in model space under
     * {@code pose}, index for index<br>
     * throws: {@link IllegalArgumentException} if the pose names a bone this
     * rig lacks
     */
    public Xform[] place(Pose pose) {
        if (pose.highestBone() >= bones.size()) {
            throw new IllegalArgumentException("the pose names bone " + pose.highestBone() + " of " + bones.size());
        }
        Xform[] world = new Xform[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            Xform absolute = pose.absolute(i);
            if (absolute != null) {
                world[i] = absolute;
                continue;
            }
            Quat turn = b.rest().times(pose.local(i));
            if (b.isRoot()) {
                world[i] = new Xform(turn, b.pivot());
            } else {
                Bone parent = bones.get(b.parent());
                world[i] = world[b.parent()].compose(new Xform(turn, b.pivot().minus(parent.pivot())));
            }
        }
        return world;
    }

    /** effects: returns the smallest box round every vertex of every bone's mesh placed under {@code pose}, model units */
    public Region bounds(Pose pose) {
        Xform[] world = place(pose);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < bones.size(); i++) {
            for (Face f : meshes.get(i).faces()) {
                for (Corner c : f.corners()) {
                    Vec p = world[i].apply(meshes.get(i).positions().get(c.position()));
                    minX = Math.min(minX, p.x());
                    minY = Math.min(minY, p.y());
                    minZ = Math.min(minZ, p.z());
                    maxX = Math.max(maxX, p.x());
                    maxY = Math.max(maxY, p.y());
                    maxZ = Math.max(maxZ, p.z());
                    any = true;
                }
            }
        }
        return any ? new Region(new Vec(minX, minY, minZ), new Vec(maxX, maxY, maxZ)) : new Region(Vec.ZERO, Vec.ZERO);
    }
}
