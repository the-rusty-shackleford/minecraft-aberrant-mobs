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
 * The Face-Stealer on a headless server. Its profile is in the registry as
 * the file says, and a creature made from it is sized, named and healthy
 * as the profile says; its parts lie along its body and follow its walk;
 * only the cracked segment takes a blow; its feet stand on the floor,
 * most of them at any moment, while it walks; a clip played on the server
 * fires its cues on their ticks and ends; it crawls the floor, climbs the
 * wall and crosses the ceiling; it digs a coherent tunnel to a target
 * through rock and leaves bedrock alone; a pounce lands where it aimed.
 *
 * <p>The arena template is 15 by 9 by 15, the tall one 15 by 16 by 15; a
 * floor of stone is laid on them.
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
    public void thePartsLieAlongTheBodyAndFollowItsWalk(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        helper.assertTrue(a.body() != null, "the server read the model from the jar");
        helper.assertValueEqual(a.body().chain().length, 12, "twelve chain segments");
        a.setScriptedWalk(new com.chunkworks.aberrantmobs.domain.Vec(0.3, 0.0, 0.0), 30);
        helper.runAtTickTime(40, () -> {
            net.neoforged.neoforge.entity.PartEntity<?>[] parts = a.getParts();
            helper.assertValueEqual(parts.length, Aberrant.MAX_PARTS, "born with its parts");
            double headX = parts[0].getX(), tailX = parts[11].getX();
            helper.assertTrue(Math.abs(headX - a.getX()) < 0.05, "the head part is on the head: " + (headX - a.getX()));
            helper.assertTrue(headX - tailX > 6.0 && headX - tailX < 7.5, "the tail part trails the body's length west: " + (headX - tailX));
            helper.assertTrue(parts[5].getBbWidth() > 2.0f && parts[12].getBbWidth() < 0.05f, "segments have boxes, the spare parts are specks");
            helper.assertTrue(parts[5].getBoundingBox().minY < a.getY() + 0.5 && parts[5].getBoundingBox().maxY > a.getY() + 1.5, "a segment's box sits on the body's axis");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void onlyTheCrackedSegmentTakesABlow(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        int weak = a.weakSegment();
        helper.assertTrue(weak >= 2 && weak <= 9, "the crack is on one of the candidates: " + weak);
        net.minecraft.world.entity.player.Player p = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(a.getX(), a.getY(), a.getZ() - 5.0);
        net.neoforged.neoforge.entity.PartEntity<?>[] parts = a.getParts();
        float full = a.getHealth();
        helper.assertTrue(!parts[weak == 5 ? 6 : 5].hurt(helper.getLevel().damageSources().playerAttack(p), 7.0f), "a plate rings");
        helper.assertValueEqual(a.getHealth(), full, "and nothing is lost");
        helper.assertTrue(!a.hurt(helper.getLevel().damageSources().playerAttack(p), 7.0f), "the head's own box is plating too");
        helper.assertValueEqual(a.getHealth(), full, "still whole");
        a.invulnerableTime = 0;
        helper.assertTrue(parts[weak].hurt(helper.getLevel().damageSources().playerAttack(p), 7.0f), "the crack takes the blow");
        helper.assertTrue(Math.abs(a.getHealth() - (full - 7.0f)) < 1e-4, "seven off: " + a.getHealth());
        a.invulnerableTime = 0;
        helper.assertTrue(parts[3 == weak ? 4 : 3].hurt(helper.getLevel().damageSources().explosion(null, null), 10.0f), "an explosion lands anywhere");
        helper.assertTrue(Math.abs(a.getHealth() - (full - 12.0f)) < 1e-4, "at half: " + a.getHealth());
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public void mostFeetStandOnTheFloorWhileItWalks(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setScriptedWalk(new com.chunkworks.aberrantmobs.domain.Vec(0.3, 0.0, 0.0), 40);
        helper.runAtTickTime(25, () -> {
            com.chunkworks.aberrantmobs.domain.Legs.Foot[] feet = a.feet();
            helper.assertValueEqual(feet.length, 26, "a foot per leg");
            int planted = 0, swinging = 0;
            for (com.chunkworks.aberrantmobs.domain.Legs.Foot f : feet) {
                if (f.swinging()) {
                    swinging++;
                }
                if (f.planted()) {
                    planted++;
                    com.chunkworks.aberrantmobs.domain.Vec anchor = f.anchor();
                    helper.assertTrue(anchor.y() - at.y > -0.05 && anchor.y() - at.y < 0.3, "an anchor rests on the floor's top: " + (anchor.y() - at.y));
                    BlockPos under = BlockPos.containing(anchor.x(), anchor.y() - 0.15, anchor.z());
                    helper.assertTrue(!helper.getLevel().getBlockState(under).getCollisionShape(helper.getLevel(), under).isEmpty(), "and on something solid: " + under);
                }
            }
            helper.assertTrue(planted >= 16, "most feet are down mid-walk: " + planted + " planted, " + swinging + " swinging");
            helper.assertTrue(swinging <= 10, "at most the share swings: " + swinging);
            helper.assertTrue(a.speed() > 0.2, "while it walks: " + a.speed());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aClipPlaysOnTheServerAndFiresItsCuesOnTheirTicks(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        helper.assertTrue(a.clipPlaying() == null && a.lastCue() == null, "nothing plays at first");
        a.play(com.chunkworks.aberrantmobs.domain.FaceStealerClips.COIL);
        helper.assertValueEqual(a.clipPlaying(), "coil", "the coil plays");
        helper.runAtTickTime(9, () -> helper.assertValueEqual(a.lastCue(), com.chunkworks.aberrantmobs.domain.FaceStealerClips.CUE_CLICK, "the click by its fifth tick"));
        helper.runAtTickTime(16, () -> helper.assertValueEqual(a.lastCue(), com.chunkworks.aberrantmobs.domain.FaceStealerClips.CUE_HISS, "the hiss by its twelfth"));
        helper.runAtTickTime(22, () -> {
            helper.assertTrue(a.clipPlaying() == null, "and the clip has ended: " + a.clipPlaying());
            helper.assertTrue(a.lastCueTick() > 0, "the cue's tick was kept: " + a.lastCueTick());
            a.play(com.chunkworks.aberrantmobs.domain.FaceStealerClips.DEATH);
            helper.assertValueEqual(a.clipPlaying(), "death", "a new clip starts at once");
            helper.succeed();
        });
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    @GameTest(template = "tall", timeoutTicks = 200)
    public void itCrawlsTheFloorClimbsTheWallAndCrossesTheCeiling(GameTestHelper helper) {
        layFloor(helper);
        fill(helper, 12, FLOOR, 0, 14, 12, 14, Blocks.STONE);   // a wall across the east end
        fill(helper, 0, 13, 0, 14, 15, 14, Blocks.STONE);       // a ceiling over it all
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setScriptedWalk(new com.chunkworks.aberrantmobs.domain.Vec(0.3, 0.0, 0.0), 180);
        java.util.Set<com.chunkworks.aberrantmobs.domain.Crawl.Normal> seen = new java.util.HashSet<>();
        for (int t = 2; t < 180; t += 2) {
            helper.runAtTickTime(t, () -> {
                if (a.crawlPose() != null) {
                    seen.add(a.crawlPose().normal());
                }
            });
        }
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(a.crawlPose() != null && a.crawlPose().normal() == com.chunkworks.aberrantmobs.domain.Crawl.Normal.WEST, "on the wall, whose face looks west: " + a.crawlPose());
            helper.assertTrue(a.getY() - at.y > 2.0, "and up it: " + (a.getY() - at.y));
            helper.assertTrue(Math.abs(a.getX() - (at.x + 12.0 - 2.5 - 23.3 / 16.0)) < 0.3, "its clearance off the wall: " + (a.getX() - at.x));
        });
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(a.crawlPose() != null && a.crawlPose().normal() == com.chunkworks.aberrantmobs.domain.Crawl.Normal.DOWN, "under the ceiling, whose face looks down: " + a.crawlPose());
            helper.assertTrue(a.getY() - at.y > 6.0, "hanging high: " + (a.getY() - at.y));
            helper.assertTrue(a.crawlPose().heading().x() < -0.9, "heading back west along it: " + a.crawlPose().heading());
        });
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(seen.contains(com.chunkworks.aberrantmobs.domain.Crawl.Normal.UP) && seen.contains(com.chunkworks.aberrantmobs.domain.Crawl.Normal.WEST)
                    && seen.contains(com.chunkworks.aberrantmobs.domain.Crawl.Normal.DOWN), "floor, wall and ceiling seen: " + seen);
            helper.succeed();
        });
    }

    @GameTest(template = "tall", timeoutTicks = 260)
    public void itDigsACoherentTunnelToItsTargetAndLeavesBedrockAlone(GameTestHelper helper) {
        layFloor(helper);
        fill(helper, 6, FLOOR, 0, 14, 15, 14, Blocks.STONE);   // a hill filling the east end to the top
        fill(helper, 9, FLOOR, 7, 9, FLOOR + 2, 7, Blocks.BEDROCK);   // three of bedrock in its straight way
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        Vec3 goal = helper.absoluteVec(new Vec3(13.5, FLOOR + 23.3 / 16.0, 7.5));
        a.setCrawlTarget(new com.chunkworks.aberrantmobs.domain.Vec(goal.x, goal.y, goal.z), true, 0.45);
        helper.runAtTickTime(240, () -> {
            helper.assertTrue(a.getX() - at.x > 9.0, "it went in nine blocks and more: " + (a.getX() - at.x));
            helper.assertTrue(a.blocksDug() > 20, "cutting its way: " + a.blocksDug());
            for (int y = FLOOR; y <= FLOOR + 2; y++) {
                helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(9, y, 7));
            }
            com.chunkworks.aberrantmobs.domain.Trail trail = a.trail(1.0);
            for (double d = 0.0; d <= Math.min(9.0, a.distance()); d += 0.5) {
                com.chunkworks.aberrantmobs.domain.Vec p = trail.at(d).pos();
                BlockPos head = BlockPos.containing(p.x(), p.y(), p.z());
                helper.assertTrue(helper.getLevel().getBlockState(head).isAir(), "the way behind the head is open at " + d + ": " + head);
                helper.assertTrue(helper.getLevel().getBlockState(head.north()).isAir() && helper.getLevel().getBlockState(head.south()).isAir(), "and wide: " + head);
            }
            helper.assertTrue(a.crawlPose() != null && !a.crawlPose().airborne(), "still on a face");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public void aPounceLandsWhereItAimed(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        Vec3 spot = helper.absoluteVec(new Vec3(9.5, FLOOR, 7.5));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(a.pounce(new com.chunkworks.aberrantmobs.domain.Vec(spot.x, spot.y, spot.z)), "it leaps");
            helper.assertValueEqual(a.clipPlaying(), "pounce", "with its clip");
        });
        helper.runAtTickTime(8, () -> helper.assertTrue(a.crawlPose() != null && a.crawlPose().airborne() && a.getY() - at.y > 0.5, "in the air: " + a.crawlPose()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(a.crawlPose() != null && a.crawlPose().normal() == com.chunkworks.aberrantmobs.domain.Crawl.Normal.UP, "landed on the floor: " + a.crawlPose());
            helper.assertTrue(Math.abs(a.getX() - spot.x) < 1.5 && Math.abs(a.getZ() - spot.z) < 1.0, "where it aimed: " + (a.getX() - spot.x) + ", " + (a.getZ() - spot.z));
            helper.assertTrue(!a.crawling(), "and is still");
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
            helper.assertTrue(a.getY() - at.y > -0.05 && a.getY() - at.y < 0.5, "on the floor, its box centred on its axis: " + (a.getY() - at.y));
            helper.assertTrue(a.crawlPose() != null && a.crawlPose().normal() == com.chunkworks.aberrantmobs.domain.Crawl.Normal.UP, "clinging to the floor");
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(a.yBodyRot + 90.0f)) < 1.0f, "facing east: " + a.yBodyRot);
            helper.assertTrue(a.distance() > 7.0, "the distance counted: " + a.distance());
            helper.assertTrue(a.speed() < 0.01, "and it stopped when the walk ran out: " + a.speed());
            helper.succeed();
        });
    }
}
