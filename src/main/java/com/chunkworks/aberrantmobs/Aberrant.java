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
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
 */
public class Aberrant extends Monster {
    private static final EntityDataAccessor<String> DATA_PROFILE = SynchedEntityData.defineId(Aberrant.class, EntityDataSerializers.STRING);

    /** How far beyond its box a creature is still drawn: a body eleven blocks long trails well past its head. */
    private static final double CULL_REACH = 12.0;

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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Profile", entityData.get(DATA_PROFILE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Profile")) {
            entityData.set(DATA_PROFILE, tag.getString("Profile"));
            refreshDimensions();
        }
    }
}
