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
 * And no blow, however hard, takes more than the cap: the crack gives a
 * little at a time, so a creature is killed by so many blows at the least,
 * whatever the weapon. Which segment a blow was aimed at is judged from
 * the blow's own geometry ({@link #aimed}, {@link #nearest}), since the
 * segments' boxes overlap along the body. Immutable.
 *
 * <p>RI: {@code segments >= 1}; {@code 0 <= weak < segments};
 *     {@code explosionShare} in [0, 1]; {@code blowCap > 0} (infinite for
 *     no cap).
 * AF: AF(segments, weak, explosionShare, blowCap) = "a body of
 *     {@code segments} armoured segments, the one at index {@code weak}
 *     cracked, that takes {@code explosionShare} of a blast anywhere and
 *     never more than {@code blowCap} from one blow".
 */
public record Carapace(int segments, int weak, double explosionShare, double blowCap) {
    /** Half a blast lands anywhere: TNT is a legitimate miner's answer. */
    public static final double EXPLOSION_SHARE = 0.5;
    /** No cap on a blow. */
    public static final double UNCAPPED = Double.POSITIVE_INFINITY;

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
        if (segments < 1 || weak < 0 || weak >= segments || !(explosionShare >= 0 && explosionShare <= 1) || !(blowCap > 0)) {
            throw new IllegalArgumentException("a carapace of " + segments + " with the weak one at " + weak + ", a blow capped at " + blowCap);
        }
    }

    /**
     * requires: {@code segments >= 1}, {@code blowCap > 0}
     * effects: returns a fresh carapace of {@code segments} whose cracked
     * segment is chosen by {@code roll} from {@code candidates} (indices),
     * or from every segment when there are no candidates, no blow taking
     * more than {@code blowCap}
     */
    public static Carapace fresh(int segments, int[] candidates, long roll, double blowCap) {
        if (candidates == null || candidates.length == 0) {
            return new Carapace(segments, (int) Math.floorMod(roll, segments), EXPLOSION_SHARE, blowCap);
        }
        return new Carapace(segments, candidates[(int) Math.floorMod(roll, candidates.length)], EXPLOSION_SHARE, blowCap);
    }

    /**
     * requires: {@code hit.amount() >= 0}
     * effects: returns what {@code hit} comes to: the whole of it, with a
     * flinch, on the cracked segment; {@code explosionShare} of an
     * explosion anywhere, with a flinch; a clang otherwise -- and never
     * more than {@code blowCap}, however hard the blow
     */
    public Verdict route(Hit hit) {
        if (!(hit.amount() >= 0)) {
            throw new IllegalArgumentException("a blow's amount is not negative");
        }
        if (hit.segment() == weak) {
            return new Verdict(Math.min(hit.amount(), blowCap), hit.amount() > 0);
        }
        if (hit.cause() == Cause.EXPLOSION) {
            return new Verdict(Math.min(hit.amount() * explosionShare, blowCap), hit.amount() > 0);
        }
        return Verdict.CLANG;
    }

    /**
     * requires: {@code dir} not zero
     * effects: returns the index of the segment among {@code centres} whose
     * centre lies nearest the ray from {@code from} along {@code dir} --
     * within {@code reach} of the ray and not behind {@code from} -- or -1
     * when none does: the segment a blow along that ray was aimed at, since
     * the segments' boxes overlap along the body and the box the game's
     * own pick names may be a neighbour's; of segments equally near the
     * ray, the one met first along it
     */
    public static int aimed(Vec from, Vec dir, Vec[] centres, double reach) {
        if (!(dir.length() > 0)) {
            throw new IllegalArgumentException("a ray has a direction");
        }
        Vec d = dir.normalized();
        int best = -1;
        double bestOff = reach;
        double bestAlong = Double.MAX_VALUE;
        for (int k = 0; k < centres.length; k++) {
            Vec to = centres[k].minus(from);
            double along = to.dot(d);
            if (along < 0) {
                continue;
            }
            double off = to.minus(d.times(along)).length();
            if (off < bestOff - 1e-9 || (Math.abs(off - bestOff) <= 1e-9 && along < bestAlong)) {
                bestOff = off;
                bestAlong = along;
                best = k;
            }
        }
        return best;
    }

    /** effects: returns the index of the segment among {@code centres} whose centre is nearest {@code point}, the first of equals; -1 for no segments */
    public static int nearest(Vec point, Vec[] centres) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int k = 0; k < centres.length; k++) {
            double distance = centres[k].minus(point).length();
            if (distance < bestDistance) {
                bestDistance = distance;
                best = k;
            }
        }
        return best;
    }

    /** effects: returns this carapace moulted: the crack moved to the segment {@code roll} picks from {@code candidates}, never the same one when there is a choice */
    public Carapace moulted(int[] candidates, long roll) {
        Carapace next = fresh(segments, candidates, roll, blowCap);
        if (next.weak == weak && (candidates == null ? segments : candidates.length) > 1) {
            next = fresh(segments, candidates, roll + 1, blowCap);
        }
        return next;
    }
}
