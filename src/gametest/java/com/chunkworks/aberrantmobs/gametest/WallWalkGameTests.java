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

import com.chunkworks.aberrantmobs.ModContent;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: floor to each of four walls to ceiling; full set/removal; dry/water.
 * The chitin armour on a headless server. A player wearing the full set
 * walked into a wall takes it: gravity toward the wall, on the ground of
 * it, its box lying along the wall's normal, and climbs it with no fall;
 * the set taken off mid-wall, it lets go and lands on the floor in the
 * world's frame; water lets go too. Without the set, a wall is a wall.
 * The player is driven by the server's own move each tick, as the server
 * re-runs a client's reported moves.
 */
@GameTestHolder(com.chunkworks.aberrantmobs.AberrantMobsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallWalkGameTests {
    private static final int FLOOR = 4;

    public WallWalkGameTests() {}

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    private static ServerPlayer wearer(GameTestHelper helper, double x, double z, boolean dressed) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, -90.0f, 0.0f);
        if (dressed) {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.CHITIN_HELMET.get()));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.CHITIN_CHESTPLATE.get()));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.CHITIN_LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.CHITIN_BOOTS.get()));
        }
        return p;
    }

    /**
     * effects: one tick of a walk: a step forward in the player's own frame
     * with its gravity, moved by the server as a reported move would be,
     * then the rule, as the player tick would run it -- a mock player has
     * no connection to tick it
     */
    private static void step(ServerPlayer p, double forward) {
        WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(true, forward > 0, p.getYRot(), p.getXRot()));
        WallWalk.walk(p, forward);
    }

    @GameTest(template = "tall", timeoutTicks = 200, batch = "wallwalk")
    public void theFullSetTakesAWallAndClimbsItAndLetsGoWithoutIt(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, FLOOR - 1, 14, Blocks.STONE);
        fill(helper, 10, FLOOR, 0, 14, 14, 14, Blocks.STONE);   // a wall to the east, its face at x = 10
        ServerPlayer p = wearer(helper, 5.5, 7.5, true);
        helper.assertTrue(WallWalk.wears(p), "the full set on");
        helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "the world's frame to start");
        Vec3 wall = helper.absoluteVec(new Vec3(10.0, FLOOR, 7.5));
        for (int t = 1; t <= 60; t++) {
            helper.runAtTickTime(t, () -> step(p, 0.15));
        }
        helper.runAtTickTime(40, () -> {
            Frame f = WallWalk.frameOf(p);
            helper.assertValueEqual(f.gravity(), Gravity.EAST, "the wall's face looks west: it pulls east");
            helper.assertTrue(p.onGround(), "on the ground of the wall");
            AABB box = p.getBoundingBox();
            helper.assertTrue(Math.abs(box.getXsize() - 1.8) < 1e-6 && Math.abs(box.getYsize() - 0.6) < 1e-6, "the box lies along the wall's normal: " + box.getXsize() + " by " + box.getYsize());
            helper.assertTrue(Math.abs(box.maxX - wall.x) < 0.05, "its feet on the face: " + (box.maxX - wall.x));
            helper.assertTrue(p.getY() - wall.y > 1.0, "and it has climbed: " + (p.getY() - wall.y));
            helper.assertTrue(p.fallDistance == 0.0f && p.getHealth() >= p.getMaxHealth() - 1e-3, "no fall, no harm: " + p.fallDistance + ", " + p.getHealth());
            helper.assertTrue(p.getEyePosition().x < box.maxX - 1.0, "its eyes are out from the wall: " + p.getEyePosition().x);
            helper.assertTrue(Math.abs(p.getYRot()) < 1e-3, "turned to face up the wall: yaw " + p.getYRot());
            helper.assertTrue(p.getLookAngle().y > 0.9, "looking along its own forward, which is up the wall: " + p.getLookAngle());
        });
        helper.runAtTickTime(61, () -> {
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
        for (int t = 62; t <= 120; t++) {
            helper.runAtTickTime(t, () -> {
                p.move(MoverType.PLAYER, p.getDeltaMovement().add(0.0, -0.08, 0.0).scale(0.98));
                p.setDeltaMovement(p.getDeltaMovement().add(0.0, -0.08, 0.0).scale(0.98));
                if (p.onGround()) {
                    p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
                }
                WallWalk.rule(p);
            });
        }
        helper.runAtTickTime(64, () -> {
            helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "without the helmet it lets go");
            helper.assertTrue(p.getBoundingBox().getYsize() > 1.7, "the game's own box again");
        });
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(p.onGround() && Math.abs(p.getY() - wall.y) < 0.1, "fallen back to the floor: " + (p.getY() - wall.y));
            helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "in the world's frame");
            helper.succeed();
        });
    }

    @GameTest(template = "tall", timeoutTicks = 120, batch = "wallwalk_water")
    public void waterLetsGoAndWithoutTheSetAWallIsAWall(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, FLOOR - 1, 14, Blocks.STONE);
        fill(helper, 10, FLOOR, 0, 14, 14, 14, Blocks.STONE);
        fill(helper, 7, FLOOR, 5, 9, FLOOR, 9, Blocks.WATER);   // a pool at the foot of the wall
        ServerPlayer dressed = wearer(helper, 5.5, 7.5, true);
        ServerPlayer bare = wearer(helper, 5.5, 2.5, false);
        for (int t = 1; t <= 40; t++) {
            helper.runAtTickTime(t, () -> {
                step(dressed, 0.15);
                step(bare, 0.15);
            });
        }
        helper.runAtTickTime(45, () -> {
            helper.assertValueEqual(WallWalk.frameOf(dressed), Frame.WORLD, "the pool lets go before the wall is taken");
            helper.assertValueEqual(WallWalk.frameOf(bare), Frame.WORLD, "the undressed stay in the world's frame");
            helper.assertTrue(bare.getX() < helper.absoluteVec(new Vec3(10.0, 0, 0)).x - 0.2 && bare.onGround(), "stopped at the wall: " + bare.getX());
            helper.succeed();
        });
    }
    /** effects: walks each wall onto the ceiling through real collision and transition rules. */
    @GameTest(template = "tall", timeoutTicks = 150, batch = "wallwalk_axes")
    public void allFourWallsLeadToTheCeilingAndArmourRemovalLetsGo(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, FLOOR - 1, 14, Blocks.STONE);
        fill(helper, 0, FLOOR, 0, 1, 14, 14, Blocks.STONE);
        fill(helper, 12, FLOOR, 0, 14, 14, 14, Blocks.STONE);
        fill(helper, 0, FLOOR, 0, 14, 14, 1, Blocks.STONE);
        fill(helper, 0, FLOOR, 12, 14, 14, 14, Blocks.STONE);
        fill(helper, 0, 14, 0, 14, 14, 14, Blocks.STONE);
        Gravity[] walls = {Gravity.EAST, Gravity.WEST, Gravity.SOUTH, Gravity.NORTH};
        float[] yaws = {-90, 90, 0, 180};
        ServerPlayer[] players = new ServerPlayer[4];
        for (int i = 0; i < players.length; i++) {
            players[i] = wearer(helper, 7.5, 7.5, true);
            players[i].setYRot(yaws[i]);
            players[i].setYHeadRot(yaws[i]);
        }
        for (int t = 1; t <= 110; t++) {
            helper.runAtTickTime(t, () -> {
                for (ServerPlayer player : players) step(player, 0.15);
            });
        }
        helper.runAtTickTime(40, () -> {
            for (int i = 0; i < players.length; i++) {
                helper.assertValueEqual(WallWalk.frameOf(players[i]).gravity(), walls[i], "took wall " + walls[i]);
                helper.assertTrue(players[i].onGround(), "grounded on " + walls[i]);
            }
        });
        helper.runAtTickTime(111, () -> {
            try {
                for (int i = 0; i < players.length; i++) {
                    ServerPlayer player = players[i];
                    helper.assertValueEqual(WallWalk.frameOf(player).gravity(), Gravity.UP, "ceiling reached from " + walls[i]);
                    helper.assertTrue(player.onGround() && player.fallDistance == 0, "ceiling is its ground from " + walls[i]);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    WallWalk.rule(player);
                    helper.assertValueEqual(WallWalk.frameOf(player), Frame.WORLD, "helmet removal from ceiling after " + walls[i]);
                    helper.assertTrue(helper.getLevel().noCollision(player, player.getBoundingBox()), "released box clears ceiling");
                }
            } finally {
                for (ServerPlayer player : players) player.discard();
            }
            helper.succeed();
        });
    }

    /** Partitions: ordinary push; brief Jump; wrong view; backward movement; deliberate hold. */
    @GameTest(template = "tall", batch = "cling_intent")
    public void bumpsAndIncidentalInputNeverAttach(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, FLOOR - 1, 14, Blocks.STONE);
        fill(helper, 10, FLOOR, 0, 14, 14, 14, Blocks.STONE);
        for (int mode=0; mode<6; mode++) {
            ServerPlayer p = wearer(helper, 9.69, 7.5, true);
            try {
                for (int tick=0; tick<8; tick++) {
                    boolean jump = mode != 0 && (mode != 1 || tick < 3);
                    float yaw = mode == 2 ? 0 : -90;
                    float pitch = mode == 3 ? 90 : 0;
                    WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(jump, mode != 4, yaw, pitch));
                    WallWalk.walk(p, 0.15);
                }
                helper.assertValueEqual(WallWalk.frameOf(p).gravity(), mode == 5 ? Gravity.EAST : Gravity.DOWN,
                        "entry partition " + mode);
                helper.assertTrue(helper.getLevel().noCollision(p), "valid player box after input partition " + mode);
            } finally { p.discard(); }
        }
        helper.succeed();
    }

    /** Ordinary floor ledges stay ordinary even with the full set. */
    @GameTest(template = "tall", batch = "cling_ledge")
    public void walkingOffAnOrdinaryLedgeDoesNotInventSupport(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, 14, 14, Blocks.AIR);
        fill(helper, 0, 0, 0, 6, 3, 14, Blocks.STONE);
        ServerPlayer p = wearer(helper, 5.5, 7.5, true);
        try {
            for (int tick=0; tick<20; tick++) {
                WallWalk.walk(p, 0.15);
                helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "ordinary ledge tick " + tick);
            }
            helper.assertTrue(!p.onGround() && p.getY() < helper.absoluteVec(new Vec3(0,4,0)).y,
                    "normal unsupported fall after the ledge");
        } finally { p.discard(); }
        helper.succeed();
    }

    /** Existing hold stays attached; release/new press detaches safely; held Jump cannot reattach. */
    @GameTest(template = "tall", batch = "cling_release")
    public void jumpReleasesWallAndCeilingWithAReattachmentGuard(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, 3, 14, Blocks.STONE);
        fill(helper, 10, 4, 0, 14, 14, 14, Blocks.STONE);
        fill(helper, 0, 14, 0, 14, 14, 14, Blocks.STONE);
        for (Gravity gravity : new Gravity[] {Gravity.EAST, Gravity.UP}) {
            ServerPlayer p = wearer(helper, 6, 7.5, true);
            try {
                FrameCarrier c = (FrameCarrier)p;
                c.aberrantmobs$setFrame(Frame.of(gravity));
                p.setPos(helper.absoluteVec(gravity == Gravity.EAST ? new Vec3(10,8,7.5) : new Vec3(6,14,7.5)));
                c.aberrantmobs$setGesture(new com.chunkworks.aberrantmobs.domain.frame.ClingGesture(true,4,0,false,false));
                WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(true,true,0,0));
                WallWalk.prepare(p);
                helper.assertValueEqual(WallWalk.frameOf(p).gravity(), gravity, "entry hold keeps " + gravity);
                WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(false,false,0,0));
                WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(true,false,0,0));
                WallWalk.prepare(p);
                helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "fresh jump releases " + gravity);
                helper.assertTrue(helper.getLevel().noCollision(p), "upright release fits " + gravity);
                helper.assertTrue(p.getDeltaMovement().dot(WallWalk.vec3(Frame.of(gravity).up())) > 0.4,
                        "jump moves away from " + gravity);
                p.setYRot(-90);
                for (int tick=0; tick<20; tick++) {
                    WallWalk.input(p, new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(true,true,-90,0));
                    WallWalk.walk(p, 0.15);
                    helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "held release cannot stick again");
                }
            } finally { p.discard(); }
        }
        helper.succeed();
    }

    /** Attached convex edges need an actual face; removed support releases to world gravity. */
    @GameTest(template = "tall", batch = "cling_corner")
    public void wrapsOntoRealLedgeAndLetsGoWhenTheSurfaceDisappears(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, 14, 14, Blocks.AIR);
        fill(helper, 10, 0, 0, 14, 8, 14, Blocks.STONE);
        ServerPlayer p = wearer(helper, 5.5, 7.5, true);
        try {
            FrameCarrier c = (FrameCarrier)p;
            c.aberrantmobs$setFrame(Frame.of(Gravity.EAST));
            p.setPos(helper.absoluteVec(new Vec3(10,8.7,7.5)));
            p.setYRot(0);
            for (int tick=0; tick<8 && WallWalk.bent(p); tick++) WallWalk.walk(p, 0.15);
            helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "climbed over the real top edge");
            helper.assertTrue(p.onGround() && helper.getLevel().noCollision(p), "stood on the top, clear of rock");
            c.aberrantmobs$setFrame(Frame.of(Gravity.EAST));
            p.setPos(helper.absoluteVec(new Vec3(10,6,7.5)));
            fill(helper, 10, 0, 0, 14, 8, 14, Blocks.AIR);
            WallWalk.walk(p, 0.15);
            helper.assertValueEqual(WallWalk.frameOf(p), Frame.WORLD, "removed wall cannot pull through empty space");
        } finally { p.discard(); }
        helper.succeed();
    }

    /** A real convex-corner endpoint is replayed exactly, while unsupported/forged endpoints retain normal checks. */
    @GameTest(template = "tall", batch = "cling_edge_replay")
    public void serverReplaysTheActualOuterCornerWithoutTrustingTheEndpoint(GameTestHelper helper) {
        fill(helper, 0, 0, 0, 14, 14, 14, Blocks.AIR);
        fill(helper, 10, 0, 0, 14, 8, 14, Blocks.STONE);
        ServerPlayer client = wearer(helper,5,7.5,true), server = wearer(helper,5,7.5,true);
        Vec3 start = helper.absoluteVec(new Vec3(10,9.25,7.5));
        try {
            for (ServerPlayer p : new ServerPlayer[] {client,server}) {
                ((FrameCarrier)p).aberrantmobs$setFrame(Frame.of(Gravity.EAST));
                p.setPos(start);
                p.setYRot(0);
                p.setOnGround(true);
            }
            WallWalk.walk(client,0.15);
            helper.assertValueEqual(WallWalk.frameOf(client),Frame.WORLD,"client goes over the convex top edge");
            helper.assertTrue(client.onGround(),"client landed on the real top face");
            Vec3 endpoint = client.position();
            client.discard();
            Vec3 reported = endpoint.subtract(start);
            Vec3 attempted = WallWalk.replayMove(server,reported);
            helper.assertTrue(attempted.distanceTo(reported)>0.2,"fixture exercises the corner displacement");
            server.move(MoverType.PLAYER,attempted);
            WallWalk.rule(server);
            helper.assertValueEqual(WallWalk.frameOf(server),Frame.WORLD,"server followed the same corner");
            helper.assertTrue(server.position().distanceTo(endpoint)<1e-5,"all reported coordinates match actual supported stance");
            helper.assertTrue(server.onGround() && helper.getLevel().noCollision(server),"server is safely supported");
            ((FrameCarrier)server).aberrantmobs$setFrame(Frame.of(Gravity.EAST));
            server.setPos(start);
            Vec3 forged = reported.add(0,-2,0);
            helper.assertValueEqual(WallWalk.replayMove(server,forged),forged,"penetrating endpoint gets no stance exception");
        } finally { client.discard(); server.discard(); }
        helper.succeed();
    }

    /** The client's airborne forward acceleration at a wall is 0.0196 blocks per tick. */
    @GameTest(template = "tall", batch = "cling_air_entry")
    public void heldJumpCanAttachWhileTheEntryJumpIsStillRising(GameTestHelper helper) {
        fill(helper,0,0,0,14,3,14,Blocks.STONE);
        fill(helper,10,4,0,14,14,14,Blocks.STONE);
        ServerPlayer p=wearer(helper,9.69,7.5,true);
        try {
            p.setPos(helper.absoluteVec(new Vec3(9.69,5,7.5)));
            p.setOnGround(false);
            for (int i=0;i<4;i++) {
                WallWalk.input(p,new com.chunkworks.aberrantmobs.domain.frame.ClingIntent(true,true,-90,0));
                p.move(MoverType.PLAYER,new Vec3(0.0196,0.16,0));
                WallWalk.rule(p);
            }
            helper.assertValueEqual(WallWalk.frameOf(p).gravity(),Gravity.EAST,"attach during the deliberate entry jump");
            helper.assertTrue(p.getDeltaMovement().y == 0,"no outward jump remains after entry");
        } finally { p.discard(); }
        helper.succeed();
    }

}
