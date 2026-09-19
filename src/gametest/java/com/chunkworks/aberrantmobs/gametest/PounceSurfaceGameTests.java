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
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: pounce landing on floor, ceiling and each wall; actual server creature,
 * collision cells and flight. World gravity remains physical during a leap; a target's
 * supporting surface must not cause penetration or leave the creature airborne.
 */
@GameTestHolder("aberrantmobs")
@PrefixGameTestTemplate(false)
public final class PounceSurfaceGameTests {
    public PounceSurfaceGameTests() {}
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void floor(GameTestHelper h) { check(h, Gravity.DOWN); }
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void ceiling(GameTestHelper h) { check(h, Gravity.UP); }
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void west(GameTestHelper h) { check(h, Gravity.WEST); }
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void east(GameTestHelper h) { check(h, Gravity.EAST); }
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void north(GameTestHelper h) { check(h, Gravity.NORTH); }
    @GameTest(template="pounce", timeoutTicks=90, batch="pounce_surfaces")
    public void south(GameTestHelper h) { check(h, Gravity.SOUTH); }

    private static void check(GameTestHelper h, Gravity gravity) {
        for (int x=0; x<31; x++) for (int z=0; z<31; z++) h.setBlock(new BlockPos(x,3,z), Blocks.STONE);
        Vec3 start = new Vec3(15.5,4,15.5);
        Vec3 target = switch(gravity) {
            case DOWN -> new Vec3(22.5,4,15.5);
            case UP -> new Vec3(20.5,11,15.5);
            case EAST -> new Vec3(23,7,15.5);
            case WEST -> new Vec3(8,7,15.5);
            case NORTH -> new Vec3(15.5,7,8);
            case SOUTH -> new Vec3(15.5,7,23);
        };
        if (gravity != Gravity.DOWN) {
            for(int a=0; a<31; a++) for(int b=4; b<20; b++) {
                BlockPos block = switch(gravity) {
                    case UP -> new BlockPos(a,11,b);
                    case EAST -> new BlockPos(23,b,a);
                    case WEST -> new BlockPos(7,b,a);
                    case NORTH -> new BlockPos(a,b,7);
                    case SOUTH -> new BlockPos(a,b,23);
                    default -> throw new AssertionError();
                };
                h.setBlock(block, Blocks.STONE);
            }
        }
        Vec3 at=h.absoluteVec(start), aim=h.absoluteVec(target);
        Aberrant creature=Aberrant.create(h.getLevel(), FaceStealerGameTests.PROTOCOL_FIXTURE,at.x,at.y,at.z,-90);
        h.assertTrue(creature != null,"creature exists");
        creature.setNoAi(true);
        creature.setPersistenceRequired();
        h.getLevel().addFreshEntity(creature);
        h.runAtTickTime(5,()->h.assertTrue(creature.pounce(new Vec(aim.x,aim.y,aim.z)),"reachable pounce toward "+gravity+", tick="+creature.tickCount+", removed="+creature.getRemovalReason()+", pose="+creature.crawlPose()));
        h.runAtTickTime(6,()->h.assertTrue(creature.crawlPose().airborne(),"actual airborne flight toward "+gravity));
        h.runAtTickTime(60,()->{
            try {
            var pose=creature.crawlPose();
            h.assertTrue(pose != null && !pose.airborne(),"landed toward "+gravity+": "+pose);
            h.assertTrue(pose.normal().dir.dot(gravity.dir.times(-1))>0.99,"correct landing surface "+gravity+": "+pose);
            Vec delta=pose.centre().minus(new Vec(aim.x,aim.y,aim.z));
            Vec tangent=delta.minus(pose.normal().dir.times(delta.dot(pose.normal().dir)));
            h.assertTrue(tangent.length()<3.5,"within its grab reach of target on "+gravity+": "+tangent);
            h.assertTrue(h.getLevel().noCollision(creature),"free collision box on "+gravity);
            h.succeed();
            } finally { creature.discard(); }
        });
    }
}
