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
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The name stays camera-facing, anchored beyond the head along the body's blended up. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void aberrantmobs$nameAnchor(PoseStack pose, double x, double y, double z,
            Entity entity, Component name, PoseStack unusedPose, MultiBufferSource buffer, int light, float partialTick) {
        if (!WallWalkClient.presenting(entity)) {
            pose.translate(x, y, z);
            return;
        }
        Vec at = WallWalkClient.frameRotation(entity, partialTick).rotate(new Vec(x, y, z));
        at = at.plus(WallWalkClient.bodyOffset(entity, partialTick));
        pose.translate(at.x(), at.y(), at.z());
    }
}
