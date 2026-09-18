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
package com.chunkworks.aberrantmobs.wallwalk;

import com.chunkworks.aberrantmobs.domain.frame.ClingIntent;
import com.chunkworks.aberrantmobs.domain.frame.ClingGesture;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a player carries for the wall-walk: its frame (which way is down;
 * synced), the wall its last move was stopped by and the move it tried
 * (for the transition rule), and whether it stood on ground last tick. Implemented
 * on {@code Player} by a mixin.
 */
public interface FrameCarrier {
    /** effects: returns this player's immutable manual-input history. */
    ClingGesture aberrantmobs$gesture();
    /** effects: replaces the input history. */
    void aberrantmobs$setGesture(ClingGesture gesture);
    /** effects: returns the latest manual controls, without any requested position or frame. */
    ClingIntent aberrantmobs$intent();
    /** effects: records the controls and their receipt tick for stale-input rejection. */
    void aberrantmobs$setIntent(ClingIntent intent, int tick);
    /** effects: returns the player tick of the latest input sample. */
    int aberrantmobs$inputTick();

    Frame aberrantmobs$frame();

    void aberrantmobs$setFrame(Frame frame);

    @Nullable
    Vec3 aberrantmobs$lastWall();

    @Nullable
    Vec3 aberrantmobs$lastTried();

    void aberrantmobs$setLastWall(@Nullable Vec3 normal, @Nullable Vec3 tried);

    boolean aberrantmobs$wasOnGround();

    void aberrantmobs$setWasOnGround(boolean onGround);

    /** Where the entity stood as its move began, for reading the wall off the game's own move. */
    @Nullable
    Vec3 aberrantmobs$movePre();

    void aberrantmobs$setMovePre(@Nullable Vec3 pos);
}
