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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The game objects this mod registers: one entity type for every creature. */
public final class ModContent {
    private ModContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AberrantMobs.NAMESPACE);

    /**
     * Every creature is this one entity type, sized and skinned by its
     * profile; the profile id rides its synced data. The size here is the
     * default before a profile is set.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Aberrant>> ABERRANT = ENTITIES.register("aberrant",
            () -> EntityType.Builder.of(Aberrant::new, MobCategory.MONSTER).sized(2.5f, 2.5f).clientTrackingRange(12).updateInterval(2).build("aberrant"));

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(ABERRANT.get(), Monster.createMonsterAttributes().build()));
    }
}
