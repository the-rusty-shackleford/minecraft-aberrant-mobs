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

import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Converts the game's local-angle direction queries at their world-facing boundary. */
@Mixin(Direction.class)
public abstract class DirectionMixin {
    @Inject(method = "orderedByNearest", at = @At("RETURN"), cancellable = true)
    private static void aberrantmobs$worldDirections(Entity entity, CallbackInfoReturnable<Direction[]> ci) {
        if (!WallWalk.bent(entity)) {
            return;
        }
        Frame frame = WallWalk.frameOf(entity);
        Direction[] directions = ci.getReturnValue();
        for (int i = 0; i < directions.length; i++) {
            Direction d = directions[i];
            Vec world = frame.toWorld(new Vec(d.getStepX(), d.getStepY(), d.getStepZ()));
            directions[i] = Direction.getNearest(world.x(), world.y(), world.z());
        }
    }

    @Inject(method = "getFacingAxis", at = @At("HEAD"), cancellable = true)
    private static void aberrantmobs$worldAxis(Entity entity, Direction.Axis axis, CallbackInfoReturnable<Direction> ci) {
        if (WallWalk.bent(entity)) {
            Vec3 look = entity.getViewVector(1.0f);
            ci.setReturnValue(Direction.fromAxisAndDirection(axis, axis.choose(look.x, look.y, look.z) > 0
                    ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
        }
    }
}
