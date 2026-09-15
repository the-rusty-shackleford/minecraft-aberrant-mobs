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

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.domain.BbRig;
import com.chunkworks.aberrantmobs.domain.Bone;
import com.chunkworks.aberrantmobs.domain.Mesh;
import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Rig;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every creature's project in every resource pack, read once per reload
 * (F3+T included): {@code assets/<ns>/aberrantmobs/model/<name>.bbmodel},
 * saved as it is, is the rig {@code <ns>:<name>}. A project's embedded
 * texture is registered as {@code aberrantmobs:bbmodel/<ns>/<name>}. A
 * file that is not a project is logged with its pack and the reason and
 * stands in as a box, so a broken creature mod shows a box where the
 * creature is, not nothing. Reading is pure and runs on the reload's
 * worker; the skins built from rigs are cached per profile in {@link Skin}.
 */
public final class RigLibrary extends SimplePreparableReloadListener<RigLibrary.Loaded> {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs");
    public static final RigLibrary INSTANCE = new RigLibrary();
    private static final String PREFIX = "aberrantmobs/model/";
    private static final String BBMODEL = ".bbmodel";

    /** What a reload found: the rigs, and the PNG bytes of every embedded texture by rig id. */
    public record Loaded(Map<ResourceLocation, Rig> rigs, Map<ResourceLocation, byte[]> textures) {}

    private volatile Map<ResourceLocation, Rig> rigs = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> embedded = Map.of();
    private final Rig placeholder = new Rig(List.of(new Bone(BbRig.ROOT, "", -1, Vec.ZERO, Quat.IDENTITY)), List.of(Mesh.placeholder(16.0)), Optional.empty(), 0, 0, List.of());

    private RigLibrary() {}

    /** effects: returns the texture a project {@code id} embeds, if it embeds one */
    public Optional<ResourceLocation> embeddedTexture(ResourceLocation id) {
        return Optional.ofNullable(embedded.get(id));
    }

    /** effects: returns the rig {@code id} names, or the placeholder box (logged) */
    public Rig get(ResourceLocation id) {
        Rig r = rigs.get(id);
        if (r == null) {
            LOG.warn("aberrantmobs: no model {}; drawing a box", id);
            return placeholder;
        }
        return r;
    }

    /** effects: returns whether {@code id} is a project the packs carry */
    public boolean has(ResourceLocation id) {
        return rigs.containsKey(id);
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Rig> out = new HashMap<>();
        Map<ResourceLocation, byte[]> textures = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(PREFIX.substring(0, PREFIX.length() - 1), id -> id.getPath().endsWith(BBMODEL)).entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String name = path.substring(PREFIX.length(), path.length() - BBMODEL.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
            try (Reader reader = e.getValue().openAsReader()) {
                Rig rig = BbRig.parse(readAll(reader));
                for (String warning : rig.warnings()) {
                    LOG.warn("aberrantmobs: model {} ({}): {}", id, e.getValue().sourcePackId(), warning);
                }
                out.put(id, rig);
                rig.texture().ifPresent(png -> textures.put(id, png));
            } catch (IllegalArgumentException ex) {
                LOG.error("aberrantmobs: model {} ({}) is not a Blockbench project: {}; drawing a box", id, e.getValue().sourcePackId(), ex.getMessage());
                out.put(id, placeholder);
            } catch (IOException | RuntimeException ex) {
                LOG.error("aberrantmobs: cannot read model {} ({}): {}", id, e.getValue().sourcePackId(), ex.toString());
                out.put(id, placeholder);
            }
        }
        return new Loaded(Map.copyOf(out), Map.copyOf(textures));
    }

    /** effects: returns the id the embedded texture of project {@code id} is registered under */
    static ResourceLocation textureId(ResourceLocation model) {
        return AberrantMobs.id("bbmodel/" + model.getNamespace() + "/" + model.getPath());
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    @Override
    protected void apply(Loaded prepared, ResourceManager manager, ProfilerFiller profiler) {
        rigs = prepared.rigs();
        Map<ResourceLocation, ResourceLocation> registered = new HashMap<>();
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (Map.Entry<ResourceLocation, byte[]> e : prepared.textures().entrySet()) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(e.getValue()));
                ResourceLocation id = textureId(e.getKey());
                textureManager.register(id, new DynamicTexture(image));
                registered.put(e.getKey(), id);
            } catch (IOException | RuntimeException ex) {
                LOG.error("aberrantmobs: model {} embeds a texture that is not a PNG: {}", e.getKey(), ex.toString());
            }
        }
        embedded = Map.copyOf(registered);
        Skin.invalidate();
        LOG.info("aberrantmobs: {} models loaded, {} with their own texture", rigs.size(), embedded.size());
    }
}
