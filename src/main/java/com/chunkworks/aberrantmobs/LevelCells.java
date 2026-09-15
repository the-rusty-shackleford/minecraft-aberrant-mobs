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
package com.chunkworks.aberrantmobs;

import com.chunkworks.aberrantmobs.domain.Cells;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The level as {@link Cells}: a fluid is FLUID; a block with no collision
 * is AIR; a block the wither cannot break or one with a block entity is
 * HARD; anything else solid is ROCK. One block read per cell, through a
 * cursor this reader owns, so a tick of casts allocates nothing. Not
 * thread-safe: one per caller, on the level's own thread.
 */
public final class LevelCells implements Cells {
    private final BlockGetter level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelCells(BlockGetter level) {
        this.level = level;
    }

    @Override
    public Kind at(int x, int y, int z) {
        BlockState state = level.getBlockState(cursor.set(x, y, z));
        if (!state.getFluidState().isEmpty()) {
            return Kind.FLUID;
        }
        if (state.getCollisionShape(level, cursor).isEmpty()) {
            return Kind.AIR;
        }
        if (state.is(BlockTags.WITHER_IMMUNE) || state.hasBlockEntity()) {
            return Kind.HARD;
        }
        return Kind.ROCK;
    }
}
