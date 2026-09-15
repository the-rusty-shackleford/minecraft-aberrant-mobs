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
 * What a creature means to do this tick: a verb by name and its
 * arguments as the tree wrote them (strings; a verb reads what it needs).
 * Immutable. RI: verb non-null; args copied.
 */
public record Intent(String verb, Map<String, String> args) {
    /** Nothing to do. */
    public static final Intent NONE = new Intent("none", Map.of());

    public Intent {
        if (verb == null) {
            throw new IllegalArgumentException("an intent has a verb");
        }
        args = Map.copyOf(args);
    }

    /** effects: returns the argument named, or {@code fallback} */
    public String arg(String name, String fallback) {
        return args.getOrDefault(name, fallback);
    }

    public boolean isNone() {
        return verb.equals(NONE.verb());
    }
}
