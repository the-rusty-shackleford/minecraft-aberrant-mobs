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

import com.chunkworks.aberrantmobs.client.WallWalkClient;
import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A wearer's model stands along its frame: turned about its feet before the game's own yaw and pose. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void aberrantmobs$standInFrame(LivingEntity entity, PoseStack stack, float bob, float bodyYaw, float partialTick, float scale, CallbackInfo ci) {
        if (!WallWalk.bent(entity) && WallWalkClient.blendOf(entity) == null) {
            return;
        }
        Quat q = WallWalkClient.frameRotation(entity, partialTick);
        stack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
    }
}
