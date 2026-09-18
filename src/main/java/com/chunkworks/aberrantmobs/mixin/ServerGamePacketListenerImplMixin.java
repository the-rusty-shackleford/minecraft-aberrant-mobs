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
package com.chunkworks.aberrantmobs.mixin;

import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The server re-runs every move a client reports and then compares where
 * its player got to with where the client says it is; a survival player
 * more than a quarter block off is "moved wrongly" and teleported back. A
 * wearer's client applies the frame rule right after its own move --
 * taking a wall moves its feet onto the wall's face -- and reports the
 * position after it, so the server applies the same rule right after its
 * re-run, before the comparison: the two then agree. Applied only at the
 * end of the server's tick, as it first was, every take was a "moved
 * wrongly" and a teleport back, and a wearer in survival never climbed;
 * creative skips the check, which is how the booth missed it. A bent-frame
 * transition report already includes a stance displacement; its inverse is used
 * only when collision and the transition rule reproduce the full reported position.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @org.spongepowered.asm.mixin.Shadow private double lastGoodX;
    @org.spongepowered.asm.mixin.Shadow private double lastGoodY;
    @org.spongepowered.asm.mixin.Shadow private double lastGoodZ;
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void aberrantmobs$replayBeforeStance(ServerPlayer wearer, MoverType type, Vec3 reported) {
        if (WallWalk.wears(wearer) || WallWalk.bent(wearer)) {
            Vec3 wanted = new Vec3(lastGoodX, lastGoodY, lastGoodZ).add(reported);
            WallWalk.prepare(wearer);
            reported = wanted.subtract(wearer.position());
        }
        wearer.move(type, WallWalk.replayMove(wearer, reported));
        WallWalk.rule(wearer);
    }
    // Vanilla infers a jump from increasing WORLD Y plus a false ground flag.
    // Climbing a wall can satisfy both; only manual cling input may detach it.
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void aberrantmobs$worldJumpOnly(ServerPlayer wearer) {
        if (!WallWalk.bent(wearer)) wearer.jumpFromGround();
    }

}
