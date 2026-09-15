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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A way through rock and air for a body ({@link Crawl.Rules}): A* over
 * cells, six-connected, air within the body's hold of a face cheap (a
 * face within {@link Crawl.Rules#hold} cells along an axis: where the
 * head's axis can ride, so a bore wider than three is planned down its
 * middle and not along its walls), air with none dearer, rock at the cost
 * of digging it, hard cells and fluid never -- nor rock within the body's
 * margin of them (the cells its bore reaches out from the way,
 * {@link Crawl.Rules#margin}), so the bore that follows the way never
 * breaches them. Bounded by a budget of expansions, so a hopeless search
 * ends. Pure.
 *
 * <p>Three things keep a plan cheap. The heuristic knows how deep in rock
 * the target lies ({@link #rockDepth}): every way in must cross that many
 * cells that are not air, each at the rock cost at least, so the open air
 * about a buried target is not flooded before the search commits to the
 * rock (with the plain taxicab bound at the air cost, a hunt to a point
 * ten blocks into a hill spent eight to sixteen thousand expansions on the
 * field around it; with this, five hundred to two thousand). Every cell
 * is read from the world once ({@link Table}), where the search asks
 * after each many times over -- as the neighbour of six, and among the
 * twenty-seven about every rock beside it -- and the read of the level is
 * the dear part of a plan. And the search keeps its state in that same
 * table and a heap of primitives, so an expansion is a few probes and
 * allocates nothing.
 */
public final class Burrow {
    private Burrow() {}

    /** What a cell costs to pass: air by a face, air with none, rock. RI: all positive. */
    public record Costs(double air, double unsupported, double rock) {
        public Costs {
            if (!(air > 0) || !(unsupported > 0) || !(rock > 0)) {
                throw new IllegalArgumentException("costs are positive");
            }
        }
    }

    /** Stalking digs dearly and keeps to the open; hunting digs straight for it. */
    public static final Costs STALK = new Costs(1, 4, 14);
    public static final Costs HUNT = new Costs(1, 4, 5);
    /** Expansions a plan may spend. */
    public static final int BUDGET = 4000;
    /** The heuristic looks this far into the rock about the target for the nearest air, cells. */
    static final int DEPTH_LOOK = 8;

    /** The six face neighbours: +X, -X, +Y, -Y, +Z, -Z. */
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    /**
     * requires: {@code budget > 0}
     * effects: returns the cheapest path of cells from {@code from} to
     * {@code to} inclusive under {@code costs} for a body of {@code rules}
     * (the start cell costs nothing whatever it is): air with a face
     * within the body's hold at the air cost, air with none at the
     * unsupported cost, rock at the rock cost, and every rock cell of it
     * the body's margin or more, in every direction, from anything hard
     * or wet; or nothing when none exists or the search spends more than
     * {@code budget} expansions
     */
    public static Optional<List<Cell>> plan(Cells cells, Cell from, Cell to, Costs costs, Crawl.Rules rules, int budget) {
        return search(cells, from, to, costs, rules.margin(), rules.hold(), budget, false);
    }

    /**
     * requires: {@code budget > 0}
     * effects: returns the path {@link #plan} would, or, when {@code to}
     * cannot be reached or the budget runs out, the cheapest path to the
     * reachable cell nearest {@code to} (by taxicab distance) that the
     * search saw -- the best a creature cut off by water or bedrock can do;
     * nothing only when {@code from} itself cannot be left
     */
    public static Optional<List<Cell>> planNearest(Cells cells, Cell from, Cell to, Costs costs, Crawl.Rules rules, int budget) {
        return search(cells, from, to, costs, rules.margin(), rules.hold(), budget, true);
    }

    private static Optional<List<Cell>> search(Cells world, Cell from, Cell to, Costs costs, int margin, int hold, int budget, boolean nearest) {
        if (margin < 0 || hold < 1 || budget <= 0) {
            throw new IllegalArgumentException("a margin of cells, a hold of at least one, and a budget of expansions");
        }
        Table t = new Table(world, margin, hold);
        int depth = rockDepth(t, to, DEPTH_LOOK);
        double air = costs.air();
        double shell = costs.rock() - costs.air();
        long fromKey = Table.pack(from.x(), from.y(), from.z());
        long toKey = Table.pack(to.x(), to.y(), to.z());
        Heap open = new Heap();
        t.g[t.slot(fromKey)] = 0.0;
        open.push(heuristic(from, to, depth, air, shell), fromKey);
        int expansions = 0;
        long bestKey = fromKey;
        int bestDistance = from.manhattan(to);
        while (open.size > 0) {
            long key = open.pop();
            int i = t.slot(key);
            if (t.closed[i]) {
                continue;
            }
            t.closed[i] = true;
            if (key == toKey) {
                return Optional.of(t.unwind(toKey, fromKey));
            }
            int x = Table.x(key), y = Table.y(key), z = Table.z(key);
            int distance = Math.abs(x - to.x()) + Math.abs(y - to.y()) + Math.abs(z - to.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestKey = key;
            }
            if (++expansions > budget) {
                return nearest && bestKey != fromKey ? Optional.of(t.unwind(bestKey, fromKey)) : Optional.empty();
            }
            double here = t.g[i];   // read before the neighbours are looked at: their reads may grow the table under i
            for (int k = 0; k < 6; k++) {
                int nx = x + DX[k], ny = y + DY[k], nz = z + DZ[k];
                long next = Table.pack(nx, ny, nz);
                Passage passage = t.passage(next);
                if (passage == Passage.NEVER) {
                    continue;
                }
                int j = t.slot(next);
                if (t.closed[j]) {
                    continue;
                }
                double tentative = here + passage.cost(costs);
                if (tentative < t.g[j]) {
                    t.g[j] = tentative;
                    t.came[j] = key;
                    int d = Math.abs(nx - to.x()) + Math.abs(ny - to.y()) + Math.abs(nz - to.z());
                    open.push(tentative + d * air + Math.min(d, depth) * shell, next);
                }
            }
        }
        return nearest && bestKey != fromKey ? Optional.of(t.unwind(bestKey, fromKey)) : Optional.empty();
    }

    /**
     * effects: returns a bound below the cost of any way from {@code c} to
     * {@code to}, where no cell within {@code depth} of {@code to} is air:
     * the taxicab distance at {@code air}, plus {@code shell} (the rock
     * cost's excess over air) for each of the cells within {@code depth} of
     * the target that the way must enter -- it enters one at every distance
     * below its own, and each of those is not air, so costs rock at least.
     * Consistent: between neighbours it changes by at most the cost of the
     * one nearer the target.
     */
    static double heuristic(Cell c, Cell to, int depth, double air, double shell) {
        int d = c.manhattan(to);
        return d * air + Math.min(d, depth) * shell;
    }

    /**
     * requires: {@code look >= 0}
     * effects: returns the taxicab distance from {@code c} to the nearest
     * cell that is air, or {@code look} when none lies nearer than that: 0
     * for a cell that is air; for a buried one, how many cells that are not
     * air any way from the open must cross to reach it
     */
    static int rockDepth(Cells cells, Cell c, int look) {
        for (int r = 0; r < look; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int ry = r - Math.abs(dx);
                for (int dy = -ry; dy <= ry; dy++) {
                    int dz = ry - Math.abs(dy);
                    if (cells.at(c.x() + dx, c.y() + dy, c.z() + dz) == Cells.Kind.AIR
                            || (dz != 0 && cells.at(c.x() + dx, c.y() + dy, c.z() - dz) == Cells.Kind.AIR)) {
                        return r;
                    }
                }
            }
        }
        return look;
    }

    /** What a cell is to a way: air by a face, air with none, rock that may be cut, or never passed. */
    enum Passage {
        AIR_BY_A_FACE, AIR_ALONE, ROCK, NEVER;

        /** effects: returns what passing a cell of this kind costs under {@code costs}, infinite for NEVER */
        double cost(Costs costs) {
            return switch (this) {
                case AIR_BY_A_FACE -> costs.air();
                case AIR_ALONE -> costs.unsupported();
                case ROCK -> costs.rock();
                case NEVER -> Double.POSITIVE_INFINITY;
            };
        }
    }

    /**
     * effects: returns what {@code c} is to a way for a body keeping
     * {@code margin} cells from harm and riding within {@code hold} of a
     * face: air by a face within the hold, or alone; hard cells and fluid
     * are never passed, nor rock with a hard or fluid cell anywhere within
     * {@code margin} of it in every direction (the cells about it the bore
     * would reach), since the bore that cuts it would breach that
     */
    static Passage passage(Cells cells, Cell c, int margin, int hold) {
        return passage(cells, c.x(), c.y(), c.z(), margin, hold);
    }

    private static Passage passage(Cells cells, int x, int y, int z, int margin, int hold) {
        return switch (cells.at(x, y, z)) {
            case AIR -> supported(cells, x, y, z, hold) ? Passage.AIR_BY_A_FACE : Passage.AIR_ALONE;
            case ROCK -> clearAbout(cells, x, y, z, margin) ? Passage.ROCK : Passage.NEVER;
            case HARD, FLUID -> Passage.NEVER;
        };
    }

    /** effects: returns whether no cell within {@code margin} of {@code c} in every direction is hard or fluid */
    static boolean clearAbout(Cells cells, Cell c, int margin) {
        return clearAbout(cells, c.x(), c.y(), c.z(), margin);
    }

    private static boolean clearAbout(Cells cells, int x, int y, int z, int margin) {
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dy = -margin; dy <= margin; dy++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    Cells.Kind k = cells.at(x + dx, y + dy, z + dz);
                    if (k == Cells.Kind.HARD || k == Cells.Kind.FLUID) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** effects: returns whether a solid cell lies within {@code hold} cells of {@code c} along one of the six axes: a face a head there can ride */
    static boolean supported(Cells cells, Cell c, int hold) {
        return supported(cells, c.x(), c.y(), c.z(), hold);
    }

    private static boolean supported(Cells cells, int x, int y, int z, int hold) {
        for (int k = 0; k < 6; k++) {
            for (int d = 1; d <= hold; d++) {
                if (cells.at(x + DX[k] * d, y + DY[k] * d, z + DZ[k] * d).solid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * One search's table of the cells it has met, keyed by the cell packed
     * into a long, open addressing, doubling when three quarters full. Per
     * cell: what it is (read from the world once, on first asking), what
     * passing it is (worked out once), the cheapest cost known to it, the
     * cell that cost came through, and whether it is closed. Also the
     * search's view of the world as {@link Cells}, so every rule reads
     * through the memory.
     * RI: the arrays are one length, a power of two; a slot whose kind is 0
     * is empty, else its kind is the cell's {@code Kind} ordinal plus one
     * and its key the cell; keys are unique; a passage is 0 (not yet asked)
     * or the {@code Passage} ordinal plus one; g is infinite until a cost is
     * known; size counts the filled slots, under three quarters of the
     * length. Not thread-safe: one per search, on the level's own thread.
     */
    static final class Table implements Cells {
        private static final Kind[] KINDS = Kind.values();
        private static final Passage[] PASSAGES = Passage.values();
        private static final int FIRST = 4096;

        private final Cells world;
        private final int margin;
        private final int hold;
        private long[] keys = new long[FIRST];
        private byte[] kinds = new byte[FIRST];
        private byte[] passages = new byte[FIRST];
        private double[] g = infinite(FIRST);
        private long[] came = new long[FIRST];
        private boolean[] closed = new boolean[FIRST];
        private int size;
        private int reads;

        /** requires: {@code margin >= 0}, {@code hold >= 1} */
        Table(Cells world, int margin, int hold) {
            this.world = world;
            this.margin = margin;
            this.hold = hold;
        }

        @Override
        public Kind at(int x, int y, int z) {
            int i = slot(pack(x, y, z));   // first: finding the slot may grow the table the index is into
            return KINDS[kinds[i] - 1];
        }

        /** effects: returns what the cell {@code key} is to a way, as {@link Burrow#passage} says of the world, worked out once per cell */
        Passage passage(long key) {
            int i = slot(key);
            byte p = passages[i];
            if (p != 0) {
                return PASSAGES[p - 1];
            }
            Passage found = Burrow.passage(this, x(key), y(key), z(key), margin, hold);
            // Reading the cells about it may have grown the table: find its slot afresh.
            passages[slot(key)] = (byte) (found.ordinal() + 1);
            return found;
        }

        /** effects: returns how many cells have been read from the world */
        int reads() {
            return reads;
        }

        /** effects: returns the slot of the cell {@code key}, read from the world and remembered on its first asking */
        int slot(long key) {
            int mask = keys.length - 1;
            int i = spread(key) & mask;
            while (kinds[i] != 0) {
                if (keys[i] == key) {
                    return i;
                }
                i = (i + 1) & mask;
            }
            keys[i] = key;
            kinds[i] = (byte) (world.at(x(key), y(key), z(key)).ordinal() + 1);
            reads++;
            if (++size * 4 > keys.length * 3) {
                grow();
                return slot(key);
            }
            return i;
        }

        /** effects: returns the cells from {@code from} to {@code end} along the way each came through */
        List<Cell> unwind(long end, long from) {
            List<Cell> path = new ArrayList<>();
            for (long key = end; ; key = came[slot(key)]) {
                path.add(new Cell(x(key), y(key), z(key)));
                if (key == from) {
                    break;
                }
            }
            Collections.reverse(path);
            return List.copyOf(path);
        }

        private void grow() {
            long[] oldKeys = keys;
            byte[] oldKinds = kinds, oldPassages = passages;
            double[] oldG = g;
            long[] oldCame = came;
            boolean[] oldClosed = closed;
            int length = oldKeys.length * 2;
            keys = new long[length];
            kinds = new byte[length];
            passages = new byte[length];
            g = infinite(length);
            came = new long[length];
            closed = new boolean[length];
            int mask = length - 1;
            for (int j = 0; j < oldKeys.length; j++) {
                if (oldKinds[j] != 0) {
                    int i = spread(oldKeys[j]) & mask;
                    while (kinds[i] != 0) {
                        i = (i + 1) & mask;
                    }
                    keys[i] = oldKeys[j];
                    kinds[i] = oldKinds[j];
                    passages[i] = oldPassages[j];
                    g[i] = oldG[j];
                    came[i] = oldCame[j];
                    closed[i] = oldClosed[j];
                }
            }
        }

        private static double[] infinite(int length) {
            double[] out = new double[length];
            Arrays.fill(out, Double.POSITIVE_INFINITY);
            return out;
        }

        /** effects: returns the cell as one long: 26 bits of x, 26 of z, 12 of y, distinct within any world */
        static long pack(int x, int y, int z) {
            return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        }

        static int x(long key) {
            return (int) (key >> 38);
        }

        static int y(long key) {
            return (int) (key << 52 >> 52);
        }

        static int z(long key) {
            return (int) (key << 26 >> 38);
        }

        private static int spread(long key) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }
    }

    /**
     * The open set: a binary heap of (f, cell key), least f first; a cell may
     * be in it more than once, the search skipping entries of a closed cell.
     * RI: for every i > 0, f[(i - 1) / 2] <= f[i]; size <= f.length = keys.length.
     */
    private static final class Heap {
        private double[] f = new double[1024];
        private long[] keys = new long[1024];
        private int size;

        void push(double priority, long key) {
            if (size == f.length) {
                f = Arrays.copyOf(f, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (f[parent] <= priority) {
                    break;
                }
                f[i] = f[parent];
                keys[i] = keys[parent];
                i = parent;
            }
            f[i] = priority;
            keys[i] = key;
        }

        /** requires: not empty; effects: removes and returns the key with the least f */
        long pop() {
            long top = keys[0];
            double priority = f[--size];
            long key = keys[size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && f[child + 1] < f[child]) {
                    child++;
                }
                if (f[child] >= priority) {
                    break;
                }
                f[i] = f[child];
                keys[i] = keys[child];
                i = child;
            }
            if (size > 0) {
                f[i] = priority;
                keys[i] = key;
            }
            return top;
        }
    }
}
