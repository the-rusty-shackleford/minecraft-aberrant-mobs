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
import java.util.Map;

/**
 * The Face-Stealer's authored animations, in code, on the bones of nfx's
 * file. Angles are Blockbench's, degrees, about each bone's own pivot: a
 * negative X on a segment rears it (its tail end goes down, its front up,
 * since the segments run tailward along -Z); a Y turn on a forcipule
 * swings the pincer (the left one, at +X, opens outward with -Y, the right
 * with +Y); the head rears with -X too. Offsets are Blockbench's units
 * along the bone's own axes, sixteen to a block: a segment lifted along +Y
 * rises off the surface it stands on, whatever way up that is.
 *
 * <p>The seven: <b>coil</b> (the charge: the front four segments rise into
 * an S -- each lifted and tilted to the slope of the chain there, the
 * head a block and a half up -- the head rears, the pincers spread, over
 * fifteen ticks; the click at five, the hiss at twelve), <b>pounce</b>
 * (the leap: from the coil's crest the body straightens and drops back to
 * the ground in four ticks, the pincers wide; the screech at launch),
 * <b>strike</b> (the dig's beat: the pincers snap shut over six ticks, the
 * blow lands on the closing frame), <b>grab</b> (the pincers close on the
 * target, the head dips), <b>bite</b> (the head rears and lifts, then
 * drives down below its rest), <b>flinch</b> (the body jerks back and
 * settles), <b>death</b> (the body rolls, sinks to the ground and the legs
 * curl in over forty ticks).
 */
public final class FaceStealerClips {
    private FaceStealerClips() {}

    public static final String CUE_CLICK = "click";
    public static final String CUE_HISS = "hiss";
    public static final String CUE_SCREECH = "screech";
    public static final String CUE_STRIKE = "strike";
    public static final String CUE_GRAB = "grab";
    public static final String CUE_BITE = "bite";
    public static final String CUE_CRACK = "crack";

    private static final String HEAD = "head";
    private static final String LEFT = "forcipule_l";
    private static final String RIGHT = "forcipule_r";
    private static final String[] FRONT = {"s01", "s02", "s03", "s04"};
    private static final String[] LEGS_L = {"head_leg_l", "s01_leg_l", "s02_leg_l", "s03_leg_l", "s04_leg_l", "s05_leg_l", "s06_leg_l", "s07_leg_l", "s08_leg_l", "s09_leg_l", "s10_leg_l", "tail_leg1_l", "tail_leg2_l"};
    private static final String[] LEGS_R = {"head_leg_r", "s01_leg_r", "s02_leg_r", "s03_leg_r", "s04_leg_r", "s05_leg_r", "s06_leg_r", "s07_leg_r", "s08_leg_r", "s09_leg_r", "s10_leg_r", "tail_leg1_r", "tail_leg2_r"};

    /** The coil's crest: how high each of the front bones is lifted (units) and how it tilts (degrees) at the top of the charge. */
    private static final String[] CREST_BONES = {HEAD, "s01", "s02", "s03", "s04"};
    private static final double[] CREST_LIFT = {24, 20, 12, 6, 2};
    private static final double[] CREST_TILT = {-30, -18, -28, -22, -12};

    public static final Clip COIL = crest(Clip.builder("coil", 15), 8, 0.6, 15, 1.0)
            .key(LEFT, 0, 0, 0, 0).key(LEFT, 15, 0, -40, 0)
            .key(RIGHT, 0, 0, 0, 0).key(RIGHT, 15, 0, 40, 0)
            .cue(5, CUE_CLICK).cue(12, CUE_HISS)
            .build();

    /** effects: keys the front bones at rest at tick 0, then at {@code f1} of the crest at {@code t1} and {@code f2} of it at {@code t2} */
    private static Clip.Builder crest(Clip.Builder b, int t1, double f1, int t2, double f2) {
        for (int i = 0; i < CREST_BONES.length; i++) {
            b.key(CREST_BONES[i], 0, 0, 0, 0)
                    .key(CREST_BONES[i], t1, CREST_TILT[i] * f1, 0, 0, 0, CREST_LIFT[i] * f1, 0)
                    .key(CREST_BONES[i], t2, CREST_TILT[i] * f2, 0, 0, 0, CREST_LIFT[i] * f2, 0);
        }
        return b;
    }

    public static final Clip POUNCE = uncrest(Clip.builder("pounce", 12), 4)
            .key(HEAD, 12, 0, 0, 0)
            .key(LEFT, 0, 0, -40, 0).key(LEFT, 12, 0, -45, 0)
            .key(RIGHT, 0, 0, 40, 0).key(RIGHT, 12, 0, 45, 0)
            .cue(0, CUE_SCREECH)
            .build();

    /** effects: keys the front bones at the crest at tick 0 and flat by {@code t}, the head thrown a little forward-down on the way */
    private static Clip.Builder uncrest(Clip.Builder b, int t) {
        for (int i = 0; i < CREST_BONES.length; i++) {
            b.key(CREST_BONES[i], 0, CREST_TILT[i], 0, 0, 0, CREST_LIFT[i], 0)
                    .key(CREST_BONES[i], t, i == 0 ? 10 : 0, 0, 0, 0, 0, 0);
        }
        return b;
    }

    public static final Clip STRIKE = Clip.builder("strike", 6)
            .key(LEFT, 0, 0, -35, 0).key(LEFT, 4, 0, 15, 0).key(LEFT, 6, 0, 0, 0)
            .key(RIGHT, 0, 0, 35, 0).key(RIGHT, 4, 0, -15, 0).key(RIGHT, 6, 0, 0, 0)
            .key(HEAD, 0, -8, 0, 0).key(HEAD, 4, 6, 0, 0).key(HEAD, 6, 0, 0, 0)
            .cue(4, CUE_STRIKE)
            .build();

    public static final Clip GRAB = Clip.builder("grab", 8)
            .key(LEFT, 0, 0, -45, 0).key(LEFT, 5, 0, 20, 0).key(LEFT, 8, 0, 18, 0)
            .key(RIGHT, 0, 0, 45, 0).key(RIGHT, 5, 0, -20, 0).key(RIGHT, 8, 0, -18, 0)
            .key(HEAD, 0, 0, 0, 0).key(HEAD, 8, 12, 0, 0)
            .cue(5, CUE_GRAB)
            .build();

    public static final Clip BITE = Clip.builder("bite", 14)
            .key(HEAD, 0, 12, 0, 0).key(HEAD, 8, -35, 0, 0, 0, 8, 0).key(HEAD, 12, 20, 0, 0, 0, -4, 2).key(HEAD, 14, 0, 0, 0)
            .key("s01", 0, 0, 0, 0).key("s01", 8, -10, 0, 0, 0, 5, 0).key("s01", 14, 0, 0, 0)
            .key("s02", 0, 0, 0, 0).key("s02", 8, -10, 0, 0, 0, 2, 0).key("s02", 14, 0, 0, 0)
            .key(LEFT, 0, 0, 18, 0).key(LEFT, 12, 0, 25, 0).key(LEFT, 14, 0, 0, 0)
            .key(RIGHT, 0, 0, -18, 0).key(RIGHT, 12, 0, -25, 0).key(RIGHT, 14, 0, 0, 0)
            .cue(12, CUE_BITE)
            .build();

    public static final Clip FLINCH = Clip.builder("flinch", 20)
            .key(HEAD, 0, 0, 0, 0).key(HEAD, 3, -20, 0, 0).key(HEAD, 20, 0, 0, 0)
            .keys(0, 0, 0, 0, FRONT).keys(3, -8, 0, 0, FRONT).keys(20, 0, 0, 0, FRONT)
            .cue(0, CUE_CRACK).cue(3, CUE_SCREECH)
            .build();

    private static final String[] CHAIN = {HEAD, "s01", "s02", "s03", "s04", "s05", "s06", "s07", "s08", "s09", "s10", "tail"};
    /** How far the dead body sinks toward the ground, units: the axis from twenty-three over the feet to ten. */
    private static final double SINK = 13;

    public static final Clip DEATH = build(Clip.builder("death", 40)
            .key(LEFT, 0, 0, 0, 0).key(LEFT, 40, 0, 30, 0)
            .key(RIGHT, 0, 0, 0, 0).key(RIGHT, 40, 0, -30, 0));

    private static Clip build(Clip.Builder death) {
        for (String c : CHAIN) {
            death.key(c, 0, 0, 0, 0).key(c, 40, c.equals(HEAD) ? 25 : 0, 0, 28, 0, -SINK, 0);
        }
        for (String l : LEGS_L) {
            death.key(l, 0, 0, 0, 0).key(l, 40, 0, 0, 55);
        }
        for (String r : LEGS_R) {
            death.key(r, 0, 0, 0, 0).key(r, 40, 0, 0, -55);
        }
        return death.build();
    }

    public static final Map<String, Clip> ALL = Map.of(
            COIL.name(), COIL, POUNCE.name(), POUNCE, STRIKE.name(), STRIKE, GRAB.name(), GRAB,
            BITE.name(), BITE, FLINCH.name(), FLINCH, DEATH.name(), DEATH);

    public static final List<Clip> LIST = List.of(COIL, POUNCE, STRIKE, GRAB, BITE, FLINCH, DEATH);
}
