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
import com.chunkworks.aberrantmobs.api.AberrantMobs;
import com.chunkworks.aberrantmobs.domain.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Partitions: saved creatures above/below their habitat; pounce beyond either
 * boundary; direct daylight versus shade, night and artificial light. These
 * fixtures load the shipped profile and exercise actual server ticks and NBT.
 */
@GameTestHolder("aberrant_habitat")
@PrefixGameTestTemplate(false)
public final class HabitatGameTests {
    public HabitatGameTests() {}

    private static BlockPos origin(GameTestHelper h) {
        return h.absolutePos(BlockPos.ZERO);
    }

    private static void floor(GameTestHelper h, int y) {
        BlockPos o = origin(h);
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            h.getLevel().setBlock(new BlockPos(o.getX()+x,y,o.getZ()+z), Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static Aberrant creature(GameTestHelper h, double y) {
        BlockPos o = origin(h);
        Aberrant a = Aberrant.create(h.getLevel(), AberrantMobs.id("face_stealer"), o.getX()+15.5, y, o.getZ()+15.5, -90);
        h.assertTrue(a != null, "shipped Face-Stealer profile loads");
        a.setNoAi(true);
        a.setPersistenceRequired();
        h.getLevel().addFreshEntity(a);
        return a;
    }

    @GameTest(template="pounce", timeoutTicks=40, batch="habitat_saved")
    public void savedSurfaceCreatureIsRemovedWithoutLoot(GameTestHelper h) { savedOutside(h, 40); }

    @GameTest(template="pounce", timeoutTicks=40, batch="habitat_saved")
    public void savedTooDeepCreatureIsRemovedWithoutLoot(GameTestHelper h) { savedOutside(h, -48); }

    private static void savedOutside(GameTestHelper h, int y) {
        floor(h, y-1);
        Aberrant old = creature(h,y);
        CompoundTag saved = new CompoundTag();
        h.assertTrue(old.save(saved), "actual creature saved with its profile and persistence");
        old.discard();
        Aberrant loaded = (Aberrant) EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
        h.assertTrue(loaded != null, "actual NBT reload");
        h.getLevel().addFreshEntity(loaded);
        h.runAtTickTime(5,()->{
            h.assertTrue(loaded.isRemoved(), "existing out-of-band creature removed on load at Y="+y);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    loaded.getBoundingBox().inflate(8)).isEmpty(), "removal creates no loot");
            h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=40, batch="habitat_pounce")
    public void cannotPounceAboveHabitat(GameTestHelper h) { pounceOutside(h,-12,-5); }

    @GameTest(template="pounce", timeoutTicks=40, batch="habitat_pounce")
    public void cannotPounceBelowHabitat(GameTestHelper h) { pounceOutside(h,-27,-35); }

    private static void pounceOutside(GameTestHelper h, int y, int targetY) {
        floor(h,y-1);
        Aberrant a=creature(h,y);
        h.runAtTickTime(5,()->{
            h.assertTrue(a.isAlive(), "valid creature remains alive");
            h.assertTrue(!a.pounce(new Vec(a.getX()+3,targetY,a.getZ())), "pounce outside habitat refused");
            a.discard();
            h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=100, batch="habitat_sun")
    public void directSunlightDamagesThroughTheCarapaceAtDepth(GameTestHelper h) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,h.getLevel().getServer());
        h.getLevel().setDayTime(6000);
        h.getLevel().setWeatherParameters(6000,0,false,false);
        floor(h,-25);
        Aberrant a=creature(h,-24);
        h.runAtTickTime(60,()->{
            h.assertTrue(h.getLevel().canSeeSky(a.blockPosition().above(4)), "sun reaches the underground fixture");
            h.assertTrue(a.isAlive() && a.getHealth()<a.getMaxHealth(), "sunlight actually damages the armored creature: "+a.getHealth());
            a.discard();
            h.succeed();
        });
    }
    @GameTest(template="pounce", timeoutTicks=400, batch="habitat_spawn")
    public void naturalSpawnNeedsDarknessAndAWholePocketInsideTheBand(GameTestHelper h) {
        BlockPos o=origin(h);
        var level=h.getLevel();
        // Other protocol fixtures do not participate in this isolated spawn-cap experiment.
        for (Aberrant a:level.getEntitiesOfClass(Aberrant.class,new net.minecraft.world.phys.AABB(o).inflate(160))) a.discard();
        for (int x=0;x<17;x++) for(int z=10;z<21;z++) for(int y=-35;y<=-7;y++)
            level.setBlock(new BlockPos(o.getX()+x,y,o.getZ()+z),Blocks.STONE.defaultBlockState(),3);
        for(int y:new int[]{-45,-32,-24,-8})
            level.setBlock(new BlockPos(o.getX()+5,y,o.getZ()+15),Blocks.AIR.defaultBlockState(),3);
        BlockPos valid=new BlockPos(o.getX()+5,-24,o.getZ()+15);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.SKY,valid)==0,"light engine finished the enclosed cave"))
                .thenExecute(()->{
                    for(int y:new int[]{-45,-32,-8,10,70}) {
                        BlockPos bad=new BlockPos(valid.getX(),y,valid.getZ());
                        h.assertTrue(!natural(h,bad),"outside band or pocket straddles its boundary at "+y);
                    }
                    h.assertTrue(natural(h,valid),"dark cave with a valid rock pocket admits a natural spawn");
                    Aberrant a=com.chunkworks.aberrantmobs.ModContent.ABERRANT.get().create(level);
                    a.moveTo(valid.getX()+0.5,valid.getY(),valid.getZ()+0.5,0,0);
                    a.finalizeSpawn(level,level.getCurrentDifficultyAt(valid),net.minecraft.world.entity.MobSpawnType.NATURAL,null);
                    a.setNoAi(true);a.setPersistenceRequired();level.addFreshEntity(a);
                    h.assertTrue(a.profileId().equals(AberrantMobs.id("face_stealer")),"shipped profile selected");
                    h.assertTrue(a.blocksDug()==125,"full five-wide pocket bored");
                    h.assertTrue(Math.abs(a.getX()-valid.getX()-0.5)>=7,"spawned inside the wall");
                    h.runAfterDelay(30,()->{
                        h.assertTrue(a.isAlive(),"naturally spawned creature survives the habitat enforcement");
                        assertBody(h,a);a.discard();h.succeed();
                    });
                });
    }

    private static boolean natural(GameTestHelper h,BlockPos pos) {
        return net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(com.chunkworks.aberrantmobs.ModContent.ABERRANT.get(),
                h.getLevel(),net.minecraft.world.entity.MobSpawnType.NATURAL,pos,h.getLevel().getRandom());
    }

    private static void assertBody(GameTestHelper h,Aberrant a) {
        h.assertTrue(a.isAlive(),"creature stays alive at the boundary");
        for(var part:a.getParts()) if(part.getBbWidth()>0.1)
            h.assertTrue(part.getBoundingBox().minY>=-32 && part.getBoundingBox().maxY<=-7,"entire segment stays in band: "+part.getBoundingBox());
    }

    @GameTest(template="pounce", timeoutTicks=190, batch="habitat_crawl")
    public void pursuitCannotDigOrClimbAboveTheBand(GameTestHelper h) { verticalPursuit(h,true); }

    @GameTest(template="pounce", timeoutTicks=190, batch="habitat_crawl")
    public void pursuitCannotDigOrFallBelowTheBand(GameTestHelper h) { verticalPursuit(h,false); }

    private static void verticalPursuit(GameTestHelper h,boolean up) {
        BlockPos o=origin(h);
        floor(h,-29);
        // A rock wall reaches beyond both boundaries. Actual planner/crawl/strike cues
        // must make progress toward it but never cut the protected rows beyond the band.
        for(int x=23;x<29;x++) for(int z=0;z<31;z++) for(int y=-37;y<=0;y++)
            h.getLevel().setBlock(new BlockPos(o.getX()+x,y,o.getZ()+z),Blocks.STONE.defaultBlockState(),3);
        Aberrant a=creature(h,-28);
        double startX=a.getX();
        h.runAtTickTime(4,()->a.setCrawlTarget(new Vec(o.getX()+26.5,up?2:-40,o.getZ()+15.5),true,0.45));
        for(int t=5;t<=160;t++) h.runAtTickTime(t,()->assertBody(h,a));
        h.runAtTickTime(165,()->{
            h.assertTrue(a.getX()>startX+2,"actual pursuit progresses before the boundary");
            for(int x=23;x<29;x++) for(int z=0;z<31;z++) for(int y:new int[]{-33,-7,0})
                h.assertTrue(h.getLevel().getBlockState(new BlockPos(o.getX()+x,y,o.getZ()+z)).is(Blocks.STONE),"no dig outside allowed rows");
            a.discard();h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=400, batch="habitat_shade")
    public void roofAndTorchesProtectFromSunlight(GameTestHelper h) {
        h.getLevel().setDayTime(6000);h.getLevel().setWeatherParameters(6000,0,false,false);
        floor(h,-25);floor(h,-10);
        BlockPos o=origin(h);
        h.getLevel().setBlock(new BlockPos(o.getX()+17,-22,o.getZ()+15),Blocks.TORCH.defaultBlockState(),3);
        Aberrant a=creature(h,-24);
        h.startSequence().thenWaitUntil(()->h.assertTrue(!h.getLevel().canSeeSky(a.blockPosition().above(4)),"light engine has shaded the fixture"))
                .thenExecute(()->a.setHealth(a.getMaxHealth())).thenIdle(40).thenExecute(()->{
                    h.assertTrue(a.getHealth()==a.getMaxHealth(),"artificial light does not cause sun damage");
                    a.discard();
                }).thenSucceed();
    }

    @GameTest(template="pounce", timeoutTicks=100, batch="habitat_night")
    public void exposedAtNightTakesNoSunDamage(GameTestHelper h) {
        h.getLevel().setDayTime(18000);h.getLevel().updateSkyBrightness();floor(h,-25);
        Aberrant a=creature(h,-24);
        h.runAtTickTime(60,()->{
            h.assertTrue(!h.getLevel().isDay(),"nighttime fixture");
            h.assertTrue(a.getHealth()==a.getMaxHealth(),"no sunlight damage at night");
            a.discard();h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=100, batch="habitat_existing")
    public void savedInBandCreatureKeepsHealthAndGainsSunlightVulnerability(GameTestHelper h) {
        h.getLevel().setDayTime(6000);h.getLevel().setWeatherParameters(6000,0,false,false);floor(h,-25);
        Aberrant old=creature(h,-24);old.setHealth(60);
        old.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.45); // saved by 1.3.0
        CompoundTag saved=new CompoundTag();old.save(saved);old.discard();
        Aberrant loaded=(Aberrant)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
        h.getLevel().addFreshEntity(loaded);
        h.assertTrue(loaded.getHealth()==60,"load preserves existing health");
        h.runAtTickTime(60,()->{
            h.assertTrue(loaded.isAlive() && loaded.getHealth()<60,"existing creature gains sunlight damage without respawning");
            loaded.discard();h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=100, batch="habitat_attack")
    public void cannotGrabAcrossTheUpperBoundaryOrBiteAfterVictimEscapes(GameTestHelper h) {
        floor(h,-14);
        Aberrant a=creature(h,-13);
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(a.getX()+1,-6,a.getZ(),0,0);
        h.assertTrue(!a.grab(player),"no grab beyond habitat even within pincer reach");
        h.runAtTickTime(65,()->{
            player.moveTo(a.getX()+1,-12,a.getZ(),0,0);
            h.assertTrue(a.grab(player),"same player can be grabbed inside habitat");
            a.bite();
            player.moveTo(a.getX(),0,a.getZ(),0,0);
        });
        h.runAtTickTime(85,()->{
            h.assertTrue(!a.holding() && player.isAlive(),"escaping victim is released before pending bite");
            a.discard();player.discard();h.succeed();
        });
    }

    @GameTest(template="pounce", timeoutTicks=60, batch="habitat_deepdark")
    public void deepDarkRejectsSpawningMovementAndLoadedCreatures(GameTestHelper h) {
        BlockPos o=origin(h);
        var level=h.getLevel();
        var dark=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_DARK);
        java.util.Map<BlockPos,net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> original=new java.util.HashMap<>();
        java.util.List<net.minecraft.world.level.chunk.LevelChunk> changed=new java.util.ArrayList<>();
        for(int cx=(o.getX()-16)>>4;cx<=(o.getX()+47)>>4;cx++) for(int cz=(o.getZ()-16)>>4;cz<=(o.getZ()+47)>>4;cz++) {
            var chunk=level.getChunk(cx,cz);changed.add(chunk);
            for(int qx=cx*4;qx<cx*4+4;qx++) for(int qz=cz*4;qz<cz*4+4;qz++) for(int qy=level.getMinBuildHeight()/4;qy<level.getMaxBuildHeight()/4;qy++)
                original.put(new BlockPos(qx,qy,qz),chunk.getNoiseBiome(qx,qy,qz));
        }
        for(int x=(o.getX()-16)>>4;x<=(o.getX()+47)>>4;x++) for(int z=(o.getZ()-16)>>4;z<=(o.getZ()+47)>>4;z++)
            level.getChunk(x,z).fillBiomesFromNoise((qx,qy,qz,sampler)->dark,level.getChunkSource().randomState().sampler());
        floor(h,-25);
        BlockPos at=new BlockPos(o.getX()+15,-24,o.getZ()+15);
        h.assertTrue(!natural(h,at),"Deep Dark cannot spawn one");
        Aberrant a=creature(h,-24);
        h.assertTrue(!a.habitatAllows(new Vec(at.getX(),at.getY(),at.getZ())),"Deep Dark is outside movement habitat");
        h.runAtTickTime(5,()->{
            try {
                h.assertTrue(a.isRemoved(),"existing Deep Dark creature removed");h.succeed();
            } finally {
                for(var chunk:changed) chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->original.get(new BlockPos(qx,qy,qz)),level.getChunkSource().randomState().sampler());
            }
        });
    }

    @GameTest(template="sprint", timeoutTicks=130, batch="habitat_chase_speed")
    public void sprintingPlayerOpensAGapDuringAnActualHunt(GameTestHelper h) {
        BlockPos o=origin(h);
        var level=h.getLevel();level.setDayTime(18000);level.updateSkyBrightness();
        for(int x=0;x<100;x++) for(int z=0;z<31;z++)
            level.setBlock(new BlockPos(o.getX()+x,-25,o.getZ()+z),Blocks.STONE.defaultBlockState(),3);
        Aberrant a=Aberrant.create(level,AberrantMobs.id("face_stealer"),o.getX()+15.5,-24,o.getZ()+15.5,-90);
        a.setPersistenceRequired();level.addFreshEntity(a);
        a.remember(com.chunkworks.aberrantmobs.domain.mind.Memory.fresh("hunt",123));
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(o.getX()+32,-24,o.getZ()+15.5,-90,0);player.yHeadRot=-90;
        double[] start=new double[3];
        for(int t=1;t<=85;t++) h.runAtTickTime(t,()->{
            // This fixture player's ticks are manual; use the real vanilla travel
            // method, terrain friction and sprint attribute rather than a speed constant.
            player.setSprinting(true);player.setOnGround(true);
            player.travel(new net.minecraft.world.phys.Vec3(0,0,1));
        });
        h.runAtTickTime(15,()->{start[0]=player.getX()-a.getX();start[1]=a.getX();start[2]=player.getX();});
        h.runAtTickTime(85,()->{
            try {
                h.assertTrue(player.isAlive() && !a.holding(),"sprinting player remains free");
                h.assertTrue(a.getX()-start[1]>10,"creature actually pursued: "+(a.getX()-start[1]));
                h.assertTrue(player.getX()-start[2]>17.5,"player actually sprinted on the server: "+(player.getX()-start[2]));
                double gained=player.getX()-a.getX()-start[0];
                h.assertTrue(gained>1,"sprinting opens a gap in an actual hunt; gain="+gained+", creature="+(a.getX()-start[1])+", player="+(player.getX()-start[2]));
                h.succeed();
            } finally {a.discard();player.discard();}
        });
    }

}
