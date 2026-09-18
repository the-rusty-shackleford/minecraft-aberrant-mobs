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
package com.chunkworks.aberrantmobs.client;

import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import com.chunkworks.aberrantmobs.wallwalk.ClingInput;
import com.chunkworks.aberrantmobs.domain.frame.ClingIntent;
import com.chunkworks.aberrantmobs.AberrantMobsMod;
import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.frame.Blend;
import com.chunkworks.aberrantmobs.domain.frame.CameraAngles;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The wall-walk as the client sees it: when a player's frame changes, its
 * rotation swings to the new one over a few ticks ({@link Blend}), and
 * the camera of a wearer is the frame's rotation composed with its look,
 * given back to the game as yaw, pitch and roll. The model's turn comes
 * from the same blend.
 */
@EventBusSubscriber(modid = AberrantMobsMod.MOD_ID, value = Dist.CLIENT)
public final class WallWalkClient {
    private WallWalkClient() {}

    /** effects: samples manual Jump before auto-jump, sends it before movement, and consumes it while clinging. */
    @SubscribeEvent
    public static void onInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        if (!WallWalk.wears(p) && !WallWalk.bent(p)) return;
        var keys = event.getInput();
        boolean attached = WallWalk.bent(p);
        var intent = new ClingIntent(keys.jumping, keys.forwardImpulse > 0, p.getYRot(), p.getXRot());
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ClingInput(intent.jump(), intent.forward(), intent.yaw(), intent.pitch()));
        WallWalk.input(p, intent);
        WallWalk.prepare(p);
        if (attached) keys.jumping = false;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (Player p : mc.level.players()) {
            if (!(p instanceof FrameCarrier c)) {
                continue;
            }
            Frame f = c.aberrantmobs$frame();
            int code = f.code();
            if (code != c.aberrantmobs$seenCode()) {
                Quat from = frameRotation(p, 0.0f);
                c.aberrantmobs$setBlend(new Blend(from, f.rotation(), Blend.TICKS, p.tickCount));
                c.aberrantmobs$setSeenCode(code);
            }
            Blend b = c.aberrantmobs$blend();
            if (b != null && b.done(p.tickCount)) {
                c.aberrantmobs$setBlend(null);
            }
        }
    }

    @Nullable
    public static Blend blendOf(Entity e) {
        return e instanceof FrameCarrier c ? c.aberrantmobs$blend() : null;
    }

    /** effects: returns the rotation the entity's frame shows as this frame: its blend's, or its frame's outright */
    public static Quat frameRotation(Entity e, float partialTick) {
        if (!(e instanceof FrameCarrier c)) {
            return Quat.IDENTITY;
        }
        Blend b = c.aberrantmobs$blend();
        return b != null ? b.at(e.tickCount + partialTick) : c.aberrantmobs$frame().rotation();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity e = event.getCamera().getEntity();
        if (!(e instanceof FrameCarrier c) || (c.aberrantmobs$frame().gravity().isDown() && c.aberrantmobs$blend() == null)) {
            return;
        }
        Quat frame = frameRotation(e, (float) event.getPartialTick());
        Quat look = CameraAngles.compose(new CameraAngles.Angles(event.getYaw(), event.getPitch(), event.getRoll()));
        CameraAngles.Angles a = CameraAngles.decompose(frame.times(look));
        event.setYaw((float) a.yaw());
        event.setPitch((float) a.pitch());
        event.setRoll((float) a.roll());
    }
}
