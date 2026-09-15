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
import com.chunkworks.aberrantmobs.domain.Body;
import com.chunkworks.aberrantmobs.domain.ChainPose;
import com.chunkworks.aberrantmobs.domain.LegGait;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.chunkworks.aberrantmobs.domain.Trail;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.chunkworks.aberrantmobs.domain.Vec;
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
 * Draws a creature: its skin under the pose its body gives it this frame.
 * The chain is laid along the head's trail from where the game
 * interpolates the head between ticks (the lag between that point and the
 * trail's newest sample carries every segment back by the same amount),
 * set aside by the writhe, and the legs skitter by the gait; the head's
 * children (pincers, antennae) and each segment's legs hang from their
 * bones. A profile whose names do not fit its model is drawn at rest,
 * turned to its yaw, so the mistake is visible and not fatal. One draw
 * call a creature.
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
        int overlay = creature.hurtTime > 0 ? OverlayTexture.pack(0, true) : OverlayTexture.NO_OVERLAY;
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture));
        poseStack.pushPose();
        Body body = skin.body;
        if (body == null) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-creature.bodyYaw(partialTick)));
            RigDrawer.draw(skin, Pose.REST, poseStack, out, packedLight, overlay, null, null);
        } else {
            RigDrawer.draw(skin, posed(creature, p, body, partialTick), poseStack, out, packedLight, overlay, null, null);
        }
        poseStack.popPose();
        super.render(creature, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** effects: returns the body's pose this frame, relative to the point the game draws the creature from */
    private static Pose posed(Aberrant creature, CreatureProfile p, Body body, float partialTick) {
        Trail trail = creature.trail(body.axisHeight(), body.length());
        Undulation undulation = p.rig().undulation();
        Undulation.Wave wave = creature.wave(undulation);
        // The game draws from the head's interpolated position; the trail's newest sample is its position at the
        // last tick, so the drawn head sits `lag` behind that sample along the path, and so does every segment.
        double x = Mth.lerp(partialTick, creature.xo, creature.getX());
        double y = Mth.lerp(partialTick, creature.yo, creature.getY());
        double z = Mth.lerp(partialTick, creature.zo, creature.getZ());
        Vec origin = new Vec(x, y, z);
        Vec drawnAxis = origin.plus(creature.up().times(body.axisHeight()));
        double lag = Math.min(drawnAxis.minus(trail.at(0).pos()).length(), 2.0);
        ChainPose chain = ChainPose.of(trail, body.arcBack(), lag, undulation, wave);
        LegGait.LegPose[] legs = p.rig().gait().poses(body.legPairs(), creature.distance() + creature.speed() * partialTick, creature.speed());
        return body.pose(chain, legs, origin);
    }
}
