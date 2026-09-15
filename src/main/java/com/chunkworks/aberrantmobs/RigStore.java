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
package com.chunkworks.aberrantmobs;

import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.BbRig;
import com.chunkworks.aberrantmobs.domain.Body;
import com.chunkworks.aberrantmobs.domain.Rig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rigs as the server knows them: read from the mods' own jars, where a
 * creature's project ships as an asset ({@code assets/<ns>/aberrantmobs/
 * model/<name>.bbmodel}), so that the server can lay a body's segments
 * along its trail -- the parts the world collides with and hits -- from
 * the same bones the client draws. A resource pack cannot change what the
 * server collides with, and should not. Read once per id, kept for the
 * run; a project the jars lack, or one that will not parse, is logged
 * once and answered with nothing, and such a creature has no parts.
 */
public final class RigStore {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs");
    private static final Map<ResourceLocation, Optional<Rig>> RIGS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Optional<Body>> BODIES = new ConcurrentHashMap<>();

    private RigStore() {}

    /** effects: returns the rig the project {@code model} holds, if a mod jar carries it and it parses */
    public static Optional<Rig> rig(ResourceLocation model) {
        return RIGS.computeIfAbsent(model, RigStore::read);
    }

    /** effects: returns the body of {@code profile} as its rig and names say, if both are good */
    public static Optional<Body> body(ResourceLocation profileId, CreatureProfile profile) {
        return BODIES.computeIfAbsent(profileId, id -> rig(profile.model()).flatMap(rig -> {
            try {
                CreatureProfile.RigSpec r = profile.rig();
                return Optional.of(Body.of(rig, r.head(), r.chain(), r.legs(), r.left(), r.right(), profile.scale()));
            } catch (IllegalArgumentException e) {
                LOG.error("aberrantmobs: the profile {}'s rig does not fit its model: {}", id, e.getMessage());
                return Optional.empty();
            }
        }));
    }

    private static Optional<Rig> read(ResourceLocation model) {
        String path = "/assets/" + model.getNamespace() + "/aberrantmobs/model/" + model.getPath() + ".bbmodel";
        try (InputStream in = RigStore.class.getResourceAsStream(path)) {
            if (in == null) {
                LOG.error("aberrantmobs: no model {} in any mod jar ({})", model, path);
                return Optional.empty();
            }
            Rig rig = BbRig.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (String warning : rig.warnings()) {
                LOG.warn("aberrantmobs: model {}: {}", model, warning);
            }
            return Optional.of(rig);
        } catch (IOException | IllegalArgumentException e) {
            LOG.error("aberrantmobs: model {} cannot be read: {}", model, e.toString());
            return Optional.empty();
        }
    }
}
