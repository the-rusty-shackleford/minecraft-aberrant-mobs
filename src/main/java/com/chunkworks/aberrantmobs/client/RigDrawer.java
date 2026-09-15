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

import com.chunkworks.aberrantmobs.domain.BakedMesh;
import com.chunkworks.aberrantmobs.domain.Pose;
import com.chunkworks.aberrantmobs.domain.Quat;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.Xform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Puts a skin into a vertex consumer under a pose: one placement per bone
 * from the rig, each bone's baked mesh pushed through it. Four vertices
 * per quad with the position, the colour, the texture coordinate, the
 * overlay, the light and the quad's normal turned by the placement.
 */
public final class RigDrawer {
    private RigDrawer() {}

    public static final int WHITE = 0xFFFFFFFF;

    /**
     * effects: emits every bone's mesh from {@code meshes} (indexed by bone)
     * through {@code out} standing as {@code pose} says, on top of
     * {@code stack}'s current transform; bone {@code i} tinted {@code tint[i]}
     * (WHITE when tint is null) and lit {@code light[i]} (packedLight when
     * light is null)
     */
    public static void draw(Skin skin, BakedMesh[] meshes, Pose pose, PoseStack stack, VertexConsumer out, int packedLight, int overlay, int[] tint, int[] light) {
        Xform[] placed = skin.rig.place(pose);
        Vector3f p = new Vector3f();
        Vector3f nv = new Vector3f();
        for (int b = 0; b < placed.length; b++) {
            BakedMesh mesh = meshes[b];
            if (mesh.quadCount() == 0) {
                continue;
            }
            int argb = tint == null ? WHITE : tint[b];
            int l = light == null ? packedLight : light[b];
            emit(skin, mesh, placed[b], stack, out, argb, l, overlay, p, nv);
        }
    }

    /** effects: emits one bone's mesh from {@code meshes} alone, under {@code pose}, lit {@code light} and tinted {@code argb} */
    public static void drawBone(Skin skin, BakedMesh[] meshes, int bone, Pose pose, PoseStack stack, VertexConsumer out, int light, int overlay, int argb) {
        BakedMesh mesh = meshes[bone];
        if (mesh.quadCount() == 0) {
            return;
        }
        Xform[] placed = skin.rig.place(pose);
        emit(skin, mesh, placed[bone], stack, out, argb, light, overlay, new Vector3f(), new Vector3f());
    }

    /**
     * effects: emits one quad through {@code out} under bone {@code bone}'s
     * placement in {@code pose}: its corners {@code corners} (bone space,
     * blocks) pushed {@code lift} along the bone's +Z, textured by
     * {@code uv} (u0, v0, u1, v1 in texture space) with u across the
     * bone's X from -X to +X and v down the bone's Y
     */
    public static void drawQuad(Skin skin, int bone, Pose pose, Vec[] corners, double lift, float[] uv, PoseStack stack, VertexConsumer out, int argb, int light, int overlay) {
        Xform placement = skin.rig.place(pose)[bone];
        stack.pushPose();
        Vec t = placement.translation();
        stack.translate(t.x() * skin.scale, t.y() * skin.scale, t.z() * skin.scale);
        Quat q = placement.rotation();
        stack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        Matrix4f m = stack.last().pose();
        Matrix3f n = stack.last().normal();
        Vector3f nv = new Vector3f(0, 0, 1);
        n.transform(nv);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec c : corners) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minY = Math.min(minY, c.y());
            maxY = Math.max(maxY, c.y());
        }
        Vector3f p = new Vector3f();
        for (Vec c : corners) {
            p.set((float) c.x(), (float) c.y(), (float) (c.z() + lift));
            m.transformPosition(p);
            float fu = maxX > minX ? (float) ((c.x() - minX) / (maxX - minX)) : 0f;
            float fv = maxY > minY ? (float) ((maxY - c.y()) / (maxY - minY)) : 0f;
            out.addVertex(p.x(), p.y(), p.z(), argb, uv[0] + (uv[2] - uv[0]) * fu, uv[1] + (uv[3] - uv[1]) * fv, overlay, light, nv.x(), nv.y(), nv.z());
        }
        stack.popPose();
    }

    private static void emit(Skin skin, BakedMesh mesh, Xform placement, PoseStack stack, VertexConsumer out, int argb, int light, int overlay, Vector3f p, Vector3f nv) {
        stack.pushPose();
        Vec t = placement.translation();
        stack.translate(t.x() * skin.scale, t.y() * skin.scale, t.z() * skin.scale);
        Quat q = placement.rotation();
        stack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        Matrix4f m = stack.last().pose();
        Matrix3f n = stack.last().normal();
        for (int i = 0; i < mesh.quadCount(); i++) {
            nv.set(mesh.normal(i, 0), mesh.normal(i, 1), mesh.normal(i, 2));
            n.transform(nv);
            for (int k = 0; k < 4; k++) {
                p.set(mesh.position(i, k, 0), mesh.position(i, k, 1), mesh.position(i, k, 2));
                m.transformPosition(p);
                out.addVertex(p.x(), p.y(), p.z(), argb, mesh.uv(i, k, 0), mesh.uv(i, k, 1), overlay, light, nv.x(), nv.y(), nv.z());
            }
        }
        stack.popPose();
    }
}
