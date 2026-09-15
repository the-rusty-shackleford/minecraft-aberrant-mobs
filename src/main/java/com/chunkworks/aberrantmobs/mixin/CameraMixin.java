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
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The camera sits at a wearer's eyes, which are up along its frame, not the world's. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void aberrantmobs$eyeInFrame(Camera camera, double x, double y, double z, BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick) {
        if (!WallWalk.bent(entity)) {
            ((CameraAccessor) camera).aberrantmobs$setPosition(x, y, z);
            return;
        }
        Vec feet = new Vec(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ()));
        Vec eye = WallWalk.frameOf(entity).eye(feet, Mth.lerp(partialTick, eyeHeightOld, eyeHeight));
        ((CameraAccessor) camera).aberrantmobs$setPosition(eye.x(), eye.y(), eye.z());
    }
}
