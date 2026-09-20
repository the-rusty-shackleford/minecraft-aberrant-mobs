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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
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

    /**
     * requires: item-use context; effects: preserves vanilla spawner configuration, otherwise
     * validates the configured creature before adding it or spending an egg; throws: nothing.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockEntity(clicked) instanceof Spawner) return super.useOn(context);
        BlockPos at = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        if (!spawnChecked(level, context.getItemInHand(), context.getPlayer(), at, true,
                !clicked.equals(at) && context.getClickedFace() == Direction.UP)) return InteractionResult.FAIL;
        context.getItemInHand().consume(1, context.getPlayer());
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clicked);
        return InteractionResult.CONSUME;
    }

    /**
     * requires: world, player and hand; effects: validates source-fluid egg use before spawning
     * and consumption, reporting a refused habitat to the player; throws: nothing.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        BlockPos at = hit.getBlockPos();
        if (!(level.getBlockState(at).getBlock() instanceof LiquidBlock)) return InteractionResultHolder.pass(stack);
        if (!level.mayInteract(player, at) || !player.mayUseItemAt(at, hit.getDirection(), stack))
            return InteractionResultHolder.fail(stack);
        if (!spawnChecked(server, stack, player, at, false, false)) return InteractionResultHolder.fail(stack);
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        return InteractionResultHolder.consume(stack);
    }

    private boolean spawnChecked(ServerLevel level, ItemStack stack, @Nullable Player player,
            BlockPos at, boolean offset, boolean offsetMore) {
        var type = getType(stack);
        Entity creature = type.create(level, EntityType.createDefaultStackConfig(level, stack, player),
                at, MobSpawnType.SPAWN_EGG, offset, offsetMore);
        Component failure = creature == null ? Component.translatable("commands.summon.failed") : ManualSpawns.failure(creature);
        if (failure != null) {
            if (creature != null) creature.discard();
            if (player != null) player.displayClientMessage(failure, false);
            return false;
        }
        return level.tryAddFreshEntityWithPassengers(creature);
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
