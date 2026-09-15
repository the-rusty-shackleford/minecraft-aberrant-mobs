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
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import com.chunkworks.aberrantmobs.domain.Cell;
import com.chunkworks.aberrantmobs.domain.Habitat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Where the world may put a creature on its own: the game offers a cave
 * floor (the biome modifier adds the type to every overworld biome); the
 * site must be deep and dark enough for some profile's habitat, not in
 * the deep dark, have a wall thick enough beside it for a pocket, hold no
 * other creature within that habitat's exclusion, and the level fewer
 * than its cap. The creature then bores its pocket into that wall in
 * {@link Aberrant#finalizeSpawn}, so it begins in the rock. Spawned by
 * a command or an egg, none of this applies.
 */
public final class SpawnRules {
    private SpawnRules() {}

    static void register(RegisterSpawnPlacementsEvent event) {
        event.register(ModContent.ABERRANT.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpawnRules::mayPlace, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** effects: returns whether a creature of some profile may come into the world at {@code pos}, as the class says */
    public static boolean mayPlace(EntityType<Aberrant> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        ServerLevel server = level.getLevel();
        if (server.getBiome(pos).is(Biomes.DEEP_DARK)) {
            return false;
        }
        int sky = server.getBrightness(LightLayer.SKY, pos);
        int block = server.getBrightness(LightLayer.BLOCK, pos);
        for (Holder.Reference<CreatureProfile> p : server.registryAccess().registryOrThrow(AberrantMobs.CREATURES).holders().toList()) {
            if (p.value().habitat().isEmpty()) {
                continue;
            }
            Habitat.Rules rules = p.value().habitat().get().rules();
            if (Habitat.deepAndDark(rules, pos.getY(), sky, block) && siteBeside(server, pos) && roomFor(server, pos, rules)) {
                return true;
            }
        }
        return false;
    }

    /** effects: returns whether a wall beside {@code pos} is thick enough for a pocket */
    public static boolean siteBeside(ServerLevel level, BlockPos pos) {
        return Habitat.siteInWall(new LevelCells(level), new Cell(pos.getX(), pos.getY(), pos.getZ()), Habitat.SITE_DEPTH).isPresent();
    }

    /** effects: returns whether no creature stands within the rules' exclusion of {@code pos} and the level holds fewer than the cap */
    static boolean roomFor(ServerLevel level, BlockPos pos, Habitat.Rules rules) {
        AABB near = new AABB(pos).inflate(rules.exclusion());
        if (!level.getEntities(EntityTypeTest.forClass(Aberrant.class), near, Aberrant::isAlive).isEmpty()) {
            return false;
        }
        int count = 0;
        for (var e : level.getEntities().getAll()) {
            if (e instanceof Aberrant && e.isAlive() && ++count >= rules.cap()) {
                return false;
            }
        }
        return true;
    }
}
