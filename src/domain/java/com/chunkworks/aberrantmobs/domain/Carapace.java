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

/**
 * The armour and its one flaw: which segment of the body is cracked, and
 * what a blow on any segment comes to. Damage lands only on the cracked
 * segment; anywhere else it rings off the plating and does nothing, but
 * an explosion, which a miner has to hand, does half wherever it goes.
 * Immutable.
 *
 * <p>RI: {@code segments >= 1}; {@code 0 <= weak < segments};
 *     {@code explosionShare} in [0, 1].
 * AF: AF(segments, weak, explosionShare) = "a body of {@code segments}
 *     armoured segments, the one at index {@code weak} cracked, that takes
 *     {@code explosionShare} of a blast anywhere".
 */
public record Carapace(int segments, int weak, double explosionShare) {
    /** Half a blast lands anywhere: TNT is a legitimate miner's answer. */
    public static final double EXPLOSION_SHARE = 0.5;

    /** What caused a blow, as far as the plating cares. */
    public enum Cause { MELEE, PROJECTILE, EXPLOSION, OTHER }

    /** A blow: on which segment (-1 for no segment: the body's own box), how hard, and how. */
    public record Hit(int segment, double amount, Cause cause) {}

    /**
     * What the plating makes of a blow: the damage the body takes (0 for a
     * clang) and whether it flinches. RI: damage >= 0.
     */
    public record Verdict(double damage, boolean flinch) {
        public static final Verdict CLANG = new Verdict(0.0, false);

        public Verdict {
            if (!(damage >= 0)) {
                throw new IllegalArgumentException("damage is not negative: " + damage);
            }
        }

        public boolean isClang() {
            return damage == 0.0 && !flinch;
        }
    }

    public Carapace {
        if (segments < 1 || weak < 0 || weak >= segments || !(explosionShare >= 0 && explosionShare <= 1)) {
            throw new IllegalArgumentException("a carapace of " + segments + " with the weak one at " + weak);
        }
    }

    /**
     * requires: {@code segments >= 1}
     * effects: returns a fresh carapace of {@code segments} whose cracked
     * segment is chosen by {@code roll} from {@code candidates} (indices),
     * or from every segment when there are no candidates
     */
    public static Carapace fresh(int segments, int[] candidates, long roll) {
        if (candidates == null || candidates.length == 0) {
            return new Carapace(segments, (int) Math.floorMod(roll, segments), EXPLOSION_SHARE);
        }
        return new Carapace(segments, candidates[(int) Math.floorMod(roll, candidates.length)], EXPLOSION_SHARE);
    }

    /**
     * requires: {@code hit.amount() >= 0}
     * effects: returns what {@code hit} comes to: the whole of it, with a
     * flinch, on the cracked segment; {@code explosionShare} of an
     * explosion anywhere, with a flinch; a clang otherwise
     */
    public Verdict route(Hit hit) {
        if (!(hit.amount() >= 0)) {
            throw new IllegalArgumentException("a blow's amount is not negative");
        }
        if (hit.segment() == weak) {
            return new Verdict(hit.amount(), hit.amount() > 0);
        }
        if (hit.cause() == Cause.EXPLOSION) {
            return new Verdict(hit.amount() * explosionShare, hit.amount() > 0);
        }
        return Verdict.CLANG;
    }

    /** effects: returns this carapace moulted: the crack moved to the segment {@code roll} picks from {@code candidates}, never the same one when there is a choice */
    public Carapace moulted(int[] candidates, long roll) {
        Carapace next = fresh(segments, candidates, roll);
        if (next.weak == weak && (candidates == null ? segments : candidates.length) > 1) {
            next = fresh(segments, candidates, roll + 1);
        }
        return next;
    }
}
