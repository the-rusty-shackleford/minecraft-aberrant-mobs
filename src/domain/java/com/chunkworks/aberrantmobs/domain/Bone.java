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

/**
 * One joint of a rig: a Blockbench group, kept. Immutable.
 *
 * <p>RI: name non-null; parent is -1 (the root) or the index of a bone that
 *     comes earlier in its rig; pivot finite; rest is a rotation.
 * AF: AF(name, path, parent, pivot, rest) = "the joint named {@code name}
 *     at {@code path} in the outliner, hung from bone {@code parent}, that
 *     turns its cubes and its children about {@code pivot} (model units)
 *     by {@code rest} when the model stands as it was saved".
 */
public record Bone(String name, String path, int parent, Vec pivot, Quat rest) {
    public Bone {
        if (name == null || path == null || pivot == null || rest == null) {
            throw new IllegalArgumentException("a bone has a name, a path, a pivot and a rest rotation");
        }
        if (parent < -1) {
            throw new IllegalArgumentException("a bone's parent is -1 or an index: " + parent);
        }
    }

    public boolean isRoot() {
        return parent < 0;
    }
}
