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

import com.chunkworks.aberrantmobs.domain.Cell;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A creature's dig on the level: the cells it was told to cut become air,
 * nothing drops (eaten), and, when loud, each block breaks with its
 * particles and sound and a game event the world may hear; quiet, the
 * blocks simply go.
 */
public final class DigWorld {
    private DigWorld() {}

    /** effects: cuts every cell of {@code rock} to air on {@code digger}'s account; returns how many were solid before */
    public static int dig(ServerLevel level, Entity digger, List<Cell> rock, boolean loud) {
        int n = 0;
        for (Cell c : rock) {
            BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (loud) {
                level.levelEvent(2001, pos, Block.getId(state));
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (loud) {
                level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(digger, state));
            }
            n++;
        }
        return n;
    }
}
