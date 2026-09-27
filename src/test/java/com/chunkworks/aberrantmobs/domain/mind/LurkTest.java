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
package com.chunkworks.aberrantmobs.domain.mind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.aberrantmobs.domain.Json;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The shipped Face-Stealer's lurk: prey that leaves the band is not chased
 * into it and not forgotten; the creature takes a post beside where they
 * left and waits for them to come back through.
 *
 * Partitions: entered from hunt and from stalk; prey back inside the band
 * near (grabbed), far (stalked again), staring (hunted); prey no longer
 * known (waited for, then left); hurt hard at the post (flees); the new
 * senses are in the vocabulary and a slip is not.
 */
final class LurkTest {
    private static final Path SHIPPED =
            Path.of("src/main/resources/data/aberrantmobs/aberrantmobs/creature/face_stealer.json");
    private static final Set<String> VERBS =
            Set.of("hold", "wander", "approach", "chase", "flee", "dig", "climb", "pounce", "grab", "release", "bite");

    private static Tree shipped() throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(SHIPPED));
        return TreeJson.parse(root.get("mind"), VERBS);
    }

    /** Prey known and this far off, inside the band or not, with a post to take. */
    private static Senses.Builder known(boolean inBand, double distance) {
        return Senses.builder()
                .flag("target.known", true)
                .flag("target.in_band", inBand)
                .number("target.distance", distance)
                .point("target.last_pos", new Vec(0, -40, 0))
                .point("ambush.post", new Vec(3, -20, 0));
    }

    @Test
    void huntedPreyThatLeavesTheBandIsLurkedFor() throws IOException {
        Tree t = shipped();
        Mind.Decision d = Mind.tick(t, known(false, 12).build(), Memory.fresh("hunt", 1));
        assertEquals("lurk", d.memory().mode());
        assertEquals("approach", d.intent().verb());
        assertEquals("ambush.post", d.intent().arg("target", null));
        assertEquals("true", d.intent().arg("quiet", null));
        assertEquals("true", d.intent().arg("dig", null));
        assertTrue(d.memory().timer("ambush") >= 5999 && d.memory().timer("ambush") <= 6000, "the ambush clock is set on entry: " + d.memory().timer("ambush"));
    }

    @Test
    void stalkedPreyThatLeavesTheBandIsLurkedFor() throws IOException {
        Mind.Decision d = Mind.tick(shipped(), known(false, 30).build(), Memory.fresh("stalk", 1));
        assertEquals("lurk", d.memory().mode());
        assertEquals("approach", d.intent().verb());
        assertEquals("ambush.post", d.intent().arg("target", null));
    }

    @Test
    void preyBackInsideTheBandAndCloseIsGrabbedFromThePost() throws IOException {
        Mind.Decision d = Mind.tick(shipped(), known(true, 4).build(), Memory.fresh("lurk", 1));
        assertEquals("lurk", d.memory().mode());
        assertEquals("grab", d.intent().verb());
    }

    @Test
    void preyBackInsideTheBandButFarIsStalkedAgain() throws IOException {
        Mind.Decision d = Mind.tick(shipped(), known(true, 20).build(), Memory.fresh("lurk", 1));
        assertEquals("stalk", d.memory().mode());
    }

    @Test
    void aStareFromInsideTheBandStartsTheHunt() throws IOException {
        Mind.Decision d = Mind.tick(shipped(), known(true, 20).flag("target.eye_contact", true).build(), Memory.fresh("lurk", 1));
        assertEquals("hunt", d.memory().mode());
    }

    @Test
    void preyOutOfTheBandKeepsItAtThePostWhileKnown() throws IOException {
        Tree t = shipped();
        Memory m = Memory.fresh("lurk", 1).withTimer("ambush", 6000);
        for (int i = 0; i < 7000; i++) {
            Mind.Decision d = Mind.tick(t, known(false, 15).build(), m);
            m = d.memory();
            assertEquals("lurk", m.mode(), "tick " + i);
            assertEquals("approach", d.intent().verb(), "tick " + i);
        }
    }

    @Test
    void preyNoLongerKnownIsWaitedForThenLeftBehind() throws IOException {
        Tree t = shipped();
        Memory m = Memory.fresh("lurk", 1).withTimer("ambush", 6000);
        int ticks = 0;
        while (m.mode().equals("lurk") && ticks < 7000) {
            m = Mind.tick(t, Senses.NONE, m).memory();
            ticks++;
        }
        assertEquals("roam", m.mode());
        assertTrue(ticks >= 5990 && ticks <= 6001, "five minutes of patience, not less: " + ticks);
    }

    @Test
    void hurtHardAtThePostItFlees() throws IOException {
        Mind.Decision d = Mind.tick(shipped(), known(false, 3).flag("hurt_hard", true).build(), Memory.fresh("lurk", 1));
        assertEquals("flee", d.memory().mode());
        assertEquals(40, d.memory().timer("fled"), 1);
    }

    @Test
    void theVocabularyHasTheNewSensesAndNotASlip() {
        Senses s = Senses.builder()
                .flag("target.in_band", true)
                .point("target.last_in_band", new Vec(1, -20, 1))
                .point("ambush.post", new Vec(4, -20, 1))
                .build();
        assertTrue(s.flag("target.in_band"));
        assertTrue(s.point("ambush.post").near(new Vec(4, -20, 1), 1e-12));
        assertThrows(IllegalArgumentException.class, () -> Senses.builder().flag("target.inband", true));
        assertThrows(IllegalArgumentException.class, () -> Senses.builder().point("ambush.pos", Vec.ZERO));
    }
}
