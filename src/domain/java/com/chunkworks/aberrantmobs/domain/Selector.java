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

import java.util.Set;

/**
 * Which faces of a mesh make a part: those whose material is one of
 * {@code materials} (any, if empty), whose group -- or, for a group that is a path of
 * names joined by slashes, any name on it -- is one of {@code groups}
 * (any, if empty), and whose centre lies in {@code region}. A profile names
 * a needle this way -- material {@code needle}, x at most -5 -- because the
 * bundle's meshes carry no groups and the trailer's carry many.
 */
public record Selector(Set<String> materials, Set<String> groups, Region region) {
    public Selector {
        materials = Set.copyOf(materials);
        groups = Set.copyOf(groups);
        if (region == null) {
            region = Region.ALL;
        }
    }

    /** effects: returns the selector of every face with {@code material} */
    public static Selector material(String material) {
        return new Selector(Set.of(material), Set.of(), Region.ALL);
    }

    /** effects: returns the selector of every face in {@code group} */
    public static Selector group(String group) {
        return new Selector(Set.of(), Set.of(group), Region.ALL);
    }

    /** effects: returns this selector narrowed to {@code region} */
    public Selector within(Region r) {
        return new Selector(materials, groups, r);
    }

    /** effects: returns whether {@code face}, whose centre is {@code centre}, is selected */
    /** effects: returns whether the face's group, a path of names joined by slashes, has one of the selector's groups as a component */
    private boolean inGroup(String group) {
        if (group == null) {
            return false;
        }
        if (groups.contains(group)) {
            return true;
        }
        for (String part : group.split("/")) {
            if (groups.contains(part)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Face face, Vec centre) {
        return (materials.isEmpty() || materials.contains(face.material()))
                && (groups.isEmpty() || inGroup(face.group()))
                && region.contains(centre);
    }
}
