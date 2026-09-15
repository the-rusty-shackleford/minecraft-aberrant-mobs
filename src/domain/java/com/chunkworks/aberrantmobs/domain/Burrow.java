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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A way through rock and air: A* over cells, six-connected, air cheap,
 * air with no face to cling to dearer, rock at the cost of digging it,
 * hard cells and fluid never -- nor rock beside them, so the bore that
 * follows the way never breaches them. Bounded by a budget of expansions,
 * so a hopeless search ends. Pure.
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

    private record Node(Cell cell, double g, double f) {}

    /**
     * requires: {@code budget > 0}
     * effects: returns the cheapest path of cells from {@code from} to
     * {@code to} inclusive under {@code costs} (the start cell costs
     * nothing whatever it is), or nothing when none exists or the search
     * spends more than {@code budget} expansions
     */
    public static Optional<List<Cell>> plan(Cells cells, Cell from, Cell to, Costs costs, int budget) {
        if (budget <= 0) {
            throw new IllegalArgumentException("a budget of expansions");
        }
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Cell, Double> g = new HashMap<>();
        Map<Cell, Cell> came = new HashMap<>();
        Set<Cell> closed = new HashSet<>();
        g.put(from, 0.0);
        open.add(new Node(from, 0.0, from.manhattan(to) * costs.air()));
        int expansions = 0;
        while (!open.isEmpty()) {
            Node node = open.poll();
            if (!closed.add(node.cell())) {
                continue;
            }
            if (node.cell().equals(to)) {
                return Optional.of(unwind(came, to));
            }
            if (++expansions > budget) {
                return Optional.empty();
            }
            for (Cell next : node.cell().neighbours()) {
                if (closed.contains(next)) {
                    continue;
                }
                double cost = cost(cells, next, costs);
                if (Double.isInfinite(cost)) {
                    continue;
                }
                double tentative = node.g() + cost;
                Double known = g.get(next);
                if (known == null || tentative < known) {
                    g.put(next, tentative);
                    came.put(next, node.cell());
                    open.add(new Node(next, tentative, tentative + next.manhattan(to) * costs.air()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * effects: returns what passing {@code c} costs, infinite when it cannot
     * be passed: hard cells and fluid never, nor rock with a hard or fluid
     * cell anywhere in the three-by-three-by-three about it, since the
     * bore that cuts it would breach that
     */
    private static double cost(Cells cells, Cell c, Costs costs) {
        return switch (cells.at(c)) {
            case AIR -> supported(cells, c) ? costs.air() : costs.unsupported();
            case ROCK -> clearAbout(cells, c) ? costs.rock() : Double.POSITIVE_INFINITY;
            case HARD, FLUID -> Double.POSITIVE_INFINITY;
        };
    }

    /** effects: returns whether no cell within one of {@code c} in every direction is hard or fluid */
    static boolean clearAbout(Cells cells, Cell c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Cells.Kind k = cells.at(c.x() + dx, c.y() + dy, c.z() + dz);
                    if (k == Cells.Kind.HARD || k == Cells.Kind.FLUID) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** effects: returns whether any face neighbour of {@code c} is solid */
    static boolean supported(Cells cells, Cell c) {
        for (Cell n : c.neighbours()) {
            if (cells.at(n).solid()) {
                return true;
            }
        }
        return false;
    }

    private static List<Cell> unwind(Map<Cell, Cell> came, Cell to) {
        List<Cell> path = new ArrayList<>();
        for (Cell c = to; c != null; c = came.get(c)) {
            path.add(c);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }
}
