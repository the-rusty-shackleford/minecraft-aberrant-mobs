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
package com.chunkworks.aberrantmobs.wallwalk;

import com.chunkworks.aberrantmobs.AberrantMobsMod;
import com.chunkworks.aberrantmobs.domain.frame.ClingIntent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Immutable input-only payload. RI: validated by ClingIntent; AF: one manual control sample. */
public record ClingInput(boolean jump, boolean forward, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<ClingInput> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AberrantMobsMod.MOD_ID, "cling_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClingInput> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClingInput::jump, ByteBufCodecs.BOOL, ClingInput::forward,
            ByteBufCodecs.FLOAT, ClingInput::yaw, ByteBufCodecs.FLOAT, ClingInput::pitch, ClingInput::new);

    /** effects: validates and captures input; throws: IllegalArgumentException for invalid angles. */
    public ClingInput { new ClingIntent(jump, forward, yaw, pitch); }

    /** effects: registers ordered, main-thread input handling; clients cannot choose a frame or bypass collisions. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, CODEC, (input, context) ->
                WallWalk.input(context.player(), new ClingIntent(input.jump, input.forward, input.yaw, input.pitch)));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
