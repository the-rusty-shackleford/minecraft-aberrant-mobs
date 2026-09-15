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
 * through rock and leaves bedrock alone; sent over a thick wall it climbs
 * it rather than cutting it; a pounce lands where it aimed.
 * With its mind on: a distant step turns roaming into prowling toward a
 * vague bearing, a near noise is placed exactly, a block broken in the
 * world reaches its ears through the game's events, a player seen
 * underground is stalked -- out of their view -- and their eye contact
 * starts the hunt. The grab holds a player at the maw, refuses their
 * dismount, pinches; the bite devours through the damage pipeline as
 * {@code aberrantmobs:devoured} and takes the face; a player blessed at
 * the miracle's door survives it, is let go, and the creature flees;
 * hunting a player in sight it coils, then pounces. The spawn rules
 * refuse the lit surface and accept a dark pocket by thick rock, where a
 * creature spawned takes the fitting profile and bores its pocket into the
 * wall; killed, it drops its chitin and its cracked plate and experience.
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
                helper.assertTrue(helper.getLevel().getBlockState(head.north()).isAir() && helper.getLevel().getBlockState(head.south()).isAir(),
                        "and wide at " + d + " behind, " + p + ": " + head + " north " + helper.getLevel().getBlockState(head.north()).getBlock()
                                + " south " + helper.getLevel().getBlockState(head.south()).getBlock() + "; the head at " + a.crawlPose());
            }
            helper.assertTrue(a.crawlPose() != null && !a.crawlPose().airborne(), "still on a face");
            helper.succeed();
        });
    }

    @GameTest(template = "tall", timeoutTicks = 260)
    public void sentOverAThickWallItClimbsItRatherThanCuttingIt(GameTestHelper helper) {
        layFloor(helper);
        // A wall across its way, seven thick and seven high: at hunting costs the way over it is cheaper than
        // the way through it, so the way climbs. Digging is allowed, and must not be used on the wall the way
        // climbs -- the booth found the creature cutting the foot of a hill its way went over, then standing
        // blocked for good with no wall left to take.
        fill(helper, 6, FLOOR, 0, 12, FLOOR + 6, 14, Blocks.STONE);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setNoAi(true);   // moved by the test, not by its mind
        Vec3 goal = helper.absoluteVec(new Vec3(13.5, FLOOR + 23.3 / 16.0, 7.5));
        a.setCrawlTarget(new com.chunkworks.aberrantmobs.domain.Vec(goal.x, goal.y, goal.z), true, 0.45);
        double[] highest = {0.0};
        for (int t = 2; t < 240; t += 2) {
            helper.runAtTickTime(t, () -> {
                if (a.crawlPose() != null) {
                    highest[0] = Math.max(highest[0], a.crawlPose().centre().y() - at.y);
                }
            });
        }
        helper.runAtTickTime(240, () -> {
            helper.assertTrue(a.blocksDug() == 0, "the wall it climbs is not cut: " + a.blocksDug() + " blocks dug");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, FLOOR + 1, 7));
            helper.assertTrue(highest[0] > 7.5, "its head went over the top: highest " + highest[0]);
            helper.assertTrue(!a.crawling(), "and it arrived: at x " + (a.getX() - at.x) + ", " + (a.crawling() ? "still under way" : "done"));
            helper.assertTrue(a.getX() - at.x > 10.0, "beyond the wall: " + (a.getX() - at.x));
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
        a.setNoAi(true);   // moved by the test, not by its mind
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

    /** effects: takes away every player another test left near this one's arena, so a minded creature sees only its own */
    private static void clearPlayers(GameTestHelper helper) {
        for (net.minecraft.world.entity.player.Player p : helper.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.player.Player.class), helper.getBounds().inflate(96), e -> true)) {
            p.discard();
        }
    }

    private static Aberrant minded(GameTestHelper helper, double x, double z, float yaw) {
        clearPlayers(helper);
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, yaw);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        return a;
    }

    // The minded tests run one to a batch: batches run in turn, so no creature sees another test's player.
    @GameTest(template = "arena", timeoutTicks = 60, batch = "mind_prowl")
    public void aDistantStepTurnsRoamingIntoProwlingTowardAVagueBearing(GameTestHelper helper) {
        layFloor(helper);
        Aberrant a = minded(helper, 2.5, 7.5, -90.0f);
        helper.assertTrue(a.profile() != null && a.profile().mind().isPresent(), "the Face-Stealer has a mind");
        helper.runAtTickTime(2, () -> {
            helper.assertValueEqual(a.mode(), "roam", "it starts roaming");
            helper.assertValueEqual(a.verb(), "wander", "and wanders");
            com.chunkworks.aberrantmobs.domain.Vec far = a.axis().plus(new com.chunkworks.aberrantmobs.domain.Vec(250, 0, 0));
            a.hear(new com.chunkworks.aberrantmobs.domain.Hearing.Sound(far, 1.0, a.tickCount, "someone"));
        });
        helper.runAtTickTime(22, () -> {
            helper.assertValueEqual(a.mode(), "prowl", "a step heard: prowling");
            helper.assertValueEqual(a.verb(), "approach", "toward it");
            com.chunkworks.aberrantmobs.domain.Hearing.Estimate e = a.hearing().estimate(a.axis(), a.tickCount).orElseThrow();
            helper.assertTrue(e.error() >= 100.0, "only a vague bearing at that range: " + e.error());
            helper.assertTrue(a.crawlPose() != null && a.crawlPose().heading().x() > 0.5, "heading that way: " + a.crawlPose().heading());
            a.hear(new com.chunkworks.aberrantmobs.domain.Hearing.Sound(a.axis().plus(new com.chunkworks.aberrantmobs.domain.Vec(20, 0, 0)), 1.0, a.tickCount, "someone"));
        });
        helper.runAtTickTime(24, () -> {
            com.chunkworks.aberrantmobs.domain.Hearing.Estimate e = a.hearing().estimate(a.axis(), a.tickCount).orElseThrow();
            helper.assertTrue(e.error() == 0.0 && Math.abs(e.distance() - 20.0) < 1.0, "a near noise is placed exactly: " + e.error() + " at " + e.distance());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "mind_ears")
    public void aBlockBrokenNearbyReachesItsEarsThroughTheWorld(GameTestHelper helper) {
        layFloor(helper);
        Aberrant a = minded(helper, 2.5, 7.5, -90.0f);
        helper.runAtTickTime(5, () -> {
            helper.assertValueEqual(a.hearing().remembered(), 0, "nothing heard yet");
            helper.destroyBlock(new BlockPos(11, FLOOR - 1, 7));
        });
        helper.runAtTickTime(12, () -> {
            helper.assertValueEqual(a.hearing().remembered(), 1, "the world's block break was heard");
            helper.assertValueEqual(a.mode(), "prowl", "and prowled toward");
            helper.assertTrue(a.hearing().estimate(a.axis(), a.tickCount).orElseThrow().loud(), "a loud one");
            helper.succeed();
        });
    }

    @GameTest(template = "tall", timeoutTicks = 260, batch = "mind_stalk")
    public void aPlayerSeenUndergroundIsStalkedInTheDarkAndEyeContactStartsTheHunt(GameTestHelper helper) {
        layFloor(helper);
        fill(helper, 0, 13, 0, 14, 15, 14, Blocks.STONE);   // a ceiling: underground
        fill(helper, 7, FLOOR, 4, 7, FLOOR + 4, 10, Blocks.STONE);   // a wall between them: no line of sight
        Aberrant a = minded(helper, 2.5, 7.5, -90.0f);
        net.minecraft.server.level.ServerPlayer p = helper.makeMockServerPlayerInLevel();   // in the level, unlike a mock player
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(12.5, FLOOR, 7.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, 90.0f, 0.0f);
        // The player stares at where the creature is the whole time; through the wall that means nothing.
        helper.onEachTick(() -> p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX(), a.getEyeY(), a.getZ())));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(p.blockPosition()), "the player is under the ceiling: sky light " + helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, p.blockPosition()));
            helper.assertValueEqual(a.mode(), "stalk", "a player sensed underground: stalking");
            helper.assertValueEqual(a.verb(), "approach", "out of their sight, closing in");
        });
        helper.succeedWhen(() -> {
            helper.assertValueEqual(a.mode(), "hunt", "through the wall and into their eyes: the hunt is on");
            helper.assertTrue(a.blocksDug() > 0, "it bored through: " + a.blocksDug());
            helper.assertTrue(java.util.Set.of("chase", "pounce", "grab").contains(a.verb()), "coming for them: " + a.verb());
        });
    }

    private static net.minecraft.server.level.ServerPlayer survivor(GameTestHelper helper, double x, double z, float yaw) {
        net.minecraft.server.level.ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, yaw, 0.0f);
        p.yHeadRot = yaw;
        return p;   // a fresh player has three seconds of respawn protection, which the bite honours: the tests wait it out
    }

    /** A fresh server player's respawn protection, ticks; the bite honours it. */
    private static final int SPAWN_PROTECTION = 62;

    @GameTest(template = "arena", timeoutTicks = 160, batch = "grab")
    public void theGrabHoldsThemAtTheMawAndTheBiteDevoursThem(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setNoAi(true);
        net.minecraft.server.level.ServerPlayer p = survivor(helper, 9.5, 7.5, 90.0f);
        float full = p.getHealth();
        helper.runAtTickTime(SPAWN_PROTECTION + 3, () -> {
            helper.assertTrue(a.grab(p), "the pincers close on a player within reach");
            helper.assertValueEqual(a.clipPlaying(), "grab", "the grab plays");
        });
        helper.runAtTickTime(SPAWN_PROTECTION + 6, () -> {
            helper.assertTrue(p.getVehicle() == a && a.holding() && a.held() == p, "held: riding the creature");
            com.chunkworks.aberrantmobs.domain.Vec maw = a.axis().plus(a.facing().times(22.8 / 16.0));
            double off = new com.chunkworks.aberrantmobs.domain.Vec(p.getX(), p.getY() + p.getBbHeight() / 2.0, p.getZ()).minus(maw).length();
            helper.assertTrue(off < 1.5, "at the maw: " + off);
            p.stopRiding();
            helper.assertTrue(p.getVehicle() == a, "and cannot climb off");
        });
        helper.runAtTickTime(SPAWN_PROTECTION + 12, () -> {
            helper.assertTrue(p.getHealth() < full, "pinched as the pincers closed: " + p.getHealth());
            helper.assertTrue(a.bite(), "the bite");
            helper.assertValueEqual(a.clipPlaying(), "bite", "plays");
        });
        helper.runAtTickTime(SPAWN_PROTECTION + 30, () -> {
            helper.assertTrue(!p.isAlive(), "devoured: " + p.getHealth());
            helper.assertTrue(p.getLastDamageSource() != null && p.getLastDamageSource().is(AberrantMobs.DEVOURED), "by the devouring: " + p.getLastDamageSource());
            helper.assertTrue(a.face() != null && p.getGameProfile().getName().equals(a.face().getName()), "and it wears their face: " + a.face());
            helper.assertTrue(!a.holding() && a.held() == null, "the pincers empty");
            helper.succeed();
        });
    }

    @GameTest(template = "tall", timeoutTicks = 320, batch = "blessed")
    public void aBlessedPlayerSurvivesTheBiteIsLetGoAndTheCreatureFlees(GameTestHelper helper) {
        layFloor(helper);
        fill(helper, 0, 13, 0, 14, 15, 14, Blocks.STONE);
        Aberrant a = minded(helper, 4.5, 7.5, -90.0f);
        a.setNoAi(true);   // asleep until the player's respawn protection is over
        net.minecraft.server.level.ServerPlayer p = survivor(helper, 6.3, 7.5, 90.0f);
        p.addTag("blessed");
        int seenBefore = GameTestMod.Blessing.devouredSeen;
        helper.runAtTickTime(SPAWN_PROTECTION, () -> {
            a.setNoAi(false);
            a.hurtSegment(a.weakSegment(), helper.getLevel().damageSources().playerAttack(p), 1.0f);
        });
        helper.runAtTickTime(SPAWN_PROTECTION + 4, () -> helper.assertValueEqual(a.mode(), "hunt", "struck: hunting"));
        helper.succeedWhen(() -> {
            helper.assertTrue(GameTestMod.Blessing.devouredSeen > seenBefore, "the devouring blow reached the miracle's door");
            helper.assertTrue(p.isAlive(), "and the blessed one lives: " + p.getHealth());
            helper.assertTrue(p.getVehicle() == null && !a.holding(), "let go");
            helper.assertValueEqual(a.mode(), "flee", "and the creature flees");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "pounce")
    public void huntingAPlayerInSightItCoilsThenPounces(GameTestHelper helper) {
        layFloor(helper);
        Aberrant a = minded(helper, 2.5, 7.5, -90.0f);
        net.minecraft.server.level.ServerPlayer p = survivor(helper, 9.5, 7.5, 90.0f);
        java.util.List<String> clips = new java.util.ArrayList<>();
        helper.onEachTick(() -> {
            String c = a.clipPlaying();
            if (c != null && (clips.isEmpty() || !clips.get(clips.size() - 1).equals(c))) {
                clips.add(c);
            }
        });
        helper.runAtTickTime(2, () -> a.hurtSegment(a.weakSegment(), helper.getLevel().damageSources().playerAttack(p), 1.0f));
        helper.succeedWhen(() -> {
            int coil = clips.indexOf("coil"), pounce = clips.indexOf("pounce");
            helper.assertTrue(coil >= 0, "it coiled: " + clips);
            helper.assertTrue(pounce > coil, "then pounced: " + clips);
            double d = a.axis().minus(new com.chunkworks.aberrantmobs.domain.Vec(p.getX(), p.getY() + 1, p.getZ())).length();
            helper.assertTrue(d < 3.5 || a.holding(), "and landed on them: " + d);
        });
    }

    @GameTest(template = "tall", timeoutTicks = 200, batch = "spawn")
    public void theSpawnRulesRefuseTheLitSurfaceAndAcceptADarkPocketByThickRock(GameTestHelper helper) {
        layFloor(helper);
        BlockPos lit = helper.absolutePos(new BlockPos(7, FLOOR, 7));
        helper.assertTrue(!net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(com.chunkworks.aberrantmobs.ModContent.ABERRANT.get(), helper.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, lit, helper.getLevel().getRandom()), "not on the lit surface");
        fill(helper, 0, FLOOR, 0, 14, 15, 14, Blocks.STONE);   // the whole template rock
        fill(helper, 7, 5, 7, 7, 7, 7, Blocks.AIR);            // a chimney of cave in the middle, dark, seven of rock each way
        BlockPos pocket = helper.absolutePos(new BlockPos(7, 5, 7));
        // The light engine works off the server thread and the gametest server does not pace its ticks, so the
        // pocket goes dark after a wall-clock delay, not a tick count: wait for it.
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, pocket) == 0,
                "dark: sky " + helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, pocket))).thenExecute(() -> {
            helper.assertTrue(com.chunkworks.aberrantmobs.SpawnRules.siteBeside(helper.getLevel(), pocket), "a wall six thick lies beside it");
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(com.chunkworks.aberrantmobs.ModContent.ABERRANT.get(), helper.getLevel(), net.minecraft.world.entity.MobSpawnType.NATURAL, pocket, helper.getLevel().getRandom()), "a dark pocket by thick rock will do");
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(com.chunkworks.aberrantmobs.ModContent.ABERRANT.get(), helper.getLevel(), net.minecraft.world.entity.MobSpawnType.COMMAND, lit, helper.getLevel().getRandom()), "a command puts it anywhere");
            // Come into the world there: it takes the Face-Stealer's profile and bores its pocket six into the wall.
            Aberrant a = com.chunkworks.aberrantmobs.ModContent.ABERRANT.get().create(helper.getLevel());
            a.setPos(pocket.getX() + 0.5, pocket.getY(), pocket.getZ() + 0.5);
            a.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pocket), net.minecraft.world.entity.MobSpawnType.NATURAL, null);
            helper.getLevel().addFreshEntity(a);
            helper.assertValueEqual(a.profileId(), FACE_STEALER, "the profile whose habitat fits");
            helper.assertTrue(Math.abs(a.getX() - pocket.getX() - 0.5) > 5.5 || Math.abs(a.getZ() - pocket.getZ() - 0.5) > 5.5, "in the wall, six blocks off: " + (a.getX() - pocket.getX()) + ", " + (a.getZ() - pocket.getZ()));
            BlockPos inside = BlockPos.containing(a.getX(), a.getY() + 1.0, a.getZ());
            helper.assertTrue(helper.getLevel().getBlockState(inside).isAir(), "in a pocket it bored: " + inside);
            helper.assertTrue(a.blocksDug() >= 20, "the pocket's rock cut: " + a.blocksDug());
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "loot")
    public void killedItDropsItsChitinItsCrackedPlateAndSometimesTheFace(GameTestHelper helper) {
        layFloor(helper);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, FLOOR, 7.5));
        Aberrant a = Aberrant.create(helper.getLevel(), FACE_STEALER, at.x, at.y, at.z, -90.0f);
        helper.assertTrue(a != null, "the creature is made");
        helper.getLevel().addFreshEntity(a);
        a.setNoAi(true);
        helper.assertValueEqual(a.getDefaultLootTable().location(), AberrantMobs.id("creature/face_stealer"), "the profile's loot table");
        net.minecraft.world.item.ItemStack trophy = com.chunkworks.aberrantmobs.StolenFaceItem.of(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "nfx"));
        helper.assertValueEqual(com.chunkworks.aberrantmobs.StolenFaceItem.whose(trophy), "nfx", "a stolen face names its owner");
        net.minecraft.world.entity.player.Player killer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.getParts()[a.weakSegment()].hurt(helper.getLevel().damageSources().playerAttack(killer), 1.0e6f), "a player's killing blow on the crack");
            helper.assertTrue(a.isDeadOrDying(), "dying");
            helper.assertValueEqual(a.clipPlaying(), "death", "the death clip plays");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(a.isRemoved(), "taken away after its forty ticks");
            int chitin = 0, plates = 0;
            for (net.minecraft.world.entity.item.ItemEntity item : helper.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class), helper.getBounds().inflate(4), e -> true)) {
                if (item.getItem().is(com.chunkworks.aberrantmobs.ModContent.CHITIN.get())) {
                    chitin += item.getItem().getCount();
                }
                if (item.getItem().is(com.chunkworks.aberrantmobs.ModContent.CRACKED_CARAPACE.get())) {
                    plates += item.getItem().getCount();
                }
            }
            helper.assertTrue(chitin >= 12 && chitin <= 18, "twelve to eighteen chitin: " + chitin);
            helper.assertValueEqual(plates, 1, "and its cracked plate");
            helper.assertTrue(!helper.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.ExperienceOrb.class), helper.getBounds().inflate(4), e -> true).isEmpty(), "and experience");
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
        a.setNoAi(true);   // moved by the test, not by its mind
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
