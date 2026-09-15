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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Blockbench project ({@code .bbmodel}) read into a {@link Rig}: the
 * outliner's groups become bones, each keeping its origin and rotation,
 * hung from the group that holds it; each cube becomes six faces of its
 * group's mesh, turned about the cube's own origin as Blockbench turns it
 * and expressed relative to the group's origin, with a UV rectangle per
 * face; the first embedded texture comes along. Nothing of the file is
 * baked away that Blockbench would let a modeller pose.
 *
 * <p>Blockbench's frame is Minecraft's: +X east, +Y up, +Z south. Rotations,
 * of cubes and of groups alike, are applied about the origin X then Y then
 * Z. Cube coordinates in a project are in model space whatever their
 * group, so a cube's corners are moved into bone space by subtracting the
 * group's origin; the group's rotation is left to {@link Rig#place}. UVs
 * are in the project's resolution and become fractions of it, rows from
 * the bottom as a mesh counts them.
 *
 * <p>Bone 0 is the model's root at the origin, a bone of the reader's own,
 * so that cubes outside every group have somewhere to hang and every group
 * has a parent. A format-5 project keeps its folders' names in a
 * {@code groups} list keyed by uuid; an older one names the outliner's
 * nodes themselves; both are read.
 */
public final class BbRig {
    private BbRig() {}

    private static final String[] DIRECTIONS = {"north", "south", "east", "west", "up", "down"};

    /** The name of the reader's own root bone. */
    public static final String ROOT = "";

    /** A bone under construction: its cubes' faces gather here until the rig is built. */
    private static final class Building {
        final Bone bone;
        final List<Vec> positions = new ArrayList<>();
        final List<Uv> uvs = new ArrayList<>();
        final List<Face> faces = new ArrayList<>();

        Building(Bone bone) {
            this.bone = bone;
        }
    }

    /**
     * effects: returns the project's rig<br>
     * throws: {@link IllegalArgumentException} when the text is not a
     * Blockbench project (not JSON, or no elements), or no face survived
     */
    public static Rig parse(String text) {
        Object root = Json.parse(text);
        List<Object> elements = Json.list(Json.get(root, "elements"));
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("no elements: not a Blockbench project");
        }
        double resW = Json.number(Json.get(Json.get(root, "resolution"), "width"), 16);
        double resH = Json.number(Json.get(Json.get(root, "resolution"), "height"), 16);
        List<String> warnings = new ArrayList<>();

        // Textures: index -> material name; the first embedded image.
        List<String> materials = new ArrayList<>();
        byte[] image = null;
        int imageW = 0, imageH = 0;
        for (Object t : Json.list(Json.get(root, "textures"))) {
            String name = Json.string(Json.get(t, "name"), "texture");
            int dot = name.lastIndexOf('.');
            materials.add(dot > 0 ? name.substring(0, dot) : name);
            if (image == null) {
                String source = Json.string(Json.get(t, "source"), "");
                int comma = source.indexOf("base64,");
                if (source.startsWith("data:image/png") && comma > 0) {
                    try {
                        image = Base64.getDecoder().decode(source.substring(comma + 7));
                        imageW = (int) Json.number(Json.get(t, "width"), resW);
                        imageH = (int) Json.number(Json.get(t, "height"), resH);
                    } catch (IllegalArgumentException e) {
                        warnings.add("texture " + name + ": bad base64, ignored");
                    }
                }
            }
        }
        if (materials.isEmpty()) {
            materials.add("texture");
        }

        // The outliner: groups become bones in pre-order (parents first); element uuid -> bone index.
        Map<String, String> groupNames = new HashMap<>();
        for (Object g : Json.list(Json.get(root, "groups"))) {
            groupNames.put(Json.string(Json.get(g, "uuid"), ""), Json.string(Json.get(g, "name"), "group"));
        }
        List<Building> bones = new ArrayList<>();
        bones.add(new Building(new Bone(ROOT, ROOT, -1, Vec.ZERO, Quat.IDENTITY)));
        Map<String, Integer> boneOf = new HashMap<>();
        walk(Json.list(Json.get(root, "outliner")), 0, ROOT, bones, boneOf, groupNames);

        for (Object e : elements) {
            if (!Json.bool(Json.get(e, "visibility"), true)) {
                continue;
            }
            String type = Json.string(Json.get(e, "type"), "cube");
            String name = Json.string(Json.get(e, "name"), type);
            String uuid = Json.string(Json.get(e, "uuid"), "");
            Building b = bones.get(boneOf.getOrDefault(uuid, 0));
            String group = b.bone.path().isEmpty() ? name : b.bone.path() + "/" + name;
            double[] origin = Json.numbers(Json.get(e, "origin"), 0, 0, 0);
            double[] rotation = Json.numbers(Json.get(e, "rotation"), 0, 0, 0);
            if (type.equals("cube")) {
                cube(e, name, group, origin, rotation, b, resW, resH, materials, warnings);
            } else if (type.equals("mesh")) {
                meshElement(e, name, group, origin, rotation, b, resW, resH, materials, warnings);
            } else {
                warnings.add(name + ": element type " + type + " skipped");
            }
        }
        List<Bone> boneList = new ArrayList<>(bones.size());
        List<Mesh> meshes = new ArrayList<>(bones.size());
        int faces = 0;
        for (Building b : bones) {
            boneList.add(b.bone);
            meshes.add(Mesh.of(b.positions, b.uvs, b.faces));
            faces += b.faces.size();
        }
        if (faces == 0) {
            throw new IllegalArgumentException("no faces: every element was empty or invisible");
        }
        return new Rig(boneList, meshes, Optional.ofNullable(image), imageW, imageH, warnings);
    }

    private static void walk(List<Object> nodes, int parent, String path, List<Building> bones, Map<String, Integer> boneOf, Map<String, String> groupNames) {
        for (Object n : nodes) {
            if (n instanceof String uuid) {
                boneOf.put(uuid, parent);
            } else {
                Object named = Json.get(n, "name");
                String name = named != null ? Json.string(named, "group") : groupNames.getOrDefault(Json.string(Json.get(n, "uuid"), ""), "group");
                double[] origin = Json.numbers(Json.get(n, "origin"), 0, 0, 0);
                double[] rotation = Json.numbers(Json.get(n, "rotation"), 0, 0, 0);
                String childPath = path.isEmpty() ? name : path + "/" + name;
                Bone bone = new Bone(name, childPath, parent, new Vec(origin[0], origin[1], origin[2]),
                        Quat.fromEulerXYZDegrees(rotation[0], rotation[1], rotation[2]));
                bones.add(new Building(bone));
                walk(Json.list(Json.get(n, "children")), bones.size() - 1, childPath, bones, boneOf, groupNames);
            }
        }
    }

    private static void cube(Object e, String name, String group, double[] origin, double[] rotation, Building b, double resW, double resH,
                             List<String> materials, List<String> warnings) {
        double[] from = Json.numbers(Json.get(e, "from"), 0, 0, 0);
        double[] to = Json.numbers(Json.get(e, "to"), 1, 1, 1);
        double inflate = Json.number(Json.get(e, "inflate"), 0);
        double x0 = Math.min(from[0], to[0]) - inflate, x1 = Math.max(from[0], to[0]) + inflate;
        double y0 = Math.min(from[1], to[1]) - inflate, y1 = Math.max(from[1], to[1]) + inflate;
        double z0 = Math.min(from[2], to[2]) - inflate, z1 = Math.max(from[2], to[2]) + inflate;
        Map<String, Object> faceMap = Json.map(Json.get(e, "faces"));
        boolean boxUv = Json.bool(Json.get(e, "box_uv"), false);
        double[] uvOffset = Json.numbers(Json.get(e, "uv_offset"), 0, 0);
        Vec pivot = b.bone.pivot();
        int flat = 0;
        for (String dir : DIRECTIONS) {
            Object f = faceMap.get(dir);
            if (f == null && !boxUv) {
                continue;
            }
            Object tex = Json.get(f, "texture");
            if (tex == null && f != null && !boxUv) {
                continue;
            }
            int texture = (int) Json.number(tex, 0);
            String material = texture >= 0 && texture < materials.size() ? materials.get(texture) : materials.get(0);
            double[] uv = boxUv ? boxUvOf(dir, uvOffset, x1 - x0, y1 - y0, z1 - z0) : Json.numbers(Json.get(f, "uv"), 0, 0, resW, resH);
            int turn = ((int) Json.number(Json.get(f, "rotation"), 0) / 90) & 3;
            // The face's four corners in texture order: top-left, top-right, bottom-right, bottom-left, seen from outside.
            Vec[] c = switch (dir) {
                case "north" -> new Vec[] {v(x1, y1, z0), v(x0, y1, z0), v(x0, y0, z0), v(x1, y0, z0)};
                case "south" -> new Vec[] {v(x0, y1, z1), v(x1, y1, z1), v(x1, y0, z1), v(x0, y0, z1)};
                case "east" -> new Vec[] {v(x1, y1, z1), v(x1, y1, z0), v(x1, y0, z0), v(x1, y0, z1)};
                case "west" -> new Vec[] {v(x0, y1, z0), v(x0, y1, z1), v(x0, y0, z1), v(x0, y0, z0)};
                case "up" -> new Vec[] {v(x0, y1, z0), v(x1, y1, z0), v(x1, y1, z1), v(x0, y1, z1)};
                default -> new Vec[] {v(x0, y0, z1), v(x1, y0, z1), v(x1, y0, z0), v(x0, y0, z0)};
            };
            // Blockbench counts texture rows from the top; a mesh's v runs from the bottom.
            Uv[] t = {new Uv(uv[0] / resW, 1.0 - uv[1] / resH), new Uv(uv[2] / resW, 1.0 - uv[1] / resH), new Uv(uv[2] / resW, 1.0 - uv[3] / resH), new Uv(uv[0] / resW, 1.0 - uv[3] / resH)};
            // Wound top-left, bottom-left, bottom-right, top-right so that, seen from outside, the corners run
            // counter-clockwise and the normal points out of the cube, whatever its turn.
            List<Corner> corners = new ArrayList<>(4);
            int base = b.positions.size();
            for (int k : new int[] {0, 3, 2, 1}) {
                Vec p = rotate(c[k], origin, rotation).minus(pivot);
                b.positions.add(p);
                b.uvs.add(t[(k + turn) & 3]);
                corners.add(new Corner(b.positions.size() - 1, b.uvs.size() - 1));
            }
            if (area(b.positions, corners) < 1e-9) {
                // A flat cube's edge-on faces span no area: dropped, with their corners.
                b.positions.subList(base, b.positions.size()).clear();
                b.uvs.subList(b.uvs.size() - 4, b.uvs.size()).clear();
                flat++;
                continue;
            }
            b.faces.add(new Face(material, group, corners, Vec.Y));
        }
        if (flat > 0) {
            warnings.add(name + ": " + flat + " face(s) with no area skipped");
        }
    }

    /** Blockbench's box UV: the classic entity layout from the offset, the sides in a row under the top and bottom. */
    private static double[] boxUvOf(String dir, double[] off, double w, double h, double d) {
        double u = off[0], v = off[1];
        return switch (dir) {
            case "up" -> new double[] {u + d, v, u + d + w, v + d};
            case "down" -> new double[] {u + d + w, v + d, u + d + w + w, v};
            case "east" -> new double[] {u, v + d, u + d, v + d + h};
            case "north" -> new double[] {u + d, v + d, u + d + w, v + d + h};
            case "west" -> new double[] {u + d + w, v + d, u + d + w + d, v + d + h};
            default -> new double[] {u + d + w + d, v + d, u + d + w + d + w, v + d + h};
        };
    }

    private static void meshElement(Object e, String name, String group, double[] origin, double[] rotation, Building b, double resW, double resH,
                                    List<String> materials, List<String> warnings) {
        Map<String, Object> vertices = Json.map(Json.get(e, "vertices"));
        Map<String, Vec> at = new HashMap<>();
        for (Map.Entry<String, Object> v : vertices.entrySet()) {
            double[] p = Json.numbers(v.getValue(), 0, 0, 0);
            at.put(v.getKey(), new Vec(p[0] + origin[0], p[1] + origin[1], p[2] + origin[2]));
        }
        Vec pivot = b.bone.pivot();
        for (Map.Entry<String, Object> f : Json.map(Json.get(e, "faces")).entrySet()) {
            List<Object> keys = Json.list(Json.get(f.getValue(), "vertices"));
            Map<String, Object> faceUv = Json.map(Json.get(f.getValue(), "uv"));
            int texture = (int) Json.number(Json.get(f.getValue(), "texture"), 0);
            String material = texture >= 0 && texture < materials.size() ? materials.get(texture) : materials.get(0);
            if (keys.size() < 3) {
                warnings.add(name + ": face " + f.getKey() + " has " + keys.size() + " corners, skipped");
                continue;
            }
            List<Corner> corners = new ArrayList<>(keys.size());
            int base = b.positions.size();
            boolean ok = true;
            for (Object k : keys) {
                Vec p = at.get(String.valueOf(k));
                if (p == null) {
                    ok = false;
                    break;
                }
                double[] uv = Json.numbers(faceUv.get(String.valueOf(k)), 0, 0);
                b.positions.add(rotate(p, origin, rotation).minus(pivot));
                b.uvs.add(new Uv(uv[0] / resW, 1.0 - uv[1] / resH));
                corners.add(new Corner(b.positions.size() - 1, b.uvs.size() - 1));
            }
            if (!ok || area(b.positions, corners) < 1e-9) {
                b.positions.subList(base, b.positions.size()).clear();
                b.uvs.subList(b.uvs.size() - corners.size(), b.uvs.size()).clear();
                warnings.add(name + ": face " + f.getKey() + (ok ? " has no area" : " names a vertex it does not have") + ", skipped");
                continue;
            }
            b.faces.add(new Face(material, group, corners, Vec.Y));
        }
    }

    /** effects: returns twice the polygon's area (the length of Newell's normal before normalising) */
    private static double area(List<Vec> positions, List<Corner> corners) {
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < corners.size(); i++) {
            Vec a = positions.get(corners.get(i).position());
            Vec b = positions.get(corners.get((i + 1) % corners.size()).position());
            nx += (a.y() - b.y()) * (a.z() + b.z());
            ny += (a.z() - b.z()) * (a.x() + b.x());
            nz += (a.x() - b.x()) * (a.y() + b.y());
        }
        return Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    private static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    /** effects: returns {@code p} turned about {@code origin} by the degrees in {@code r}: X, then Y, then Z */
    static Vec rotate(Vec p, double[] origin, double[] r) {
        if (r[0] == 0 && r[1] == 0 && r[2] == 0) {
            return p;
        }
        double x = p.x() - origin[0], y = p.y() - origin[1], z = p.z() - origin[2];
        double a = Math.toRadians(r[0]);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        y = y1;
        z = z1;
        double b = Math.toRadians(r[1]);
        double x1 = x * Math.cos(b) + z * Math.sin(b), z2 = -x * Math.sin(b) + z * Math.cos(b);
        x = x1;
        z = z2;
        double g = Math.toRadians(r[2]);
        double x2 = x * Math.cos(g) - y * Math.sin(g), y2 = x * Math.sin(g) + y * Math.cos(g);
        return new Vec(x2 + origin[0], y2 + origin[1], z + origin[2]);
    }
}
