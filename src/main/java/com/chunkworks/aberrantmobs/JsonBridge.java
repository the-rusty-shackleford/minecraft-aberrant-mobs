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
package com.chunkworks.aberrantmobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gson's JSON as the domain's plain values ({@link
 * com.chunkworks.aberrantmobs.domain.Json}'s shape: maps, lists, doubles,
 * strings, booleans, null), so the tree's grammar lives once, in the
 * tested layer, and a datapack's JSON reaches it unchanged.
 */
public final class JsonBridge {
    private JsonBridge() {}

    /** effects: returns {@code json} as plain values, objects keeping their order */
    public static Object plain(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : ((JsonObject) json).entrySet()) {
                out.put(e.getKey(), plain(e.getValue()));
            }
            return out;
        }
        if (json.isJsonArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonElement e : (JsonArray) json) {
                out.add(plain(e));
            }
            return out;
        }
        JsonPrimitive p = json.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            return p.getAsDouble();
        }
        return p.getAsString();
    }
}
