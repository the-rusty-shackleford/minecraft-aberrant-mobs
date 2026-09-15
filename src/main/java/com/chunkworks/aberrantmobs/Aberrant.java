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
package com.chunkworks.aberrantmobs;

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.Trail;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.chunkworks.aberrantmobs.domain.Vec;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A creature: the one entity type of the protocol, sized, skinned and, in
 * later phases, minded by its {@link CreatureProfile}. The profile id is
 * synced data, so a client that first sees it knows what it is; the
 * profile itself is looked up in the registry each time, never cached, so
 * a reload is honoured.
 *
 * <p>The body follows the head: each side keeps its own {@link Trail} of
 * where the head's axis has been (nothing is synced for it: both sides
 * have the head's positions already) and the writhe's {@link
 * Undulation.Wave}, advanced once a tick by the ground speed. The trail
 * is seeded straight behind the head the first time it is asked for, so
 * a creature that just appeared has a whole body.
 */
public class Aberrant extends Monster {
    private static final EntityDataAccessor<String> DATA_PROFILE = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);

    /** How far beyond its box a creature is still drawn: a body eleven blocks long trails well past its head. */
    private static final double CULL_REACH = 12.0;
    /** Trail samples kept: a body of eleven blocks at the slowest crawl, and then some. */
    private static final int TRAIL_CAPACITY = 256;

    @Nullable
    private Trail trail;
    private Undulation.Wave wave;
    /** Blocks travelled along the ground, for the legs' phase, and last tick's ground speed. */
    private double distance;
    private double speed;
    /** How high the head's axis runs over the feet, blocks, as the body says; NaN until a side that knows the rig tells it. */
    private double axisHeight = Double.NaN;

    public Aberrant(EntityType<? extends Aberrant> type, Level level) {
        super(type, level);
    }

    /**
     * effects: returns a new creature of profile {@code id} at {@code (x, y, z)} facing {@code yaw},
     * with the profile's health, or null when no such profile is loaded
     */
    @Nullable
    public static Aberrant create(Level level, ResourceLocation id, double x, double y, double z, float yaw) {
        if (AberrantMobs.profile(level.registryAccess(), id).isEmpty()) {
            return null;
        }
        Aberrant a = ModContent.ABERRANT.get().create(level);
        if (a == null) {
            return null;
        }
        a.setProfileId(id);
        a.moveTo(x, y, z, yaw, 0.0f);
        a.yHeadRot = yaw;
        a.yBodyRot = yaw;
        return a;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFILE, "");
    }

    /** effects: returns this creature's profile, or null before one is set or when the packs lost it */
    @Nullable
    public CreatureProfile profile() {
        String id = entityData.get(DATA_PROFILE);
        if (id.isEmpty()) {
            return null;
        }
        return AberrantMobs.profile(level().registryAccess(), ResourceLocation.parse(id)).map(Holder.Reference::value).orElse(null);
    }

    /** effects: returns the profile's id, or null before one is set */
    @Nullable
    public ResourceLocation profileId() {
        String id = entityData.get(DATA_PROFILE);
        return id.isEmpty() ? null : ResourceLocation.parse(id);
    }

    /** effects: makes this a creature of profile {@code id}: its size and health from the profile, its name too */
    public void setProfileId(ResourceLocation id) {
        entityData.set(DATA_PROFILE, id.toString());
        CreatureProfile p = profile();
        if (p != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.stats().health());
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(p.stats().speed());
            setHealth((float) p.stats().health());
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PROFILE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        CreatureProfile p = profile();
        if (p == null) {
            return super.getDefaultDimensions(pose);
        }
        return EntityDimensions.scalable((float) p.body().width(), (float) p.body().height()).withEyeHeight((float) p.body().eyeHeight());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(CULL_REACH);
    }

    @Override
    public Component getName() {
        if (hasCustomName()) {
            return super.getName();
        }
        ResourceLocation id = profileId();
        return id == null ? super.getName() : Component.translatable("creature." + id.getNamespace() + "." + id.getPath());
    }

    // --- the body -------------------------------------------------------

    /** effects: returns the unit direction the head faces along the ground, from the body's yaw */
    public Vec facing() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** effects: returns the up of the surface the head clings to: the floor's, in this phase */
    public Vec up() {
        return Vec.Y;
    }

    /** effects: returns where the head's axis is now: over the feet by {@code axisHeight} along the up */
    private Vec axis(double axisHeight) {
        return new Vec(getX(), getY(), getZ()).plus(up().times(axisHeight));
    }

    /**
     * effects: returns this side's trail of the head's axis, seeded straight
     * behind the head over {@code bodyLength} blocks the first time; from then
     * on {@code axisHeight} is remembered and the trail grows every tick
     */
    public Trail trail(double axisHeight, double bodyLength) {
        if (trail == null) {
            this.axisHeight = axisHeight;
            trail = Trail.seeded(axis(axisHeight), facing(), up(), Math.max(1.0, bodyLength), TRAIL_CAPACITY);
        }
        return trail;
    }

    /** effects: returns the writhe's state on this side, at rest until the body has ticked */
    public Undulation.Wave wave(Undulation undulation) {
        if (wave == null) {
            wave = undulation.rest();
        }
        return wave;
    }

    /** effects: returns how far the body has travelled along the ground, blocks */
    public double distance() {
        return distance;
    }

    /** effects: returns last tick's ground speed, blocks a tick */
    public double speed() {
        return speed;
    }

    /** A walk the server was told to make: a ground velocity, blocks a tick, for so many ticks. */
    @Nullable
    private Vec walk;
    private int walkTicks;

    /**
     * effects: from now on, for {@code ticks} ticks, the server walks this
     * creature at {@code velocity} (blocks a tick, along the ground) facing
     * that way, gravity kept -- the booth's and the tests' way to move it
     * until the crawl arrives
     */
    public void setScriptedWalk(Vec velocity, int ticks) {
        walk = velocity;
        walkTicks = ticks;
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!level().isClientSide() && walk != null && walkTicks > 0) {
            walkTicks--;
            double vy = onGround() && getDeltaMovement().y <= 0 ? -0.04 : getDeltaMovement().y - 0.08;
            setDeltaMovement(walk.x(), vy, walk.z());
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (onGround() && getDeltaMovement().y < 0) {
                setDeltaMovement(getDeltaMovement().x, 0.0, getDeltaMovement().z);
            }
            if (walk.x() != 0 || walk.z() != 0) {
                float yaw = (float) Math.toDegrees(Math.atan2(-walk.x(), walk.z()));
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            return;
        }
        super.travel(input);
    }

    @Override
    public void tick() {
        super.tick();
        double dx = getX() - xo, dz = getZ() - zo;
        speed = Math.sqrt(dx * dx + dz * dz);
        distance += speed;
        CreatureProfile p = profile();
        if (p != null) {
            wave = p.rig().undulation().advance(wave(p.rig().undulation()), speed);
        }
        if (trail != null && !Double.isNaN(axisHeight)) {
            trail.push(axis(axisHeight), up());
        }
    }

    // --- saving ---------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Profile", entityData.get(DATA_PROFILE));
        tag.putDouble("Distance", distance);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Profile")) {
            entityData.set(DATA_PROFILE, tag.getString("Profile"));
            refreshDimensions();
        }
        distance = tag.getDouble("Distance");
    }

    /** effects: returns the yaw of the body between ticks, for the frame */
    public float bodyYaw(float partialTick) {
        return Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
    }
}
