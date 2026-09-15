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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * What a creature is, as a datapack says it: the project that draws it and
 * the scale it is drawn at, the box the world collides with, and its stats.
 * The mind, the rig's names, the habitat and the loot join in later phases;
 * every field is checked in its compact constructor, so a bad file is
 * refused at load naming the field, never at first sight.
 *
 * <p>Model units are Blockbench's; {@code scale} turns them into blocks
 * (a sixteenth for a model drawn at the game's pixel).
 */
public record CreatureProfile(ResourceLocation model, double scale, Body body, Stats stats) {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(CreatureProfile::model),
            Codec.DOUBLE.optionalFieldOf("scale", 1.0 / 16.0).forGetter(CreatureProfile::scale),
            Body.CODEC.fieldOf("body").forGetter(CreatureProfile::body),
            Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT).forGetter(CreatureProfile::stats)
    ).apply(i, CreatureProfile::new));

    public CreatureProfile {
        if (!(scale > 0.0) || !Double.isFinite(scale)) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }
    }

    /** The box the world collides with, blocks: a square footprint {@code width} wide, {@code height} tall; the eye at {@code eye_height}. */
    public record Body(double width, double height, double eyeHeight) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("width").forGetter(Body::width),
                Codec.DOUBLE.fieldOf("height").forGetter(Body::height),
                Codec.DOUBLE.optionalFieldOf("eye_height", -1.0).forGetter(Body::eyeHeight)
        ).apply(i, Body::new));

        public Body {
            if (!(width > 0.0) || !(height > 0.0)) {
                throw new IllegalArgumentException("a body has a positive width and height: " + width + " x " + height);
            }
            if (eyeHeight < 0.0) {
                eyeHeight = height * 0.85;
            }
        }
    }

    /** What the creature is made of: its health, and how fast it goes. */
    public record Stats(double health, double speed) {
        public static final Stats DEFAULT = new Stats(20.0, 0.3);
        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("health", 20.0).forGetter(Stats::health),
                Codec.DOUBLE.optionalFieldOf("speed", 0.3).forGetter(Stats::speed)
        ).apply(i, Stats::new));

        public Stats {
            if (!(health > 0.0) || !(speed >= 0.0)) {
                throw new IllegalArgumentException("health is positive and speed not negative: " + health + ", " + speed);
            }
        }
    }
}
