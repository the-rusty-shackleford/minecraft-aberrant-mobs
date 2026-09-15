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

import com.chunkworks.aberrantmobs.domain.LegGait;
import com.chunkworks.aberrantmobs.domain.Undulation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * What a creature is, as a datapack says it: the project that draws it and
 * the scale it is drawn at, the bones that make its body and how that body
 * moves, the box the world collides with, and its stats. The mind, the
 * habitat and the loot join in later phases; every field is checked in its
 * compact constructor, so a bad file is refused at load naming the field,
 * never at first sight.
 *
 * <p>Model units are Blockbench's; {@code scale} turns them into blocks
 * (a sixteenth for a model drawn at the game's pixel).
 */
public record CreatureProfile(ResourceLocation model, double scale, RigSpec rig, Body body, Stats stats) {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(CreatureProfile::model),
            Codec.DOUBLE.optionalFieldOf("scale", 1.0 / 16.0).forGetter(CreatureProfile::scale),
            RigSpec.CODEC.fieldOf("rig").forGetter(CreatureProfile::rig),
            Body.CODEC.fieldOf("body").forGetter(CreatureProfile::body),
            Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT).forGetter(CreatureProfile::stats)
    ).apply(i, CreatureProfile::new));

    public CreatureProfile {
        if (!(scale > 0.0) || !Double.isFinite(scale)) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }
    }

    /**
     * The bones of the body by name: the head, the chain of segments behind
     * it tail-ward, the leg pairs by the prefix their left and right bones
     * share, and the writhe and the gait the body moves with.
     */
    public record RigSpec(String head, List<String> chain, List<String> legs, String left, String right, Undulation undulation, LegGait gait) {
        private static final Codec<Undulation> UNDULATION = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("amplitude", Undulation.FACE_STEALER.amplitudeMoving()).forGetter(Undulation::amplitudeMoving),
                Codec.DOUBLE.optionalFieldOf("idle_amplitude", Undulation.FACE_STEALER.amplitudeRest()).forGetter(Undulation::amplitudeRest),
                Codec.DOUBLE.optionalFieldOf("wavelength", Undulation.FACE_STEALER.wavelength()).forGetter(Undulation::wavelength),
                Codec.DOUBLE.optionalFieldOf("speed_ref", Undulation.FACE_STEALER.speedRef()).forGetter(Undulation::speedRef),
                Codec.DOUBLE.optionalFieldOf("idle_speed", Undulation.FACE_STEALER.restSpeed()).forGetter(Undulation::restSpeed),
                Codec.DOUBLE.optionalFieldOf("vertical", Undulation.FACE_STEALER.verticalRatio()).forGetter(Undulation::verticalRatio)
        ).apply(i, Undulation::new));
        private static final Codec<LegGait> GAIT = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("stride_deg", LegGait.FACE_STEALER.strideDeg()).forGetter(LegGait::strideDeg),
                Codec.DOUBLE.optionalFieldOf("lift_deg", LegGait.FACE_STEALER.liftDeg()).forGetter(LegGait::liftDeg),
                Codec.DOUBLE.optionalFieldOf("cycle_blocks", LegGait.FACE_STEALER.cycleBlocks()).forGetter(LegGait::cycleBlocks),
                Codec.DOUBLE.optionalFieldOf("wave", LegGait.FACE_STEALER.waveRad()).forGetter(LegGait::waveRad),
                Codec.DOUBLE.optionalFieldOf("speed_ref", LegGait.FACE_STEALER.speedRef()).forGetter(LegGait::speedRef)
        ).apply(i, LegGait::new));
        public static final Codec<RigSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("head").forGetter(RigSpec::head),
                Codec.STRING.listOf().fieldOf("chain").forGetter(RigSpec::chain),
                Codec.STRING.listOf().optionalFieldOf("legs", List.of()).forGetter(RigSpec::legs),
                Codec.STRING.optionalFieldOf("left", "_l").forGetter(RigSpec::left),
                Codec.STRING.optionalFieldOf("right", "_r").forGetter(RigSpec::right),
                UNDULATION.optionalFieldOf("undulation", Undulation.FACE_STEALER).forGetter(RigSpec::undulation),
                GAIT.optionalFieldOf("gait", LegGait.FACE_STEALER).forGetter(RigSpec::gait)
        ).apply(i, RigSpec::new));

        public RigSpec {
            if (head == null || head.isEmpty()) {
                throw new IllegalArgumentException("the rig names its head bone");
            }
            chain = List.copyOf(chain);
            legs = List.copyOf(legs);
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("the chain has at least one segment");
            }
        }
    }

    /**
     * The boxes the world meets, blocks: the head's, a square footprint
     * {@code width} wide and {@code height} tall with the eye at
     * {@code eye_height}, and each chain segment's ({@code segment}, the
     * head's by default); and the weak spot: which chain bones may carry
     * the crack, and which cubes of the cracked segment glow (a glob on
     * the cube's name, {@code *} for anything).
     */
    public record Body(double width, double height, double eyeHeight, Segment segment, WeakSpot weakSpot) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("width").forGetter(Body::width),
                Codec.DOUBLE.fieldOf("height").forGetter(Body::height),
                Codec.DOUBLE.optionalFieldOf("eye_height", -1.0).forGetter(Body::eyeHeight),
                Segment.CODEC.optionalFieldOf("segment", new Segment(-1.0, -1.0)).forGetter(Body::segment),
                WeakSpot.CODEC.optionalFieldOf("weak_spot", WeakSpot.NONE).forGetter(Body::weakSpot)
        ).apply(i, Body::new));

        public Body {
            if (!(width > 0.0) || !(height > 0.0)) {
                throw new IllegalArgumentException("a body has a positive width and height: " + width + " x " + height);
            }
            if (eyeHeight < 0.0) {
                eyeHeight = height * 0.85;
            }
            if (segment.width() < 0.0) {
                segment = new Segment(width, height);
            }
        }
    }

    /** A chain segment's box, blocks; negative means the head's. */
    public record Segment(double width, double height) {
        public static final Codec<Segment> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("width").forGetter(Segment::width),
                Codec.DOUBLE.fieldOf("height").forGetter(Segment::height)
        ).apply(i, Segment::new));

        public Segment {
            if ((width >= 0.0) != (height >= 0.0) || width == 0.0 || height == 0.0) {
                throw new IllegalArgumentException("a segment box has a positive width and height, or neither: " + width + " x " + height);
            }
        }
    }

    /** Where the crack may be (chain bone names; none means anywhere) and what glows there. */
    public record WeakSpot(List<String> candidates, String glow) {
        public static final WeakSpot NONE = new WeakSpot(List.of(), "");
        public static final Codec<WeakSpot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("candidates", List.of()).forGetter(WeakSpot::candidates),
                Codec.STRING.optionalFieldOf("glow", "").forGetter(WeakSpot::glow)
        ).apply(i, WeakSpot::new));

        public WeakSpot {
            candidates = List.copyOf(candidates);
        }

        /** effects: returns whether the cube named {@code name} glows: the glob matches it, {@code *} standing for any run of characters */
        public boolean glows(String name) {
            if (glow.isEmpty()) {
                return false;
            }
            return name.matches(java.util.regex.Pattern.quote(glow).replace("*", "\\E.*\\Q"));
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
