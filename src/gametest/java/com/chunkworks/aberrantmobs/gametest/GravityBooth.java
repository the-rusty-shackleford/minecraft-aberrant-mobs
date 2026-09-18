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
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real-client gravity adapter gate, selected with -PboothGravity.
 * Partitions: six frame axes; local/remote player; first/front/back camera;
 * oblique local look; explosion packet; armour removal. The remote actor is
 * server-controlled and reaches the observer through the real tracker. Forced
 * frame scenes test rendering/packet adapters; the full booth separately tests
 * real survival movement and the transition onto a wall.
 */
public final class GravityBooth {
    private GravityBooth() {}
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant gravity booth");
    private static final UUID ACTOR = UUID.fromString("18d6c072-08e5-4274-9536-9e813ea3e695");
    private static FakePlayer actor;
    private static Vec3 feet;
    private static Frame oldFrame;
    private static boolean eyesCaptured;

    /** effects: prepares a floating support plane and a named, armoured server actor. */
    public static void setUp(ServerPlayer observer) {
        var level = observer.serverLevel();
        level.setDayTime(6000);
        feet = new Vec3(0, level.getMinBuildHeight() + 16, 0);
        observer.setGameMode(GameType.CREATIVE);
        observer.setNoGravity(true);
        observer.getAbilities().flying = true;
        observer.onUpdateAbilities();
        actor = new FakePlayer(level, new GameProfile(ACTOR, "ChitinWearer"));
        actor.setNoGravity(true);
        dress(actor);
        observer.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, actor));
        level.addNewPlayer(actor);
        scene(observer, Frame.WORLD);
    }

    private static void dress(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.CHITIN_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.CHITIN_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModContent.CHITIN_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModContent.CHITIN_BOOTS.get()));
    }

    private static void plane(ServerPlayer observer, Frame frame, boolean clear) {
        for (int x = -3; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                Vec offset = frame.toWorld(new Vec(x + 0.5, -0.5, z + 0.5));
                BlockPos pos = BlockPos.containing(feet.add(WallWalk.vec3(offset)));
                observer.serverLevel().setBlockAndUpdate(pos, (clear ? Blocks.AIR : Blocks.SMOOTH_STONE).defaultBlockState());
            }
        }
    }

    private static void scene(ServerPlayer observer, Frame frame) {
        if (oldFrame != null) plane(observer, oldFrame, true);
        plane(observer, frame, false);
        oldFrame = frame;
        ((FrameCarrier) actor).aberrantmobs$setFrame(frame);
        actor.moveTo(feet.x, feet.y, feet.z, 180, 0);
        actor.setYHeadRot(180);
        actor.setYBodyRot(180);
        actor.setDeltaMovement(Vec3.ZERO);
        actor.doTick();
        Vec3 view = feet.add(WallWalk.vec3(frame.toWorld(new Vec(4, 4, 5))));
        observer.getAbilities().flying = true;
        observer.onUpdateAbilities();
        observer.setDeltaMovement(Vec3.ZERO);
        observer.teleportTo(observer.serverLevel(), view.x, view.y, view.z, 0, 0);
        observer.lookAt(EntityAnchorArgument.Anchor.EYES, feet.add(WallWalk.vec3(frame.up().times(1.1))));
    }

    /** effects: runs one bounded tick of the adapter scenes, then closes the rendering client. */
    public static void tick(Minecraft mc, int tick) {
        mc.options.hideGui = false; // vanilla intentionally hides player names with the HUD
        if (tick < 480) {
            int index = tick / 80;
            int phase = tick % 80;
            Frame frame = Frame.of(Gravity.values()[index]);
            if (phase == 10) onServer(mc, sp -> scene(sp, frame));
            if (phase == 60) {
                Player remote = mc.level.getPlayerByUUID(ACTOR);
                LOG.info("booth: observer {} at={} flying={} noGravity={} actor={}", frame.gravity(),
                        mc.player.position(), mc.player.getAbilities().flying, mc.player.isNoGravity(), remote == null ? null : remote.position());
                check("remote frame " + frame.gravity(), () -> remote != null && WallWalk.frameOf(remote).equals(frame));
                shoot(mc, "gravity-observer-" + frame.gravity().name().toLowerCase(java.util.Locale.ROOT));
            }
            return;
        }
        if (tick == 480) onServer(mc, sp -> {
            actor.discard();
            plane(sp, oldFrame, true);
            dress(sp);
            sp.setGameMode(GameType.SURVIVAL);
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.setNoGravity(true);
        });
        if (tick >= 500 && tick < 1040) {
            int index = (tick - 500) / 90;
            int phase = (tick - 500) % 90;
            Frame frame = Frame.of(Gravity.values()[index]);
            if (phase == 0) eyesCaptured = false;
            if (phase == 0) onServer(mc, sp -> {
                if (oldFrame != null) plane(sp, oldFrame, true);
                plane(sp, frame, false);
                oldFrame = frame;
                ((FrameCarrier) sp).aberrantmobs$setFrame(frame);
                sp.teleportTo(sp.serverLevel(), feet.x, feet.y, feet.z, 37, 20);
                sp.setDeltaMovement(Vec3.ZERO);
            });
            if (phase == 30) {
                check("local frame " + frame.gravity(), () -> WallWalk.frameOf(mc.player).equals(frame));
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            if (phase == 40) {
                camera(mc, frame, false);
                shoot(mc, "gravity-back-" + frame.gravity().name().toLowerCase(java.util.Locale.ROOT));
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (phase == 50) {
                camera(mc, frame, true);
                shoot(mc, "gravity-front-" + frame.gravity().name().toLowerCase(java.util.Locale.ROOT));
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            // Client ticks can catch up several at once under shaders. Sample only
            // after a first-person frame was actually rendered, not the prior front view.
            if (phase >= 55 && phase < 85 && !eyesCaptured && !mc.gameRenderer.getMainCamera().isDetached()) {
                eyesCaptured = true;
                int bright = 0;
                try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    for (int y = pixels.getHeight() / 3; y < pixels.getHeight() * 2 / 3; y++) {
                        for (int x = pixels.getWidth() / 3; x < pixels.getWidth() * 2 / 3; x++) {
                            int color = pixels.getPixelRGBA(x, y);
                            if (Math.max(color & 255, Math.max((color >> 8) & 255, (color >> 16) & 255)) > 32) bright++;
                        }
                    }
                }
                final int visible = bright;
                check("unobstructed first-person view " + frame.gravity(), () -> visible > 1000);
                shoot(mc, "gravity-eyes-" + frame.gravity().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (phase == 85 && !eyesCaptured) {
                check("first-person frame rendered " + frame.gravity(), () -> false);
            }
            if (phase == 60) {
                Vec3 before = mc.player.getDeltaMovement();
                Vec3 impulse = new Vec3(0.12, 0.23, -0.34);
                // A packet stimulus into the real client handler, in one client tick;
                // the server suite separately exercises the real explosion producer.
                mc.getConnection().handleExplosion(new ClientboundExplodePacket(feet.x + 8, feet.y, feet.z,
                        0, List.of(), impulse, Explosion.BlockInteraction.KEEP,
                        ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE));
                Vec3 actual = WallWalk.vec3(frame.toWorld(WallWalk.vec(mc.player.getDeltaMovement().subtract(before))));
                check("client blast conversion " + frame.gravity(), () -> actual.distanceTo(impulse) < 1e-6);
                mc.player.setDeltaMovement(before);
            }
            return;
        }
        if (tick == 1040) onServer(mc, sp -> sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        if (tick == 1070) {
            check("removing armour restores world frame on client", () -> WallWalk.frameOf(mc.player).equals(Frame.WORLD));
            onServer(mc, sp -> check("removing armour restores world frame on server", () -> WallWalk.frameOf(sp).equals(Frame.WORLD)));
        }
        if (tick == 1100 || tick == 1200) {
            boolean low = tick == 1100;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            onServer(mc, sp -> {
                dress(sp);
                Frame frame = Frame.of(Gravity.EAST);
                ((FrameCarrier) sp).aberrantmobs$setFrame(frame);
                double ground = sp.serverLevel().getMinBuildHeight() + 4;
                sp.teleportTo(sp.serverLevel(), 0, ground + (low ? 1 : 6), 0, 0, 0);
                sp.setDeltaMovement(Vec3.ZERO);
                // Same wall and look; height is the only changed variable.
                for (int y = 0; y < 12; y++) for (int z = -3; z <= 3; z++) {
                    sp.serverLevel().setBlockAndUpdate(new BlockPos(0, (int) ground + y, z), Blocks.SMOOTH_STONE.defaultBlockState());
                }
            });
        }
        if (tick == 1160 || tick == 1260) {
            boolean low = tick == 1160;
            var camera = mc.gameRenderer.getMainCamera();
            Vec3 offset = camera.getPosition().subtract(mc.player.getEyePosition());
            double ground = mc.level.getMinBuildHeight() + 4;
            LOG.info("booth: camera ground clearance low={} eye={} camera={} distance={}", low,
                    mc.player.getEyePosition(), camera.getPosition(), offset.length());
            check("rear camera stays behind wall look low=" + low,
                    () -> offset.normalize().dot(mc.player.getViewVector(1)) < -0.9999);
            check("rear camera respects ground clearance low=" + low,
                    // Vanilla measures from the centre to rays offset by 0.1 in X/Z:
                    // one block of height gives sqrt(1 + .1² + .1²), not exactly 1.
                    () -> low ? Math.abs(offset.length() - Math.sqrt(1.02)) < 0.002 && camera.getPosition().y >= ground - 0.02
                            : Math.abs(offset.length() - 4) < 0.02);
            shoot(mc, "gravity-low-clearance-" + (low ? "low" : "high"));
        }
        if (tick == 1300) {
            LOG.info("booth: PASS all checks ran");
            mc.stop();
        }
    }

    private static void camera(Minecraft mc, Frame frame, boolean front) {
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 look = mc.player.getViewVector(1);
        Vec3 cameraLook = new Vec3(camera.getLookVector());
        Vec3 offset = camera.getPosition().subtract(mc.player.getEyePosition());
        double sign = front ? -1 : 1;
        if (cameraLook.dot(look) * sign <= 0.9999) {
            var expected = WallWalk.aabb(frame.box(WallWalk.vec(mc.player.position()), mc.player.getBbWidth(), mc.player.getBbHeight()));
            LOG.info("booth: camera diagnostic frame={} cachedFits={} frameFits={} box={} expected={} look={} cameraLook={}",
                    frame.gravity(), mc.level.noCollision(mc.player), mc.level.noCollision(mc.player, expected),
                    mc.player.getBoundingBox(), expected, look, cameraLook);
        }
        check("camera view " + frame.gravity() + " front=" + front, () -> cameraLook.dot(look) * sign > 0.9999);
        check("camera clearance " + frame.gravity() + " front=" + front,
                () -> Math.abs(offset.length() - 4) < 0.02 && offset.normalize().dot(look) * sign < -0.9999);
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) action.accept(sp);
        });
    }

    private static void check(String what, BooleanSupplier condition) {
        try {
            if (condition.getAsBoolean()) LOG.info("booth: PASS {}", what);
            else LOG.error("booth: FAIL {}", what);
        } catch (RuntimeException e) {
            LOG.error("booth: FAIL {} -- {}", what, e.toString());
        }
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("booth: {}", message.getString()));
    }
}
