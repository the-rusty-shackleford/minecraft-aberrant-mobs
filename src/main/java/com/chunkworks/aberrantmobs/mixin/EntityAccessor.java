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

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The entity's own private parts the wall-walk's move needs to call. */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Invoker("checkFallDamage")
    void aberrantmobs$checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos);

    @Invoker("tryCheckInsideBlocks")
    void aberrantmobs$tryCheckInsideBlocks();

    @Invoker("getBlockSpeedFactor")
    float aberrantmobs$blockSpeedFactor();

    @Invoker("updateInWaterStateAndDoFluidPushing")
    boolean aberrantmobs$updateInWaterState();

    @Accessor("mainSupportingBlockPos")
    void aberrantmobs$setMainSupportingBlockPos(Optional<BlockPos> pos);

    @Accessor("mainSupportingBlockPos")
    Optional<BlockPos> aberrantmobs$mainSupportingBlockPos();

    @Accessor("onGroundNoBlocks")
    void aberrantmobs$setOnGroundNoBlocks(boolean value);
}
