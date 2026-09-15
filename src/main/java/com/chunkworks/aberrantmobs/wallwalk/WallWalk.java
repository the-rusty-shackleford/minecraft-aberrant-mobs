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
package com.chunkworks.aberrantmobs.wallwalk;

import com.chunkworks.aberrantmobs.AberrantMobsMod;
import com.chunkworks.aberrantmobs.ModContent;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.domain.frame.Transition;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The chitin's gift: a player wearing the full set walks on walls and
 * ceilings as if they were ground. The server decides every tick, after
 * the player moved, from its own copy of the pure {@link Transition}
 * rules; the client runs the same rule on its own player a tick ahead, so
 * the two agree within the game's tolerance and no packet is added: the
 * frame rides the player's synced data as one byte. A wearer's motion,
 * look and box are reckoned in the frame's local axes by the mixins on
 * the entity; here only which frame it is changes.
 *
 * <p>A wearer takes a wall it walks into hard enough, wraps over an edge
 * it walks off, and lets go -- to the world's own down, the least way out
 * that fits -- when the set comes off, in water, flying, riding, gliding,
 * or as a spectator.
 */
@EventBusSubscriber(modid = AberrantMobsMod.MOD_ID)
public final class WallWalk {
    private WallWalk() {}

    /** effects: returns whether {@code e} wears the full chitin set */
    public static boolean wears(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.CHITIN_HELMET.get())
                && e.getItemBySlot(EquipmentSlot.CHEST).is(ModContent.CHITIN_CHESTPLATE.get())
                && e.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.CHITIN_LEGGINGS.get())
                && e.getItemBySlot(EquipmentSlot.FEET).is(ModContent.CHITIN_BOOTS.get());
    }

    /** effects: returns the frame {@code e} is in: the world's for anything but a player */
    public static Frame frameOf(Entity e) {
        return e instanceof FrameCarrier c ? c.aberrantmobs$frame() : Frame.WORLD;
    }

    /** effects: returns whether the frame rules bend {@code e}'s motion: a player in a frame other than the world's */
    public static boolean bent(Entity e) {
        return e instanceof FrameCarrier c && !c.aberrantmobs$frame().gravity().isDown();
    }

    public static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    public static Vec3 vec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    public static AABB aabb(Frame.Box b) {
        return new AABB(b.lo().x(), b.lo().y(), b.lo().z(), b.hi().x(), b.hi().y(), b.hi().z());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() && !p.isLocalPlayer()) {
            return;   // another client's player: the server's byte tells us
        }
        rule(p);
    }

    /** effects: one tick of the rule for {@code p}: its frame changed as the class says */
    public static void rule(Player p) {
        if (!(p instanceof FrameCarrier c)) {
            return;
        }
        Frame f = c.aberrantmobs$frame();
        boolean wearing = wears(p) && !p.isSpectator() && !p.getAbilities().flying && !p.isPassenger() && !p.isInWaterOrBubble() && !p.isInLava() && !p.isFallFlying() && !p.isSleeping();
        double w = p.getBbWidth(), h = p.getBbHeight();
        Vec feet = new Vec(p.getX(), p.getY(), p.getZ());
        if (!wearing) {
            if (!f.gravity().isDown()) {
                letGo(p, c, f, feet, w, h);
            }
            c.aberrantmobs$setLastWall(null, null);
            c.aberrantmobs$setWasOnGround(p.onGround());
            return;
        }
        Predicate<Frame.Box> fits = fits(p);
        Vec3 wall = c.aberrantmobs$lastWall();
        Vec3 tried = c.aberrantmobs$lastTried();
        if (wall != null && tried != null) {
            Optional<Transition.Stance> s = Transition.intoWall(f, feet, w, h, vec(tried), vec(wall), fits);
            if (s.isPresent()) {
                take(p, c, s.get());
                c.aberrantmobs$setLastWall(null, null);
                c.aberrantmobs$setWasOnGround(true);
                return;
            }
        }
        if (c.aberrantmobs$wasOnGround() && !p.onGround() && !p.isShiftKeyDown() && tried != null) {
            Vec localMove = f.toLocal(vec(tried));
            if (localMove.y() <= 0.0 && p.getDeltaMovement().y <= 0.0) {
                Optional<Transition.Stance> s = Transition.overEdge(f, feet, w, h, vec(tried), fits);
                if (s.isPresent()) {
                    take(p, c, s.get());
                    c.aberrantmobs$setLastWall(null, null);
                    c.aberrantmobs$setWasOnGround(true);
                    return;
                }
            }
        }
        c.aberrantmobs$setWasOnGround(p.onGround());
    }

    private static Predicate<Frame.Box> fits(Player p) {
        return box -> p.level().noCollision(p, aabb(box));
    }

    /** effects: puts {@code p} in the stance: its frame, its feet, its yaw so the way it was going carries on; its local velocity kept, its fall forgotten */
    private static void take(Player p, FrameCarrier c, Transition.Stance s) {
        c.aberrantmobs$setFrame(s.frame());
        float yaw = Double.isNaN(s.yaw()) ? p.getYRot() : (float) s.yaw();
        if (p instanceof ServerPlayer sp && sp.connection != null) {
            sp.connection.teleport(s.feet().x(), s.feet().y(), s.feet().z(), yaw, p.getXRot());
        } else {
            p.setPos(s.feet().x(), s.feet().y(), s.feet().z());
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.fallDistance = 0.0f;
        p.setOnGround(true);
    }

    /**
     * effects: moves {@code p} one tick along its own heading in its frame at
     * {@code forward} blocks with its gravity, as the server would re-run a
     * reported walk, and applies the rule -- the tests' and the booth's way
     * to walk a player the server owns
     */
    public static void walk(Player p, double forward) {
        Frame f = frameOf(p);
        Vec local = Frame.headingOf(p.getYRot()).times(forward).plus(new Vec(0.0, -0.08, 0.0));
        p.move(net.minecraft.world.entity.MoverType.PLAYER, vec3(f.toWorld(local)));
        ((com.chunkworks.aberrantmobs.mixin.EntityAccessor) p).aberrantmobs$updateInWaterState();   // a player the server owns without a connection is never base-ticked
        rule(p);
    }

    private static void letGo(Player p, FrameCarrier c, Frame f, Vec feet, double w, double h) {
        Optional<Transition.Stance> s = Transition.release(f, feet, w, h, fits(p));
        Vec3 local = p.getDeltaMovement();
        Vec3 world = vec3(f.toWorld(vec(local)));
        c.aberrantmobs$setFrame(Frame.WORLD);
        if (s.isPresent()) {
            p.setPos(s.get().feet().x(), s.get().feet().y(), s.get().feet().z());
        }
        p.setDeltaMovement(world);   // the world's frame: local is world
        p.fallDistance = 0.0f;
    }
}
