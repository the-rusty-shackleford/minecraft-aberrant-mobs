/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs;

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

/** Manual spawn validation and profile-named branches of vanilla's operator-only summon command. */
@EventBusSubscriber(modid = AberrantMobsMod.MOD_ID)
public final class ManualSpawns {
    private ManualSpawns() {}

    /**
     * requires: a fully configured, unadded entity; effects: returns a player-facing rejection
     * reason, or null when its initial state is permitted; throws: nothing.
     */
    @Nullable
    public static Component failure(Entity entity) {
        if (!(entity instanceof Aberrant creature)) return null;
        if (creature.profile() == null || creature.isRemoved())
            return Component.translatable("spawn.aberrantmobs.missing_profile");
        if (creature.level().getDifficulty() == Difficulty.PEACEFUL)
            return Component.translatable("spawn.aberrantmobs.peaceful", creature.getName());
        if (!creature.fitsHabitat()) {
            var habitat = creature.profile().habitat().orElseThrow();
            return Component.translatable("spawn.aberrantmobs.habitat", creature.getName(),
                    habitat.rules().yMin(), habitat.rules().yMax());
        }
        return null;
    }

    /**
     * requires: command registration event; effects: adds a named summon for each loaded profile
     * without shadowing registered entity IDs; throws: nothing.
     */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        registerName(event, AberrantMobs.id("aberrant"), null);
        event.getBuildContext().lookup(AberrantMobs.CREATURES).ifPresent(profiles ->
                profiles.listElements().forEach(profile -> {
                    ResourceLocation id = profile.key().location();
                    if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) registerName(event, id, id);
                }));
    }

    private static void registerName(RegisterCommandsEvent event, ResourceLocation name, @Nullable ResourceLocation profile) {
        event.getDispatcher().register(Commands.literal("summon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal(name.toString())
                        .executes(c -> summon(c.getSource(), profile, c.getSource().getPosition(), new CompoundTag(), true))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> summon(c.getSource(), profile, Vec3Argument.getVec3(c, "pos"), new CompoundTag(), true))
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(c -> summon(c.getSource(), profile, Vec3Argument.getVec3(c, "pos"),
                                                CompoundTagArgument.getCompoundTag(c, "nbt"), false))))));
    }

    private static int summon(CommandSourceStack source, @Nullable ResourceLocation profile, Vec3 position,
            CompoundTag supplied, boolean randomize) throws CommandSyntaxException {
        if (!Level.isInSpawnableBounds(BlockPos.containing(position)))
            throw error(Component.translatable("commands.summon.invalidPosition"));
        CompoundTag tag = supplied.copy();
        tag.putString("id", AberrantMobs.id("aberrant").toString());
        if (profile != null) tag.putString("Profile", profile.toString());
        var level = source.getLevel();
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(position.x, position.y, position.z, e.getYRot(), e.getXRot());
            return e;
        });
        if (!(entity instanceof Aberrant creature))
            throw error(Component.translatable("commands.summon.failed"));
        if (randomize || !tag.contains("Profile"))
            creature.finalizeSpawn(level, level.getCurrentDifficultyAt(creature.blockPosition()), MobSpawnType.COMMAND, null);
        var passengers = creature.getSelfAndPassengers().toList();
        for (Entity passenger : passengers) {
            Component failure = failure(passenger);
            if (failure != null) {
                passengers.forEach(Entity::discard);
                throw error(failure);
            }
        }
        if (!level.tryAddFreshEntityWithPassengers(creature))
            throw error(Component.translatable("commands.summon.failed.uuid"));
        source.sendSuccess(() -> Component.translatable("commands.summon.success", creature.getDisplayName()), true);
        return 1;
    }

    private static CommandSyntaxException error(Component reason) {
        return new SimpleCommandExceptionType(reason).create();
    }
}
