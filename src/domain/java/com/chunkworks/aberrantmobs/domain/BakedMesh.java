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
package com.chunkworks.aberrantmobs.domain;

import java.util.List;

/**
 * A mesh as vertices ready to draw: four per quad, each a position (in
 * blocks), a texture coordinate (the game's v, growing downward) and one
 * flat normal per quad. Plain arrays, so it is built once at resource
 * load and read every frame with no allocation.
 *
 * <p>RI: positions has 12 floats per quad, uvs 8, normals 3; quads >= 0.
 * AF: quad i is vertices 4i..4i+3 in order, with normal i.
 */
public final class BakedMesh {
    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int quads;

    private BakedMesh(float[] positions, float[] uvs, float[] normals, int quads) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.quads = quads;
        assert positions.length == quads * 12 && uvs.length == quads * 8 && normals.length == quads * 3;
    }

    /**
     * effects: returns {@code mesh} baked with its positions scaled by
     * {@code unitsToBlocks} (a pixel mesh: 1/16) and its v flipped to the
     * game's texture space
     */
    public static BakedMesh of(Mesh mesh, double unitsToBlocks) {
        List<Mesh.Quad> quads = mesh.quads();
        float[] pos = new float[quads.size() * 12];
        float[] uv = new float[quads.size() * 8];
        float[] nrm = new float[quads.size() * 3];
        int i = 0;
        for (Mesh.Quad q : quads) {
            Corner[] corners = {q.a(), q.b(), q.c(), q.d()};
            for (int k = 0; k < 4; k++) {
                Vec p = mesh.positions().get(corners[k].position());
                Uv t = mesh.uvs().get(corners[k].uv());
                pos[i * 12 + k * 3] = (float) (p.x() * unitsToBlocks);
                pos[i * 12 + k * 3 + 1] = (float) (p.y() * unitsToBlocks);
                pos[i * 12 + k * 3 + 2] = (float) (p.z() * unitsToBlocks);
                uv[i * 8 + k * 2] = (float) t.u();
                uv[i * 8 + k * 2 + 1] = (float) (1.0 - t.v());
            }
            Vec n = q.face().normal();
            nrm[i * 3] = (float) n.x();
            nrm[i * 3 + 1] = (float) n.y();
            nrm[i * 3 + 2] = (float) n.z();
            i++;
        }
        return new BakedMesh(pos, uv, nrm, quads.size());
    }

    public int quadCount() {
        return quads;
    }

    /** effects: returns the x, y or z (component 0..2) of vertex k (0..3) of quad i, blocks */
    public float position(int i, int k, int component) {
        return positions[i * 12 + k * 3 + component];
    }

    /** effects: returns the u (0) or v (1) of vertex k of quad i */
    public float uv(int i, int k, int component) {
        return uvs[i * 8 + k * 2 + component];
    }

    /** effects: returns the normal's component of quad i */
    public float normal(int i, int component) {
        return normals[i * 3 + component];
    }
}
