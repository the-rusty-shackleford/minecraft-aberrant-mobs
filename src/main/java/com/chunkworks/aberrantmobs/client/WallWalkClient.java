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
import com.chunkworks.aberrantmobs.domain.frame.PoseBlend;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.mixin.CameraAccessor;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import com.chunkworks.aberrantmobs.domain.frame.CameraAngles;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The wall-walk as the client sees it: when a player's frame changes, its
 * complete visible pose moves continuously to the new one ({@link PoseBlend}), and
 * the camera of a wearer is the frame's rotation composed with its look,
 * given back to the game as yaw, pitch and roll. The model's turn comes
 * from the same pose-correction rule.
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

    private static final Map<Entity, Presentation> BODIES = new WeakHashMap<>();
    private static Entity cameraEntity;
    private static Presentation cameraPose;
    private static Vec cameraEye;
    private static net.minecraft.client.multiplayer.ClientLevel scene;

    /**
     * Client-owned display history. RI: frame/rotation/position describe the last
     * sampled visible pose; correction is null or fades a frame discontinuity.
     * AF: the pose currently shown for one physical entity. No entity reference is
     * retained, so the weak body cache cannot retain an unloaded player.
     */
    private static final class Presentation {
        private Frame frame;
        private Quat rotation;
        private Vec position;
        private PoseBlend correction;
        private double sampledAt = Double.NaN;

        private Presentation(Frame frame, Quat rotation, Vec position) {
            this.frame = frame; this.rotation = rotation; this.position = position;
        }

        private void sample(Frame targetFrame, Quat targetRotation, Vec targetPosition, double time) {
            // A teleport or a player returning to view has no continuous displayed path to preserve.
            if (Double.isNaN(sampledAt) || time < sampledAt || time-sampledAt > 40 || targetPosition.minus(position).length() > 8) {
                frame = targetFrame; correction = null;
            } else if (!targetFrame.equals(frame)) {
                correction = PoseBlend.between(rotation, position, targetRotation, targetPosition, time);
                frame = targetFrame;
            }
            sampledAt = time;
            if (correction != null && correction.done(time)) correction = null;
            rotation = correction == null ? targetRotation : correction.rotation(targetRotation, time);
            position = correction == null ? targetPosition : correction.position(targetPosition, time);
        }
    }

    /** effects: clears display history when changing worlds; primes wearers before their first frame change. */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (scene != mc.level) {
            BODIES.clear(); cameraEntity = null; cameraPose = null; cameraEye = null; scene = mc.level;
        }
        if (mc.level == null) return;
        BODIES.entrySet().removeIf(entry -> !WallWalk.wears((Player)entry.getKey()) && !WallWalk.bent(entry.getKey())
                && ((entry.getValue().frame.equals(Frame.WORLD) && (entry.getValue().correction == null
                    || entry.getValue().correction.done(entry.getKey().tickCount)))
                    || entry.getKey().tickCount-entry.getValue().sampledAt > 40));
        for (Player p : mc.level.players()) {
            if (WallWalk.wears(p) && !BODIES.containsKey(p)) body(p, 1);
        }
    }

    private static boolean stanceFits(Entity e, Frame frame) {
        // A tracked gravity byte can change while a stationary entity's cached
        // bounding box still has its previous axes. Validate the current stance.
        return e.level().noCollision(e, WallWalk.aabb(frame.box(WallWalk.vec(e.position()), e.getBbWidth(), e.getBbHeight())));
    }

    private static Vec feet(Entity e, float partial) {
        return new Vec(Mth.lerp(partial,e.xo,e.getX()), Mth.lerp(partial,e.yo,e.getY()), Mth.lerp(partial,e.zo,e.getZ()));
    }

    private static Quat bodyYaw(Entity e, float partial) {
        float yaw = e instanceof LivingEntity l ? Mth.rotLerp(partial,l.yBodyRotO,l.yBodyRot) : e.getViewYRot(partial);
        return CameraAngles.compose(new CameraAngles.Angles(yaw,0,0));
    }

    private static Presentation body(Entity e, float partial) {
        Frame f = WallWalk.frameOf(e);
        Quat rotation = f.rotation().times(bodyYaw(e,partial));
        Vec position = feet(e,partial);
        Presentation state = BODIES.get(e);
        if (state == null) {
            state = new Presentation(f,rotation,position);
            BODIES.put(e,state);
        }
        // Tracked gravity may arrive before the matching stance coordinates. Keep
        // the last valid display pose until the new physical box is outside blocks.
        if (!f.equals(state.frame) && !stanceFits(e, WallWalk.frameOf(e))) return state;
        state.sample(f,rotation,position,e.tickCount+partial);
        return state;
    }

    /** effects: returns whether a player requires the presentation adapter, including a return to world gravity. */
    public static boolean presenting(Entity e) {
        return e instanceof Player p && (WallWalk.wears(p) || WallWalk.bent(e) || BODIES.containsKey(e));
    }

    /** effects: returns the displayed frame rotation, compensating for the body's rebased local yaw. */
    public static Quat frameRotation(Entity e, float partial) {
        return body(e,partial).rotation.times(bodyYaw(e,partial).conjugate());
    }

    /** effects: returns the displayed body's translation relative to its physical interpolated feet. */
    public static Vec bodyOffset(Entity e, float partial) {
        return body(e,partial).position.minus(feet(e,partial));
    }

    /** effects: returns whether this camera still carries a frame-change correction. */
    public static boolean cameraPresenting(Entity e) {
        return presenting(e) || (e == cameraEntity && cameraPose != null);
    }

    /** effects: returns the collision-limited eye position computed for this camera setup. */
    public static Vec cameraEye(Entity e, Vec physicalEye) {
        return e == cameraEntity && cameraEye != null ? cameraEye : physicalEye;
    }

    /** effects: preserves the last displayed camera pose at a frame change, then eases its correction while look/movement remain live. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity e = event.getCamera().getEntity();
        if (!(e instanceof Player p)) return;
        if (!WallWalk.wears(p) && !WallWalk.bent(e) && (cameraPose == null || (cameraPose.frame.equals(Frame.WORLD) && cameraPose.correction == null))) {
            cameraEntity = null; cameraPose = null; cameraEye = null;
            return;
        }
        Frame frame = WallWalk.frameOf(e);
        float partial = (float) event.getPartialTick();
        var access = (CameraAccessor) event.getCamera();
        Vec eye = frame.eye(feet(e,partial), Mth.lerp(partial,access.aberrantmobs$eyeHeightOld(),access.aberrantmobs$eyeHeight()));
        Quat target = frame.rotation().times(CameraAngles.compose(new CameraAngles.Angles(event.getYaw(),event.getPitch(),event.getRoll())));
        if (cameraEntity != e || cameraPose == null) {
            cameraEntity = e;
            cameraPose = new Presentation(frame,target,eye);
        }
        if (!frame.equals(cameraPose.frame) && !stanceFits(e, WallWalk.frameOf(e))) {
            cameraEye = cameraPose.position;
            CameraAngles.Angles held = CameraAngles.decompose(cameraPose.rotation);
            event.setYaw((float)held.yaw()); event.setPitch((float)held.pitch()); event.setRoll((float)held.roll());
            return;
        }
        Vec previousEye = cameraPose.position;
        cameraPose.sample(frame,target,eye,e.tickCount+partial);
        cameraEye = cameraPose.position;
        if (cameraPose.correction != null && cameraEye.minus(previousEye).length() > 1e-6) {
            var hit = e.level().clip(new ClipContext(WallWalk.vec3(previousEye),WallWalk.vec3(cameraEye),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,e));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec direction = cameraEye.minus(previousEye).normalized();
                double distance = Math.max(0,WallWalk.vec(hit.getLocation()).minus(previousEye).length()-0.1);
                cameraEye = previousEye.plus(direction.times(distance));
                cameraPose.position = cameraEye;
            }
        }
        if (frame.gravity().isDown() && cameraPose.correction == null) return;
        CameraAngles.Angles a = CameraAngles.decompose(cameraPose.rotation);
        event.setYaw((float)a.yaw()); event.setPitch((float)a.pitch()); event.setRoll((float)a.roll());
    }
}
