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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The game objects this mod registers: one entity type for every creature,
 * the loot the first one drops (chitin, its cracked plate, a stolen face),
 * and the sounds its cues play.
 */
public final class ModContent {
    private ModContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AberrantMobs.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AberrantMobs.NAMESPACE);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AberrantMobs.NAMESPACE);

    /**
     * Every creature is this one entity type, sized and skinned by its
     * profile; the profile id rides its synced data. The size here is the
     * default before a profile is set.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Aberrant>> ABERRANT = ENTITIES.register("aberrant",
            () -> EntityType.Builder.of(Aberrant::new, MobCategory.MONSTER).sized(2.5f, 2.5f).fireImmune().clientTrackingRange(12).updateInterval(2).build("aberrant"));

    public static final DeferredItem<Item> CHITIN = ITEMS.registerSimpleItem("chitin");
    public static final DeferredItem<Item> CRACKED_CARAPACE = ITEMS.registerSimpleItem("cracked_carapace", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> STOLEN_FACE = ITEMS.registerItem("stolen_face", StolenFaceItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    public static final DeferredHolder<SoundEvent, SoundEvent> SKITTER = sound("skitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIG_LOUD = sound("dig_loud");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIG_QUIET = sound("dig_quiet");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = sound("click");
    public static final DeferredHolder<SoundEvent, SoundEvent> HISS = sound("hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREECH = sound("screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAB = sound("grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE = sound("bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACK = sound("crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = sound("breath");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AberrantMobs.id(name)));
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(ABERRANT.get(), Monster.createMonsterAttributes().build()));
        modBus.addListener(SpawnRules::register);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
                event.accept(CHITIN);
                event.accept(CRACKED_CARAPACE);
                event.accept(STOLEN_FACE);
            }
        });
    }
}
