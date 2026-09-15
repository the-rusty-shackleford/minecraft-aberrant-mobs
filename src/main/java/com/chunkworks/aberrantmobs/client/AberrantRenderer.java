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
package com.chunkworks.aberrantmobs.client;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a creature: its skin under its pose, turned to its yaw. The model
 * faces +Z at yaw 0 (Blockbench's south, the game's), so the body is turned
 * by the negative of the entity's yaw as the game's own models are. One
 * draw call a creature. Later phases hand a posed body in; this one draws
 * the rest pose.
 */
public final class AberrantRenderer extends EntityRenderer<Aberrant> {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");

    public AberrantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(Aberrant creature) {
        CreatureProfile p = creature.profile();
        return p == null ? MISSING : Skin.of(creature.profileId(), p).texture;
    }

    @Override
    public void render(Aberrant creature, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        CreatureProfile p = creature.profile();
        if (p == null) {
            return;
        }
        Skin skin = Skin.of(creature.profileId(), p);
        float yaw = Mth.rotLerp(partialTick, creature.yBodyRotO, creature.yBodyRot);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        int overlay = creature.hurtTime > 0 ? OverlayTexture.pack(0, true) : OverlayTexture.NO_OVERLAY;
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture));
        RigDrawer.draw(skin, Pose.REST, poseStack, out, packedLight, overlay, null, null);
        poseStack.popPose();
        super.render(creature, entityYaw, partialTick, poseStack, buffers, packedLight);
    }
}
