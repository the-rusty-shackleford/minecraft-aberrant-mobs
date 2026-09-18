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
import com.chunkworks.aberrantmobs.AberrantPart;
import com.chunkworks.aberrantmobs.domain.Clip;
import com.chunkworks.aberrantmobs.domain.Crawl;
import com.chunkworks.aberrantmobs.domain.FaceStealerClips;
import com.chunkworks.aberrantmobs.domain.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The protocol on film, with the Face-Stealer as the sitter: a flat world
 * at noon, the creature summoned on the grass facing east, photographed
 * from the side, from its front quarter and up close on its face; then
 * walking, from the side and from above, its feet planted; then each
 * authored clip at its key frames; then climbing a wall of stone across
 * its way, and digging into a hill toward a point inside it. Each
 * frame is saved as {@code booth-<name>.png} in the run's screenshots
 * folder and judged by eye afterwards; the verdict lines ({@code booth:
 * PASS} / {@code booth: FAIL}) are what the Gradle task reads. Silent from
 * the first tick. Active only under {@code aberrantmobs.photobooth}.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class PhotoBooth {
    private PhotoBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs booth");
    private static final boolean ACTIVE = Boolean.getBoolean("aberrantmobs.photobooth");

    private enum Phase { TITLE, LOADING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int SETTLE = 60;
    /** Where the creature stands, its head toward +X. */
    private static final double X = 0.5;
    private static final double Z = 0.5;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID creature;
    /** The x of the dig hill's face, for the dig's verdict; the tree's trunk and the pillar, for theirs. */
    private static double hillFace;
    private static double treeX;
    private static double pillarX;
    /** The wall used by the survival walking fixture. */
    private static double wallX;
    private static String lastClip;
    private static Clip awaitedClip;
    private static int[] clipShots;
    private static int clipWait;
    private static int clipStarted = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick, before the title music: Rusty listens to music while these run.
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.RUNNING;
                    tick = 0;
                    mc.options.hideGui = true;
                    if (Boolean.getBoolean("aberrantmobs.artBooth")) {
                        steps = List.of();
                        onServer(mc, ArtBooth::setUp);
                    } else if (Boolean.getBoolean("aberrantmobs.gravityBooth")) {
                        steps = List.of();
                        onServer(mc, GravityBooth::setUp);
                    } else {
                        steps = plan(mc);
                        onServer(mc, PhotoBooth::setUp);
                    }
                }
            }
            case RUNNING -> {
                if (Boolean.getBoolean("aberrantmobs.artBooth")) {
                    ArtBooth.tick(mc, tick++);
                    return;
                }
                if (Boolean.getBoolean("aberrantmobs.gravityBooth")) {
                    GravityBooth.tick(mc, tick++);
                    return;
                }
                Aberrant observed = find(mc);
                String clip = observed == null ? null : observed.clipPlaying();
                if (!java.util.Objects.equals(lastClip, clip)) {
                    LOG.info("booth: clip observation t={} clip={} entityTick={}", tick, clip, observed == null ? -1 : observed.tickCount);
                    lastClip = clip;
                }
                if (awaitedClip != null) {
                    sampleClip(mc, observed);
                    return; // the scene clock waits; the real server and client keep ticking
                }
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("Aberrant Mobs booth", GameType.CREATIVE, false, Difficulty.NORMAL,   // a monster is discarded in peaceful
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("aberrantmobs-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the creature on the grass, head toward +X; the player hovering north of it, looking south across its side. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        double y = level.getMinBuildHeight() + 4;   // the flat preset's grass tops at -60
        Aberrant a = Aberrant.create(level, FaceStealerGameTests.FACE_STEALER, X, y, Z, -90.0f);
        if (a == null) {
            LOG.error("booth: FAIL the Face-Stealer profile is registered -- Aberrant.create returned null");
            return;
        }
        a.setNoAi(true);
        level.addFreshEntity(a);
        creature = a.getUUID();
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.teleportTo(level, X - 3.0, y + 6.0, Z - 24.0, 0.0f, 12.0f);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X - 3.0, y + 1.5, Z));
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = 0;
        s.add(new Step(t += SETTLE * 2, () -> {
            for (String id : List.of("skitter", "dig_loud", "dig_quiet", "click", "hiss", "screech", "grab", "bite", "crack", "death", "breath")) {
                verdict("the sound " + id + " resolves", () -> mc.getSoundManager().getSoundEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aberrantmobs", id)) != null ? null : "no sound event " + id);
            }
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-side");
            verdict("the client has the creature", () -> find(mc) != null ? null : "no Aberrant among the entities for rendering");
            verdict("from the side it fills the middle of the frame", () -> drawn > 4000 ? null : "creature pixels " + drawn);
        }));
        // The front quarter: ahead of the head and to its right, looking back at it.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), X + 18.0, y + 7.5, Z + 13.5, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X - 1.5, y + 1.8, Z));
        })));
        s.add(new Step(t += SETTLE / 2, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-quarter");
            verdict("from the front quarter it is there", () -> drawn > 3000 ? null : "creature pixels " + drawn);
        }));
        // The face, from three blocks ahead of the head at its eye height.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), X + 8.0, y + 1.95, Z, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X + 3.0, y + 2.1, Z));
        })));
        s.add(new Step(t += SETTLE / 2, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-face");
            verdict("its face fills the frame up close", () -> drawn > 12000 ? null : "creature pixels " + drawn);
        }));
        // The walk: back to the side view, the creature sent twenty blocks along +X under the game's own
        // navigation, a frame every twenty ticks -- the chain along its trail, the writhe, the legs.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), X + 12.0, y + 7.5, Z - 27.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X + 12.0, y + 1.5, Z));
            for (var e : sp.serverLevel().getEntities().getAll()) {
                if (e instanceof Aberrant a && a.getUUID().equals(creature)) {
                    a.setScriptedWalk(new Vec(0.3, 0.0, 0.0), 90);
                }
            }
        })));
        for (int i = 1; i <= 3; i++) {
            final int n = i;
            s.add(new Step(t += 20, () -> {
                int drawn = count(mc, PhotoBooth::creature);
                shoot(mc, "booth-walk-" + n);
                if (n == 3) {
                    Aberrant a = find(mc);
                    verdict("it walked a good way", () -> a != null && a.getX() - X > 5.0 ? null : "x " + (a == null ? null : a.getX() - X));
                    verdict("and is drawn walking", () -> drawn > 2500 ? null : "creature pixels " + drawn);
                }
            }));
        }
        // From straight above, walking: the writhe is a wave in the body seen from the top, the legs a
        // wave of steps down each side.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            for (var e : sp.serverLevel().getEntities().getAll()) {
                if (e instanceof Aberrant a && a.getUUID().equals(creature)) {
                    sp.teleportTo(sp.serverLevel(), a.getX() + 3.0, y + 21.0, Z + 0.01, 0.0f, 90.0f);
                }
            }
        })));
        s.add(new Step(t += 12, () -> shoot(mc, "booth-walk-top")));
        // The crack under a sword (Rusty's report: the glowing segment only clanged; dynamite worked): once the walk
        // is over and the body stands still, the booth player, within a sword's reach north of the cracked segment,
        // aims at it; the client's crosshair must find a part of the body, and a blow on it must take health --
        // the server judges the segment from the attacker's look. (Aimed while the body still walked, the blow
        // landed three segments behind the crack, where the crack had been: the scene, not the rule.)
        s.add(new Step(t += 20, () -> onServer(mc, sp -> onCreature(sp, a -> {
            net.neoforged.neoforge.entity.PartEntity<?> crack = a.getParts()[a.weakSegment()];
            double middle = crack.getY() + crack.getBbHeight() / 2.0;
            sp.teleportTo(sp.serverLevel(), crack.getX(), middle - 1.62, crack.getZ() - crack.getBbWidth() / 2.0 - 2.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(crack.getX(), middle, crack.getZ()));
        }))));
        s.add(new Step(t += 10, () -> {
            Aberrant a = find(mc);
            shoot(mc, "booth-crack-aim");
            verdict("the client's cracked part stands on the body", () -> a != null && a.getParts()[a.weakSegment()].position().distanceTo(a.position()) < 12.0 ? null
                    : "the part is at " + (a == null ? null : a.getParts()[a.weakSegment()].position()) + ", the creature at " + (a == null ? null : a.position()));
            // The parts' boxes overlap along the body, and the head's own box the first of them, so the box the
            // crosshair names may be a neighbour's or the creature's own: the server judges the segment from the
            // attacker's look, and the blow's landing is the test of that.
            verdict("aiming at the crack, the crosshair finds the body", () -> mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e && (e.getEntity() instanceof AberrantPart || e.getEntity() instanceof Aberrant) ? null
                    : "the crosshair is on " + (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e ? e.getEntity() : mc.hitResult));
            verdict("the client's parts are numbered from the creature's id", () -> {
                if (a == null) {
                    return "no creature";
                }
                for (int i = 0; i < a.getParts().length; i++) {
                    if (a.getParts()[i].getId() != a.getId() + i + 1) {
                        return "part " + i + " has id " + a.getParts()[i].getId() + " under creature " + a.getId();
                    }
                }
                return null;
            });
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e && mc.gameMode != null && mc.player != null) {
                LOG.info("booth: the client attacks entity {} ({}) from {} looking {}", e.getEntity().getId(), e.getEntity(), mc.player.getEyePosition(), mc.player.getLookAngle());
                mc.gameMode.attack(mc.player, e.getEntity());
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }));
        s.add(new Step(t += 10, () -> {
            Aberrant a = find(mc);
            verdict("and a blow on the crack takes health", () -> a != null && a.getHealth() < a.getMaxHealth() ? null : "health " + (a == null ? null : a.getHealth() + " of " + a.getMaxHealth()));
        }));
        // The clips: each played on the server where the walk left the creature, shot at its key frames --
        // the coil, the bite, the flinch and the death from the side, the pincers' clips from the front quarter.
        record Shot(Clip clip, boolean quarter, int... at) {}
        List<Shot> shots = List.of(
                new Shot(FaceStealerClips.COIL, false, 8, 15), new Shot(FaceStealerClips.POUNCE, true, 4),
                new Shot(FaceStealerClips.STRIKE, true, 4), new Shot(FaceStealerClips.GRAB, true, 5),
                new Shot(FaceStealerClips.BITE, false, 8, 12), new Shot(FaceStealerClips.FLINCH, false, 3),
                new Shot(FaceStealerClips.DEATH, false, 20, 39));
        for (Shot shot : shots) {
            s.add(new Step(t += 20, () -> onServer(mc, sp -> onCreature(sp, a -> frame(sp, a, shot.quarter())))));
            s.add(new Step(t += 10, () -> {
                awaitedClip = shot.clip();
                clipShots = shot.at();
                clipWait = 0;
                clipStarted = -1;
                onServer(mc, sp -> onCreature(sp, a -> {
                    LOG.info("booth: server starts clip={} entityTick={}", shot.clip().name(), a.tickCount);
                    a.play(shot.clip());
                }));
            }));
            t += shot.clip().ticks();
        }
        s.add(new Step(t += 20, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            verdict("it is drawn after its death clip", () -> drawn > 2000 ? null : "creature pixels " + drawn);
        }));
        // The stolen face: it wears the booth player's, seen up close from ahead; then the grab, from the held one's own eyes.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            a.setFace(sp.getGameProfile());
            sp.teleportTo(sp.serverLevel(), a.getX() + 9.75, y + 1.95, Z, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX() + 3.0, y + 2.1, Z));
        }))));
        s.add(new Step(t += 30, () -> {
            shoot(mc, "booth-face-stolen");
            Aberrant a = find(mc);
            verdict("the client knows whose face it wears", () -> a != null && a.face() != null && a.face().getName().equals(mc.player.getGameProfile().getName()) ? null : "face " + (a == null ? null : a.face()));
        }));
        s.add(new Step(t += 2, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(sp.serverLevel(), a.getX() + 3.0, y, Z, 90.0f, 0.0f);   // within the pincers' reach, clear of the head's box
            verdict("the pincers take the booth player", () -> a.grab(sp) ? null : "the grab was refused at " + (a.getX() + 3.0));
        }))));
        s.add(new Step(t += 4, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX() + 1.5, y + 3.3, Z));
        }))));
        s.add(new Step(t += 8, () -> {
            shoot(mc, "booth-grab-held");
            verdict("the client is held", () -> mc.player != null && mc.player.getVehicle() instanceof Aberrant ? null : "the player rides " + (mc.player == null ? null : mc.player.getVehicle()));
        }));
        s.add(new Step(t += 2, () -> onServer(mc, sp -> onCreature(sp, a -> {
            a.release();
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        }))));
        // The climb: a wall of stone across its way, fifteen high and five thick; it walks into it, up it and over it.
        // At 0.3 a tick it meets the wall (its lookahead 2.4 from the face ten off) by tick 26 and is over the top by
        // about tick 76.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            int wx = (int) Math.floor(a.getX()) + 10;
            fill(sp, wx, (int) y, (int) Z - 6, wx + 4, (int) y + 14, (int) Z + 6, Blocks.STONE);
            a.setScriptedWalk(new Vec(0.3, 0.0, 0.0), 160);
            sp.teleportTo(sp.serverLevel(), wx - 1.5, y + 9.0, Z - 25.5, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(wx - 1.5, y + 6.0, Z));
        }))));
        s.add(new Step(t += 30, () -> shoot(mc, "booth-climb-1")));
        s.add(new Step(t += 14, () -> {
            shoot(mc, "booth-climb-2");
            Aberrant a = find(mc);
            verdict("it is on the wall", () -> a != null && a.syncedNormal() == Crawl.Normal.WEST ? null : "its face is " + (a == null ? null : a.syncedNormal()));
        }));
        s.add(new Step(t += 26, () -> shoot(mc, "booth-climb-3")));
        s.add(new Step(t += 26, () -> shoot(mc, "booth-climb-4")));
        // The dig: set down on the ground before a hill of stone, sent to a point inside it, digging. The hill is
        // eighteen high and twenty-five wide so that boring in through its face (sixteen cells of rock at hunting
        // costs) is the cheapest way to the point: over the top or in from a side would be dearer, and the creature
        // takes the cheapest way.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            double px = Math.floor(a.getX()) + 18.0;
            int hx = (int) px + 9;
            hillFace = hx;
            fill(sp, hx, (int) y, (int) Z - 12, hx + 21, (int) y + 17, (int) Z + 12, Blocks.STONE);
            a.moveTo(px, y, Z, -90.0f, 0.0f);
            a.resetCrawl();
            a.setCrawlTarget(new Vec(hx + 15.5, y + 23.3 * 1.5 / 16.0, Z), true, 0.45);
            sp.teleportTo(sp.serverLevel(), hx - 1.5, y + 6.0, Z - 21.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(hx - 1.5, y + 2.25, Z));
        }))));
        s.add(new Step(t += 40, () -> shoot(mc, "booth-dig-1")));
        s.add(new Step(t += 60, () -> onServer(mc, sp -> onCreature(sp, a -> {
            // From the tunnel's mouth, looking in after it.
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), a.getX() - 13.5, y + 2.1, Z, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX(), y + 1.8, Z));
        }))));
        s.add(new Step(t += 10, () -> {
            shoot(mc, "booth-dig-2");
            // More than one strike's worth cut (a straight section is 82), and its head inside the hill.
            onServer(mc, sp -> onCreature(sp, a -> verdict("it dug its way in", () -> a.blocksDug() > 82 && a.getX() > hillFace + 2.0 ? null
                    : "dug " + a.blocksDug() + " blocks, at x " + a.getX() + " with the hill's face at " + hillFace)));
        }));
        // What Rusty met on the surface (2026-09-15): a tree in its way, and things it got stuck on or flipped
        // over at. Set down in the open beyond the hill and sent past an oak, digging allowed as the mind allows
        // it; then past a lone pillar of stone. The frames say whether it climbs, chews, hangs or stands.
        s.add(new Step(t += 20, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            double px = hillFace + 30.0;
            int tx = (int) px + 12;
            treeX = tx;
            oak(sp, tx, (int) y, (int) Z);
            a.moveTo(px, y, Z, -90.0f, 0.0f);
            a.resetCrawl();
            a.setCrawlTarget(new Vec(tx + 12.5, y + 23.3 * 1.5 / 16.0, Z), true, 0.45);
            sp.teleportTo(sp.serverLevel(), tx - 2.0, y + 6.0, Z - 22.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(tx - 2.0, y + 3.5, Z));
        }))));
        s.add(new Step(t += 40, () -> shoot(mc, "booth-tree-1")));
        s.add(new Step(t += 40, () -> shoot(mc, "booth-tree-2")));
        s.add(new Step(t += 80, () -> shoot(mc, "booth-tree-3")));
        s.add(new Step(t += 80, () -> {
            shoot(mc, "booth-tree-4");
            Aberrant a = find(mc);
            verdict("it got past the tree", () -> a != null && a.getX() > treeX + 4.0 ? null : "at x " + (a == null ? null : a.getX() - treeX) + " from the trunk, on " + (a == null ? null : a.syncedNormal()));
            verdict("and stands on the ground past it", () -> a != null && a.syncedNormal() == Crawl.Normal.UP ? null : "on " + (a == null ? null : a.syncedNormal()));
        }));
        s.add(new Step(t += 20, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            double px = treeX + 30.0;
            int cx = (int) px + 12;
            pillarX = cx;
            fill(sp, cx, (int) y, (int) Z, cx, (int) y + 7, (int) Z, Blocks.STONE);
            a.moveTo(px, y, Z, -90.0f, 0.0f);
            a.resetCrawl();
            a.setCrawlTarget(new Vec(cx + 12.5, y + 23.3 * 1.5 / 16.0, Z), true, 0.45);
            sp.teleportTo(sp.serverLevel(), cx - 2.0, y + 6.0, Z - 22.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(cx - 2.0, y + 3.5, Z));
        }))));
        s.add(new Step(t += 40, () -> shoot(mc, "booth-pillar-1")));
        s.add(new Step(t += 40, () -> shoot(mc, "booth-pillar-2")));
        s.add(new Step(t += 80, () -> shoot(mc, "booth-pillar-3")));
        s.add(new Step(t += 80, () -> {
            shoot(mc, "booth-pillar-4");
            Aberrant a = find(mc);
            verdict("it got past the pillar", () -> a != null && a.getX() > pillarX + 4.0 ? null : "at x " + (a == null ? null : a.getX() - pillarX) + " from the pillar, on " + (a == null ? null : a.syncedNormal()));
            verdict("and stands on the ground past it", () -> a != null && a.syncedNormal() == Crawl.Normal.UP ? null : "on " + (a == null ? null : a.syncedNormal()));
        }));
        // The chitin armour: the booth player in the full set, set down before a wall and walked into it;
        // from its own eyes the world rolls, from behind it stands on the wall.
        s.add(new Step(t += 20, () -> onServer(mc, sp -> onCreature(sp, a -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            double px = Math.floor(a.getX()) + 30.0;
            int wx = (int) px + 6;
            fill(sp, wx, (int) y, (int) Z - 5, wx + 3, (int) y + 8, (int) Z + 5, Blocks.STONE);
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.chunkworks.aberrantmobs.ModContent.CHITIN_HELMET.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.chunkworks.aberrantmobs.ModContent.CHITIN_CHESTPLATE.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(com.chunkworks.aberrantmobs.ModContent.CHITIN_LEGGINGS.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(com.chunkworks.aberrantmobs.ModContent.CHITIN_BOOTS.get()));
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(sp.serverLevel(), px, y, Z, -90.0f, 0.0f);
            // In survival, as Rusty plays: the server's check on every reported move ("moved wrongly", a teleport
            // back) is skipped for a creative player, and it is that check the wall-walk has to pass.
            sp.setGameMode(GameType.SURVIVAL);
            wallX = wx;
        }))));
        s.add(new Step(t += 3, () -> verdict("the client was let go of the maw", () -> mc.player != null && !mc.player.isPassenger() ? null : "the client still rides " + (mc.player == null ? null : mc.player.getVehicle()))));
        // Send a snapshot after each ordinary movement packet on the same connection.
        // Comparing live client/server entities across threads can mix different ticks.
        s.add(new Step(t += 4, () -> {
            MovementSample.reset();
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(true);
        }));
        for (int i = 1; i <= 60; i++) {
            s.add(new Step(t + i, () -> {
                if (mc.player != null) {
                    Vec3 position = mc.player.position();
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new MovementSample(position.x, position.y, position.z));
                }
            }));
        }
        s.add(new Step(t += 60, () -> { mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); }));
        s.add(new Step(t += 6, () -> {
            shoot(mc, "booth-wallwalk-eyes");
            int visible = count(mc, rgb -> Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255)) > 32);
            verdict("the first-person wall view stays visible", () -> visible > 1000 ? null : "bright pixels " + visible);
            verdict("the client stands on the wall", () -> mc.player != null && com.chunkworks.aberrantmobs.wallwalk.WallWalk.frameOf(mc.player).gravity() == com.chunkworks.aberrantmobs.domain.frame.Gravity.EAST
                    ? null : "the client's gravity is " + (mc.player == null ? null : com.chunkworks.aberrantmobs.wallwalk.WallWalk.frameOf(mc.player).gravity()));
            double y = mc.level.getMinBuildHeight() + 4;
            verdict("and has climbed it in survival", () -> mc.player != null && mc.player.getY() - y > 3.0 ? null : "the client is " + (mc.player == null ? null : mc.player.getY() - y) + " up");
            verdict("the server agreed with every move", MovementSample::verdict);
            onServer(mc, sp -> verdict("and stands on the wall itself", () -> com.chunkworks.aberrantmobs.wallwalk.WallWalk.frameOf(sp).gravity() == com.chunkworks.aberrantmobs.domain.frame.Gravity.EAST && sp.getY() - y > 3.0
                    ? null : "the server's gravity is " + com.chunkworks.aberrantmobs.wallwalk.WallWalk.frameOf(sp).gravity() + ", " + (sp.getY() - y) + " up"));
            // From the front: the camera backs off along the look, which on a wall is up it and into open air.
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        }));
        s.add(new Step(t += 6, () -> {
            shoot(mc, "booth-wallwalk-third");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }));
        s.add(new Step(t += 6, () -> {
            shoot(mc, "booth-wallwalk-back");
            var camera = mc.gameRenderer.getMainCamera();
            LOG.info("booth: camera back position={} eye={} look={} cameraLook={}", camera.getPosition(),
                    mc.player.getEyePosition(), mc.player.getLookAngle(), camera.getLookVector());
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }));
        s.add(new Step(t += 10, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    private static void fill(ServerPlayer sp, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    sp.serverLevel().setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * effects: grows an oak with its trunk's foot at {@code (x, y, z)}: six logs, two five-wide layers of leaves
     * without their corners on the fourth and fifth, a ring of eight on the sixth and a cross on the seventh, the
     * leaves persistent so they do not decay in the frame
     */
    private static void oak(ServerPlayer sp, int x, int y, int z) {
        for (int i = 0; i < 6; i++) {
            sp.serverLevel().setBlock(new BlockPos(x, y + i, z), Blocks.OAK_LOG.defaultBlockState(), 3);
        }
        net.minecraft.world.level.block.state.BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int dy = 3; dy <= 4; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((Math.abs(dx) == 2 && Math.abs(dz) == 2) || (dx == 0 && dz == 0)) {
                        continue;
                    }
                    sp.serverLevel().setBlock(new BlockPos(x + dx, y + dy, z + dz), leaves, 3);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    sp.serverLevel().setBlock(new BlockPos(x + dx, y + 5, z + dz), leaves, 3);
                }
            }
        }
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            sp.serverLevel().setBlock(new BlockPos(x + d[0], y + 6, z + d[1]), leaves, 3);
        }
    }

    /** effects: puts the camera on the creature's front half from the north side, or from its front quarter */
    private static void frame(ServerPlayer sp, Aberrant a, boolean quarter) {
        double y = sp.serverLevel().getMinBuildHeight() + 4;
        if (quarter) {
            sp.teleportTo(sp.serverLevel(), a.getX() + 12.0, y + 6.0, Z + 9.75, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX() - 0.75, y + 2.1, Z));
        } else {
            sp.teleportTo(sp.serverLevel(), a.getX() - 3.75, y + 6.0, Z - 19.5, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX() - 3.75, y + 1.8, Z));
        }
    }

    /** effects: photographs keyframes from observed playback; a missing clip fails after forty real client ticks. */
    private static void sampleClip(Minecraft mc, Aberrant actor) {
        if (clipStarted < 0) {
            if (actor == null || !awaitedClip.name().equals(actor.clipPlaying())) {
                if (++clipWait >= 40) {
                    verdict("the client plays the " + awaitedClip.name(), () -> "clip did not arrive within forty client ticks");
                    awaitedClip = null;
                }
                return;
            }
            clipStarted = actor.tickCount;
            verdict("the client plays the " + awaitedClip.name(), () -> null);
        }
        int age = actor.tickCount - clipStarted + 1;
        for (int at : clipShots) {
            if (age == at) shoot(mc, "booth-" + awaitedClip.name() + "-" + at);
        }
        if (age >= clipShots[clipShots.length - 1]) awaitedClip = null;
    }

    private static void onCreature(ServerPlayer sp, Consumer<Aberrant> action) {
        for (var e : sp.serverLevel().getEntities().getAll()) {
            if (e instanceof Aberrant a && a.getUUID().equals(creature)) {
                action.accept(a);
            }
        }
    }

    private static Aberrant find(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Aberrant a && e.getUUID().equals(creature)) {
                return a;
            }
        }
        return null;
    }

    /** effects: a pixel that is neither the sky's blue nor the grass's green: the creature, in a frame of nothing else */
    private static boolean creature(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        boolean sky = b > r + 20 && b > g;
        boolean grass = g > r + 15 && g > b + 15;
        return !sky && !grass;
    }

    /** effects: counts the pixels of the middle of the frame (rows 30..75 %, columns 15..85 %) that pass {@code test} */
    private static int count(Minecraft mc, IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = (int) (h * 0.30); y < (int) (h * 0.75); y++) {
                for (int x = (int) (w * 0.15); x < (int) (w * 0.85); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    if (test.test(rgb)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("booth: {}", message.getString()));
    }

    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
