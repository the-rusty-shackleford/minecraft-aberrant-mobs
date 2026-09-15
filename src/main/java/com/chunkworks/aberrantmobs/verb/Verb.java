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
package com.chunkworks.aberrantmobs.verb;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.domain.mind.Intent;

/**
 * What a creature can do: the mind names a verb and its arguments each
 * tick; the entity begins a verb when the mind's verb changes, ticks it
 * while it stays, and ends it when another takes over. A verb instance
 * belongs to one creature and may keep state between ticks.
 */
public interface Verb {
    /** effects: starts doing {@code intent} for {@code creature} */
    void begin(Aberrant creature, Intent intent);

    /** effects: one tick of doing {@code intent} (its arguments may have changed) */
    void tick(Aberrant creature, Intent intent);

    /** effects: stops; leaves the creature in no motion of this verb's making */
    void end(Aberrant creature);
}
