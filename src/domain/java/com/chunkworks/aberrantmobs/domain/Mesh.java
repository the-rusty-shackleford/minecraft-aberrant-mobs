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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A polygon soup: positions, texture coordinates, and faces that index
 * them, each face with a material and a group name and a unit normal. What
 * a creature looks like, before the client turns
 * it into vertices.
 *
 * <p>Normals are never taken from the file: they are computed from the
 * corners (Newell's method, sound for n-gons and for slightly bent quads)
 * and oriented by {@link #orientedOutward()}, because the meshes we get are
 * wound inconsistently and every piece of them is a convex solid.
 *
 * <p>RI: every corner's indices are in range; faces, positions and uvs are
 *     immutable lists; every face's normal is unit.
 * AF: AF(positions, uvs, faces) = "the polygons {(material_i, group_i,
 *     [positions[c.position] for c in corners_i], [uvs[c.uv]],
 *     normal_i)}".
 */
public final class Mesh {
    private final List<Vec> positions;
    private final List<Uv> uvs;
    private final List<Face> faces;

    private Mesh(List<Vec> positions, List<Uv> uvs, List<Face> faces) {
        this.positions = List.copyOf(positions);
        this.uvs = List.copyOf(uvs);
        this.faces = List.copyOf(faces);
        for (Face f : this.faces) {
            for (Corner c : f.corners()) {
                if (c.position() >= this.positions.size() || c.uv() >= this.uvs.size()) {
                    throw new IllegalArgumentException("a corner points past the tables: " + c);
                }
            }
        }
    }

    /**
     * effects: returns the mesh of {@code faces} over the tables, with every
     * face's normal recomputed from its corners<br>
     * throws: {@link IllegalArgumentException} if an index is out of range
     * or a face is degenerate (its corners span no area)
     */
    public static Mesh of(List<Vec> positions, List<Uv> uvs, List<Face> faces) {
        List<Face> normalized = new ArrayList<>(faces.size());
        for (Face f : faces) {
            normalized.add(f.withNormal(newell(positions, f.corners())));
        }
        return new Mesh(positions, uvs, normalized);
    }

    public List<Vec> positions() {
        return positions;
    }

    public List<Uv> uvs() {
        return uvs;
    }

    public List<Face> faces() {
        return faces;
    }

    public int faceCount() {
        return faces.size();
    }

    /** effects: returns the material names in order of first use */
    public List<String> materials() {
        Set<String> seen = new LinkedHashSet<>();
        for (Face f : faces) {
            seen.add(f.material());
        }
        return List.copyOf(seen);
    }

    /** effects: returns the group names in order of first use, the empty name included if any face has none */
    public List<String> groups() {
        Set<String> seen = new LinkedHashSet<>();
        for (Face f : faces) {
            seen.add(f.group());
        }
        return List.copyOf(seen);
    }

    /** effects: returns the smallest box around every position a face uses; the origin's box for an empty mesh */
    public Region bounds() {
        if (faces.isEmpty()) {
            return new Region(Vec.ZERO, Vec.ZERO);
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Face f : faces) {
            for (Corner c : f.corners()) {
                Vec p = positions.get(c.position());
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
            }
        }
        return new Region(new Vec(minX, minY, minZ), new Vec(maxX, maxY, maxZ));
    }

    /** effects: returns the centre of {@code face}: the mean of its corners */
    public Vec centre(Face face) {
        List<Vec> pts = new ArrayList<>(face.corners().size());
        for (Corner c : face.corners()) {
            pts.add(positions.get(c.position()));
        }
        return Vec.centroid(pts);
    }

    /** effects: returns the sub-mesh of the faces {@code selector} picks, sharing the tables */
    public Mesh part(Selector selector) {
        return part(f -> selector.matches(f, centre(f)));
    }

    /** effects: returns the sub-mesh of the faces {@code keep} accepts, sharing the tables */
    public Mesh part(Predicate<Face> keep) {
        List<Face> kept = new ArrayList<>();
        for (Face f : faces) {
            if (keep.test(f)) {
                kept.add(f);
            }
        }
        return new Mesh(positions, uvs, kept);
    }

    /** effects: returns this mesh less every face that is in {@code other} (by identity of the face value) */
    public Mesh without(Mesh other) {
        Set<Face> gone = new java.util.HashSet<>(other.faces);
        return part(f -> !gone.contains(f));
    }

    /** effects: returns the mesh with every position mapped by {@code t}, normals recomputed */
    public Mesh transformed(Transform t) {
        List<Vec> moved = new ArrayList<>(positions.size());
        for (Vec p : positions) {
            moved.add(t.apply(p));
        }
        List<Face> re = new ArrayList<>(faces.size());
        for (Face f : faces) {
            re.add(f.withNormal(newell(moved, f.corners())));
        }
        return new Mesh(moved, uvs, re);
    }

    /**
     * effects: returns the mesh with every face's normal pointing away from
     * the centre of the connected piece it belongs to -- faces sharing a
     * position index are one piece -- flipping the corner order of any face
     * that pointed inward. Right for pieces that are convex, which the
     * meshes this mod draws are; idempotent.
     */
    public Mesh orientedOutward() {
        int[] parent = new int[positions.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (Face f : faces) {
            int first = f.corners().get(0).position();
            for (Corner c : f.corners()) {
                union(parent, first, c.position());
            }
        }
        Map<Integer, List<Vec>> members = new HashMap<>();
        for (Face f : faces) {
            for (Corner c : f.corners()) {
                members.computeIfAbsent(find(parent, c.position()), k -> new ArrayList<>()).add(positions.get(c.position()));
            }
        }
        Map<Integer, Vec> centres = new HashMap<>();
        members.forEach((root, pts) -> centres.put(root, Vec.centroid(pts)));
        List<Face> oriented = new ArrayList<>(faces.size());
        for (Face f : faces) {
            Vec centre = centres.get(find(parent, f.corners().get(0).position()));
            Vec outward = centre(f).minus(centre);
            oriented.add(f.normal().dot(outward) < 0 ? f.flipped() : f);
        }
        return new Mesh(positions, uvs, oriented);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    /** Four corners to draw as one quad; a triangle repeats its last corner. */
    public record Quad(Face face, Corner a, Corner b, Corner c, Corner d) {}

    /**
     * effects: returns every face as quads: a quad as itself, a triangle
     * with its last corner repeated, an n-gon fanned from its first corner
     * into pairs of triangles, {@code ceil((n - 2) / 2)} quads -- right for
     * convex faces, which these are
     */
    public List<Quad> quads() {
        List<Quad> out = new ArrayList<>(faces.size());
        for (Face f : faces) {
            List<Corner> c = f.corners();
            int n = c.size();
            if (n == 4) {
                out.add(new Quad(f, c.get(0), c.get(1), c.get(2), c.get(3)));
            } else if (n == 3) {
                out.add(new Quad(f, c.get(0), c.get(1), c.get(2), c.get(2)));
            } else {
                for (int i = 1; i + 1 < n; i += 2) {
                    Corner last = i + 2 < n ? c.get(i + 2) : c.get(i + 1);
                    out.add(new Quad(f, c.get(0), c.get(i), c.get(i + 1), last));
                }
            }
        }
        return out;
    }

    /** effects: returns a closed box of six quads, {@code size} on a side about the origin, material "placeholder", full-texture uvs */
    public static Mesh placeholder(double size) {
        double h = size / 2;
        List<Vec> p = List.of(new Vec(-h, -h, -h), new Vec(h, -h, -h), new Vec(h, h, -h), new Vec(-h, h, -h),
                new Vec(-h, -h, h), new Vec(h, -h, h), new Vec(h, h, h), new Vec(-h, h, h));
        List<Uv> uv = List.of(new Uv(0, 0), new Uv(1, 0), new Uv(1, 1), new Uv(0, 1));
        int[][] sides = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 4, 7, 3}};
        List<Face> faces = new ArrayList<>();
        for (int[] s : sides) {
            faces.add(new Face("placeholder", "", List.of(new Corner(s[0], 0), new Corner(s[1], 1), new Corner(s[2], 2), new Corner(s[3], 3)), Vec.Y));
        }
        return of(p, uv, faces).orientedOutward();
    }

    /**
     * effects: returns the unit normal of the polygon {@code corners}
     * describes over {@code positions} by Newell's method<br>
     * throws: {@link IllegalArgumentException} if the polygon spans no area
     */
    public static Vec newell(List<Vec> positions, Collection<Corner> corners) {
        double nx = 0, ny = 0, nz = 0;
        List<Corner> c = List.copyOf(corners);
        for (int i = 0; i < c.size(); i++) {
            Vec a = positions.get(c.get(i).position());
            Vec b = positions.get(c.get((i + 1) % c.size()).position());
            nx += (a.y() - b.y()) * (a.z() + b.z());
            ny += (a.z() - b.z()) * (a.x() + b.x());
            nz += (a.x() - b.x()) * (a.y() + b.y());
        }
        Vec n = new Vec(nx, ny, nz);
        if (n.length() < 1e-9) {
            throw new IllegalArgumentException("a face with no area: " + corners);
        }
        return n.normalized();
    }
}
