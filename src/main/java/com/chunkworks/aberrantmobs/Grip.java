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

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * The pincers' grip: a creature holding someone does not let them climb
 * off. A dismount from a creature that is holding is refused unless the
 * creature itself is letting go. The server alone enforces it: a client
 * only mirrors what the server says, and its passenger update can arrive
 * a packet before the held flag drops, so a client that enforced too would
 * refuse its own release and ride forever.
 */
@EventBusSubscriber(modid = AberrantMobsMod.MOD_ID)
public final class Grip {
    private Grip() {}

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof Aberrant a && a.holding() && !a.releasing()) {
            event.setCanceled(true);
        }
    }
}
