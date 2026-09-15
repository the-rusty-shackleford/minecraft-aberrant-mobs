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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.jetbrains.annotations.Nullable;

/**
 * The spawn egg of the one entity type: which creature it spawns is the
 * profile named in its entity data, so the creative tab offers one egg
 * per creature profile loaded, each named after its creature. An egg
 * with no profile in its data spawns whichever creature fits the spot,
 * or the first one known.
 */
public final class AberrantEggItem extends DeferredSpawnEggItem {
    /** The chitin's dark plum, and the glow of the crack. */
    private static final int BACKGROUND = 0x2B1F2E;
    private static final int HIGHLIGHT = 0xE8742A;

    public AberrantEggItem(Item.Properties properties) {
        super(ModContent.ABERRANT, BACKGROUND, HIGHLIGHT, properties);
    }

    /** effects: returns one egg that spawns the creature of profile {@code id} */
    public static ItemStack of(Item egg, ResourceLocation id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", AberrantMobs.id("aberrant").toString());
        tag.putString("Profile", id.toString());
        ItemStack stack = new ItemStack(egg);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    /** effects: returns the profile {@code stack}'s entity data names, or null when it names none */
    @Nullable
    public static ResourceLocation profileOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null || !data.contains("Profile")) {
            return null;
        }
        return ResourceLocation.tryParse(data.copyTag().getString("Profile"));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = profileOf(stack);
        if (id == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.aberrantmobs.aberrant_spawn_egg.of", Component.translatable("creature." + id.getNamespace() + "." + id.getPath()));
    }
}
