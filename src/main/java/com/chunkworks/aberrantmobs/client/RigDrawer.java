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
     * effects: emits every bone of {@code skin} through {@code out} standing
     * as {@code pose} says, on top of {@code stack}'s current transform;
     * bone {@code i} tinted {@code tint[i]} (WHITE when tint is null) and lit
     * {@code light[i]} (packedLight when light is null)
     */
    public static void draw(Skin skin, Pose pose, PoseStack stack, VertexConsumer out, int packedLight, int overlay, int[] tint, int[] light) {
        Xform[] placed = skin.rig.place(pose);
        Vector3f p = new Vector3f();
        Vector3f nv = new Vector3f();
        for (int b = 0; b < placed.length; b++) {
            BakedMesh mesh = skin.bones[b];
            if (mesh.quadCount() == 0) {
                continue;
            }
            stack.pushPose();
            Vec t = placed[b].translation();
            stack.translate(t.x() * skin.scale, t.y() * skin.scale, t.z() * skin.scale);
            Quat q = placed[b].rotation();
            stack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
            Matrix4f m = stack.last().pose();
            Matrix3f n = stack.last().normal();
            int argb = tint == null ? WHITE : tint[b];
            int l = light == null ? packedLight : light[b];
            for (int i = 0; i < mesh.quadCount(); i++) {
                nv.set(mesh.normal(i, 0), mesh.normal(i, 1), mesh.normal(i, 2));
                n.transform(nv);
                for (int k = 0; k < 4; k++) {
                    p.set(mesh.position(i, k, 0), mesh.position(i, k, 1), mesh.position(i, k, 2));
                    m.transformPosition(p);
                    out.addVertex(p.x(), p.y(), p.z(), argb, mesh.uv(i, k, 0), mesh.uv(i, k, 1), overlay, l, nv.x(), nv.y(), nv.z());
                }
            }
            stack.popPose();
        }
    }
}
