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
package com.chunkworks.aberrantmobs.domain.mind;

import java.util.List;

/**
 * A node of a mode's tree, evaluated top down each tick against the
 * senses and the memory. Each yields an {@link Outcome}: decided (with an
 * intent and the memory as changed) or not (the enclosing select moves
 * on). Immutable; a sealed tree of records.
 *
 * <ul>
 * <li>{@link Select}: the first child that decides.
 * <li>{@link Sequence}: every child in turn, memory threaded through;
 *     decided if any child was, with the last intent any gave.
 * <li>{@link When}: {@code then} if the condition holds, else undecided.
 * <li>{@link Act}: decided, with the intent.
 * <li>{@link Enter}: decided with no intent, the memory's mode changed;
 *     the mind then evaluates the new mode once.
 * <li>{@link SetTimer}: sets the timer; undecided (a step in a sequence).
 * <li>{@link Wait}: starts the timer if it was never set and is undecided
 *     while it runs; when it has run out, clears it and evaluates
 *     {@code then}: patience.
 * <li>{@link Cooldown}: if the timer is not running, sets it and evaluates
 *     {@code then}; else undecided: at most once per so many ticks.
 * </ul>
 */
public sealed interface Node permits Node.Select, Node.Sequence, Node.When, Node.Act, Node.Enter, Node.SetTimer, Node.Wait, Node.Cooldown {
    /** What evaluating a node came to. */
    record Outcome(boolean decided, Intent intent, Memory memory) {
        static Outcome undecided(Memory m) {
            return new Outcome(false, Intent.NONE, m);
        }
    }

    Outcome eval(Senses senses, Memory memory);

    record Select(List<Node> children) implements Node {
        public Select {
            children = List.copyOf(children);
        }

        @Override
        public Outcome eval(Senses s, Memory m) {
            for (Node child : children) {
                Outcome o = child.eval(s, m);
                m = o.memory();
                if (o.decided()) {
                    return o;
                }
            }
            return Outcome.undecided(m);
        }
    }

    record Sequence(List<Node> children) implements Node {
        public Sequence {
            children = List.copyOf(children);
        }

        @Override
        public Outcome eval(Senses s, Memory m) {
            boolean decided = false;
            Intent intent = Intent.NONE;
            for (Node child : children) {
                Outcome o = child.eval(s, m);
                m = o.memory();
                if (o.decided()) {
                    decided = true;
                    if (!o.intent().isNone()) {
                        intent = o.intent();
                    }
                }
            }
            return new Outcome(decided, intent, m);
        }
    }

    record When(Cond cond, Node then) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            return cond.holds(s, m) ? then.eval(s, m) : Outcome.undecided(m);
        }
    }

    record Act(Intent intent) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            return new Outcome(true, intent, m);
        }
    }

    record Enter(String mode) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            return new Outcome(true, Intent.NONE, m.withMode(mode));
        }
    }

    record SetTimer(String name, int ticks) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            return Outcome.undecided(m.withTimer(name, ticks));
        }
    }

    record Wait(String name, int ticks, Node then) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            if (!m.hasTimer(name)) {
                return Outcome.undecided(m.withTimer(name, ticks));
            }
            if (m.timer(name) > 0) {
                return Outcome.undecided(m);
            }
            return then.eval(s, m.withoutTimer(name));
        }
    }

    record Cooldown(String name, int ticks, Node then) implements Node {
        @Override
        public Outcome eval(Senses s, Memory m) {
            if (m.timer(name) > 0) {
                return Outcome.undecided(m);
            }
            return then.eval(s, m.withTimer(name, ticks));
        }
    }
}
