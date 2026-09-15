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

/**
 * One tick of thought: the memory aged, the current mode's tree evaluated
 * (the start mode when the memory names none of the tree's), and, if that
 * entered another mode, the new mode evaluated once more -- a second entry
 * is kept but not followed this tick, so a cycle of entries can never
 * loop. The tick's intent is the first decided one: an act that came
 * before the entry (a release before a flight) is carried out, and the
 * entered mode's own choice counts only when the entering pass gave none.
 * Deterministic: randomness is a sense. Pure.
 */
public final class Mind {
    private Mind() {}

    /** What a tick decided: the intent to carry out and the memory to keep. */
    public record Decision(Intent intent, Memory memory) {}

    /**
     * effects: returns the decision of {@code tree} for {@code senses} from
     * {@code memory}, as the class says
     */
    public static Decision tick(Tree tree, Senses senses, Memory memory) {
        Memory m = memory.aged();
        String mode = tree.modes().containsKey(m.mode()) ? m.mode() : tree.start();
        m = m.withMode(mode);
        Node.Outcome o = tree.modes().get(mode).eval(senses, m);
        m = o.memory();
        Intent first = o.decided() ? o.intent() : Intent.NONE;
        if (!m.mode().equals(mode) && tree.modes().containsKey(m.mode())) {
            Node.Outcome again = tree.modes().get(m.mode()).eval(senses, m);
            Intent second = again.decided() ? again.intent() : Intent.NONE;
            return new Decision(first.isNone() ? second : first, again.memory());
        }
        return new Decision(first, m);
    }
}
