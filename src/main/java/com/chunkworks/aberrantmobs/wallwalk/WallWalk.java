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
import com.chunkworks.aberrantmobs.domain.frame.ClingIntent;
import com.chunkworks.aberrantmobs.domain.frame.ClingGesture;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.domain.frame.Transition;
import java.util.Optional;
import java.util.function.Predicate;
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
 * Deliberate six-axis wall walking. Manual Jump plus forward and a view toward
 * a real wall permits entry; connected supported corners then follow movement.
 * A fresh Jump press releases. Ordinary bumps and world-floor ledges do not attach.
 * Input precedes normal movement packets; the server independently validates
 * collisions and stance changes before vanilla compares the reported position.
 * The resulting frame remains one server-synced byte.
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

    /**
     * requires: impulse is a finite world vector
     * effects: adds that impulse to the entity's stored velocity, converting to the
     * wearer's local axes once; ordinary entities retain the game's world velocity
     */
    public static void addWorldImpulse(Entity entity, Vec3 impulse) {
        Vec3 local = bent(entity) ? vec3(frameOf(entity).toLocal(vec(impulse))) : impulse;
        entity.setDeltaMovement(entity.getDeltaMovement().add(local));
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

    private static boolean eligible(Player p) {
        return wears(p) && !p.isSpectator() && !p.getAbilities().flying && !p.isPassenger()
                && !p.isInWaterOrBubble() && !p.isInLava() && !p.isFallFlying() && !p.isSleeping();
    }

    /** effects: records one ordered manual input sample, without trusting any client position or gravity. */
    public static void input(Player p, ClingIntent intent) {
        if (!(p instanceof FrameCarrier c)) return;
        if (!wears(p) && !bent(p)) return;
        c.aberrantmobs$setIntent(intent, p.tickCount);
        c.aberrantmobs$setGesture(c.aberrantmobs$gesture().next(intent.jump(), bent(p)));
    }

    /** effects: applies a requested/environmental release before movement, only where an upright box fits. */
    public static void prepare(Player p) {
        if (!(p instanceof FrameCarrier c) || !bent(p)) return;
        if (!eligible(p) || c.aberrantmobs$gesture().detach()) {
            boolean jump = eligible(p) && c.aberrantmobs$gesture().detach();
            letGo(p, c, frameOf(p), vec(p.position()), p.getBbWidth(), p.getBbHeight(), jump);
        }
    }

    private static boolean mayEnter(Player p, FrameCarrier c, Vec normal) {
        return p.tickCount - c.aberrantmobs$inputTick() <= 5 && c.aberrantmobs$gesture().ready()
                && c.aberrantmobs$intent().faces(frameOf(p), normal);
    }

    /** effects: resolves this move's contact once; unsupported attached players return to world gravity. */
    public static void rule(Player p) {
        if (!(p instanceof FrameCarrier c)) return;
        prepare(p);
        Frame f = c.aberrantmobs$frame();
        Vec3 wall = c.aberrantmobs$lastWall(), tried = c.aberrantmobs$lastTried();
        c.aberrantmobs$setLastWall(null, null);
        if (!eligible(p)) {
            c.aberrantmobs$setWasOnGround(p.onGround());
            return;
        }
        double w = p.getBbWidth(), h = p.getBbHeight();
        Vec feet = vec(p.position());
        if (wall != null && tried != null && (bent(p) || mayEnter(p, c, vec(wall)))) {
            var stance = Transition.intoWall(f, feet, w, h, vec(tried), vec(wall), fits(p));
            if (stance.isPresent() && supported(p, stance.get())) {
                take(p, c, stance.get());
                c.aberrantmobs$setWasOnGround(true);
                return;
            }
        }
        // Floor ledges are ordinary walking. Only an already attached wearer wraps.
        if (bent(p) && tried != null && !supported(p, new Transition.Stance(f, feet, Double.NaN))) {
            var stance = !p.isShiftKeyDown() ? edge(p, f, feet, vec(tried)) : Optional.<Transition.Stance>empty();
            if (stance.isPresent()) {
                take(p, c, stance.get());
                c.aberrantmobs$setWasOnGround(true);
                return;
            }
            letGo(p, c, f, feet, w, h, false);
        }
        c.aberrantmobs$setWasOnGround(p.onGround());
    }

    private static boolean supported(Player p, Transition.Stance stance) {
        AABB foot = WallWalkMove.underfoot(stance.frame(), aabb(stance.frame().box(stance.feet(), p.getBbWidth(), p.getBbHeight())));
        return p.level().getBlockCollisions(p, foot).iterator().hasNext();
    }

    // Trace the actual outward face beneath the old ledge. A fit in empty space
    // alone is not a surface, and a guessed offset must never become grounded.
    private static Optional<Transition.Stance> edge(Player p, Frame f, Vec feet, Vec move) {
        Vec tangent = move.minus(f.up().times(move.dot(f.up())));
        if (tangent.length() < Transition.INTO_WALL) return Optional.empty();
        Vec normal = Gravity.nearest(tangent).dir;
        double inset = p.getBbWidth() / 2.0 + 0.01;
        Vec start = feet.minus(f.up().times(inset)).plus(normal.times(0.05));
        Vec end = start.minus(normal.times(p.getBbWidth() + 0.5));
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(vec3(start), vec3(end),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !vec(Vec3.atLowerCornerOf(hit.getDirection().getNormal())).near(normal, 1e-6)) return Optional.empty();
        return Transition.overEdge(f, feet, p.getBbWidth(), p.getBbHeight(), move, vec(hit.getLocation()), fits(p))
                .filter(stance -> supported(p, stance));
    }

    /**
     * requires: reported is the finite world displacement in a normal movement packet.
     * effects: returns the displacement to replay before an eligible wearer's supported
     * stance change. A candidate is used only if real collision plus the existing
     * transition rule reproduces all three reported coordinates; otherwise returns
     * reported unchanged. Does not move the player or run collision side effects.
     */
    public static Vec3 replayMove(Player p, Vec3 reported) {
        if (!eligible(p)) return reported;
        Frame before = frameOf(p);
        double halfHeight = p.getBbHeight() / 2.0;
        // An outer corner reports the new feet on the exposed face, not the
        // short movement past the edge. Reconstruct only a collision-checked
        // candidate whose actual face trace reproduces the complete endpoint.
        if (bent(p) && vec(reported).dot(before.up()) < -p.getBbWidth() / 2.0
                && supported(p, new Transition.Stance(before, vec(p.position()), Double.NaN))) {
            for (Gravity gravity : Gravity.values()) {
                if (gravity == before.gravity() || gravity == before.gravity().opposite()) continue;
                Vec normal = Frame.of(gravity).up();
                Vec attempt = vec(reported).plus(before.up().times(p.getBbWidth() / 2.0 + 0.01))
                        .plus(normal.times(p.getBbWidth() / 2.0 + 1e-4));
                Vec3 delta = vec3(attempt);
                Vec after = vec(p.position()).plus(vec(WallWalkMove.collide(p, before, delta)));
                if (supported(p, new Transition.Stance(before, after, Double.NaN))) continue;
                var stance = edge(p, before, after, attempt);
                if (stance.isPresent() && stance.get().feet().near(vec(p.position().add(reported)), 1e-5)) return delta;
            }
        }
        // Into-wall stances shift the feet half a body along the old up.
        // Ordinary grounded moves do not pay for four speculative collision checks.
        if (vec(reported).dot(before.up()) < halfHeight - 0.05) return reported;
        Vec feet = vec(p.position());
        Vec wanted = feet.plus(vec(reported));
        for (var gravity : Gravity.values()) {
            if (gravity == before.gravity() || gravity == before.gravity().opposite()) continue;
            Frame next = Frame.of(gravity);
            if (!bent(p) && !mayEnter(p, (FrameCarrier) p, next.up())) continue;
            Vec shift = before.up().times(halfHeight).minus(next.up().times(p.getBbWidth() / 2.0));
            Vec attempt = vec(reported).minus(shift);
            // The packet contains the already-clipped travel. Probe a bounded sliver
            // into the candidate wall so collision can confirm the face it stopped on.
            attempt = attempt.plus(gravity.dir.times(Transition.INTO_WALL + 1e-4));
            Vec3 delta = vec3(attempt);
            Vec3 collided = WallWalkMove.collide(p, before, delta);
            Vec stopped = attempt.minus(vec(collided));
            if (stopped.dot(gravity.dir) < 1e-5) continue;
            var stance = Transition.intoWall(before, feet.plus(vec(collided)), p.getBbWidth(), p.getBbHeight(),
                    attempt, next.up(), fits(p));
            if (stance.isPresent() && supported(p, stance.get()) && stance.get().feet().minus(wanted).length() < 1e-5) return delta;
        }
        return reported;
    }

    private static Predicate<Frame.Box> fits(Player p) {
        return box -> p.level().noCollision(p, aabb(box));
    }

    /**
     * effects: puts {@code p} in the stance: its frame, its feet, its yaw so
     * the way it was going carries on; its tangent velocity kept and outward momentum cancelled, its fall
     * forgotten. Never a teleport: on the server the rule runs inside the
     * re-run of the client's own move, whose reported position is this
     * stance's already, and a teleport would drop the client's next moves
     * until it answered
     */
    private static void take(Player p, FrameCarrier c, Transition.Stance s) {
        c.aberrantmobs$setFrame(s.frame());
        if (s.frame().gravity().isDown()) c.aberrantmobs$setGesture(c.aberrantmobs$gesture().released());
        float yaw = Double.isNaN(s.yaw()) ? p.getYRot() : (float) s.yaw();
        p.setPos(s.feet().x(), s.feet().y(), s.feet().z());
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.fallDistance = 0.0f;
        p.setOnGround(true);
        p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1));
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

    private static void letGo(Player p, FrameCarrier c, Frame f, Vec feet, double w, double h, boolean jump) {
        Optional<Transition.Stance> stance = Transition.release(f, feet, w, h, fits(p));
        if (stance.isEmpty()) return; // keep the valid box until there is room to stand upright
        Vec3 look = p.getLookAngle();
        Vec world = f.toWorld(vec(p.getDeltaMovement()));
        if (jump) world = world.plus(f.up().times(0.42));
        c.aberrantmobs$setFrame(Frame.WORLD);
        p.setPos(vec3(stance.get().feet()));
        p.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
        p.setYHeadRot(p.getYRot());
        p.yBodyRot = p.getYRot();
        p.setDeltaMovement(vec3(world));
        p.setOnGround(false);
        p.fallDistance = 0.0f;
        c.aberrantmobs$setLastWall(null, null);
        c.aberrantmobs$setGesture(c.aberrantmobs$gesture().released());
    }
}
