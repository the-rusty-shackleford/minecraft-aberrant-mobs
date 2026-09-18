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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A wearer's travel is reckoned in its own frame -- gravity along its
 * down, a jump along its up, friction on its floor -- and its velocity
 * stays local; only the move into the world is turned to world axes. A
 * knockback, given in the world's horizontal, is turned into the frame
 * before it lands on that local velocity.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void updateWalkAnimation(float distance);

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$surfaceWalk(boolean includeHeight, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!WallWalk.bent(self)) return;
        var delta = WallWalk.frameOf(self).toLocal(WallWalk.vec(self.position().subtract(self.xo,self.yo,self.zo)));
        updateWalkAnimation((float)Math.sqrt(delta.x()*delta.x()+delta.z()*delta.z()+(includeHeight ? delta.y()*delta.y() : 0)));
        ci.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double aberrantmobs$bodyMoveX(LivingEntity self) {
        if (!WallWalk.bent(self)) return self.getX();
        var delta = WallWalk.frameOf(self).toLocal(WallWalk.vec(self.position().subtract(self.xo,self.yo,self.zo)));
        return self.xo + delta.x();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double aberrantmobs$bodyMoveZ(LivingEntity self) {
        if (!WallWalk.bent(self)) return self.getZ();
        var delta = WallWalk.frameOf(self).toLocal(WallWalk.vec(self.position().subtract(self.xo,self.yo,self.zo)));
        return self.zo + delta.z();
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void aberrantmobs$knockbackInFrame(double strength, double x, double z, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!WallWalk.bent(self)) {
            return;
        }
        ci.cancel();
        net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event = net.neoforged.neoforge.common.CommonHooks.onLivingKnockBack(self, (float) strength, x, z);
        if (event.isCanceled()) {
            return;
        }
        double s = event.getStrength() * (1.0 - self.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        if (s <= 0.0) {
            return;
        }
        self.hasImpulse = true;
        // The push comes as the world's horizontal away from the attacker; in the frame it is whatever that is across the wearer's floor.
        com.chunkworks.aberrantmobs.domain.Vec local = WallWalk.frameOf(self).toLocal(new com.chunkworks.aberrantmobs.domain.Vec(event.getRatioX(), 0.0, event.getRatioZ()));
        double lx = local.x(), lz = local.z();
        if (lx * lx + lz * lz < 1.0E-5) {
            lx = (Math.random() - Math.random()) * 0.01;
            lz = (Math.random() - Math.random()) * 0.01;
        }
        Vec3 dir = new Vec3(lx, 0.0, lz).normalize().scale(s);
        Vec3 dm = self.getDeltaMovement();
        self.setDeltaMovement(dm.x / 2.0 - dir.x, self.onGround() ? Math.min(0.4, dm.y / 2.0 + s) : dm.y, dm.z / 2.0 - dir.z);
    }
    @Redirect(method = {"travel", "handleRelativeFrictionAndCalculateMovement"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void aberrantmobs$moveInFrame(LivingEntity self, MoverType type, Vec3 local) {
        if (WallWalk.bent(self)) {
            self.move(type, WallWalk.vec3(WallWalk.frameOf(self).toWorld(WallWalk.vec(local))));
        } else {
            self.move(type, local);
        }
    }
}
