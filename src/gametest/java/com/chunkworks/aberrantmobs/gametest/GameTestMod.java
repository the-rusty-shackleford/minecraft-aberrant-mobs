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
package com.chunkworks.aberrantmobs.gametest;

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The test mod: the gametests and the photo booth ride in it, exercising
 * the protocol from outside, as a pack would. Never shipped.
 */
@Mod(GameTestMod.MOD_ID)
public final class GameTestMod {
    public static final String MOD_ID = "aberrantmobs_gametest";

    public GameTestMod(IEventBus modBus) {}

    /**
     * Stands in for Miracle Bringer at the same door: a devouring blow on an
     * entity tagged {@code blessed} is zeroed in {@code LivingDamageEvent.Pre},
     * as the miracle would; every devouring blow seen is counted.
     */
    @EventBusSubscriber(modid = MOD_ID)
    public static final class Blessing {
        private Blessing() {}

        public static int devouredSeen;

        @SubscribeEvent
        public static void onDamage(LivingDamageEvent.Pre event) {
            if (event.getSource().is(AberrantMobs.DEVOURED)) {
                devouredSeen++;
                if (event.getEntity().getTags().contains("blessed")) {
                    event.setNewDamage(0.0f);
                }
            }
        }
    }
}
