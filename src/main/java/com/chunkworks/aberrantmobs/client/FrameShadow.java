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

import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.ContactShadow;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import org.joml.Vector3f;

/** The vanilla contact-shadow texture on nearby full support faces in the wearer's frame. */
public final class FrameShadow {
    private FrameShadow() {}
    private static final RenderType TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));

    /**
     * requires: arguments are the dispatcher's shadow pass, with a bent player
     * effects: draws the contact blob on visible full-block faces under local feet;
     * preserves vanilla brightness, distance fade and size. Does not change shader
     * shadow maps, whose direction belongs to the scene's light.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Entity entity, float weight,
            float partialTick, LevelReader level, float radius) {
        if (radius <= 0 || weight <= 0) return;
        Frame frame = WallWalk.frameOf(entity);
        Vec feet = new Vec(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        double reach = Math.min(weight / 0.5f, radius);
        Frame.Box area = frame.box(feet.minus(frame.up().times(reach + 1)), radius * 2, reach + 1);
        Direction up = Direction.getNearest(frame.up().x(), frame.up().y(), frame.up().z());
        VertexConsumer vertices = buffers.getBuffer(TYPE);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(area.lo().x()), Mth.floor(area.lo().y()), Mth.floor(area.lo().z()),
                Mth.floor(area.hi().x()), Mth.floor(area.hi().y()), Mth.floor(area.hi().z()))) {
            var state = level.getBlockState(pos);
            if (state.getRenderShape() == RenderShape.INVISIBLE || !state.isCollisionShapeFullBlock(level, pos)) continue;
            BlockPos above = pos.relative(up);
            if (level.getBlockState(above).isCollisionShapeFullBlock(level, above)) continue;
            int light = level.getMaxLocalRawBrightness(above);
            if (light <= 3) continue;
            Vec lo = new Vec(pos.getX(), pos.getY(), pos.getZ());
            var patch = ContactShadow.project(frame, feet, new Frame.Box(lo, lo.plus(new Vec(1, 1, 1))), radius, reach);
            if (patch.isEmpty()) continue;
            var q = patch.get();
            float alpha = Mth.clamp((weight + (float) q.y() * 0.5f) * 0.5f * LightTexture.getBrightness(level.dimensionType(), light), 0, 1);
            int color = FastColor.ARGB32.color(Mth.floor(alpha * 255), 255, 255, 255);
            vertex(pose.last(), vertices, frame, radius, color, q.x0(), q.y(), q.z0());
            vertex(pose.last(), vertices, frame, radius, color, q.x0(), q.y(), q.z1());
            vertex(pose.last(), vertices, frame, radius, color, q.x1(), q.y(), q.z1());
            vertex(pose.last(), vertices, frame, radius, color, q.x1(), q.y(), q.z0());
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, Frame frame, float radius,
            int color, double x, double y, double z) {
        Vec offset = frame.toWorld(new Vec(x, y + 0.001, z));
        Vector3f v = pose.pose().transformPosition((float) offset.x(), (float) offset.y(), (float) offset.z(), new Vector3f());
        vertices.addVertex(v.x(), v.y(), v.z(), color, (float) (-x / (2 * radius) + 0.5), (float) (-z / (2 * radius) + 0.5),
                OverlayTexture.NO_OVERLAY, 15728880, (float) frame.up().x(), (float) frame.up().y(), (float) frame.up().z());
    }
}
