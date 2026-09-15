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

import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import com.chunkworks.aberrantmobs.wallwalk.WallWalkMove;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A wearer's box, eyes, look and move in its own frame. Every hook
 * returns at once for an entity in the world's frame, so anyone not
 * wearing the chitin runs the game's code untouched.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "makeBoundingBox", at = @At("RETURN"), cancellable = true)
    private void aberrantmobs$frameBox(CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity) (Object) this;
        if (!WallWalk.bent(self)) {
            return;
        }
        Frame f = WallWalk.frameOf(self);
        cir.setReturnValue(WallWalk.aabb(f.box(new Vec(self.getX(), self.getY(), self.getZ()), self.getBbWidth(), self.getBbHeight())));
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$eye(CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (WallWalk.bent(self)) {
            cir.setReturnValue(WallWalk.vec3(WallWalk.frameOf(self).eye(new Vec(self.getX(), self.getY(), self.getZ()), self.getEyeHeight())));
        }
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$eyeLerped(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (WallWalk.bent(self)) {
            Vec feet = new Vec(Mth.lerp(partialTicks, self.xo, self.getX()), Mth.lerp(partialTicks, self.yo, self.getY()), Mth.lerp(partialTicks, self.zo, self.getZ()));
            cir.setReturnValue(WallWalk.vec3(WallWalk.frameOf(self).eye(feet, self.getEyeHeight())));
        }
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$eyeY(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        if (WallWalk.bent(self)) {
            cir.setReturnValue(WallWalk.frameOf(self).eye(new Vec(self.getX(), self.getY(), self.getZ()), self.getEyeHeight()).y());
        }
    }

    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    private void aberrantmobs$view(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (WallWalk.bent(self)) {
            cir.setReturnValue(WallWalk.vec3(WallWalk.frameOf(self).toWorld(WallWalk.vec(cir.getReturnValue()))));
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$move(MoverType type, Vec3 pos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player p) || self.noPhysics || !WallWalk.wears(p)) {
            return;
        }
        if (!WallWalk.bent(self)) {
            // The game's own move; the wall it meets is read off it afterwards.
            FrameCarrier c = (FrameCarrier) p;
            c.aberrantmobs$setMovePre(self.position());
            c.aberrantmobs$setLastWall(null, pos);
            return;
        }
        ci.cancel();
        WallWalkMove.move(p, WallWalk.frameOf(self), type, pos);
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void aberrantmobs$wallMet(MoverType type, Vec3 pos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player p) || WallWalk.bent(self) || !(p instanceof FrameCarrier c) || c.aberrantmobs$movePre() == null) {
            return;
        }
        Vec3 got = self.position().subtract(c.aberrantmobs$movePre());
        c.aberrantmobs$setMovePre(null);
        boolean cx = !Mth.equal(pos.x, got.x), cz = !Mth.equal(pos.z, got.z);
        if (!cx && !cz) {
            c.aberrantmobs$setLastWall(null, pos);
            return;
        }
        boolean alongX = cx && (!cz || Math.abs(pos.x) >= Math.abs(pos.z));
        Vec3 normal = alongX ? new Vec3(-Math.signum(pos.x), 0, 0) : new Vec3(0, 0, -Math.signum(pos.z));
        c.aberrantmobs$setLastWall(normal, pos);
    }

    @Inject(method = "checkSupportingBlock", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$supporting(boolean onGround, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!WallWalk.bent(self)) {
            return;
        }
        ci.cancel();
        EntityAccessor access = (EntityAccessor) self;
        if (onGround) {
            Optional<BlockPos> found = self.level().findSupportingBlock(self, WallWalkMove.underfoot(WallWalk.frameOf(self), self.getBoundingBox()));
            access.aberrantmobs$setMainSupportingBlockPos(found);
            access.aberrantmobs$setOnGroundNoBlocks(found.isEmpty());
        } else {
            access.aberrantmobs$setOnGroundNoBlocks(false);
            access.aberrantmobs$setMainSupportingBlockPos(Optional.empty());
        }
    }

    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$onPos(float offset, CallbackInfoReturnable<BlockPos> cir) {
        Entity self = (Entity) (Object) this;
        if (!WallWalk.bent(self)) {
            return;
        }
        Optional<BlockPos> found = ((EntityAccessor) self).aberrantmobs$mainSupportingBlockPos();
        if (found.isPresent()) {
            cir.setReturnValue(found.get());
            return;
        }
        Vec under = new Vec(self.getX(), self.getY(), self.getZ()).plus(WallWalk.frameOf(self).gravity().dir.times(offset));
        cir.setReturnValue(BlockPos.containing(under.x(), under.y(), under.z()));
    }
}
