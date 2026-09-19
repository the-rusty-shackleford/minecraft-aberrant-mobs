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

import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.domain.Cells;
import com.chunkworks.aberrantmobs.domain.Habitat;
import javax.annotation.Nullable;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The level as {@link Cells}: a fluid is FLUID; a block with no collision
 * is AIR; a block the wither cannot break, obsidian, one with a block
 * entity or one in {@code #aberrantmobs:undiggable} is HARD; anything
 * else solid is ROCK. A cell in a chunk that is not loaded is HARD too:
 * a read never loads a chunk, since a plan asks after thousands of cells
 * and a search that ran out past the loaded world would stall the
 * server generating chunks (a prowl toward a far bearing once cost 1.3 s
 * that way). One block read per cell, through a cursor this reader owns,
 * so a tick of casts allocates nothing. Not thread-safe: one per caller,
 * on the level's own thread.
 */
public final class LevelCells implements Cells {
    /** Blocks a creature never digs, for packs to add to. */
    public static final TagKey<Block> UNDIGGABLE = TagKey.create(Registries.BLOCK, AberrantMobs.id("undiggable"));

    private final LevelReader level;
    @Nullable private final Habitat.Rules habitat;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** effects: reads terrain without a habitat restriction */
    public LevelCells(LevelReader level) {
        this(level, null);
    }

    /** effects: reads terrain, treating cells outside {@code habitat} or in the Deep Dark as hard; null means unrestricted */
    public LevelCells(LevelReader level, @Nullable Habitat.Rules habitat) {
        this.level = level;
        this.habitat = habitat;
    }

    /**
     * requires: a finite box
     * effects: returns whether the whole box fits the habitat, without loading chunks.
     * Checks every possible quart biome used by Minecraft's fuzzy biome lookup,
     * conservatively keeping a small margin beside the Deep Dark's boundary.
     * A reader without a habitat imposes no restriction.
     */
    public boolean permits(AABB box) {
        if (habitat == null) return true;
        if (!Habitat.containsHeight(habitat, box.minY, box.maxY)) return false;
        int x0 = ((int) Math.floor(box.minX) - 2) >> 2;
        int y0 = ((int) Math.floor(box.minY) - 2) >> 2;
        int z0 = ((int) Math.floor(box.minZ) - 2) >> 2;
        int x1 = (((int) Math.floor(box.maxX) - 2) >> 2) + 1;
        int y1 = (((int) Math.floor(box.maxY) - 2) >> 2) + 1;
        int z1 = (((int) Math.floor(box.maxZ) - 2) >> 2) + 1;
        for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) {
            ChunkAccess chunk = level.getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false);
            if (chunk == null) return false;
            for (int y=y0; y<=y1; y++) {
                if (chunk.getNoiseBiome(x,y,z).is(Biomes.DEEP_DARK)) return false;
            }
        }
        return true;
    }

    @Override
    public Kind at(int x, int y, int z) {
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);   // never loads one
        if (chunk == null) {
            return Kind.HARD;
        }
        cursor.set(x,y,z);
        if (habitat != null && (y < habitat.yMin() || y > habitat.yMax()
                || level.getBiome(cursor).is(Biomes.DEEP_DARK))) return Kind.HARD;
        BlockState state = chunk.getBlockState(cursor);
        if (!state.getFluidState().isEmpty()) {
            return Kind.FLUID;
        }
        if (state.getCollisionShape(level, cursor).isEmpty()) {
            return Kind.AIR;
        }
        if (state.is(BlockTags.WITHER_IMMUNE) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                || state.hasBlockEntity() || state.is(UNDIGGABLE)) {
            return Kind.HARD;
        }
        return Kind.ROCK;
    }
}
