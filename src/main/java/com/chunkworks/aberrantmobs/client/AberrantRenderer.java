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
import com.chunkworks.aberrantmobs.domain.Legs;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Trail;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
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
 * set aside by the writhe, and each leg is aimed at its foot where the
 * creature has it planted on the world (a swinging foot advanced by the
 * partial tick); a playing clip's turns go over that. The head's children
 * (pincers, antennae) hang from their bones. The cracked segment's
 * glowing cubes are drawn full bright in a
 * pulsing ember tint, the rest of its plating as any other. A profile
 * whose names do not fit its model is drawn at rest, turned to its yaw,
 * so the mistake is visible and not fatal. Two draw calls a creature.
 */
public final class AberrantRenderer extends EntityRenderer<Aberrant> {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    /** A player skin's face and hat layer, in texture space (a 64 by 64 skin). */
    private static final float[] FACE_UV = {8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f};
    private static final float[] HAT_UV = {40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f};

    public AberrantRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** effects: returns the shadow's radius: six tenths of the profile's width (the head's box), so it is the creature's, not one size for all */
    @Override
    protected float getShadowRadius(Aberrant creature) {
        CreatureProfile p = creature.profile();
        return p == null ? 0.0f : (float) (p.body().width() * 0.6);
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
        Pose pose;
        if (body == null) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-creature.bodyYaw(partialTick)));
            pose = Pose.REST;
        } else {
            pose = posed(creature, p, skin, body, partialTick);
        }
        RigDrawer.draw(skin, skin.bones, pose, poseStack, out, packedLight, overlay, null, null);
        // The crack: its glowing cubes, and only on the cracked segment, full bright and pulsing like an ember.
        int weak = creature.weakSegment();
        if (body != null && weak >= 0 && weak < body.chain().length) {
            int bone = body.chainBone(weak);
            float pulse = 0.75f + 0.25f * Mth.sin((creature.tickCount + partialTick) * 0.25f);
            int ember = 0xFF000000 | (Math.round(255 * pulse) << 16) | (Math.round(120 * pulse) << 8) | Math.round(40 * pulse);
            RigDrawer.drawBone(skin, skin.glow, bone, pose, poseStack, out, LightTexture.FULL_BRIGHT, overlay, ember);
        }
        // The stolen face, last, since fetching another texture's buffer ends the skin's: the mask's front wears the
        // last victim's skin, its face over the painted mask and its hat layer a hair in front.
        com.mojang.authlib.GameProfile face = creature.face();
        if (face != null && skin.maskFront != null && skin.headBone >= 0) {
            net.minecraft.resources.ResourceLocation victim = net.minecraft.client.Minecraft.getInstance().getSkinManager().getInsecureSkin(face).texture();
            VertexConsumer faceOut = buffers.getBuffer(RenderType.entityCutoutNoCull(victim));
            RigDrawer.drawQuad(skin, skin.headBone, pose, skin.maskFront, 0.004, FACE_UV, poseStack, faceOut, RigDrawer.WHITE, packedLight, overlay);
            VertexConsumer hatOut = buffers.getBuffer(RenderType.entityTranslucent(victim));
            RigDrawer.drawQuad(skin, skin.headBone, pose, skin.maskFront, 0.012, HAT_UV, poseStack, hatOut, RigDrawer.WHITE, packedLight, overlay);
        }
        poseStack.popPose();
        super.render(creature, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** effects: returns the body's pose this frame, relative to the point the game draws the creature from */
    private static Pose posed(Aberrant creature, CreatureProfile p, Skin skin, Body body, float partialTick) {
        Trail trail = creature.trail(body.length());
        Undulation undulation = p.rig().undulation();
        Undulation.Wave wave = creature.wave(undulation);
        // The game draws from the head's interpolated position; the trail's newest sample is its position at the
        // last tick, so the drawn head sits `lag` behind that sample along the path, and so does every segment.
        double x = Mth.lerp(partialTick, creature.xo, creature.getX());
        double y = Mth.lerp(partialTick, creature.yo, creature.getY());
        double z = Mth.lerp(partialTick, creature.zo, creature.getZ());
        Vec origin = new Vec(x, y, z);
        Vec drawnAxis = origin.plus(new Vec(0, creature.getBbHeight() / 2.0, 0));
        double lag = Math.min(drawnAxis.minus(trail.at(0).pos()).length(), 2.0);
        ChainPose chain = ChainPose.of(trail, body.arcBack(), lag, undulation, wave);
        Legs.Foot[] feet = creature.feet();
        double dt = partialTick / Legs.swingTicks(creature.speed());
        LegGait.LegPose[] legs = new LegGait.LegPose[body.legCount()];
        for (int i = 0; i < legs.length; i++) {
            Legs.Leg leg = body.leg(i);
            Vec pos = chain.position(leg.segment());
            Quat orient = chain.orientation(leg.segment());
            Vec rest = Legs.rest(leg, pos, orient);
            Vec foot = feet[i].advanced(dt).at(rest, orient.rotate(Vec.Y), p.rig().gait().lift());
            legs[i] = Legs.aim(leg, pos, orient, foot);
        }
        return creature.overlay(skin.rig, body.pose(chain, legs, origin), partialTick);
    }
}
