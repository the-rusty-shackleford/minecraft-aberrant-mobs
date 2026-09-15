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

import com.chunkworks.aberrantmobs.domain.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a mind from its JSON (as {@link Json} parses it): {@code {"start":
 * mode, "tunables": {name: number}, "modes": {mode: node}}} where a node is
 * one of {@code {"select": [nodes]}}, {@code {"sequence": [nodes]}},
 * {@code {"when": "cond", "then": node}}, {@code {"act": "verb", ...args}},
 * {@code {"enter": "mode"}}, {@code {"timer": "name", "set": ticks}},
 * {@code {"wait": "name", "ticks": n, "then": node}}, {@code {"cooldown":
 * "name", "ticks": n, "then": node}}. Every condition's names must be
 * senses or timers, every entered mode a mode of the tree, every verb one
 * of those given; a mistake is refused naming its path, so a bad pack
 * fails at load, never at first sight.
 */
public final class TreeJson {
    private TreeJson() {}

    /**
     * effects: returns the tree {@code json} spells, its verbs among {@code verbs}<br>
     * throws: {@link IllegalArgumentException} naming the path of the first mistake
     */
    public static Tree parse(Object json, Set<String> verbs) {
        Map<String, Object> root = Json.map(json);
        String start = Json.string(root.get("start"), null);
        if (start == null) {
            throw new IllegalArgumentException("mind.start: a mind starts in a mode");
        }
        Map<String, Object> modesJson = Json.map(root.get("modes"));
        if (modesJson.isEmpty()) {
            throw new IllegalArgumentException("mind.modes: a mind has modes");
        }
        Map<String, Double> tunables = new HashMap<>();
        for (Map.Entry<String, Object> e : Json.map(root.get("tunables")).entrySet()) {
            double v = Json.number(e.getValue(), Double.NaN);
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("mind.tunables." + e.getKey() + ": a number");
            }
            tunables.put(e.getKey(), v);
        }
        Map<String, Node> modes = new HashMap<>();
        for (Map.Entry<String, Object> e : modesJson.entrySet()) {
            modes.put(e.getKey(), node(e.getValue(), "mind.modes." + e.getKey(), modesJson.keySet(), verbs));
        }
        if (!modes.containsKey(start)) {
            throw new IllegalArgumentException("mind.start: " + start + " is not a mode");
        }
        return new Tree(start, modes, tunables);
    }

    private static Node node(Object json, String path, Set<String> modes, Set<String> verbs) {
        Map<String, Object> m = Json.map(json);
        if (m.isEmpty()) {
            throw new IllegalArgumentException(path + ": a node is an object with select, sequence, when, act, enter, timer, wait or cooldown");
        }
        if (m.containsKey("select")) {
            return new Node.Select(children(m.get("select"), path + ".select", modes, verbs));
        }
        if (m.containsKey("sequence")) {
            return new Node.Sequence(children(m.get("sequence"), path + ".sequence", modes, verbs));
        }
        if (m.containsKey("when")) {
            Cond cond;
            try {
                cond = Cond.parse(Json.string(m.get("when"), ""));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ".when: " + e.getMessage());
            }
            for (String name : cond.names()) {
                if (!Senses.readable(name)) {
                    throw new IllegalArgumentException(path + ".when: unknown sense '" + name + "'");
                }
            }
            return new Node.When(cond, then(m, path, modes, verbs));
        }
        if (m.containsKey("act")) {
            String verb = Json.string(m.get("act"), "");
            if (!verbs.contains(verb)) {
                throw new IllegalArgumentException(path + ".act: unknown verb '" + verb + "'");
            }
            Map<String, String> args = new HashMap<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!e.getKey().equals("act")) {
                    args.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            return new Node.Act(new Intent(verb, args));
        }
        if (m.containsKey("enter")) {
            String mode = Json.string(m.get("enter"), "");
            if (!modes.contains(mode)) {
                throw new IllegalArgumentException(path + ".enter: unknown mode '" + mode + "'");
            }
            return new Node.Enter(mode);
        }
        if (m.containsKey("timer")) {
            return new Node.SetTimer(name(m, "timer", path), ticks(m, "set", path));
        }
        if (m.containsKey("wait")) {
            return new Node.Wait(name(m, "wait", path), ticks(m, "ticks", path), then(m, path, modes, verbs));
        }
        if (m.containsKey("cooldown")) {
            return new Node.Cooldown(name(m, "cooldown", path), ticks(m, "ticks", path), then(m, path, modes, verbs));
        }
        throw new IllegalArgumentException(path + ": a node is one of select, sequence, when, act, enter, timer, wait or cooldown, not " + m.keySet());
    }

    private static List<Node> children(Object json, String path, Set<String> modes, Set<String> verbs) {
        List<Object> items = Json.list(json);
        if (items.isEmpty()) {
            throw new IllegalArgumentException(path + ": at least one child");
        }
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            out.add(node(items.get(i), path + "[" + i + "]", modes, verbs));
        }
        return out;
    }

    private static Node then(Map<String, Object> m, String path, Set<String> modes, Set<String> verbs) {
        if (!m.containsKey("then")) {
            throw new IllegalArgumentException(path + ": a then");
        }
        return node(m.get("then"), path + ".then", modes, verbs);
    }

    private static String name(Map<String, Object> m, String key, String path) {
        String name = Json.string(m.get(key), "");
        if (name.isEmpty()) {
            throw new IllegalArgumentException(path + "." + key + ": a timer's name");
        }
        return name;
    }

    private static int ticks(Map<String, Object> m, String key, String path) {
        double t = Json.number(m.get(key), -1);
        if (!(t >= 0) || t != Math.floor(t)) {
            throw new IllegalArgumentException(path + "." + key + ": a whole number of ticks");
        }
        return (int) t;
    }
}
