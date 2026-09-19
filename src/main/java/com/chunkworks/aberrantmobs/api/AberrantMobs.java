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
package com.chunkworks.aberrantmobs.api;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * The protocol's public names: the namespace, the creature registry, the
 * damage types. A creature mod needs nothing but a datapack entry in
 * {@link #CREATURES} and a Blockbench project in its assets.
 */
public final class AberrantMobs {
    private AberrantMobs() {}

    public static final String NAMESPACE = "aberrantmobs";

    /** The datapack registry of creatures: {@code data/<ns>/aberrantmobs/creature/<name>.json}. */
    public static final ResourceKey<Registry<CreatureProfile>> CREATURES = ResourceKey.createRegistryKey(id("creature"));

    /** The bite: fatal through armour, but a blow like any other to whatever listens for one. */
    public static final ResourceKey<DamageType> DEVOURED = ResourceKey.create(Registries.DAMAGE_TYPE, id("devoured"));

    /** Direct sunlight; separate from fire, to which some creatures are immune. */
    public static final ResourceKey<DamageType> SUNLIGHT = ResourceKey.create(Registries.DAMAGE_TYPE, id("sunlight"));

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /** effects: returns the creature profile {@code id} names in {@code registries}, if the packs carry it */
    public static Optional<Holder.Reference<CreatureProfile>> profile(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookupOrThrow(CREATURES).get(ResourceKey.create(CREATURES, id));
    }
}
