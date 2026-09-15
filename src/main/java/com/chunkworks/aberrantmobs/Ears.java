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

import com.chunkworks.aberrantmobs.domain.Hearing;
import com.chunkworks.aberrantmobs.domain.Vec;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * The creatures' ears on the world: every game event a player causes (a
 * step, a block broken or placed, a splash, a blow) or that no creature
 * causes (a blast) is a sound with a loudness -- a step one, a sprinting
 * step two, a block six, a blast twenty; a sneaking step nothing, so
 * silence is a defence -- fed to every creature within hearing range.
 * The server never polls a player's position: it hears what the world
 * hears. Sounds other mobs make, and a creature's own digging, are
 * nothing to it.
 */
@EventBusSubscriber(modid = AberrantMobsMod.MOD_ID)
public final class Ears {
    private Ears() {}

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity cause = event.getCause();
        double loudness = loudness(event.getVanillaEvent(), cause);
        if (loudness <= 0) {
            return;
        }
        Vec3 p = event.getEventPosition();
        Vec at = new Vec(p.x, p.y, p.z);
        String source = cause instanceof Player player ? player.getUUID().toString() : "world";
        AABB reach = new AABB(p, p).inflate(Hearing.RANGE);
        for (Aberrant a : level.getEntities(EntityTypeTest.forClass(Aberrant.class), reach, Aberrant::isAlive)) {
            a.hear(new Hearing.Sound(at, loudness, a.tickCount, source));
        }
    }

    /** effects: returns how loud {@code event} caused by {@code cause} is to a creature; zero for what it ignores */
    static double loudness(Holder<GameEvent> event, Entity cause) {
        if (cause != null && !(cause instanceof Player)) {
            return 0.0;
        }
        if (event.is(GameEvent.STEP)) {
            if (cause == null || cause.isShiftKeyDown()) {
                return 0.0;
            }
            return cause.isSprinting() ? 2.0 : 1.0;
        }
        if (event.is(GameEvent.BLOCK_DESTROY) || event.is(GameEvent.BLOCK_PLACE)) {
            return 6.0;
        }
        if (event.is(GameEvent.EXPLODE)) {
            return 20.0;
        }
        if (event.is(GameEvent.ENTITY_DIE)) {
            return 4.0;
        }
        if (event.is(GameEvent.ENTITY_DAMAGE) || event.is(GameEvent.PROJECTILE_LAND)) {
            return 3.0;
        }
        if (event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SPLASH) || event.is(GameEvent.CONTAINER_OPEN)) {
            return 2.0;
        }
        if (event.is(GameEvent.SWIM) || event.is(GameEvent.ELYTRA_GLIDE) || event.is(GameEvent.ITEM_INTERACT_FINISH) || event.is(GameEvent.ENTITY_ACTION)) {
            return cause == null ? 0.0 : 1.0;
        }
        return 0.0;
    }
}
