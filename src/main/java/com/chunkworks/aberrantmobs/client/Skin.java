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
import com.chunkworks.aberrantmobs.domain.Rig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * A creature profile as the renderer wants it: its rig, its texture, and
 * a baked mesh per bone at the profile's scale (blocks). Built once per
 * profile per reload and read every frame with no allocation. Bones with
 * no cubes of their own bake to an empty mesh and cost a matrix each.
 */
public final class Skin {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    private static final Map<ResourceLocation, Skin> CACHE = new ConcurrentHashMap<>();

    public final Rig rig;
    public final ResourceLocation texture;
    /** Per bone, in blocks, in the bone's space. */
    public final BakedMesh[] bones;
    /** Model units to blocks. */
    public final double scale;
    /** The rig read as a body by the profile's names, or null when a name is not in the rig (logged once). */
    @org.jetbrains.annotations.Nullable
    public final com.chunkworks.aberrantmobs.domain.Body body;

    private Skin(Rig rig, ResourceLocation texture, BakedMesh[] bones, double scale, com.chunkworks.aberrantmobs.domain.Body body) {
        this.rig = rig;
        this.texture = texture;
        this.bones = bones;
        this.scale = scale;
        this.body = body;
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
        BakedMesh[] bones = new BakedMesh[rig.boneCount()];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = BakedMesh.of(rig.mesh(i), p.scale());
        }
        com.chunkworks.aberrantmobs.domain.Body body = null;
        try {
            CreatureProfile.RigSpec r = p.rig();
            body = com.chunkworks.aberrantmobs.domain.Body.of(rig, r.head(), r.chain(), r.legs(), r.left(), r.right(), p.scale());
        } catch (IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger("Aberrant Mobs").error("aberrantmobs: the profile's rig does not fit the model {}: {}; drawing it still", p.model(), e.getMessage());
        }
        return new Skin(rig, texture, bones, p.scale(), body);
    }
}
