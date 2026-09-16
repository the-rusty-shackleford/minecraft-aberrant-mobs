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

import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Horizontal-only block states and rotation segments use the projected world look. */
@Mixin(UseOnContext.class)
public abstract class UseOnContextMixin {
    @Shadow public abstract Player getPlayer();

    @Inject(method = "getHorizontalDirection", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$horizontal(CallbackInfoReturnable<Direction> ci) {
        Player player = getPlayer();
        if (WallWalk.bent(player)) {
            ci.setReturnValue(Direction.fromYRot(WallWalk.frameOf(player).placementYaw(player.getYRot(), player.getXRot())));
        }
    }

    @Inject(method = "getRotation", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$rotation(CallbackInfoReturnable<Float> ci) {
        Player player = getPlayer();
        if (WallWalk.bent(player)) {
            ci.setReturnValue((float) WallWalk.frameOf(player).placementYaw(player.getYRot(), player.getXRot()));
        }
    }
}
