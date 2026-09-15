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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Not a project: refused. Bones: the root of the reader's own,
 * a group a bone with its origin and rotation, nesting parents-first, a
 * cube at the outliner's root on the root bone. Placement at rest: a cube
 * in a turned group stands where Blockbench turns it, about the group's
 * origin; a group inside a turned group turns within its parent; a cube
 * with its own turn inside a turned group gets both. The friend's file: the
 * cube and bone counts, the face count, the world bounds and a rotated
 * leg's corner all agree with an independent reading of the saved file
 * (Python, group rotations applied innermost first); the texture is the
 * embedded 512 by 512. Flat cubes: their edge-on faces are dropped, with a
 * warning, and nothing else is.
 */
final class BbRigTest {
    private static final Path FACE_STEALER = Path.of("src/main/resources/assets/aberrantmobs/aberrantmobs/model/face_stealer.bbmodel");

    private static String project(String outliner, String... elements) {
        return "{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"free\"},\"resolution\":{\"width\":16,\"height\":16},"
                + "\"elements\":[" + String.join(",", elements) + "],\"outliner\":[" + outliner + "],"
                + "\"textures\":[{\"name\":\"skin.png\",\"source\":\"\"}]}";
    }

    private static String cube(String uuid, String name, double[] from, double[] to, double[] origin, double[] rotation) {
        StringBuilder faces = new StringBuilder();
        for (String d : new String[] {"north", "south", "east", "west", "up", "down"}) {
            faces.append(faces.length() == 0 ? "" : ",").append('"').append(d).append("\":{\"uv\":[0,0,4,4],\"texture\":0}");
        }
        return "{\"type\":\"cube\",\"uuid\":\"" + uuid + "\",\"name\":\"" + name + "\",\"from\":[" + from[0] + "," + from[1] + "," + from[2] + "],\"to\":[" + to[0] + "," + to[1] + "," + to[2]
                + "],\"origin\":[" + origin[0] + "," + origin[1] + "," + origin[2] + "],\"rotation\":[" + rotation[0] + "," + rotation[1] + "," + rotation[2] + "],\"faces\":{" + faces + "}}";
    }

    private static String group(String name, double[] origin, double[] rotation, String children) {
        return "{\"name\":\"" + name + "\",\"uuid\":\"g-" + name + "\",\"origin\":[" + origin[0] + "," + origin[1] + "," + origin[2] + "],\"rotation\":["
                + rotation[0] + "," + rotation[1] + "," + rotation[2] + "],\"children\":[" + children + "]}";
    }

    /** effects: returns every vertex of every bone's mesh placed at rest */
    private static List<Vec> restVertices(Rig rig) {
        Xform[] world = rig.place(Pose.REST);
        List<Vec> out = new ArrayList<>();
        for (int i = 0; i < rig.boneCount(); i++) {
            for (Vec p : rig.mesh(i).positions()) {
                out.add(world[i].apply(p));
            }
        }
        return out;
    }

    private static boolean hasVertex(List<Vec> vertices, Vec v, double eps) {
        return vertices.stream().anyMatch(p -> p.near(v, eps));
    }

    @Test
    void notAProjectIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BbRig.parse("{\"elements\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> BbRig.parse("not json"));
    }

    @Test
    void aCubeInATurnedGroupStandsWhereBlockbenchTurnsIt() {
        String text = project(group("g", new double[] {1, 1, 1}, new double[] {0, 90, 0}, "\"c1\""),
                cube("c1", "box", new double[] {0, 0, 0}, new double[] {2, 2, 2}, new double[] {0, 0, 0}, new double[] {0, 0, 0}));
        Rig rig = BbRig.parse(text);
        assertEquals(2, rig.boneCount(), "the reader's root and the group");
        assertEquals("g", rig.bone(1).name());
        assertEquals(0, rig.bone(1).parent());
        assertTrue(rig.bone(1).pivot().near(new Vec(1, 1, 1), 1e-12));
        assertEquals(6, rig.mesh(1).faceCount(), "the cube's faces hang on the group");
        assertEquals(0, rig.mesh(0).faceCount());
        // The corner (2, 2, 2) turned 90 degrees about Y round (1, 1, 1) lands on (2, 2, 0); (0, 0, 0) on (0, 0, 2).
        List<Vec> vertices = restVertices(rig);
        assertTrue(hasVertex(vertices, new Vec(2, 2, 0), 1e-9), "the far corner turned");
        assertTrue(hasVertex(vertices, new Vec(0, 0, 2), 1e-9), "the near corner turned");
        // In bone space the cube is relative to the pivot and unturned.
        assertTrue(hasVertex(rig.mesh(1).positions(), new Vec(1, 1, 1), 1e-12));
        assertTrue(hasVertex(rig.mesh(1).positions(), new Vec(-1, -1, -1), 1e-12));
    }

    @Test
    void aGroupInsideATurnedGroupTurnsWithinItsParent() {
        String inner = group("h", new double[] {1, 0, 0}, new double[] {0, 0, 90}, "\"c1\"");
        String text = project(group("g", new double[] {0, 0, 0}, new double[] {0, 90, 0}, inner),
                cube("c1", "box", new double[] {1, 0, 0}, new double[] {2, 1, 1}, new double[] {0, 0, 0}, new double[] {0, 0, 0}));
        Rig rig = BbRig.parse(text);
        assertEquals(3, rig.boneCount());
        assertEquals(1, rig.bone(2).parent(), "h hangs from g");
        assertEquals("g/h", rig.bone(2).path());
        // (2, 1, 1): Z 90 about (1, 0, 0) gives (0, 1, 1); then Y 90 about the origin gives (1, 1, 0).
        assertTrue(hasVertex(restVertices(rig), new Vec(1, 1, 0), 1e-9));
    }

    @Test
    void aCubesOwnTurnComesBeforeItsGroups() {
        String text = project(group("g", new double[] {0, 0, 0}, new double[] {0, 90, 0}, "\"c1\""),
                cube("c1", "box", new double[] {0, 0, 0}, new double[] {2, 1, 1}, new double[] {0, 0, 0}, new double[] {0, 0, 90}));
        Rig rig = BbRig.parse(text);
        // (2, 1, 1): the cube's Z 90 about the origin gives (-1, 2, 1); the group's Y 90 gives (1, 2, 1).
        assertTrue(hasVertex(restVertices(rig), new Vec(1, 2, 1), 1e-9));
    }

    @Test
    void aCubeOutsideEveryGroupHangsOnTheRoot() {
        String text = project("\"c1\"", cube("c1", "loose", new double[] {0, 0, 0}, new double[] {1, 1, 1}, new double[] {0, 0, 0}, new double[] {0, 0, 0}));
        Rig rig = BbRig.parse(text);
        assertEquals(1, rig.boneCount());
        assertEquals(6, rig.mesh(0).faceCount());
        assertEquals("loose", rig.mesh(0).faces().get(0).group());
    }

    @Test
    void aFlatCubeLosesOnlyItsEdgeOnFaces() {
        String text = project("\"c1\"", cube("c1", "pane", new double[] {0, 0, 0}, new double[] {2, 2, 0}, new double[] {0, 0, 0}, new double[] {0, 0, 0}));
        Rig rig = BbRig.parse(text);
        assertEquals(2, rig.mesh(0).faceCount(), "north and south remain");
        assertEquals(1, rig.warnings().size());
        assertTrue(rig.warnings().get(0).contains("4 face(s) with no area"), rig.warnings().get(0));
    }

    @Test
    void theFaceStealerReadsWholeAndStandsWhereBlockbenchPutsIt() throws IOException {
        Rig rig = BbRig.parse(Files.readString(FACE_STEALER, StandardCharsets.UTF_8));
        assertEquals(44, rig.boneCount(), "43 groups and the root");
        assertEquals("face_stealer", rig.bone(1).name());
        assertTrue(rig.bone("head").isPresent() && rig.bone("s05_leg_l").isPresent() && rig.bone("tail").isPresent());
        assertEquals(rig.bone("s04").getAsInt(), rig.bone(rig.bone("s05").getAsInt()).parent(), "s05 hangs from s04");
        int faces = 0;
        for (int i = 0; i < rig.boneCount(); i++) {
            faces += rig.mesh(i).faceCount();
        }
        assertEquals(288 * 6, faces, "every cube's six faces, none flat: " + rig.warnings());
        assertTrue(rig.warnings().isEmpty(), rig.warnings().toString());
        assertTrue(rig.texture().isPresent());
        assertEquals(512, rig.textureWidth());
        assertEquals(512, rig.textureHeight());
        // The saved file read independently (Python, group rotations applied innermost first, inflate included).
        Region bounds = rig.bounds(Pose.REST);
        assertTrue(bounds.min().near(new Vec(-38.402, -0.394, -146.431), 2e-3), "min " + bounds.min());
        assertTrue(bounds.max().near(new Vec(38.402, 42.765, 48.502), 2e-3), "max " + bounds.max());
        // A leg's tarsus, turned by its own -37.87 degrees about Z and by its leg group's 10 degrees about Y.
        int leg = rig.bone("s05_leg_l").getAsInt();
        Xform place = rig.place(Pose.REST)[leg];
        boolean found = false;
        for (Face f : rig.mesh(leg).faces()) {
            if (f.group().endsWith("/s05_leg_l_tarsus")) {
                for (Corner c : f.corners()) {
                    if (place.apply(rig.mesh(leg).positions().get(c.position())).near(new Vec(33.306, 2.1723, -53.0031), 2e-3)) {
                        found = true;
                    }
                }
            }
        }
        assertTrue(found, "the tarsus corner stands where the file puts it");
    }
}
