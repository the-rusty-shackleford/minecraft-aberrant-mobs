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
package com.chunkworks.aberrantmobs.client;

import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.BakedMesh;
import com.chunkworks.aberrantmobs.domain.Body;
import com.chunkworks.aberrantmobs.domain.Face;
import com.chunkworks.aberrantmobs.domain.Mesh;
import com.chunkworks.aberrantmobs.domain.Rig;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A creature profile as the renderer wants it: its rig, its texture, a
 * baked mesh per bone at the profile's scale (blocks) with the cubes that
 * glow when the bone is the cracked segment baked apart, and the rig read
 * as a body by the profile's names. Built once per profile per reload and
 * read every frame with no allocation. Bones with no cubes of their own
 * bake to an empty mesh and cost a matrix each.
 */
public final class Skin {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs");
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    private static final Map<ResourceLocation, Skin> CACHE = new ConcurrentHashMap<>();

    public final Rig rig;
    public final ResourceLocation texture;
    /** Per bone, in blocks, in the bone's space: the cubes that never glow. */
    public final BakedMesh[] bones;
    /** Per bone: the cubes that glow when the bone is the cracked segment (empty for most). */
    public final BakedMesh[] glow;
    /** Model units to blocks. */
    public final double scale;
    /** The rig read as a body by the profile's names, or null when a name is not in the rig (logged once). */
    @Nullable
    public final Body body;
    /** The four corners of the mask cube's front (its +Z face), in the head bone's space, blocks, in the order the mesh draws them; null when the profile names no mask or the rig has none. */
    @Nullable
    public final Vec[] maskFront;
    /** The head bone, or -1. */
    public final int headBone;

    private Skin(Rig rig, ResourceLocation texture, BakedMesh[] bones, BakedMesh[] glow, double scale, @Nullable Body body, @Nullable Vec[] maskFront, int headBone) {
        this.rig = rig;
        this.texture = texture;
        this.bones = bones;
        this.glow = glow;
        this.scale = scale;
        this.body = body;
        this.maskFront = maskFront;
        this.headBone = headBone;
    }

    /** effects: returns the skin of {@code profile}, built on first use since the last reload */
    public static Skin of(ResourceLocation profileId, CreatureProfile profile) {
        return CACHE.computeIfAbsent(profileId, id -> build(profile));
    }

    /** effects: forgets every skin, so the next frame rebuilds from the reloaded rigs */
    static void invalidate() {
        CACHE.clear();
    }

    private static Skin build(CreatureProfile p) {
        Rig rig = RigLibrary.INSTANCE.get(p.model());
        ResourceLocation texture = RigLibrary.INSTANCE.embeddedTexture(p.model()).orElse(MISSING);
        CreatureProfile.WeakSpot weak = p.body().weakSpot();
        BakedMesh[] bones = new BakedMesh[rig.boneCount()];
        BakedMesh[] glow = new BakedMesh[rig.boneCount()];
        for (int i = 0; i < bones.length; i++) {
            Mesh all = rig.mesh(i);
            Mesh glowing = all.part(f -> weak.glows(cubeName(f)));
            bones[i] = BakedMesh.of(all.without(glowing), p.scale());
            glow[i] = BakedMesh.of(glowing, p.scale());
        }
        Body body = null;
        try {
            CreatureProfile.RigSpec r = p.rig();
            body = Body.of(rig, r.head(), r.chain(), r.legs(), r.left(), r.right(), p.scale());
        } catch (IllegalArgumentException e) {
            LOG.error("aberrantmobs: the profile's rig does not fit the model {}: {}; drawing it still", p.model(), e.getMessage());
        }
        int head = rig.bone(p.rig().head()).orElse(-1);
        Vec[] maskFront = head < 0 || p.rig().mask().isEmpty() ? null : maskFront(rig.mesh(head), p.rig().mask(), p.scale());
        return new Skin(rig, texture, bones, glow, p.scale(), body, maskFront, head);
    }

    /** effects: returns the corners of the +Z face of the cube named {@code mask} in {@code mesh}, scaled, in the drawn order; null when there is none */
    @Nullable
    private static Vec[] maskFront(Mesh mesh, String mask, double scale) {
        for (Mesh.Quad q : mesh.quads()) {
            if (cubeName(q.face()).equals(mask) && q.face().normal().z() > 0.9) {
                Vec[] out = new Vec[4];
                com.chunkworks.aberrantmobs.domain.Corner[] corners = {q.a(), q.b(), q.c(), q.d()};
                for (int k = 0; k < 4; k++) {
                    out[k] = mesh.positions().get(corners[k].position()).times(scale);
                }
                return out;
            }
        }
        return null;
    }

    /** effects: returns the cube's own name from a face's group path (the last component) */
    private static String cubeName(Face f) {
        String g = f.group();
        int slash = g.lastIndexOf('/');
        return slash < 0 ? g : g.substring(slash + 1);
    }
}
