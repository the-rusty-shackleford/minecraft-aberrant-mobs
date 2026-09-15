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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One segment of a body as the world meets it: a box that stands where the
 * chain puts its segment each tick, that a sword or an arrow can hit, and
 * that hands the blow to the body with its segment's number so the
 * carapace can judge it. Unassigned (a body shorter than the parts a
 * creature is born with), it is a speck nothing can hit. The parts are
 * made with the creature, as the game numbers them from its id at birth.
 */
public final class AberrantPart extends PartEntity<Aberrant> {
    private final int index;
    private float width = 0.01f;
    private float height = 0.01f;

    AberrantPart(Aberrant parent, int index) {
        super(parent);
        this.index = index;
        refreshDimensions();
    }

    /** effects: returns which segment of the chain this is, the head 0 */
    public int index() {
        return index;
    }

    /** effects: gives this part its box; a zero box makes it a speck */
    void size(double width, double height) {
        this.width = (float) width;
        this.height = (float) height;
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(width, height);
    }

    @Override
    public boolean isPickable() {
        return width > 0.05f;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return isInvulnerableTo(source) ? false : getParent().hurtSegment(index, source, amount);
    }

    @Override
    public boolean is(net.minecraft.world.entity.Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException("a part is never sent on its own");
    }
}
