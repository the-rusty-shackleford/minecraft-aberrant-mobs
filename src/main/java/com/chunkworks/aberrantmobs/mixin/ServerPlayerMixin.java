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

import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server's fall check on a reported move reads the fall along the wearer's own down. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "doCheckFallDamage", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$fallInFrame(double x, double y, double z, boolean onGround, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!WallWalk.bent(self)) {
            return;
        }
        ci.cancel();
        if (self.touchingUnloadedChunk()) {
            return;
        }
        Frame f = WallWalk.frameOf(self);
        Vec3 world = new Vec3(x, y, z);
        self.setOnGroundWithMovement(onGround, world);
        BlockPos pos = self.getOnPosLegacy();
        BlockState state = self.level().getBlockState(pos);
        ((EntityAccessor) self).aberrantmobs$checkFallDamage(f.toLocal(WallWalk.vec(world)).y(), onGround, state, pos);
    }
}
