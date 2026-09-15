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
 * from the side, from its front quarter and up close on its face. Each
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
                    steps = plan(mc);
                    onServer(mc, PhotoBooth::setUp);
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
        sp.teleportTo(level, X - 2.0, y + 4.0, Z - 16.0, 0.0f, 12.0f);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X - 2.0, y + 1.0, Z));
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = 0;
        s.add(new Step(t += SETTLE * 2, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-side");
            verdict("the client has the creature", () -> find(mc) != null ? null : "no Aberrant among the entities for rendering");
            verdict("from the side it fills the middle of the frame", () -> drawn > 4000 ? null : "creature pixels " + drawn);
        }));
        // The front quarter: ahead of the head and to its right, looking back at it.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), X + 12.0, y + 5.0, Z + 9.0, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X - 1.0, y + 1.2, Z));
        })));
        s.add(new Step(t += SETTLE / 2, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-quarter");
            verdict("from the front quarter it is there", () -> drawn > 3000 ? null : "creature pixels " + drawn);
        }));
        // The face, from three blocks ahead of the head at its eye height.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            double y = sp.serverLevel().getMinBuildHeight() + 4;
            sp.teleportTo(sp.serverLevel(), X + 6.5, y + 1.3, Z, 0.0f, 0.0f);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(X + 2.0, y + 1.4, Z));
        })));
        s.add(new Step(t += SETTLE / 2, () -> {
            int drawn = count(mc, PhotoBooth::creature);
            shoot(mc, "booth-face");
            verdict("its face fills the frame up close", () -> drawn > 12000 ? null : "creature pixels " + drawn);
        }));
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
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
