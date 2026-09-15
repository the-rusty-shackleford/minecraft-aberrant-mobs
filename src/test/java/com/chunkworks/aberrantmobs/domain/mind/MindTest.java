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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.aberrantmobs.domain.Json;
import com.chunkworks.aberrantmobs.domain.Vec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Senses: the vocabulary is enforced; absent reads as NaN,
 * false, null. Memory: aging counts running timers down and leaves them
 * at zero; set, clear, points. Nodes: select takes the first decided
 * child and threads memory; sequence runs all and keeps the last intent;
 * when gates; enter changes the mode; timer sets; wait starts, holds,
 * fires once and clears; cooldown fires then holds. Mind: the memory is
 * aged first; an unknown mode falls to the start; an entry is followed
 * once and a second entry kept but not followed; an act before an entry
 * is the tick's intent. JSON: the Face-Stealer's
 * grammar reads; a typo in a sense, an unknown mode, an unknown verb, a
 * missing then, a bad tick count are refused naming the path.
 */
final class MindTest {
    private static Senses seeing(double distance) {
        return Senses.builder().flag("target.seen", true).flag("target.known", true).number("target.distance", distance).point("target.pos", new Vec(1, 2, 3)).build();
    }

    @Test
    void sensesKeepToTheVocabularyAndReadAbsentAsNothing() {
        Senses s = seeing(5);
        assertTrue(s.flag("target.seen") && !s.flag("hurt"));
        assertEquals(5.0, s.number("target.distance"));
        assertTrue(Double.isNaN(s.number("health")));
        assertTrue(s.point("target.pos").near(new Vec(1, 2, 3), 1e-12) && s.point("home") == null);
        assertThrows(IllegalArgumentException.class, () -> Senses.builder().flag("targt.seen", true));
        assertThrows(IllegalArgumentException.class, () -> Senses.builder().number("hurt", 1));
        assertThrows(IllegalArgumentException.class, () -> Senses.builder().point("y", Vec.ZERO));
        assertTrue(Senses.NONE.point("target.pos") == null);
    }

    @Test
    void memoryAgesItsTimersToZeroAndKeepsThem() {
        Memory m = Memory.fresh("roam", 7).withTimer("a", 2).withTimer("b", 0);
        assertEquals(2, m.timer("a"));
        assertEquals(0, m.timer("never"));
        assertTrue(m.hasTimer("b") && !m.hasTimer("never"));
        Memory aged = m.aged().aged().aged();
        assertEquals(0, aged.timer("a"));
        assertTrue(aged.hasTimer("a"), "run out, still set");
        assertFalse(aged.withoutTimer("a").hasTimer("a"));
        assertTrue(m.withPoint("home", new Vec(1, 1, 1)).point("home").near(new Vec(1, 1, 1), 1e-12));
        assertTrue(m.withPoint("home", new Vec(1, 1, 1)).withPoint("home", null).point("home") == null);
        assertEquals("hunt", m.withMode("hunt").mode());
        assertThrows(IllegalArgumentException.class, () -> new Memory("x", Map.of("t", -1), Map.of(), 0));
    }

    @Test
    void theNodesDecideAsTheyAreSpecified() {
        Memory m = Memory.fresh("roam", 1);
        Intent a = new Intent("a", Map.of()), b = new Intent("b", Map.of("x", "1"));
        Node select = new Node.Select(List.of(new Node.When(Cond.parse("hurt"), new Node.Act(a)), new Node.Act(b)));
        assertEquals(b, select.eval(Senses.NONE, m).intent(), "the first decided child: the when is undecided");
        assertEquals(a, select.eval(Senses.builder().flag("hurt", true).build(), m).intent());
        Node.Outcome threaded = new Node.Select(List.of(new Node.SetTimer("t", 9), new Node.Act(a))).eval(Senses.NONE, m);
        assertEquals(9, threaded.memory().timer("t"), "memory threads through an undecided child");
        Node seq = new Node.Sequence(List.of(new Node.Act(a), new Node.SetTimer("t", 3), new Node.Act(b)));
        Node.Outcome so = seq.eval(Senses.NONE, m);
        assertTrue(so.decided() && so.intent().equals(b) && so.memory().timer("t") == 3, "all run, the last intent kept");
        assertFalse(new Node.Sequence(List.of(new Node.SetTimer("t", 1))).eval(Senses.NONE, m).decided(), "a sequence of sets decides nothing");
        Node.Outcome entered = new Node.Enter("hunt").eval(Senses.NONE, m);
        assertTrue(entered.decided() && entered.intent().isNone() && entered.memory().mode().equals("hunt"));
        // Wait: starts, holds, fires once and clears.
        Node wait = new Node.Wait("p", 2, new Node.Act(a));
        Node.Outcome w1 = wait.eval(Senses.NONE, m);
        assertFalse(w1.decided());
        assertEquals(2, w1.memory().timer("p"), "started");
        Node.Outcome w2 = wait.eval(Senses.NONE, w1.memory().aged());
        assertFalse(w2.decided(), "still running");
        Node.Outcome w3 = wait.eval(Senses.NONE, w1.memory().aged().aged());
        assertTrue(w3.decided() && w3.intent().equals(a), "run out: fires");
        assertFalse(w3.memory().hasTimer("p"), "and is cleared to start again");
        // Cooldown: fires then holds while it runs.
        Node cool = new Node.Cooldown("c", 3, new Node.Act(b));
        Node.Outcome c1 = cool.eval(Senses.NONE, m);
        assertTrue(c1.decided() && c1.memory().timer("c") == 3);
        assertFalse(cool.eval(Senses.NONE, c1.memory().aged()).decided());
        assertTrue(cool.eval(Senses.NONE, c1.memory().aged().aged().aged()).decided(), "run out: fires again");
    }

    @Test
    void theMindAgesFollowsOneEntryAndFallsToTheStart() {
        Intent roam = new Intent("wander", Map.of()), hunt = new Intent("chase", Map.of());
        Tree tree = new Tree("roam", Map.of(
                "roam", new Node.Select(List.of(new Node.When(Cond.parse("target.seen"), new Node.Enter("hunt")), new Node.Act(roam))),
                "hunt", new Node.Select(List.of(new Node.When(Cond.parse("!target.seen"), new Node.Enter("roam")), new Node.When(Cond.parse("timer.t > 0"), new Node.Act(roam)), new Node.Act(hunt))),
                "loop", new Node.Enter("loop2"), "loop2", new Node.Enter("loop")), Map.of("sight", 32.0));
        Mind.Decision d = Mind.tick(tree, Senses.NONE, Memory.fresh("roam", 1));
        assertEquals(roam, d.intent());
        assertEquals("roam", d.memory().mode());
        d = Mind.tick(tree, seeing(5), d.memory());
        assertEquals("hunt", d.memory().mode(), "entered");
        assertEquals(hunt, d.intent(), "and the new mode decided this same tick");
        d = Mind.tick(tree, Senses.NONE, d.memory().withTimer("t", 1));
        assertEquals("roam", d.memory().mode(), "back out");
        assertEquals(roam, d.intent());
        assertEquals(0, d.memory().timer("t"), "the memory was aged before deciding");
        d = Mind.tick(tree, Senses.NONE, Memory.fresh("nowhere", 1));
        assertEquals("roam", d.memory().mode(), "an unknown mode falls to the start");
        d = Mind.tick(tree, Senses.NONE, Memory.fresh("loop", 1));
        assertEquals("loop", d.memory().mode(), "an entry is followed once; the second is kept, not followed further");
        assertTrue(d.intent().isNone());
        // An act before an entry is the tick's intent; the entered mode's own choice waits for the next tick.
        Intent release = new Intent("release", Map.of());
        Tree leaving = new Tree("hunt", Map.of(
                "hunt", new Node.Sequence(List.of(new Node.Act(release), new Node.SetTimer("no_bite", 600), new Node.Enter("flee"))),
                "flee", new Node.Act(new Intent("flee", Map.of()))), Map.of());
        Mind.Decision left = Mind.tick(leaving, Senses.NONE, Memory.fresh("hunt", 1));
        assertEquals(release, left.intent(), "the release is not lost to the flight");
        assertEquals("flee", left.memory().mode());
        assertEquals(600, left.memory().timer("no_bite"));
        assertEquals("flee", Mind.tick(leaving, Senses.NONE, left.memory()).intent().verb(), "and the flight follows");
        assertEquals(32.0, tree.tunable("sight", 0), 1e-12);
        assertEquals(1.0, tree.tunable("nope", 1.0), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Tree("x", Map.of("roam", new Node.Act(roam)), Map.of()));
    }

    private static final String JSON = """
            {"start": "roam", "tunables": {"sight": 32},
             "modes": {
               "roam": {"select": [
                 {"when": "hurt", "then": {"enter": "hunt"}},
                 {"when": "heard.any && heard.error < 100", "then": {"act": "approach", "target": "heard.bearing", "quiet": true}},
                 {"act": "wander"}]},
               "hunt": {"select": [
                 {"when": "grab.held", "then": {"cooldown": "bite", "ticks": 30, "then": {"act": "bite"}}},
                 {"when": "!target.known", "then": {"wait": "patience", "ticks": 200, "then": {"enter": "roam"}}},
                 {"when": "target.distance < 9 && timer.pounced == 0", "then": {"sequence": [{"timer": "pounced", "set": 80}, {"act": "pounce"}]}},
                 {"act": "chase"}]}}}
            """;
    private static final Set<String> VERBS = Set.of("wander", "approach", "chase", "bite", "pounce");

    @Test
    void theJsonGrammarReadsAndMistakesNameTheirPath() {
        Tree t = TreeJson.parse(Json.parse(JSON), VERBS);
        assertEquals("roam", t.start());
        assertEquals(Set.of("roam", "hunt"), t.modes().keySet());
        Mind.Decision d = Mind.tick(t, Senses.NONE, Memory.fresh("roam", 1));
        assertEquals("wander", d.intent().verb());
        Senses heard = Senses.builder().flag("heard.any", true).number("heard.error", 40).build();
        d = Mind.tick(t, heard, d.memory());
        assertEquals("approach", d.intent().verb());
        assertEquals("heard.bearing", d.intent().arg("target", null));
        assertEquals("true", d.intent().arg("quiet", null), "arguments are strings as written");
        d = Mind.tick(t, Senses.builder().flag("hurt", true).flag("target.known", true).number("target.distance", 5).build(), d.memory());
        assertEquals("hunt", d.memory().mode());
        assertEquals("pounce", d.intent().verb());
        assertEquals(80, d.memory().timer("pounced"));
        d = Mind.tick(t, Senses.builder().flag("target.known", true).number("target.distance", 5).build(), d.memory());
        assertEquals("chase", d.intent().verb(), "pounced, on cooldown");
        assertThrows(IllegalArgumentException.class, () -> TreeJson.parse(Json.parse("{\"start\": \"x\", \"modes\": {\"roam\": {\"act\": \"wander\"}}}"), VERBS));
        for (String bad : List.of(
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"when\": \"targt.seen\", \"then\": {\"act\": \"wander\"}}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"enter\": \"nowhere\"}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"act\": \"fly\"}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"when\": \"hurt\"}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"timer\": \"t\", \"set\": -3}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"select\": []}}}",
                "{\"start\": \"roam\", \"modes\": {\"roam\": {\"jump\": 1}}}")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TreeJson.parse(Json.parse(bad), VERBS), bad);
            assertTrue(e.getMessage().startsWith("mind."), e.getMessage());
        }
    }
}
