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

import java.util.List;

/**
 * One polygon of a mesh: its material and group names (the two ways a
 * part is picked out of a mesh), its corners in order, and its unit normal.
 *
 * <p>RI: material non-empty; at least three corners; |normal| = 1.
 */
public record Face(String material, String group, List<Corner> corners, Vec normal) {
    public Face {
        if (material == null || material.isEmpty()) {
            throw new IllegalArgumentException("a face has a material");
        }
        if (group == null) {
            group = "";
        }
        corners = List.copyOf(corners);
        if (corners.size() < 3) {
            throw new IllegalArgumentException("a face has at least three corners");
        }
        if (Math.abs(normal.length() - 1.0) > 1e-6) {
            throw new IllegalArgumentException("a face normal is unit: " + normal);
        }
    }

    /** effects: returns this face with {@code normal} */
    public Face withNormal(Vec n) {
        return new Face(material, group, corners, n);
    }

    /** effects: returns this face with its corners reversed and its normal flipped: the other side */
    public Face flipped() {
        return new Face(material, group, corners.reversed(), normal.times(-1));
    }
}
