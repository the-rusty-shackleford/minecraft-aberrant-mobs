/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.aberrantmobs.gametest;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.domain.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: real distant block destruction beyond the old range; repeated same-player breaks;
 * near sound after relocation. Uses real chunks, block destruction and the game-event adapter.
 */
@GameTestHolder("aberrantmobs")
@PrefixGameTestTemplate(false)
public final class HearingGameTests {
    @GameTest(template = "pounce", timeoutTicks = 260, batch = "sustained_hearing")
    public void repeatedDistantMiningSharpensTheBearingThroughRealGameEvents(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++)
            h.setBlock(x, 1, z, Blocks.STONE);
        var creature = Aberrant.create(h.getLevel(), AberrantMobs.id("protocol_fixture"),
                origin.getX() + 15.5, origin.getY() + 2, origin.getZ() + 15.5, 0);
        h.assertTrue(creature != null, "test-only unrestricted creature profile exists");
        creature.setNoAi(true);
        creature.setPersistenceRequired();
        h.getLevel().addFreshEntity(creature);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos[] noise = {null};
        double[] firstError = {0};
        h.runAtTickTime(5, () -> {
            Vec head = creature.axis();
            noise[0] = BlockPos.containing(head.x() + 500, head.y(), head.z());
            h.getLevel().getChunkAt(noise[0]);
            player.moveTo(noise[0].getX(), noise[0].getY(), noise[0].getZ());
            h.getLevel().setBlock(noise[0], Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().destroyBlock(noise[0], false, player);
            var heard = creature.hearing().estimateFrom(player.getUUID().toString(), head, creature.tickCount);
            h.assertTrue(heard.isPresent(), "real mining 500 blocks away reaches the creature");
            firstError[0] = heard.orElseThrow().error();
            h.assertTrue(firstError[0] > 200, "the first block gives only a vague bearing");
            h.assertTrue(Math.abs(heard.orElseThrow().bearing().y() - (noise[0].getY() + .5)) < .01,
                    "bearing does not invent a destination above or below the source");
        });
        for (int tick = 25; tick <= 205; tick += 20) h.runAtTickTime(tick, () -> {
            h.getLevel().setBlock(noise[0], Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().destroyBlock(noise[0], false, player);
        });
        h.runAtTickTime(225, () -> {
            double improved = creature.hearing().estimateFrom(player.getUUID().toString(), creature.axis(), creature.tickCount)
                    .orElseThrow().error();
            h.assertTrue(improved < firstError[0] * .95 && improved > firstError[0] * .75,
                    "ten further real block breaks gradually improve the estimate");
            Vec head = creature.axis();
            BlockPos near = BlockPos.containing(head.x() + 60, head.y(), head.z());
            h.getLevel().setBlock(near, Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().destroyBlock(near, false, player);
            h.assertTrue(creature.hearing().estimateFrom(player.getUUID().toString(), head, creature.tickCount)
                    .orElseThrow().error() == 0, "nearby real mining is localised exactly");
            creature.discard();
            h.succeed();
        });
    }
}
