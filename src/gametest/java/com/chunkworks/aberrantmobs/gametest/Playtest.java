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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The dread, played: a real world (a fixed seed, normal generation), the
 * player in survival set down in a dark cave deep underground, blessed
 * at the miracle's door so the run outlives a bite, and a Face-Stealer
 * come into the world on its own thirty to fifty blocks off in the rock
 * beside another cave. The player breaks a block; the creature hears,
 * prowls, stalks, hunts. Every ten ticks the server logs what it is
 * doing and how far it is; every eighty the player looks its way and a
 * frame is saved as {@code playtest-<n>.png}: read the log's numbers
 * first, then the frames. The verdicts: it knew of the player within five
 * seconds, dug its way, came near, stalked then hunted, closed for the
 * kill. Silent from the first tick. Active only under
 * {@code aberrantmobs.playtest}.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class Playtest {
    private Playtest() {}

    private static final Logger LOG = LoggerFactory.getLogger("Aberrant Mobs playtest");
    private static final boolean ACTIVE = Boolean.getBoolean("aberrantmobs.playtest");
    private static final long SEED = 20260915L;
    private static final int TICKS = 1000;

    private enum Phase { TITLE, LOADING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID creature;
    private static BlockPos lair;
    // What the run saw, for the verdicts.
    private static boolean knew, stalked, hunted, grabbed, dug, pounced;
    private static int knewAt = -1;
    private static double nearest = Double.MAX_VALUE;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
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
                if (mc.level != null && mc.player != null && mc.screen == null && server != null && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.RUNNING;
                    tick = 0;
                    mc.options.hideGui = true;
                    mc.options.gamma().set(1.0);   // the cave is pitch dark and the player carries no light: the frames must still be readable
                    steps = plan(mc);
                    onServer(mc, Playtest::setUp);
                }
            }
            case RUNNING -> {
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
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);   // only the one we place
        LevelSettings settings = new LevelSettings("Aberrant Mobs playtest", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(SEED, false, false);
        mc.createWorldOpenFlows().createFreshLevel("aberrantmobs-playtest", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), mc.screen);
    }

    /** effects: the player in a dark cave, blessed; the creature come into the world in the rock by another cave thirty to fifty blocks off */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(18000L);
        BlockPos spawn = sp.blockPosition();
        BlockPos cave = findCave(level, spawn, 64, 0, null);
        if (cave == null) {
            LOG.error("playtest: FAIL no dark cave within 64 blocks of {}", spawn);
            return;
        }
        sp.setGameMode(GameType.SURVIVAL);
        sp.addTag("blessed");
        sp.teleportTo(level, cave.getX() + 0.5, cave.getY(), cave.getZ() + 0.5, 0.0f, 0.0f);
        LOG.info("playtest: the player set down in a cave at {} (sky light {})", cave, level.getBrightness(LightLayer.SKY, cave));
        BlockPos den = findCave(level, cave, 56, 30, level);
        if (den == null) {
            LOG.error("playtest: FAIL no second cave the spawn rules take thirty blocks off");
            return;
        }
        Aberrant a = com.chunkworks.aberrantmobs.ModContent.ABERRANT.get().create(level);
        a.setPos(den.getX() + 0.5, den.getY(), den.getZ() + 0.5);
        a.finalizeSpawn(level, level.getCurrentDifficultyAt(den), MobSpawnType.NATURAL, null);
        if (a.profileId() == null || a.isRemoved()) {
            LOG.error("playtest: FAIL the spawn rules took {} but no profile's habitat did (sky {}, block {})", den,
                    level.getBrightness(LightLayer.SKY, den), level.getBrightness(LightLayer.BLOCK, den));
            return;
        }
        level.addFreshEntity(a);
        creature = a.getUUID();
        lair = a.blockPosition();
        LOG.info("playtest: the Face-Stealer came into the world at {} from the cave at {}, {} blocks from the player, profile {}, pocket cut {}",
                lair, den, String.format("%.1f", Math.sqrt(lair.distSqr(cave))), a.profileId(), a.blocksDug());
    }

    /**
     * effects: returns a cell of a dark cave (three of air over rock, no sky
     * light) within {@code radius} of {@code centre} and at least
     * {@code minDist} from it, below y = -8; when {@code rules} is given,
     * one the game's own spawn rules take for the creature (dark for its
     * habitat, a wall thick enough beside it, room for it); null when none
     */
    @Nullable
    private static BlockPos findCave(ServerLevel level, BlockPos centre, int radius, int minDist, @Nullable ServerLevel rules) {
        for (int r = 0; r <= radius; r += 3) {
            for (int dx = -r; dx <= r; dx += 3) {
                for (int dz = -r; dz <= r; dz += 3) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = centre.getX() + dx, z = centre.getZ() + dz;
                    level.getChunk(x >> 4, z >> 4);
                    for (int y = -8; y >= -56; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) {
                            continue;
                        }
                        if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() || level.getBrightness(LightLayer.SKY, p) > 0) {
                            continue;
                        }
                        if (p.distSqr(centre) < (double) minDist * minDist) {
                            continue;
                        }
                        if (rules != null && !net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(com.chunkworks.aberrantmobs.ModContent.ABERRANT.get(), rules, MobSpawnType.NATURAL, p, rules.getRandom())) {
                            continue;
                        }
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        // A noise: the player breaks the block at its feet's side, loud enough to be heard across the rock.
        s.add(new Step(80, () -> onServer(mc, sp -> {
            BlockPos at = sp.blockPosition().relative(sp.getDirection());
            if (!sp.serverLevel().getBlockState(at).isAir()) {
                sp.serverLevel().destroyBlock(at, false, sp);
                LOG.info("playtest: the player broke {} at {}", sp.serverLevel().getBlockState(at), at);
            } else {
                sp.serverLevel().destroyBlock(sp.blockPosition().below(), false, sp);
                LOG.info("playtest: the player broke the block underfoot");
            }
        })));
        for (int t = 10; t <= TICKS; t += 10) {
            s.add(new Step(t, () -> onServer(mc, sp -> onCreature(sp, a -> {
                double d = a.distanceTo(sp);
                nearest = Math.min(nearest, d);
                String mode = a.mode();
                boolean knows = mode.equals("prowl") || mode.equals("stalk") || mode.equals("hunt");
                if (knows && !knew) {
                    knewAt = tick;
                }
                knew |= knows;
                stalked |= mode.equals("stalk");
                hunted |= mode.equals("hunt");
                grabbed |= sp.getVehicle() == a;
                pounced |= "pounce".equals(a.verb()) || "coil".equals(a.clipPlaying()) || "pounce".equals(a.clipPlaying());
                dug |= a.blocksDug() > 27;   // more than its own pocket
                boolean sight = a.getSensing().hasLineOfSight(sp);
                LOG.info("playtest: t={} mode={} verb={} dist={} sight={} dug={} blocked={} face={} at=({}, {}, {}) player=({}, {}, {}) health={}",
                        tick, mode, a.verb(), String.format("%.1f", d), sight, a.blocksDug(), a.lastBlocked(), a.syncedNormal(),
                        String.format("%.1f", a.getX()), String.format("%.1f", a.getY()), String.format("%.1f", a.getZ()),
                        String.format("%.1f", sp.getX()), String.format("%.1f", sp.getY()), String.format("%.1f", sp.getZ()), sp.getHealth());
            }))));
        }
        for (int t = 80, n = 1; t <= TICKS; t += 80, n++) {
            final int frame = n;
            s.add(new Step(t - 2, () -> onServer(mc, sp -> onCreature(sp, a -> sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(a.getX(), a.getY() + 1.0, a.getZ()))))));
            s.add(new Step(t, () -> shoot(mc, "playtest-" + frame)));
        }
        s.add(new Step(TICKS + 5, () -> {
            verdict("the creature came into the world", () -> creature != null ? null : "no creature");
            verdict("it knew of the player within five seconds", () -> knew && knewAt <= 100 ? null : "knew at " + knewAt);
            verdict("it dug its way", () -> dug ? null : "cut nothing beyond its pocket");
            verdict("it came near", () -> nearest < 16.0 ? null : "nearest " + nearest);
            verdict("it stalked, then hunted", () -> stalked && hunted ? null : "stalked " + stalked + ", hunted " + hunted);
            verdict("it closed for the kill", () -> grabbed || pounced || nearest < Aberrant.GRAB_REACH ? null : "nearest " + nearest + ", pounced " + pounced);
            LOG.info("playtest: grabbed {} pounced {} nearest {} player health {}", grabbed, pounced, nearest, mc.player == null ? null : mc.player.getHealth());
            LOG.info("playtest: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    private static void onCreature(ServerPlayer sp, Consumer<Aberrant> action) {
        if (creature == null) {
            return;
        }
        var e = sp.serverLevel().getEntity(creature);
        if (e instanceof Aberrant a) {
            action.accept(a);
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
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("playtest: {}", message.getString()));
    }

    private static void verdict(String what, java.util.function.Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("playtest: PASS {}", what);
        } else {
            LOG.error("playtest: FAIL {} -- {}", what, detail);
        }
    }
}
