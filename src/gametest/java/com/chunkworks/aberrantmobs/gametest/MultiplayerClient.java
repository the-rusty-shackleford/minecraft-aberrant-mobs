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

import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Independent real Minecraft clients; the wearer disables scene rendering only,
 * retaining ordinary input, physics, packet handling and connection ticks.
 * AF: stage/age represent the server's current experiment and local observation time.
 * RI: a stage is acknowledged only after this client's own replicated state passes.
 * The server never calls this class or supplies a replacement entity/backend.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class MultiplayerClient {
    private static final Logger LOG = LoggerFactory.getLogger("Aberrant multiplayer");
    private static final String ROLE = System.getProperty("aberrantmobs.multiplayer", "");
    private static final boolean ACTIVE = ROLE.equals("wearer") || ROLE.equals("observer");
    private static String stage = "";
    private static int age, total;
    private static boolean connected, acknowledged, noRenderReady, joined;
    private MultiplayerClient() {}

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent.System event) {
        if (!ACTIVE || !event.getMessage().getString().startsWith("AB_STAGE:")) return;
        event.setCanceled(true);
        stage = event.getMessage().getString().substring(9);
        age = 0; acknowledged = false;
        Minecraft mc = Minecraft.getInstance();
        LOG.info("multiplayer: stage {}", stage);
        if (stage.startsWith("impulse:")) {
            Player wearer = wearer(mc);
            String[] fields = stage.split(":");
            Vec3 expected = new Vec3(Double.parseDouble(fields[2]), Double.parseDouble(fields[3]), Double.parseDouble(fields[4]));
            // Ordered on the same TCP connection immediately after the actual motion packet.
            if (wearer == null || receivedMotion(wearer).distanceTo(expected) > 1e-6) {
                fail(mc, "motion packet in " + fields[1] + ": " + (wearer == null ? "missing player" : wearer.getDeltaMovement()) + " expected " + expected);
            } else pass(mc, "actual motion packet in " + fields[1]);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        if (++total > 6000) { fail(mc, "client timeout in " + stage); return; }
        if (!connected && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            connected = true;
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25579"),
                    new ServerData("Aberrant local verification", "127.0.0.1:25579", ServerData.Type.OTHER), false, null);
        }
        if (connected && ROLE.equals("wearer") && (noRenderReady || (mc.player != null && mc.level != null && mc.screen == null))) {
            mc.noRender = true;
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if (!noRenderReady) {
                noRenderReady = true;
                LOG.info("multiplayer: NON_RENDERING_READY");
            }
        }
        if (connected && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            fail(mc, "disconnected before completion"); return;
        }
        if (mc.player == null || mc.level == null) return;
        if (!joined && mc.screen == null && mc.level.hasChunkAt(mc.player.blockPosition())) {
            joined = true;
            mc.getConnection().sendChat("AB_JOIN");
            LOG.info("multiplayer: joined {}", mc.player.getGameProfile().getName());
        }
        mc.options.keyUp.setDown(ROLE.equals("wearer") && (stage.equals("bump") || stage.equals("walk") || stage.equals("wrap") || stage.equals("outer-walk")));
        mc.options.keyJump.setDown(ROLE.equals("wearer") && (stage.equals("walk") || stage.equals("jump-release")));
        if (stage.equals("done")) {
            mc.options.keyUp.setDown(false);
            LOG.info("multiplayer: PASS all checks ran ({})", ROLE);
            mc.stop(); return;
        }
        if (stage.equals("wrap")) LOG.info("multiplayer: wrap client {} {} {}", ROLE, mc.player.position(), WallWalk.frameOf(mc.player).gravity());
        if (stage.isEmpty() || acknowledged || ++age < 35) return;
        Player wearer = wearer(mc);
        if (wearer == null) { if (age > 180) fail(mc, "tracked wearer missing; client players=" + mc.level.players().stream().map(p -> p.getGameProfile().getName()).toList()); return; }
        boolean ok = false;
        if (stage.equals("ready")) ok = WallWalk.wears(wearer) && mc.getConnection() != null && !mc.getConnection().getConnection().isMemoryConnection();
        else if (stage.equals("bumped")) ok = !WallWalk.bent(wearer) && wearer.getX() > 15.6;
        else if (stage.equals("jumped")) ok = !WallWalk.bent(wearer) && WallWalk.wears(wearer) && mc.level.noCollision(wearer);
        else if (stage.equals("outer-ready")) ok = WallWalk.frameOf(wearer).gravity() == Gravity.EAST && Math.abs(wearer.getX()-10)<0.01;
        else if (stage.equals("outer-done")) ok = !WallWalk.bent(wearer) && wearer.getY()>69.99 && mc.level.noCollision(wearer);
        else if (stage.equals("climbed")) ok = WallWalk.frameOf(wearer).gravity() == Gravity.EAST && wearer.getY() > 69;
        else if (stage.equals("wrapped")) ok = WallWalk.frameOf(wearer).gravity() == Gravity.UP;
        else if (stage.startsWith("frame:")) {
            Gravity wanted = Gravity.valueOf(stage.substring(6));
            ok = WallWalk.frameOf(wearer).gravity() == wanted && WallWalk.wears(wearer)
                    && wearer.getBoundingBox().contains(wearer.getEyePosition());
        } else if ((stage.equals("ceiling") || stage.equals("ceiling-again"))) ok = WallWalk.frameOf(wearer).gravity() == Gravity.UP;
        else if (stage.equals("released")) ok = WallWalk.frameOf(wearer).gravity() == Gravity.DOWN
                && !WallWalk.wears(wearer) && mc.level.noCollision(wearer);
        if (ok) {
            if (ROLE.equals("observer") && (stage.startsWith("frame:") || stage.equals("climbed") || stage.equals("released")))
                Screenshot.grab(mc.gameDirectory, "multiplayer-" + stage.replace(':', '-') + ".png", mc.getMainRenderTarget(), message -> LOG.info("{}", message.getString()));
            pass(mc, stage + " observed on " + ROLE + ": " + wearer.position() + " " + WallWalk.frameOf(wearer));
        } else if (age > 180 && !stage.equals("walk") && !stage.equals("wrap") && !stage.equals("bump") && !stage.equals("jump-release") && !stage.equals("outer-walk") && !stage.isEmpty()) fail(mc, "state mismatch in " + stage + ": " + wearer.position() + " " + WallWalk.frameOf(wearer));
    }

    private static Vec3 receivedMotion(Player player) {
        if (!(player instanceof net.minecraft.client.player.RemotePlayer)) return player.getDeltaMovement();
        // Vanilla RemotePlayer stores packet motion as an interpolation target.
        try {
            var field = net.minecraft.client.player.RemotePlayer.class.getDeclaredField("lerpDeltaMovement");
            field.setAccessible(true);
            return (Vec3) field.get(player);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static Player wearer(Minecraft mc) {
        if (ROLE.equals("wearer")) return mc.player;
        return mc.level == null ? null : mc.level.players().stream()
                .filter(p -> p.getGameProfile().getName().equals("FrameWearer")).findFirst().orElse(null);
    }
    private static void pass(Minecraft mc, String detail) {
        LOG.info("multiplayer: PASS {}", detail);
        acknowledged = true;
        mc.getConnection().sendChat("AB_ACK:" + stage);
    }
    private static void fail(Minecraft mc, String detail) {
        LOG.error("multiplayer: FAIL {}", detail);
        mc.options.keyUp.setDown(false);
        mc.stop();
    }
}
