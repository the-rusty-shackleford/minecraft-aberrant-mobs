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
 * The path a head has taken: a ring of its last positions with the up of
 * the surface it clung to at each, and the arc length along them, so that
 * any point a given distance behind the head can be found and the body
 * laid along it. Mutable, and owned by one creature, which never hands it
 * out; every sample it returns is a fresh value.
 *
 * <p>A push closer than {@link #MIN_STEP} to the newest sample replaces it
 * rather than adding, so a creature standing still does not fill the ring
 * with one point. Behind the oldest sample the path is extended straight
 * on, so a freshly seeded trail is a straight body.
 *
 * <p>RI: 2 <= count <= capacity; along the ring from the oldest to the
 *     newest the arc length s never decreases and any two consecutive
 *     samples are distinct; every up is a unit vector.
 * AF: AF(ring) = "the polyline through the samples oldest to newest, the
 *     newest being where the head is now, with an up interpolated along
 *     it".
 */
public final class Trail {
    /** A head closer than this to the newest sample moves it instead of adding one, blocks. */
    public static final double MIN_STEP = 0.05;

    /** A point on the path: where it is, the direction of travel there (unit, toward the head), the surface's up (unit). */
    public record Sample(Vec pos, Vec forward, Vec up) {}

    private final int capacity;
    private final double[] x, y, z, ux, uy, uz, s;
    private int newest;
    private int count;

    private Trail(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("a trail keeps at least two samples: " + capacity);
        }
        this.capacity = capacity;
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        ux = new double[capacity];
        uy = new double[capacity];
        uz = new double[capacity];
        s = new double[capacity];
        newest = -1;
        count = 0;
    }

    /**
     * requires: {@code length > 0}, {@code forward} and {@code up} non-zero
     * effects: returns a trail of {@code capacity} samples that runs straight
     * from {@code length} blocks behind {@code pos} to {@code pos}, the
     * head having come along {@code forward} on a surface whose up is
     * {@code up}
     */
    public static Trail seeded(Vec pos, Vec forward, Vec up, double length, int capacity) {
        if (!(length > 0)) {
            throw new IllegalArgumentException("a seed has length: " + length);
        }
        Trail t = new Trail(capacity);
        Vec f = forward.normalized();
        Vec u = up.normalized();
        t.add(pos.minus(f.times(length)), u, 0.0);
        t.add(pos, u, length);
        return t;
    }

    private void add(Vec p, Vec u, double arc) {
        newest = (newest + 1) % capacity;
        x[newest] = p.x();
        y[newest] = p.y();
        z[newest] = p.z();
        ux[newest] = u.x();
        uy[newest] = u.y();
        uz[newest] = u.z();
        s[newest] = arc;
        if (count < capacity) {
            count++;
        }
        if (count >= 2) {
            checkRep();   // a seed's first sample alone is not yet a trail
        }
    }

    private int index(int behind) {
        return Math.floorMod(newest - behind, capacity);
    }

    private Vec posAt(int i) {
        return new Vec(x[i], y[i], z[i]);
    }

    private Vec upAt(int i) {
        return new Vec(ux[i], uy[i], uz[i]);
    }

    /**
     * requires: {@code up} non-zero
     * effects: records that the head is now at {@code pos} on a surface with
     * up {@code up}: a new sample when it moved at least {@link #MIN_STEP}
     * from the newest, else the newest sample moved there
     */
    public void push(Vec pos, Vec up) {
        Vec u = up.normalized();
        Vec last = posAt(newest);
        double d = pos.minus(last).length();
        if (d < MIN_STEP) {
            // Move the newest, keeping the arc consistent with the one before it.
            int prev = index(1);
            double fromPrev = pos.minus(posAt(prev)).length();
            if (fromPrev < 1e-9) {
                return;   // back on the previous sample: nothing to say
            }
            x[newest] = pos.x();
            y[newest] = pos.y();
            z[newest] = pos.z();
            ux[newest] = u.x();
            uy[newest] = u.y();
            uz[newest] = u.z();
            s[newest] = s[prev] + fromPrev;
            checkRep();
            return;
        }
        add(pos, u, s[newest] + d);
    }

    /** effects: returns how far the head is now from the oldest sample along the path, blocks */
    public double length() {
        return s[newest] - s[index(count - 1)];
    }

    public int size() {
        return count;
    }

    /**
     * requires: {@code distanceBehind >= 0}
     * effects: returns the point {@code distanceBehind} blocks back along
     * the path from the head, its direction of travel and its up; beyond
     * the oldest sample the path continues straight
     */
    public Sample at(double distanceBehind) {
        if (!(distanceBehind >= 0)) {
            throw new IllegalArgumentException("a distance behind is not negative: " + distanceBehind);
        }
        double target = s[newest] - distanceBehind;
        int oldest = index(count - 1);
        if (target <= s[oldest]) {
            int next = index(count - 2);
            Vec dir = posAt(next).minus(posAt(oldest)).normalized();
            return new Sample(posAt(oldest).minus(dir.times(s[oldest] - target)), dir, upAt(oldest));
        }
        // Walk back to the segment [i, i-1 behind] that holds target.
        int behind = 0;
        while (behind + 1 < count && s[index(behind + 1)] > target) {
            behind++;
        }
        int a = index(behind + 1);   // the older end
        int b = index(behind);       // the newer end
        double span = s[b] - s[a];
        double t = span < 1e-12 ? 1.0 : (target - s[a]) / span;
        Vec pa = posAt(a), pb = posAt(b);
        Vec pos = pa.plus(pb.minus(pa).times(t));
        Vec dir = pb.minus(pa).normalized();
        Vec up = upAt(a).plus(upAt(b).minus(upAt(a)).times(t));
        up = up.length() < 1e-9 ? upAt(b) : up.normalized();
        return new Sample(pos, dir, up);
    }

    private void checkRep() {
        assert count >= 2 && count <= capacity;
        for (int k = 0; k + 1 < count; k++) {
            int newer = index(k), older = index(k + 1);
            assert s[newer] > s[older] : "arc must increase: " + s[older] + " -> " + s[newer];
        }
    }
}
