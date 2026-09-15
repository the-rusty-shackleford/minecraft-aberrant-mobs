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

import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.mixin.EntityAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wearer's move, in a frame other than the world's: the game's own
 * {@code Entity.move} with its vertical axis the frame's up. The world
 * delta is collided against the blocks axis by axis, the frame's up axis
 * first; a step up is tried along the frame's up; the collision flags,
 * the ground, the fall and the local velocity are reckoned in the frame.
 * The wall that stopped a sideways move, and the move tried, are left on
 * the carrier for the transition rule. What is left out of the game's
 * move is what a wearer never needs: pistons, fire, flapping.
 */
public final class WallWalkMove {
    private WallWalkMove() {}

    /** effects: moves {@code p} by the world delta {@code delta} as the class says */
    public static void move(Player p, Frame f, MoverType type, Vec3 delta) {
        EntityAccessor access = (EntityAccessor) p;
        FrameCarrier carrier = (FrameCarrier) p;
        Vec3 collided = collide(p, f, delta);
        if (collided.lengthSqr() > 1.0E-7) {
            p.setPos(p.getX() + collided.x, p.getY() + collided.y, p.getZ() + collided.z);
        }
        Vec tried = f.toLocal(WallWalk.vec(delta));
        Vec got = f.toLocal(WallWalk.vec(collided));
        boolean cx = !Mth.equal(tried.x(), got.x());
        boolean cz = !Mth.equal(tried.z(), got.z());
        boolean cy = tried.y() != got.y();
        p.horizontalCollision = cx || cz;
        p.verticalCollision = cy;
        p.verticalCollisionBelow = cy && tried.y() < 0.0;
        p.minorHorizontalCollision = false;
        p.setOnGroundWithMovement(p.verticalCollisionBelow, collided);
        BlockPos onPos = p.getOnPosLegacy();
        BlockState state = p.level().getBlockState(onPos);
        access.aberrantmobs$checkFallDamage(got.y(), p.onGround(), state, onPos);
        if (p.isRemoved()) {
            return;
        }
        if (p.horizontalCollision) {
            Vec3 dm = p.getDeltaMovement();
            p.setDeltaMovement(cx ? 0.0 : dm.x, dm.y, cz ? 0.0 : dm.z);
            // The wall: the frame-horizontal axis that stopped the larger part of the move, looking back at the wearer.
            boolean alongX = cx && (!cz || Math.abs(tried.x()) >= Math.abs(tried.z()));
            Vec axis = alongX ? f.right() : f.forward();
            double sign = alongX ? Math.signum(tried.x()) : Math.signum(tried.z());
            carrier.aberrantmobs$setLastWall(WallWalk.vec3(axis.times(-sign)), delta);
        } else {
            carrier.aberrantmobs$setLastWall(null, delta);
        }
        if (cy) {
            state.getBlock().updateEntityAfterFallOn(p.level(), p);
        }
        if (p.onGround()) {
            state.getBlock().stepOn(p.level(), onPos, state, p);
        }
        access.aberrantmobs$tryCheckInsideBlocks();
        float factor = access.aberrantmobs$blockSpeedFactor();
        p.setDeltaMovement(p.getDeltaMovement().multiply(factor, 1.0, factor));
    }

    /** effects: returns how far of {@code delta} the wearer can go from its box, stepping up along the frame's up when a low edge stops it */
    static Vec3 collide(Player p, Frame f, Vec3 delta) {
        AABB box = p.getBoundingBox();
        List<VoxelShape> shapes = colliders(p, box.expandTowards(delta).expandTowards(WallWalk.vec3(f.up().times(p.maxUpStep()))));
        Vec3 moved = collideAxes(f, delta, box, shapes);
        double triedUp = WallWalk.vec(delta).dot(f.up());
        double gotUp = WallWalk.vec(moved).dot(f.up());
        Vec3 upW = WallWalk.vec3(f.up());
        Vec3 horizontal = delta.subtract(upW.scale(triedUp));
        Vec3 horizontalGot = moved.subtract(upW.scale(gotUp));
        boolean blockedAcross = !Mth.equal(horizontal.x, horizontalGot.x) || !Mth.equal(horizontal.y, horizontalGot.y) || !Mth.equal(horizontal.z, horizontalGot.z);
        boolean fellOntoSomething = gotUp != triedUp && triedUp < 0.0;
        if (p.maxUpStep() > 0.0f && (fellOntoSomething || p.onGround()) && blockedAcross) {
            Vec3 fall = fellOntoSomething ? upW.scale(gotUp) : Vec3.ZERO;
            AABB base = box.move(fall);
            Vec3 raised = collideAxes(f, upW.scale(p.maxUpStep()), base, shapes);
            Vec3 across = collideAxes(f, horizontal, base.move(raised), shapes);
            Vec3 settled = collideAxes(f, upW.scale(-p.maxUpStep()), base.move(raised).move(across), shapes);
            Vec3 stepped = fall.add(raised).add(across).add(settled);
            if (across.lengthSqr() > horizontalGot.lengthSqr()) {
                return stepped;
            }
        }
        return moved;
    }

    /** effects: returns {@code delta} collided against {@code shapes} from {@code box}, the frame's up axis first, then the lesser of the other two, then the greater */
    static Vec3 collideAxes(Frame f, Vec3 delta, AABB box, List<VoxelShape> shapes) {
        if (shapes.isEmpty() || delta.lengthSqr() == 0.0) {
            return delta;
        }
        Direction.Axis upAxis = axisOf(f.up());
        Direction.Axis[] others = others(upAxis);
        double a = Math.abs(component(delta, others[0])), b = Math.abs(component(delta, others[1]));
        Direction.Axis[] order = a < b ? new Direction.Axis[] {upAxis, others[0], others[1]} : new Direction.Axis[] {upAxis, others[1], others[0]};
        double x = delta.x, y = delta.y, z = delta.z;
        AABB at = box;
        for (Direction.Axis axis : order) {
            double d = axis == Direction.Axis.X ? x : axis == Direction.Axis.Y ? y : z;
            if (d == 0.0) {
                continue;
            }
            double got = Shapes.collide(axis, at, shapes, d);
            if (got != 0.0) {
                at = at.move(axis == Direction.Axis.X ? got : 0.0, axis == Direction.Axis.Y ? got : 0.0, axis == Direction.Axis.Z ? got : 0.0);
            }
            if (axis == Direction.Axis.X) {
                x = got;
            } else if (axis == Direction.Axis.Y) {
                y = got;
            } else {
                z = got;
            }
        }
        return new Vec3(x, y, z);
    }

    static Direction.Axis axisOf(Vec unit) {
        return Math.abs(unit.x()) > 0.5 ? Direction.Axis.X : Math.abs(unit.y()) > 0.5 ? Direction.Axis.Y : Direction.Axis.Z;
    }

    private static Direction.Axis[] others(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static double component(Vec3 v, Direction.Axis axis) {
        return axis == Direction.Axis.X ? v.x : axis == Direction.Axis.Y ? v.y : v.z;
    }

    private static List<VoxelShape> colliders(Player p, AABB reach) {
        List<VoxelShape> out = new ArrayList<>(p.level().getEntityCollisions(p, reach));
        if (p.level().getWorldBorder().isInsideCloseToBorder(p, reach)) {
            out.add(p.level().getWorldBorder().getCollisionShape());
        }
        for (VoxelShape s : p.level().getBlockCollisions(p, reach)) {
            out.add(s);
        }
        return out;
    }

    /** effects: returns the thin box on the gravity side of {@code box}, what a wearer stands on */
    public static AABB underfoot(Frame f, AABB box) {
        Vec g = f.gravity().dir;
        double e = 1.0E-6;
        if (g.x() != 0) {
            return g.x() < 0 ? new AABB(box.minX - e, box.minY, box.minZ, box.minX, box.maxY, box.maxZ) : new AABB(box.maxX, box.minY, box.minZ, box.maxX + e, box.maxY, box.maxZ);
        }
        if (g.y() != 0) {
            return g.y() < 0 ? new AABB(box.minX, box.minY - e, box.minZ, box.maxX, box.minY, box.maxZ) : new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + e, box.maxZ);
        }
        return g.z() < 0 ? new AABB(box.minX, box.minY, box.minZ - e, box.maxX, box.maxY, box.minZ) : new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + e);
    }
}
