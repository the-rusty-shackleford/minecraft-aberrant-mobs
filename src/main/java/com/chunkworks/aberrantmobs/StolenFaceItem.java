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

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * A stolen face: the mask a creature wore, dropped when it died, carrying
 * the profile of the one it was taken from, so the trophy names them.
 */
public final class StolenFaceItem extends Item {
    public StolenFaceItem(Properties properties) {
        super(properties);
    }

    /** effects: returns a stolen face of {@code profile} */
    public static ItemStack of(GameProfile profile) {
        ItemStack stack = new ItemStack(ModContent.STOLEN_FACE.get());
        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        return stack;
    }

    /** effects: returns whose face {@code stack} is, or null for nobody's */
    public static String whose(ItemStack stack) {
        ResolvableProfile p = stack.get(DataComponents.PROFILE);
        return p == null ? null : p.name().orElse(null);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = whose(stack);
        tooltip.add(name == null ? Component.translatable("item.aberrantmobs.stolen_face.nobody") : Component.translatable("item.aberrantmobs.stolen_face.whose", name));
    }
}
