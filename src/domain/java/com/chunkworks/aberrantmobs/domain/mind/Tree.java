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

import java.util.Map;

/**
 * A creature's whole mind: its modes, each a tree, and the mode it starts
 * in; and the tunables its verbs read. Immutable.
 *
 * <p>RI: start names a mode; every mode has a node; tunables finite.
 * AF: AF(start, modes, tunables) = "the state machine whose states are the
 *     modes, entered at {@code start}, each deciding by its tree".
 */
public record Tree(String start, Map<String, Node> modes, Map<String, Double> tunables) {
    public Tree {
        modes = Map.copyOf(modes);
        tunables = Map.copyOf(tunables);
        if (!modes.containsKey(start)) {
            throw new IllegalArgumentException("the start mode " + start + " is not a mode of the tree");
        }
        for (Double v : tunables.values()) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("a tunable is finite");
            }
        }
    }

    /** effects: returns the tunable named, or {@code fallback} */
    public double tunable(String name, double fallback) {
        return tunables.getOrDefault(name, fallback);
    }
}
