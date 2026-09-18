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

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * A protocol for monsters. A creature is a datapack entry and a Blockbench
 * project; this mod owns everything that happens to it. The entry point
 * registers the game objects ({@link ModContent}) and the creature
 * registry, and nothing else.
 */
@Mod(AberrantMobsMod.MOD_ID)
public final class AberrantMobsMod {
    public static final String MOD_ID = AberrantMobs.NAMESPACE;

    public AberrantMobsMod(IEventBus modBus) {
        ModContent.register(modBus);
        modBus.addListener(com.chunkworks.aberrantmobs.wallwalk.ClingInput::register);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(AberrantMobs.CREATURES, CreatureProfile.CODEC, CreatureProfile.CODEC));
    }
}
