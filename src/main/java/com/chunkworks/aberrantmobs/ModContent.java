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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The game objects this mod registers: one entity type for every creature
 * and its spawn egg, the loot the first one drops (chitin, its cracked
 * plate, a stolen face), the chitin armour, the sounds its cues play, and
 * the creative tab that shows them all with an egg per creature.
 */
public final class ModContent {
    private ModContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AberrantMobs.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AberrantMobs.NAMESPACE);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AberrantMobs.NAMESPACE);
    private static final DeferredRegister<ArmorMaterial> ARMOR = DeferredRegister.create(Registries.ARMOR_MATERIAL, AberrantMobs.NAMESPACE);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AberrantMobs.NAMESPACE);

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

    /** Chitin: netherite's plating and a tenth of a knockback resistance, mended with chitin; the full set walks on walls. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CHITIN_MATERIAL = ARMOR.register("chitin", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3, ArmorItem.Type.BODY, 11),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(CHITIN.get()), List.of(new ArmorMaterial.Layer(AberrantMobs.id("chitin"))), 2.0f, 0.1f));
    public static final DeferredItem<ArmorItem> CHITIN_HELMET = armour("chitin_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> CHITIN_CHESTPLATE = armour("chitin_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> CHITIN_LEGGINGS = armour("chitin_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> CHITIN_BOOTS = armour("chitin_boots", ArmorItem.Type.BOOTS);

    private static DeferredItem<ArmorItem> armour(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name, p -> new ArmorItem(CHITIN_MATERIAL, type, p), new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(37)));
    }

    /** One egg for the one entity type; which creature it spawns is the profile in its data ({@link AberrantEggItem#of}). */
    public static final DeferredItem<AberrantEggItem> ABERRANT_SPAWN_EGG = ITEMS.registerItem("aberrant_spawn_egg", AberrantEggItem::new);

    /** The mod's own page in the creative inventory: the drops, the armour, then an egg per creature profile loaded. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("aberrant_mobs", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + AberrantMobs.NAMESPACE))
            .icon(() -> new ItemStack(STOLEN_FACE.get()))
            .displayItems((parameters, output) -> {
                items().forEach(output::accept);
                eggs(parameters.holders(), output::accept);
            })
            .build());

    /** effects: returns what the tab shows before the eggs, in order: the drops, then the armour head to foot */
    public static List<Item> items() {
        return List.of(CHITIN.get(), CRACKED_CARAPACE.get(), STOLEN_FACE.get(), CHITIN_HELMET.get(), CHITIN_CHESTPLATE.get(), CHITIN_LEGGINGS.get(), CHITIN_BOOTS.get());
    }

    /** effects: hands {@code out} one egg per creature profile in {@code registries}, in the registry's order */
    public static void eggs(HolderLookup.Provider registries, Consumer<ItemStack> out) {
        registries.lookupOrThrow(AberrantMobs.CREATURES).listElements().forEach(p -> out.accept(AberrantEggItem.of(ABERRANT_SPAWN_EGG.get(), p.key().location())));
    }

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
        ARMOR.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(ABERRANT.get(), Monster.createMonsterAttributes().build()));
        modBus.addListener(SpawnRules::register);
        // The vanilla pages too, where a player would look for them.
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
                event.accept(CHITIN);
                event.accept(CRACKED_CARAPACE);
                event.accept(STOLEN_FACE);
            }
            if (event.getTabKey().equals(CreativeModeTabs.COMBAT)) {
                event.accept(CHITIN_HELMET);
                event.accept(CHITIN_CHESTPLATE);
                event.accept(CHITIN_LEGGINGS);
                event.accept(CHITIN_BOOTS);
            }
            if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
                eggs(event.getParameters().holders(), event::accept);
            }
        });
    }
}
