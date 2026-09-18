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

import com.chunkworks.aberrantmobs.ModContent;
import com.chunkworks.aberrantmobs.domain.Vec;
import com.chunkworks.aberrantmobs.domain.frame.Frame;
import com.chunkworks.aberrantmobs.domain.frame.Gravity;
import com.chunkworks.aberrantmobs.wallwalk.FrameCarrier;
import com.chunkworks.aberrantmobs.wallwalk.WallWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real-input moving presentation gate, -PboothPresentation. Partitions: natural
 * entry and connected corners; six supported gravities; camera start continuity;
 * limb cycle and actual rendered leg poses while walking. Shader/animation mods
 * in the isolated booth remain enabled. This client-only behavior needs a real
 * rendering client; dedicated-server tests cannot exercise it.
 */
@EventBusSubscriber(modid=GameTestMod.MOD_ID,value=Dist.CLIENT)
public final class PresentationBooth {
    private static final Logger LOG=LoggerFactory.getLogger("Chitin presentation booth");
    private static boolean active;
    private static int time;
    private static int floor;
    private static int transitions;
    private static Frame seen;
    private static org.joml.Quaternionf cameraRotation;
    private static Vec3 cameraPosition;
    private static double cameraTime;
    private static float cycleStart;
    private static float maxSpeed;
    private static float distanceStart,groundCycle,groundSpeed,groundDistance;
    private static float minLeg,maxLeg;
    private static int lastShot=-1;
    private static double maxRate;
    private static double turnStarted=-100;
    private static int lastTurnShot=-1;
    private PresentationBooth() {}

    /** effects: creates a lit closed course, equips the real connected player in survival. */
    public static void setUp(ServerPlayer p) {
        floor=p.serverLevel().getMinBuildHeight()+4;
        for(int x=0;x<=12;x++) for(int y=0;y<=12;y++) for(int z=0;z<=12;z++) {
            boolean shell=x==0||x==12||y==0||y==12||z==0||z==12;
            var block=shell ? ((x+y+z)%5==0 ? Blocks.SEA_LANTERN : Blocks.STONE) : Blocks.AIR;
            p.serverLevel().setBlockAndUpdate(new BlockPos(x,floor+y,z),block.defaultBlockState());
        }
        p.setGameMode(GameType.SURVIVAL);
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModContent.CHITIN_HELMET.get()));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModContent.CHITIN_CHESTPLATE.get()));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModContent.CHITIN_LEGGINGS.get()));
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModContent.CHITIN_BOOTS.get()));
        p.teleportTo(p.serverLevel(),6.5,floor+1,6.5,-90,0);
        p.setDeltaMovement(Vec3.ZERO);
        p.serverLevel().setDayTime(6000);
    }

    private static void scene(ServerPlayer p,Frame f) {
        Vec feet=switch(f.gravity()) {
            case DOWN -> new Vec(6.5,floor+1,6.5);
            case UP -> new Vec(6.5,floor+12,6.5);
            case EAST -> new Vec(12,floor+6.5,6.5);
            case WEST -> new Vec(1,floor+6.5,6.5);
            case NORTH -> new Vec(6.5,floor+6.5,1);
            case SOUTH -> new Vec(6.5,floor+6.5,12);
        };
        feet=feet.minus(f.forward().times(2));
        ((FrameCarrier)p).aberrantmobs$setFrame(f);
        var dirty=p.getEntityData().packDirty();
        if(dirty!=null) p.connection.send(new ClientboundSetEntityDataPacket(p.getId(),dirty));
        p.teleportTo(p.serverLevel(),feet.x(),feet.y(),feet.z(),0,0);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** effects: drives actual movement keys and checks the walking phase on each supporting surface. */
    public static void tick(Minecraft mc,int tick) {
        time=tick; active=true;
        if(tick==15) { mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); }
        if(tick==65) mc.options.keyJump.setDown(false);
        if(tick==250) {
            mc.options.keyUp.setDown(false);
            check("at least three real surface transitions",transitions>=3,"transitions="+transitions);
            check("camera angular speed bounded through moving corners",maxRate<36,"degrees/tick="+maxRate);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if(tick>=280 && tick<820) {
            int index=(tick-280)/90, phase=(tick-280)%90;
            Frame frame=Frame.of(Gravity.values()[index]);
            if(phase==0) onServer(mc,p->scene(p,frame));
            if(phase==30) {
                check("supported frame before walking "+frame.gravity(),WallWalk.frameOf(mc.player).equals(frame),WallWalk.frameOf(mc.player).toString());
                cycleStart=mc.player.walkAnimation.position(); distanceStart=mc.player.walkDist; maxSpeed=0; minLeg=Float.POSITIVE_INFINITY; maxLeg=Float.NEGATIVE_INFINITY;
                mc.options.keyUp.setDown(true);
            }
            if(phase>=31 && phase<=49) maxSpeed=Math.max(maxSpeed,mc.player.walkAnimation.speed());
            if(phase==50) {
                mc.options.keyUp.setDown(false);
                float advanced=mc.player.walkAnimation.position()-cycleStart;
                float distance=mc.player.walkDist-distanceStart;
                if(index==0) { groundCycle=advanced; groundSpeed=maxSpeed; groundDistance=distance; }
                check("walking matches ground "+frame.gravity(),advanced>5 && maxSpeed>0.5
                        && Math.abs(advanced-groundCycle)<0.001 && Math.abs(maxSpeed-groundSpeed)<0.001
                        && Math.abs(distance-groundDistance)<0.001,
                        "phase="+advanced+" speed="+maxSpeed+" bobDistance="+distance);
                check("rendered legs animate "+frame.gravity(),maxLeg-minLeg>0.2,"leg range="+(maxLeg-minLeg));
            }
        }
        if(tick==820) { mc.options.setCameraType(CameraType.FIRST_PERSON); seen=null; cameraRotation=null; }
        if(tick==835) mc.options.keyJump.setDown(true);
        if(tick==836) mc.options.keyJump.setDown(false);
        if(tick==852) check("Jump detaches after the camera turn",WallWalk.frameOf(mc.player).equals(Frame.WORLD),WallWalk.frameOf(mc.player).toString());
        if(tick==860) onServer(mc,p->scene(p,Frame.of(Gravity.UP)));
        if(tick==900) onServer(mc,p->p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY));
        if(tick==918) check("armour removal restores world gravity",WallWalk.frameOf(mc.player).equals(Frame.WORLD),WallWalk.frameOf(mc.player).toString());
        if(tick==925) onServer(mc,p->{
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModContent.CHITIN_HELMET.get()));
            for(int x=8;x<=11;x++) for(int y=1;y<=4;y++) for(int z=5;z<=7;z++)
                p.serverLevel().setBlockAndUpdate(new BlockPos(x,floor+y,z),Blocks.STONE.defaultBlockState());
            Frame f=Frame.of(Gravity.EAST);
            ((FrameCarrier)p).aberrantmobs$setFrame(f);
            var dirty=p.getEntityData().packDirty();
            if(dirty!=null) p.connection.send(new ClientboundSetEntityDataPacket(p.getId(),dirty));
            p.teleportTo(p.serverLevel(),8,floor+4.6,6.5,0,0);
            p.setDeltaMovement(Vec3.ZERO);
        });
        if(tick==955) mc.options.keyUp.setDown(true);
        if(tick>955 && tick<990 && WallWalk.frameOf(mc.player).equals(Frame.WORLD)) mc.options.keyUp.setDown(false);
        if(tick==985) check("walked over the exposed pillar edge",WallWalk.frameOf(mc.player).equals(Frame.WORLD)
                && mc.player.getY()>floor+4.99,"frame="+WallWalk.frameOf(mc.player)+" feet="+mc.player.position());
        if(tick==1000) { active=false; LOG.info("booth: PASS all checks ran"); mc.stop(); }
    }

    /** effects: measures the rendered camera and saves moving comparison frames. */
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!active) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null) return;
        var camera=mc.gameRenderer.getMainCamera();
        Frame f=WallWalk.frameOf(mc.player);
        double now=mc.player.tickCount+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if((time<250 || (time>=830 && time<855) || (time>=890 && time<925) || time>=945) && cameraRotation!=null) {
            double angle=2*Math.toDegrees(Math.acos(Math.min(1,Math.abs(cameraRotation.dot(camera.rotation())))));
            if(seen!=null && !seen.equals(f)) {
                transitions++; turnStarted=now; lastTurnShot=-1;
                double distance=cameraPosition.distanceTo(camera.getPosition());
                check("continuous camera at "+seen.gravity()+" -> "+f.gravity(),angle<2 && distance<0.15,"degrees="+angle+" blocks="+distance);
            }
            if(now-cameraTime>0.01) maxRate=Math.max(maxRate,angle/(now-cameraTime));
        }
        if((time<250 || time>=830) && now-turnStarted>=0 && now-turnStarted<12 && (int)(now-turnStarted)!=lastTurnShot) {
            lastTurnShot=(int)(now-turnStarted);
            Screenshot.grab(mc.gameDirectory,"turn-"+transitions+"-"+lastTurnShot+".png",mc.getMainRenderTarget(),ignored->{});
        }
        seen=f; cameraRotation=new org.joml.Quaternionf(camera.rotation()); cameraPosition=camera.getPosition(); cameraTime=now;
        if(time>=280 && time<820) {
            int phase=(time-280)%90;
            if(phase>=34 && phase<=49 && time!=lastShot) {
                lastShot=time;
                Screenshot.grab(mc.gameDirectory,"walking-"+f.gravity().name().toLowerCase(java.util.Locale.ROOT)+"-"+phase+".png",mc.getMainRenderTarget(),ignored->{});
            }
        }
    }

    /** effects: samples the real model after its normal animation pipeline, including installed animation mods. */
    @SubscribeEvent public static void renderedModel(RenderLivingEvent.Post<?,?> event) {
        if(!active || time<280 || (time-280)%90<31 || (time-280)%90>49) return;
        if(event.getEntity()!=Minecraft.getInstance().player) return;
        if(event.getRenderer().getModel() instanceof net.minecraft.client.model.HumanoidModel<?> model) {
            minLeg=Math.min(minLeg,model.rightLeg.xRot); maxLeg=Math.max(maxLeg,model.rightLeg.xRot);
        }
    }
    private static void check(String name,boolean ok,String detail) { LOG.info("booth: {} {} -- {}",ok?"PASS":"FAIL",name,detail); }
    private static void onServer(Minecraft mc,java.util.function.Consumer<ServerPlayer> action) {
        var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(()->action.accept(server.getPlayerList().getPlayer(id)));
    }
}
