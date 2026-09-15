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
package com.chunkworks.aberrantmobs.mixin;

import com.chunkworks.aberrantmobs.domain.frame.Blend;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every player carries a frame: one synced byte, the world's by default. */
@Mixin(Player.class)
public abstract class PlayerMixin implements FrameCarrier {
    @Unique
    private static final EntityDataAccessor<Byte> ABERRANTMOBS_FRAME = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    @Unique
    @Nullable
    private Vec3 aberrantmobs$lastWall;
    @Unique
    @Nullable
    private Vec3 aberrantmobs$lastTried;
    @Unique
    private boolean aberrantmobs$wasOnGround;
    @Unique
    @Nullable
    private Blend aberrantmobs$blend;
    @Unique
    private int aberrantmobs$seenCode;
    @Unique
    @Nullable
    private Vec3 aberrantmobs$movePre;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void aberrantmobs$defineFrame(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(ABERRANTMOBS_FRAME, Frame.WORLD.code());
    }

    @Override
    public Frame aberrantmobs$frame() {
        return Frame.decode(((Entity) (Object) this).getEntityData().get(ABERRANTMOBS_FRAME));
    }

    @Override
    public void aberrantmobs$setFrame(Frame frame) {
        Entity self = (Entity) (Object) this;
        if (self.getEntityData().get(ABERRANTMOBS_FRAME) != frame.code()) {
            self.getEntityData().set(ABERRANTMOBS_FRAME, frame.code());
            self.refreshDimensions();
        }
    }

    @Override
    @Nullable
    public Vec3 aberrantmobs$lastWall() {
        return aberrantmobs$lastWall;
    }

    @Override
    @Nullable
    public Vec3 aberrantmobs$lastTried() {
        return aberrantmobs$lastTried;
    }

    @Override
    public void aberrantmobs$setLastWall(@Nullable Vec3 normal, @Nullable Vec3 tried) {
        aberrantmobs$lastWall = normal;
        aberrantmobs$lastTried = tried;
    }

    @Override
    public boolean aberrantmobs$wasOnGround() {
        return aberrantmobs$wasOnGround;
    }

    @Override
    public void aberrantmobs$setWasOnGround(boolean onGround) {
        aberrantmobs$wasOnGround = onGround;
    }

    @Override
    @Nullable
    public Blend aberrantmobs$blend() {
        return aberrantmobs$blend;
    }

    @Override
    public void aberrantmobs$setBlend(@Nullable Blend blend) {
        aberrantmobs$blend = blend;
    }

    @Override
    public int aberrantmobs$seenCode() {
        return aberrantmobs$seenCode;
    }

    @Override
    public void aberrantmobs$setSeenCode(int code) {
        aberrantmobs$seenCode = code;
    }

    @Override
    @Nullable
    public Vec3 aberrantmobs$movePre() {
        return aberrantmobs$movePre;
    }

    @Override
    public void aberrantmobs$setMovePre(@Nullable Vec3 pos) {
        aberrantmobs$movePre = pos;
    }
}
