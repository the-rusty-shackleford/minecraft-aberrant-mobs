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

import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: all six gravity axes; axial/oblique/vertical looks; six-way and
 * horizontal-only block states; zero/nonzero pre-existing local velocity.
 * Uses vanilla placement states and an actual level explosion. The framework's
 * server player supplies a connectionless actor, never a replacement backend.
 */
@GameTestHolder(com.chunkworks.aberrantmobs.AberrantMobsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityBoundaryGameTests {
    public GravityBoundaryGameTests() {}

    /** effects: verifies placement uses the world look and only legal block facings. */
    @GameTest(template = "tall", batch = "gravity_boundaries")
    public void placementFollowsTheWorldLookInEveryFrame(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(7, 8, 7));
        player.setPos(Vec3.atCenterOf(at));
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(Items.DISPENSER), new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        try {
            for (Gravity gravity : Gravity.values()) {
                ((FrameCarrier) player).aberrantmobs$setFrame(Frame.of(gravity));
                for (float yaw : new float[] {0, 37, 90, 180, -90}) {
                    for (float pitch : new float[] {-90, -25, 0, 60, 90}) {
                        player.setYRot(yaw);
                        player.setYHeadRot(yaw);
                        player.setXRot(pitch);
                        Vec3 look = player.getLookAngle();
                        Direction nearest = context.getNearestLookingDirection();
                        double best = 0;
                        for (Direction d : Direction.values()) {
                            best = Math.max(best, look.dot(Vec3.atLowerCornerOf(d.getNormal())));
                        }
                        String label = gravity + " yaw=" + yaw + " pitch=" + pitch;
                        helper.assertTrue(look.dot(Vec3.atLowerCornerOf(nearest.getNormal())) >= best - 1e-5,
                                "nearest follows the world look: " + label + ", got " + nearest + " for " + look);
                        helper.assertValueEqual(Blocks.DISPENSER.getStateForPlacement(context).getValue(DirectionalBlock.FACING),
                                nearest.getOpposite(), "dispenser faces its placer: " + label);
                        Direction horizontal = context.getHorizontalDirection();
                        helper.assertTrue(horizontal.getAxis().isHorizontal(), "horizontal-only stays legal: " + label);
                        double horizontalBest = Math.max(Math.abs(look.x), Math.abs(look.z));
                        if (horizontalBest > 1e-5) {
                            helper.assertTrue(look.dot(Vec3.atLowerCornerOf(horizontal.getNormal())) >= horizontalBest - 1e-5,
                                    "horizontal facing projects the world look: " + label);
                        }
                        helper.assertValueEqual(Blocks.FURNACE.getStateForPlacement(context).getValue(HorizontalDirectionalBlock.FACING),
                                horizontal.getOpposite(), "furnace faces its placer: " + label);
                        if (Math.abs(look.y) > 1e-5) {
                            helper.assertValueEqual(context.getNearestLookingVerticalDirection(), look.y > 0 ? Direction.UP : Direction.DOWN,
                                    "vertical direction uses the world look: " + label);
                        }
                    }
                }
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** effects: verifies the explosion's published world impulse changes local velocity once. */
    @GameTest(template = "tall", batch = "gravity_explosions")
    public void explosionAddsWorldImpulseToLocalVelocity(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().invulnerable = true;
        player.getAbilities().flying = false;
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 8, 7.5));
        player.setPos(at);
        try {
            for (Gravity gravity : Gravity.values()) {
                Frame frame = Frame.of(gravity);
                ((FrameCarrier) player).aberrantmobs$setFrame(frame);
                for (Vec3 before : new Vec3[] {Vec3.ZERO, new Vec3(0.17, -0.23, 0.31)}) {
                    player.setDeltaMovement(before);
                    Explosion blast = helper.getLevel().explode(null, at.x - 2, at.y - 1, at.z - 1, 2, Level.ExplosionInteraction.NONE);
                    Vec3 impulse = blast.getHitPlayers().get(player);
                    helper.assertTrue(impulse != null && impulse.lengthSqr() > 1e-6, "actual blast reached " + gravity);
                    Vec3 actual = WallWalk.vec3(frame.toWorld(WallWalk.vec(player.getDeltaMovement().subtract(before))));
                    helper.assertTrue(actual.distanceTo(impulse) < 1e-7,
                            "one world impulse in " + gravity + ": expected " + impulse + ", got " + actual);
                }
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }
}
