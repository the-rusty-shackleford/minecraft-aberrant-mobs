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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. A clip: keys are exact at their ticks and slerped (turns)
 * or lerped (offsets) between; before the first key the first, after the
 * last the last; a bone not named is the identity and unmoved; cues fire
 * at their tick only; keys out of order, past the end, or a cue outside
 * are refused. The animator: nothing playing leaves the pose alone; a
 * playing clip composes its turn after a local turn and after a
 * placement's rotation and adds its offset to the bone's shift, bones it
 * does not name untouched; cues come out once each as the ticks pass; the
 * clip ends at its last tick; a new clip replaces the old at once. The
 * Face-Stealer's clips each name only bones of the file and carry their
 * cues; the coil lifts the head a block and more and rears the front.
 */
final class ClipTest {
    @Test
    void keysAreExactAndSlerpedBetween() {
        Clip c = Clip.builder("t", 10).key("b", 2, 0, 0, 0).key("b", 6, 0, 90, 0).cue(6, "hit").build();
        assertTrue(c.turn("b", 2).near(Quat.IDENTITY, 1e-9));
        assertTrue(c.turn("b", 6).near(Quat.fromEulerXYZDegrees(0, 90, 0), 1e-9));
        assertTrue(c.turn("b", 4).near(Quat.fromEulerXYZDegrees(0, 45, 0), 1e-9), "half way is half the turn");
        assertTrue(c.turn("b", 0).near(Quat.IDENTITY, 1e-9), "before the first key, the first");
        assertTrue(c.turn("b", 10).near(Quat.fromEulerXYZDegrees(0, 90, 0), 1e-9), "after the last, the last");
        assertTrue(c.turn("other", 5).near(Quat.IDENTITY, 1e-12));
        assertEquals(List.of("hit"), c.cuesAt(6));
        assertEquals(List.of(), c.cuesAt(5));
        assertTrue(c.offset("b", 4).equals(Vec.ZERO), "unmoved keys move nothing");
        Clip m = Clip.builder("m", 10).key("b", 2, 0, 0, 0).key("b", 6, 0, 0, 0, 0, 16, 4).build();
        assertTrue(m.offset("b", 4).near(new Vec(0, 8, 2), 1e-12), "half way is half the move");
        assertTrue(m.offset("b", 0).equals(Vec.ZERO) && m.offset("b", 10).near(new Vec(0, 16, 4), 1e-12));
        assertTrue(m.offset("other", 4).equals(Vec.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Clip.builder("t", 10).key("b", 6, 0, 0, 0).key("b", 2, 0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> Clip.builder("t", 10).key("b", 12, 0, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> Clip.builder("t", 10).cue(11, "x").build());
        assertThrows(IllegalArgumentException.class, () -> Clip.builder("t", 0).build());
    }

    @Test
    void theAnimatorLaysTheClipOverThePoseAndReportsItsCues() throws IOException {
        Rig rig = BodyTest.rig();
        Animator a = new Animator();
        Pose base = Pose.REST.withLocal(rig.bone("head").getAsInt(), Quat.fromEulerXYZDegrees(0, 30, 0))
                .withAbsolute(rig.bone("s01").getAsInt(), new Xform(Quat.fromEulerXYZDegrees(0, 45, 0), new Vec(1, 2, 3)));
        assertTrue(a.overlay(rig, base, 0.5) == base, "nothing playing: the pose itself");
        Clip c = Clip.builder("t", 4).key("head", 0, 0, 0, 0).key("head", 4, -40, 0, 0, 0, 8, 0)
                .key("s01", 0, 0, 0, 0).key("s01", 4, -20, 0, 0).cue(1, "first").cue(4, "last").build();
        a.play(c);
        assertTrue(a.busy());
        assertEquals(List.of("first"), a.advance());
        assertEquals(1, a.tick());
        Pose over = a.overlay(rig, base, 0.0);
        int head = rig.bone("head").getAsInt(), s01 = rig.bone("s01").getAsInt();
        assertTrue(over.local(head).near(Quat.fromEulerXYZDegrees(0, 30, 0).times(Quat.fromEulerXYZDegrees(-10, 0, 0)), 1e-9), "the clip's turn after the bone's own");
        assertTrue(over.absolute(s01).rotation().near(Quat.fromEulerXYZDegrees(0, 45, 0).times(Quat.fromEulerXYZDegrees(-5, 0, 0)), 1e-9), "and after a placement's rotation");
        assertTrue(over.absolute(s01).translation().near(new Vec(1, 2, 3), 1e-12), "a placement keeps its place");
        assertTrue(over.shift(head).near(new Vec(0, 2, 0), 1e-12), "a quarter of the way, a quarter of the lift, as a shift");
        assertTrue(over.shift(s01).equals(Vec.ZERO), "no offset, no shift");
        assertTrue(over.local(rig.bone("tail").getAsInt()).near(Quat.IDENTITY, 1e-12), "a bone the clip does not name is untouched");
        assertEquals(List.of(), a.advance());
        assertEquals(List.of(), a.advance());
        assertEquals(List.of("last"), a.advance(), "the last tick's cue as the clip ends");
        assertFalse(a.busy());
        assertNull(a.playing());
        assertTrue(a.overlay(rig, base, 0.5) == base);
        a.play(c);
        a.advance();
        a.play(c);
        assertEquals(0, a.tick(), "a new clip starts over");
    }

    @Test
    void theFaceStealersClipsNameOnlyBonesOfTheFileAndCarryTheirCues() throws IOException {
        Rig rig = BodyTest.rig();
        for (Clip c : FaceStealerClips.LIST) {
            for (String bone : c.bones()) {
                assertTrue(rig.bone(bone).isPresent(), c.name() + " names " + bone + ", not in the file");
            }
            assertTrue(c.ticks() > 0);
        }
        assertEquals(List.of(FaceStealerClips.CUE_CLICK), FaceStealerClips.COIL.cuesAt(5));
        assertEquals(List.of(FaceStealerClips.CUE_HISS), FaceStealerClips.COIL.cuesAt(12));
        assertEquals(List.of(FaceStealerClips.CUE_SCREECH), FaceStealerClips.POUNCE.cuesAt(0));
        assertEquals(List.of(FaceStealerClips.CUE_STRIKE), FaceStealerClips.STRIKE.cuesAt(4));
        assertEquals(List.of(FaceStealerClips.CUE_BITE), FaceStealerClips.BITE.cuesAt(12));
        assertEquals(7, FaceStealerClips.ALL.size());
        // The coil rears the front: s01 turns nose-up (a negative X) by its end, and the head is lifted more than a block.
        Quat s01 = FaceStealerClips.COIL.turn("s01", 15);
        assertTrue(s01.rotate(Vec.Z).y() > 0.3 && s01.rotate(new Vec(0, 0, -1)).y() < -0.3, "the segment's front (+Z) goes up, its tail end down");
        assertTrue(FaceStealerClips.COIL.offset("head", 15).y() > 16, "the head rises: " + FaceStealerClips.COIL.offset("head", 15));
        assertTrue(FaceStealerClips.COIL.offset("s04", 15).y() < FaceStealerClips.COIL.offset("s02", 15).y(), "less so down the body");
        assertTrue(FaceStealerClips.POUNCE.offset("head", 0).near(FaceStealerClips.COIL.offset("head", 15), 1e-12), "the pounce starts where the coil ends");
        assertTrue(FaceStealerClips.POUNCE.offset("head", 4).equals(Vec.ZERO), "and is flat by four");
        assertTrue(FaceStealerClips.DEATH.offset("s05", 40).y() < -8, "the dead body sinks");
    }
}
