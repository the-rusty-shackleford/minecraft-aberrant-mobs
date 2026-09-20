/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs.gametest;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.AberrantEggItem;
import com.chunkworks.aberrantmobs.ModContent;
import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: actual egg use above/in habitat; named summon at source/relative coordinates
 * with custom NBT; invalid habitat; operator/nonoperator; shipped/additional profile.
 * Uses the real item-use path, command dispatcher, world entities and server ticks.
 */
@GameTestHolder("aberrant_manual_spawn")
@PrefixGameTestTemplate(false)
public final class ManualSpawnGameTests {
    private static Vec3 position(GameTestHelper h, int y) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++)
            h.getLevel().setBlock(new BlockPos(o.getX() + x, y - 1, o.getZ() + z), Blocks.STONE.defaultBlockState(), 3);
        return new Vec3(o.getX() + 15.5, y, o.getZ() + 15.5);
    }

    private static CommandSourceStack source(GameTestHelper h, Vec3 at) {
        return h.getLevel().getServer().createCommandSourceStack()
                .withLevel(h.getLevel()).withPosition(at).withSuppressedOutput();
    }

    @GameTest(template = "pounce", timeoutTicks = 30, batch = "manual_spawns")
    public void surfaceEggRejectsWithoutConsumingOrCreatingCreature(GameTestHelper h) {
        Vec3 at = position(h, 40);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(at.add(4, 0, 0));
        var egg = AberrantEggItem.of(ModContent.ABERRANT_SPAWN_EGG.get(), AberrantMobs.id("face_stealer"));
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        var hit = new BlockHitResult(at, Direction.UP, BlockPos.containing(at).below(), false);
        var result = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result == InteractionResult.FAIL, "invalid habitat must report failed egg use");
        h.assertTrue(egg.getCount() == 1, "failed egg use preserves the egg");
        h.assertTrue(h.getLevel().getEntitiesOfClass(Aberrant.class, new AABB(at, at).inflate(5)).isEmpty(),
                "no transient doomed creature is added");
        h.succeed();
    }

    @GameTest(template = "pounce", timeoutTicks = 30, batch = "manual_spawns")
    public void undergroundEggSpawnsAndSurvivesServerTicks(GameTestHelper h) {
        Vec3 at = position(h, -20);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(at.add(4, 0, 0));
        var egg = AberrantEggItem.of(ModContent.ABERRANT_SPAWN_EGG.get(), AberrantMobs.id("face_stealer"));
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        var hit = new BlockHitResult(at, Direction.UP, BlockPos.containing(at).below(), false);
        var result = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result.consumesAction() && egg.isEmpty(), "successful survival use spends exactly one egg");
        var entities = h.getLevel().getEntitiesOfClass(Aberrant.class, new AABB(at, at).inflate(5));
        h.assertTrue(entities.size() == 1, "one creature created through actual egg use");
        var creature = entities.getFirst();
        creature.setNoAi(true);
        creature.setPersistenceRequired();
        h.assertTrue(AberrantMobs.id("face_stealer").equals(creature.profileId()), "egg selects the requested profile");
        h.runAtTickTime(5, () -> {
            h.assertTrue(creature.isAlive() && creature.getHealth() == 84, "valid egg creature remains at full profile health");
            creature.discard();
            h.succeed();
        });
    }

    @GameTest(template = "pounce", timeoutTicks = 30, batch = "manual_spawns")
    public void namedSummonUsesSourcePositionAndCorrectProfile(GameTestHelper h) throws CommandSyntaxException {
        Vec3 at = position(h, -20);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("summon aberrantmobs:fa", source(h, at))).join();
        h.assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("aberrantmobs:face_stealer")),
                "the creature name appears in command completion");
        h.assertTrue(dispatcher.execute("summon aberrantmobs:face_stealer", source(h, at)) == 1,
                "clean command succeeds without Profile NBT");
        var creatures = h.getLevel().getEntitiesOfClass(Aberrant.class, new AABB(at, at).inflate(2));
        h.assertTrue(creatures.size() == 1, "exactly one creature");
        var creature = creatures.getFirst();
        creature.setNoAi(true);
        creature.setPersistenceRequired();
        h.assertTrue(AberrantMobs.id("face_stealer").equals(creature.profileId()) && creature.getHealth() == 84,
                "correct profile and default health");
        h.runAtTickTime(5, () -> {
            h.assertTrue(creature.isAlive(), "summoned creature survives habitat enforcement");
            creature.discard();
            h.succeed();
        });
    }

    @GameTest(template = "pounce", timeoutTicks = 30, batch = "manual_spawns")
    public void namedSummonSupportsCoordinatesNbtAndAdditionalProfiles(GameTestHelper h) throws CommandSyntaxException {
        Vec3 at = position(h, 40);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        h.assertTrue(dispatcher.execute(
                "summon aberrantmobs:protocol_fixture ~1 ~ ~2 {NoAI:1b,Tags:[\"manual_spawn_regression\"]}",
                source(h, at)) == 1, "every loaded profile gets a named summon");
        var creatures = h.getLevel().getEntitiesOfClass(Aberrant.class, new AABB(at, at).inflate(5),
                a -> a.getTags().contains("manual_spawn_regression"));
        h.assertTrue(creatures.size() == 1, "one named creature with requested NBT");
        var creature = creatures.getFirst();
        h.assertTrue(creature.position().distanceTo(at.add(1, 0, 2)) < .001, "relative coordinates respected");
        h.assertTrue(creature.isNoAi() && creature.getHealth() == 84
                && AberrantMobs.id("protocol_fixture").equals(creature.profileId()), "NBT and profile retained");
        creature.discard();
        h.succeed();
    }

    @GameTest(template = "pounce", timeoutTicks = 30, batch = "manual_spawns")
    public void invalidSummonReportsHabitatAndPreservesPermissionGate(GameTestHelper h) {
        Vec3 at = position(h, 40);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        try {
            dispatcher.execute("summon aberrantmobs:face_stealer", source(h, at));
            h.fail("out-of-habitat summon must fail immediately");
        } catch (CommandSyntaxException error) {
            h.assertTrue(error.getRawMessage().getString().contains("-32"), "failure explains the habitat's depth range");
        }
        try {
            dispatcher.execute("summon aberrantmobs:aberrant ~ ~ ~ {Profile:\"aberrantmobs:face_stealer\"}", source(h, at));
            h.fail("legacy Profile-NBT summon must also explain habitat failure");
        } catch (CommandSyntaxException error) {
            h.assertTrue(error.getRawMessage().getString().contains("-32"), "legacy command reports the same habitat range");
        }
        try {
            dispatcher.execute("summon aberrantmobs:protocol_fixture", source(h, at).withPermission(0));
            h.fail("nonoperators cannot summon");
        } catch (CommandSyntaxException expected) {
            h.assertTrue(h.getLevel().getEntitiesOfClass(Aberrant.class, new AABB(at, at).inflate(5)).isEmpty(),
                    "failed commands create no creatures");
        }
        h.succeed();
    }
}
