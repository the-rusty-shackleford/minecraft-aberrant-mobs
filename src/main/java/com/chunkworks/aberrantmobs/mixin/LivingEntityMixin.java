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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A wearer's travel is reckoned in its own frame -- gravity along its
 * down, a jump along its up, friction on its floor -- and its velocity
 * stays local; only the move into the world is turned to world axes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(method = {"travel", "handleRelativeFrictionAndCalculateMovement"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void aberrantmobs$moveInFrame(LivingEntity self, MoverType type, Vec3 local) {
        if (WallWalk.bent(self)) {
            self.move(type, WallWalk.vec3(WallWalk.frameOf(self).toWorld(WallWalk.vec(local))));
        } else {
            self.move(type, local);
        }
    }
}
