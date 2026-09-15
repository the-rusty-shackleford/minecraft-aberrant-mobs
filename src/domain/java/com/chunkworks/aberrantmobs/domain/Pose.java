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

import java.util.HashMap;
import java.util.Map;

/**
 * How a rig stands: for a bone, either an extra rotation about its pivot on
 * top of its rest ({@code local}) or a placement of its own in model space
 * that replaces what its parents would give it ({@code absolute}, the
 * chain's segments); and, either way or at rest, a {@code shift} of the
 * bone (and so its children) along its own rest axes, model units -- how
 * a clip lifts a segment the chain has placed. A bone in no map stands at
 * rest. Immutable; the maps are copied in and never handed out.
 *
 * <p>RI: no bone is in both local and absolute; keys are >= 0 (whether a
 *     key names a bone of a given rig is checked where the pose meets the
 *     rig); no shift is null.
 * AF: AF(local, absolute, shift) = "bone b turns by local[b] about its
 *     pivot if b in local, is placed by absolute[b] if b in absolute, and
 *     rests otherwise; and is moved by shift[b] along its own rest frame's
 *     axes if b in shift".
 */
public final class Pose {
    public static final Pose REST = new Pose(Map.of(), Map.of(), Map.of());

    private final Map<Integer, Quat> local;
    private final Map<Integer, Xform> absolute;
    private final Map<Integer, Vec> shift;

    private Pose(Map<Integer, Quat> local, Map<Integer, Xform> absolute, Map<Integer, Vec> shift) {
        this.local = Map.copyOf(local);
        this.absolute = Map.copyOf(absolute);
        this.shift = Map.copyOf(shift);
        for (Integer b : this.local.keySet()) {
            if (b < 0 || this.absolute.containsKey(b)) {
                throw new IllegalArgumentException("bone " + b + " is posed twice or is no bone");
            }
        }
        for (Integer b : this.absolute.keySet()) {
            if (b < 0) {
                throw new IllegalArgumentException("bone " + b + " is no bone");
            }
        }
        for (Integer b : this.shift.keySet()) {
            if (b < 0) {
                throw new IllegalArgumentException("bone " + b + " is no bone");
            }
        }
    }

    /** effects: returns this pose with bone {@code bone} turned by {@code turn} about its pivot (replacing any earlier posing of it; its shift kept) */
    public Pose withLocal(int bone, Quat turn) {
        Map<Integer, Quat> l = new HashMap<>(local);
        Map<Integer, Xform> a = new HashMap<>(absolute);
        a.remove(bone);
        l.put(bone, turn);
        return new Pose(l, a, shift);
    }

    /** effects: returns this pose with bone {@code bone} placed at {@code placement} in model space (replacing any earlier posing of it; its shift kept) */
    public Pose withAbsolute(int bone, Xform placement) {
        Map<Integer, Quat> l = new HashMap<>(local);
        Map<Integer, Xform> a = new HashMap<>(absolute);
        l.remove(bone);
        a.put(bone, placement);
        return new Pose(l, a, shift);
    }

    /**
     * effects: returns this pose with bone {@code bone} (and its children)
     * moved by {@code shift} along the bone's own rest axes, model units:
     * for a placed bone the placement's axes, for any other the axes its
     * parent and its rest rotation give it (replacing any earlier shift)
     */
    public Pose withShift(int bone, Vec shift) {
        Map<Integer, Vec> sh = new HashMap<>(this.shift);
        sh.put(bone, shift);
        return new Pose(local, absolute, sh);
    }

    /** effects: returns the shift of {@code bone} along its own axes, zero when unshifted */
    public Vec shift(int bone) {
        return shift.getOrDefault(bone, Vec.ZERO);
    }

    /** effects: returns the extra turn of {@code bone}, identity when unposed */
    public Quat local(int bone) {
        return local.getOrDefault(bone, Quat.IDENTITY);
    }

    /** effects: returns the placement of {@code bone}, or null when it hangs from its parent */
    public Xform absolute(int bone) {
        return absolute.get(bone);
    }

    /** effects: returns the largest bone index this pose names, -1 for none */
    public int highestBone() {
        int h = -1;
        for (Integer b : local.keySet()) {
            h = Math.max(h, b);
        }
        for (Integer b : absolute.keySet()) {
            h = Math.max(h, b);
        }
        for (Integer b : shift.keySet()) {
            h = Math.max(h, b);
        }
        return h;
    }
}
