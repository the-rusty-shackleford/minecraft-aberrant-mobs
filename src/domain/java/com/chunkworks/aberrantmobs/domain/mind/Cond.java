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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A condition on the senses and the memory, as a tree parsed from a
 * string: {@code expr := and ('||' and)*; and := term ('&&' term)*; term
 * := '!' term | '(' expr ')' | name (op number)?} with {@code op} one of
 * {@code < <= > >= == !=}, names {@code [a-z_][a-z0-9_.]*}. A bare name is
 * a flag; a compared name is a number, or a timer ({@code timer.x}) read
 * from the memory. A comparison on a sense the creature lacks (NaN) is
 * false. Immutable; a sealed tree of records.
 */
public sealed interface Cond permits Cond.And, Cond.Or, Cond.Not, Cond.Flag, Cond.Compare {
    /** effects: returns whether this holds for {@code senses} and {@code memory} */
    boolean holds(Senses senses, Memory memory);

    /** effects: adds every sense name this condition reads to {@code out} */
    void names(Set<String> out);

    /** effects: returns the names this condition reads, sorted */
    default Set<String> names() {
        Set<String> out = new TreeSet<>();
        names(out);
        return out;
    }

    record And(List<Cond> terms) implements Cond {
        public And {
            terms = List.copyOf(terms);
        }

        @Override
        public boolean holds(Senses s, Memory m) {
            for (Cond c : terms) {
                if (!c.holds(s, m)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void names(Set<String> out) {
            terms.forEach(c -> c.names(out));
        }
    }

    record Or(List<Cond> terms) implements Cond {
        public Or {
            terms = List.copyOf(terms);
        }

        @Override
        public boolean holds(Senses s, Memory m) {
            for (Cond c : terms) {
                if (c.holds(s, m)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void names(Set<String> out) {
            terms.forEach(c -> c.names(out));
        }
    }

    record Not(Cond term) implements Cond {
        @Override
        public boolean holds(Senses s, Memory m) {
            return !term.holds(s, m);
        }

        @Override
        public void names(Set<String> out) {
            term.names(out);
        }
    }

    record Flag(String name) implements Cond {
        @Override
        public boolean holds(Senses s, Memory m) {
            return s.flag(name);
        }

        @Override
        public void names(Set<String> out) {
            out.add(name);
        }
    }

    enum Op { LT, LE, GT, GE, EQ, NE }

    record Compare(String name, Op op, double value) implements Cond {
        @Override
        public boolean holds(Senses s, Memory m) {
            double x = name.startsWith(Senses.TIMER) ? m.timer(name.substring(Senses.TIMER.length())) : s.number(name);
            if (Double.isNaN(x)) {
                return false;
            }
            return switch (op) {
                case LT -> x < value;
                case LE -> x <= value;
                case GT -> x > value;
                case GE -> x >= value;
                case EQ -> x == value;
                case NE -> x != value;
            };
        }

        @Override
        public void names(Set<String> out) {
            out.add(name);
        }
    }

    /**
     * effects: returns the condition {@code text} spells<br>
     * throws: {@link IllegalArgumentException} naming the offset of the first
     * thing that is not part of a condition
     */
    static Cond parse(String text) {
        Parser p = new Parser(text);
        Cond c = p.expr();
        p.skipSpace();
        if (p.at < text.length()) {
            throw p.error("unexpected '" + text.charAt(p.at) + "'");
        }
        return c;
    }

    /** A recursive-descent reader over the text; mutable, private to a parse. */
    final class Parser {
        private final String text;
        private int at;

        private Parser(String text) {
            this.text = text;
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException("condition \"" + text + "\" at " + at + ": " + what);
        }

        private void skipSpace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        private boolean take(String token) {
            skipSpace();
            if (text.startsWith(token, at)) {
                at += token.length();
                return true;
            }
            return false;
        }

        private Cond expr() {
            List<Cond> terms = new ArrayList<>();
            terms.add(and());
            while (take("||")) {
                terms.add(and());
            }
            return terms.size() == 1 ? terms.get(0) : new Or(terms);
        }

        private Cond and() {
            List<Cond> terms = new ArrayList<>();
            terms.add(term());
            while (take("&&")) {
                terms.add(term());
            }
            return terms.size() == 1 ? terms.get(0) : new And(terms);
        }

        private Cond term() {
            if (take("!")) {
                return new Not(term());
            }
            if (take("(")) {
                Cond inner = expr();
                if (!take(")")) {
                    throw error("expected ')'");
                }
                return inner;
            }
            skipSpace();
            int start = at;
            while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_' || text.charAt(at) == '.')) {
                at++;
            }
            if (start == at || !Character.isLetter(text.charAt(start)) && text.charAt(start) != '_') {
                throw error("expected a sense name");
            }
            String name = text.substring(start, at);
            skipSpace();
            Op op = null;
            if (take("<=")) {
                op = Op.LE;
            } else if (take(">=")) {
                op = Op.GE;
            } else if (take("==")) {
                op = Op.EQ;
            } else if (take("!=")) {
                op = Op.NE;
            } else if (take("<")) {
                op = Op.LT;
            } else if (take(">")) {
                op = Op.GT;
            }
            if (op == null) {
                return new Flag(name);
            }
            skipSpace();
            int numStart = at;
            while (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.' || text.charAt(at) == '-')) {
                at++;
            }
            try {
                return new Compare(name, op, Double.parseDouble(text.substring(numStart, at)));
            } catch (NumberFormatException e) {
                throw error("expected a number after the comparison");
            }
        }
    }
}
