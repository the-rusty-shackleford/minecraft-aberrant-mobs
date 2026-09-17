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
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A loopback-only dedicated-server fixture for two independently connected clients.
 * Partitions: natural survival wall entry; all six frames; tracked equipment and
 * position; actual velocity packets; ceiling armour release. Test orchestration uses
 * ordinary system/chat packets; player/frame/motion state uses the production protocol.
 * AF: phase, frame and acknowledgements describe the current experiment.
 * RI: acknowledgements are the two named clients; phases advance only after both agree.
 * No FakePlayer and no direct access to either client's Minecraft instance.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID)
public final class MultiplayerServer {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant multiplayer");
    private static final boolean ACTIVE = "server".equals(System.getProperty("aberrantmobs.multiplayer"));
    private static final Set<String> ACKS = new HashSet<>();
    private static final Set<String> JOINED = new HashSet<>();
    private static String phase = "waiting";
    private static int age, frame;
    private static boolean failed;
    private MultiplayerServer() {}

    @SubscribeEvent
    public static void chat(ServerChatEvent event) {
        if (!ACTIVE) return;
        if (event.getRawText().equals("AB_JOIN")) {
            event.setCanceled(true);
            JOINED.add(event.getPlayer().getGameProfile().getName());
            return;
        }
        if (!event.getRawText().startsWith("AB_ACK:")) return;
        event.setCanceled(true);
        if (event.getRawText().equals("AB_ACK:" + phase)) ACKS.add(event.getPlayer().getGameProfile().getName());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!ACTIVE) return;
        MinecraftServer server = event.getServer();
        if (failed) { if (++age > 20) server.halt(false); return; }
        if (phase.equals("done")) { if (++age > 30) server.halt(false); return; }
        var players = server.getPlayerList().getPlayers();
        ServerPlayer wearer = players.stream().filter(p -> p.getGameProfile().getName().equals("FrameWearer")).findFirst().orElse(null);
        ServerPlayer observer = players.stream().filter(p -> p.getGameProfile().getName().equals("FrameObserver")).findFirst().orElse(null);
        if (wearer == null || observer == null) {
            if (++age > 3600) fail("both independent clients did not join");
            return;
        }
        if (++age > 600 && !phase.equals("waiting")) { fail("timeout in " + phase + ", acknowledgements " + ACKS); return; }
        if (phase.equals("waiting")) {
            if (JOINED.size() != 2) return;
            if (wearer.connection.getConnection().isMemoryConnection() || observer.connection.getConnection().isMemoryConnection()) {
                fail("expected two TCP connections"); return;
            }
            var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(6000);
            for (int x = 0; x <= 16; x++) for (int y = 64; y <= 80; y++) for (int z = 0; z <= 16; z++) {
                boolean shell = x == 0 || x == 16 || y == 64 || y == 80 || z == 0 || z == 16;
                level.setBlockAndUpdate(new BlockPos(x, y, z), (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
            for (int x : new int[] {4, 8, 12}) for (int z : new int[] {4, 8, 12}) {
                level.setBlockAndUpdate(new BlockPos(x,64,z), Blocks.SEA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x,80,z), Blocks.SEA_LANTERN.defaultBlockState());
            }
            for (int y : new int[] {68,72,76}) {
                level.setBlockAndUpdate(new BlockPos(0,y,8), Blocks.SEA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(16,y,8), Blocks.SEA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(8,y,0), Blocks.SEA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(8,y,16), Blocks.SEA_LANTERN.defaultBlockState());
            }
            wearer.setGameMode(GameType.SURVIVAL);
            wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.CHITIN_HELMET.get()));
            wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.CHITIN_CHESTPLATE.get()));
            wearer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.CHITIN_LEGGINGS.get()));
            wearer.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.CHITIN_BOOTS.get()));
            wearer.inventoryMenu.sendAllDataToRemote();
            observer.setGameMode(GameType.SPECTATOR);
            wearer.teleportTo(level, 12, 65, 8, -90, 0);
            observer.teleportTo(level, 8, 71, 8, -90, 15);
            cue(server, "ready");
        } else if (phase.equals("ready") && ACKS.size() == 2) {
            LOG.info("multiplayer: PASS two real TCP players received the fixture");
            cue(server, "walk");
        } else if (phase.equals("walk") && (wearer.getY() >= 70 || age >= 100)) {
            if (WallWalk.frameOf(wearer).gravity() != Gravity.EAST || wearer.getY() < 69) {
                fail("survival climb disagreed: " + wearer.position() + " " + WallWalk.frameOf(wearer)); return;
            }
            LOG.info("multiplayer: PASS independent wearer climbed via actual movement packets: {} {}", wearer.position(), WallWalk.frameOf(wearer));
            cue(server, "climbed");
        } else if (phase.equals("climbed") && ACKS.size() == 2) {
            cue(server, "wrap");
        } else if (phase.equals("wrap")) {
            LOG.info("multiplayer: wrap server {} {}", wearer.position(), WallWalk.frameOf(wearer).gravity());
            if (WallWalk.frameOf(wearer).gravity() == Gravity.UP) cue(server, "wrapped");
        } else if (phase.equals("wrapped") && ACKS.size() == 2) {
            frame = 0;
            place(server, wearer, observer);
        } else if (phase.startsWith("frame:") && ACKS.size() == 2) {
            Gravity gravity = Gravity.values()[frame];
            wearer.setDeltaMovement(Vec3.ZERO);
            wearer.setOnGround(true);
            wearer.knockback(0.4, 0.6, 0.8);
            var packet = new ClientboundSetEntityMotionPacket(wearer);
            wearer.connection.send(packet);
            observer.connection.send(packet);
            cue(server, "impulse:" + gravity + ":" + packet.getXa() + ":" + packet.getYa() + ":" + packet.getZa());
        } else if (phase.startsWith("impulse:") && ACKS.size() == 2) {
            LOG.info("multiplayer: PASS velocity packet applied by both clients in {}", Gravity.values()[frame]);
            if (++frame < Gravity.values().length) place(server, wearer, observer);
            else {
                ((FrameCarrier) wearer).aberrantmobs$setFrame(Frame.of(Gravity.UP));
                wearer.teleportTo(server.overworld(), 8, 80, 8, 0, 0);
                wearer.connection.resetPosition();
                wearer.setDeltaMovement(Vec3.ZERO);
                wearer.fallDistance = 0;
                wearer.setHealth(wearer.getMaxHealth());
                cue(server, "ceiling");
            }
        } else if (phase.equals("ceiling") && ACKS.size() == 2) {
            wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            wearer.inventoryMenu.sendAllDataToRemote();
            cue(server, "released");
        } else if (phase.equals("released") && ACKS.size() == 2) {
            if (!WallWalk.frameOf(wearer).gravity().isDown() || !server.overworld().noCollision(wearer)) {
                fail("server release did not restore a free world-frame box"); return;
            }
            LOG.info("multiplayer: PASS all checks ran");
            cue(server, "done");
        }
    }

    private static void place(MinecraftServer server, ServerPlayer wearer, ServerPlayer observer) {
        Gravity gravity = Gravity.values()[frame];
        Vec3 at = switch (gravity) {
            case DOWN -> new Vec3(8.5, 65, 8.5);
            case UP -> new Vec3(8.5, 80, 8.5);
            case WEST -> new Vec3(1, 72.5, 8.5);
            case EAST -> new Vec3(16, 72.5, 8.5);
            case NORTH -> new Vec3(8.5, 72.5, 1);
            case SOUTH -> new Vec3(8.5, 72.5, 16);
        };
        ((FrameCarrier) wearer).aberrantmobs$setFrame(Frame.of(gravity));
        wearer.teleportTo(server.overworld(), at.x, at.y, at.z, 0, 0);
        wearer.connection.resetPosition();
        wearer.setDeltaMovement(Vec3.ZERO);
        wearer.fallDistance = 0;
        wearer.setHealth(wearer.getMaxHealth());
        wearer.connection.send(new ClientboundSetEntityMotionPacket(wearer));
        Frame stance = Frame.of(gravity);
        Vec3 eye = at.add(WallWalk.vec3(stance.up().times(5).plus(stance.right().times(2)).plus(stance.forward().times(0.8))));
        Vec3 toward = wearer.getEyePosition().subtract(eye);
        observer.teleportTo(server.overworld(), eye.x, eye.y - observer.getEyeHeight(), eye.z,
                (float)Math.toDegrees(Math.atan2(-toward.x, toward.z)),
                (float)-Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance())));
        observer.connection.resetPosition();
        cue(server, "frame:" + gravity);
    }

    private static void cue(MinecraftServer server, String next) {
        phase = next; age = 0; ACKS.clear();
        server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(Component.literal("AB_STAGE:" + next)));
        LOG.info("multiplayer: stage {}", next);
    }

    private static void fail(String message) { LOG.error("multiplayer: FAIL {}", message); failed = true; age = 0; }
}
