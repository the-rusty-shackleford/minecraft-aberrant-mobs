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

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.LoggerFactory;

/**
 * Test-only immutable client position, sent after that tick's ordinary movement packet.
 * AF: (x,y,z) is the reported position at the sampling instant. RI: all coordinates are finite.
 * The connection orders it after movement, so its main-thread handler compares the same move,
 * never the live client entity from another thread. Atomic counters publish the integrated
 * server's verdicts back to the booth; neither this packet nor the counters ship.
 */
record MovementSample(double x, double y, double z) implements CustomPacketPayload {
    static final Type<MovementSample> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "movement_sample"));
    static final StreamCodec<RegistryFriendlyByteBuf, MovementSample> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, MovementSample::x,
            ByteBufCodecs.DOUBLE, MovementSample::y,
            ByteBufCodecs.DOUBLE, MovementSample::z, MovementSample::new);
    private static final AtomicInteger SAMPLES = new AtomicInteger();
    private static final AtomicInteger DISAGREEMENTS = new AtomicInteger();

    /** Requires finite coordinates; effects: captures them; throws if a coordinate is not finite. */
    MovementSample {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("movement sample coordinates must be finite");
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (sample, context) -> {
            Vec3 reported = new Vec3(sample.x, sample.y, sample.z);
            Vec3 actual = context.player().position();
            if (actual.distanceTo(reported) > 0.5) {
                DISAGREEMENTS.incrementAndGet();
                LoggerFactory.getLogger("Aberrant Mobs booth").error(
                        "booth: movement mismatch reported={} server={}", reported, actual);
            }
            SAMPLES.incrementAndGet();
        });
    }

    static void reset() { SAMPLES.set(0); DISAGREEMENTS.set(0); }
    static String verdict() {
        int samples = SAMPLES.get();
        int disagreements = DISAGREEMENTS.get();
        return samples == 60 && disagreements == 0 ? null
                : samples + " of 60 ordered movement samples received, " + disagreements + " disagreements";
    }

    /** Requires nothing; effects: returns this test packet's type; throws nothing. */
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
