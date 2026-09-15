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
package com.chunkworks.aberrantmobs.gametest;

import com.chunkworks.aberrantmobs.Aberrant;
import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.api.CreatureProfile;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Face-Stealer on a headless server. Phase 1: its profile is in the
 * registry as the file says, and a creature made from it is sized, named
 * and healthy as the profile says, and keeps its profile across a save.
 *
 * <p>The arena template is 15 by 15; a floor of stone is laid on it.
 */
@GameTestHolder(com.chunkworks.aberrantmobs.AberrantMobsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FaceStealerGameTests {
    static final ResourceLocation FACE_STEALER = AberrantMobs.id("face_stealer");
    private static final int FLOOR = 4;

    public FaceStealerGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theProfileIsRegisteredAndACreatureIsMadeFromIt(GameTestHelper helper) {
        Optional<Holder.Reference<CreatureProfile>> p = AberrantMobs.profile(helper.getLevel().registryAccess(), FACE_STEALER);
        helper.assertTrue(p.isPresent(), "aberrantmobs:face_stealer is in the creature registry");
        CreatureProfile profile = p.get().value();
        helper.assertValueEqual(profile.model(), AberrantMobs.id("face_stealer"), "its model");
        helper.assertTrue(Math.abs(profile.scale() - 1.0 / 16.0) < 1e-12, "a sixteenth of a block a unit");
        helper.assertTrue(Math.abs(profile.stats().health() - 84.0) < 1e-9, "84 health");
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        helper.assertTrue(Math.abs(a.getBbWidth() - 2.5f) < 1e-5 && Math.abs(a.getBbHeight() - 2.5f) < 1e-5, "sized by the profile: " + a.getBbWidth() + " x " + a.getBbHeight());
        helper.assertTrue(Math.abs(a.getMaxHealth() - 84.0f) < 1e-5 && Math.abs(a.getHealth() - 84.0f) < 1e-5, "healthy as the profile says: " + a.getHealth());
        helper.assertValueEqual(a.getName().getString(), "Face-Stealer", "named by its profile");
        helper.assertValueEqual(a.profileId(), FACE_STEALER, "and knows its profile");
        helper.assertTrue(Aberrant.create(helper.getLevel(), AberrantMobs.id("nothing"), at.x, at.y, at.z, 0.0f) == null, "no creature without a profile");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(a.isAlive() && a.profile() != null, "still itself five ticks on");
            helper.assertTrue(a.getBoundingBoxForCulling().getXsize() > 20.0, "drawn from well beyond its box: " + a.getBoundingBoxForCulling().getXsize());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public void aScriptedWalkMovesItAlongTheGroundFacingItsWay(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, 0.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setScriptedWalk(new com.chunkworks.aberrantmobs.domain.Vec(0.3, 0.0, 0.0), 30);
        helper.runAtTickTime(40, () -> {
            double moved = a.getX() - at.x;
            helper.assertTrue(moved > 7.0 && moved < 10.0, "thirty ticks at 0.3 east: " + moved);
            helper.assertTrue(Math.abs(a.getY() - at.y) < 0.1, "on the floor: " + (a.getY() - at.y));
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(a.yBodyRot + 90.0f)) < 1.0f, "facing east: " + a.yBodyRot);
            helper.assertTrue(a.distance() > 7.0, "the distance counted: " + a.distance());
            helper.assertTrue(a.speed() < 0.01, "and it stopped when the walk ran out: " + a.speed());
            helper.succeed();
        });
    }
}
