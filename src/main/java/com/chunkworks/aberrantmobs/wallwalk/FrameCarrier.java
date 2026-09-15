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

import com.chunkworks.aberrantmobs.domain.frame.Blend;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a player carries for the wall-walk: its frame (which way is down;
 * synced), the wall its last move was stopped by and the move it tried
 * (for the transition rule), whether it stood on ground last tick, and on
 * the client the camera's blend from one frame to the next. Implemented
 * on {@code Player} by a mixin.
 */
public interface FrameCarrier {
    Frame aberrantmobs$frame();

    void aberrantmobs$setFrame(Frame frame);

    @Nullable
    Vec3 aberrantmobs$lastWall();

    @Nullable
    Vec3 aberrantmobs$lastTried();

    void aberrantmobs$setLastWall(@Nullable Vec3 normal, @Nullable Vec3 tried);

    boolean aberrantmobs$wasOnGround();

    void aberrantmobs$setWasOnGround(boolean onGround);

    @Nullable
    Blend aberrantmobs$blend();

    void aberrantmobs$setBlend(@Nullable Blend blend);

    int aberrantmobs$seenCode();

    void aberrantmobs$setSeenCode(int code);

    /** Where the entity stood as its move began, for reading the wall off the game's own move. */
    @Nullable
    Vec3 aberrantmobs$movePre();

    void aberrantmobs$setMovePre(@Nullable Vec3 pos);
}
