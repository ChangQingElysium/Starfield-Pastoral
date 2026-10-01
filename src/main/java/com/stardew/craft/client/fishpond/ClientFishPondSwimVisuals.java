package com.stardew.craft.client.fishpond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.fishpond.service.PondSwimSpace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-only school simulation. Population comes exclusively from server pond storage. */
public final class ClientFishPondSwimVisuals {
    private static final Map<String,School> schools=new HashMap<>();
    private ClientFishPondSwimVisuals() {}
    public static void clear(){schools.clear();}
    public record JumpFishBinding(Vec3 startPosition,float scale,float yawDegrees,float pitchDegrees) {}
    public static JumpFishBinding reserveForJump(String dimension,ItemStack fish,Vec3 start,int holdTicks) {
        Swimmer best=null;School selected=null;double distance=Double.MAX_VALUE;
        for(var school:schools.values())if(school.dimension.equals(dimension)&&ItemStack.isSameItemSameTags(fish,school.fish)) {
            for(var swimmer:school.fishList)if(swimmer.reservedUntil<school.tick && swimmer.p.distanceToSqr(start)<distance) {
                distance=swimmer.p.distanceToSqr(start);best=swimmer;selected=school;
            }
        }
        if(best==null||distance>36)return null;
        best.reservedUntil=selected.tick+holdTicks+2;
        // The splash starts at the actual surface in this column, never a rectangle outside the water.
        Vec3 surface=best.p;
        while(selected.cells.contains(BlockPos.containing(surface).above().asLong()))surface=surface.add(0,1,0);
        surface=new Vec3(surface.x,Math.floor(surface.y)+8./9,surface.z);
        return new JumpFishBinding(surface,best.scale,best.yaw,best.pitch);
    }
    public static void render(FishPondBucketBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay) {
        if(!(be.getLevel() instanceof ClientLevel level))return;
        String key=level.dimension().location()+"/"+be.getBlockPos().asLong();
        schools.values().removeIf(s->s.level!=level || level.getGameTime()-s.seen>80);
        if(!be.hasFishVisuals() || !ClientFishPondFishRenderer.available(be.getFishSignPreview())){schools.remove(key);return;}
        var school=schools.computeIfAbsent(key,k->new School(level,be));
        school.sync(be);school.advance();
        for(var fish:school.fishList) {
            if(fish.reservedUntil>=school.tick)continue;
            var p=fish.previous.lerp(fish.p,partial);
            pose.pushPose();pose.translate(p.x-be.getBlockPos().getX(),p.y-be.getBlockPos().getY(),p.z-be.getBlockPos().getZ());
            ClientFishPondFishRenderer.renderFish(school.fish,pose,buffer,level,LevelRenderer.getLightColor(level,BlockPos.containing(p)),
                Mth.rotLerp(partial,fish.oldYaw,fish.yaw),Mth.lerp(partial,fish.oldPitch,fish.pitch),fish.roll,fish.scale);
            pose.popPose();
        }
    }
    private static final class School {
        final ClientLevel level;final String dimension;final RandomSource random;
        final List<Swimmer> fishList=new ArrayList<>();Set<Long> cells=Set.of();PondSwimSpace space;
        ItemStack fish=ItemStack.EMPTY;long tick,seen;
        School(ClientLevel level,FishPondBucketBlockEntity be){this.level=level;dimension=level.dimension().location().toString();random=RandomSource.create(be.getBlockPos().asLong());tick=level.getGameTime();}
        void sync(FishPondBucketBlockEntity be) {
            seen=level.getGameTime();var next=be.getFishSignPreview();
            Set<Long> nextCells=new HashSet<>();for(long p:be.getPondWaterCells())nextCells.add(p);
            if(!cells.equals(nextCells)||!ItemStack.isSameItemSameTags(fish,next)){fishList.clear();cells=Set.copyOf(nextCells);fish=next;space=new PondSwimSpace(cells,p->!level.getFluidState(p).isEmpty());}
            int count=be.getFishPopulation();
            while(fishList.size()>count)com.stardew.craft.port.PortJava.removeLast(fishList);
            while(fishList.size()<count) {
                Vec3 p=space.sample(random,ClientFishPondFishRenderer.bottomDweller(fish));if(p==null)break;
                fishList.add(new Swimmer(p,random));
            }
        }
        void advance() {
            long now=level.getGameTime();int steps=(int)Math.min(5,Math.max(0,now-tick));
            for(int n=0;n<steps;n++)for(var swimmer:fishList)swimmer.step(this);
            tick=now;
        }
    }
    private static final class Swimmer {
        Vec3 p,previous,velocity=Vec3.ZERO;List<Vec3> route=List.of();int waypoint,idle;
        float yaw,oldYaw,pitch,oldPitch,roll;final float scale;long reservedUntil=Long.MIN_VALUE;
        Swimmer(Vec3 p,RandomSource random){this.p=previous=p;scale=.56F+random.nextFloat()*.08F;yaw=oldYaw=random.nextFloat()*360;}
        void step(School school) {
            previous=p;oldYaw=yaw;oldPitch=pitch;
            if(reservedUntil>=school.tick || ClientFishPondFishRenderer.stationary(school.fish))return;
            if(idle>0){idle--;velocity=velocity.scale(.8);return;}
            if(waypoint>=route.size()) {
                var goal=school.space.sample(school.random,ClientFishPondFishRenderer.bottomDweller(school.fish));
                route=goal==null?List.of():school.space.route(p,goal);waypoint=0;
                if(route.isEmpty()){idle=10;return;}
            }
            var delta=route.get(waypoint).subtract(p);
            if(delta.length()<.12){waypoint++;if(waypoint>=route.size())idle=10+school.random.nextInt(40);return;}
            double speed=ClientFishPondFishRenderer.bottomDweller(school.fish)?.012:.033;
            Vec3 desired=delta.normalize().scale(speed);
            for(var other:school.fishList)if(other!=this) {
                Vec3 apart=p.subtract(other.p);double length=apart.length();
                if(length>.001&&length<.7)desired=desired.add(apart.scale((.7-length)*.025/length));
            }
            velocity=velocity.lerp(desired,.12);
            Vec3 next=p.add(velocity);
            if(!school.space.clear(p,next)){velocity=Vec3.ZERO;route=List.of();idle=5;return;}
            p=next;
            if(velocity.lengthSqr()>.000001) {
                float target=(float)Math.toDegrees(Math.atan2(velocity.z,velocity.x));
                float turn=Mth.clamp(Mth.wrapDegrees(target-yaw),-8,8);yaw+=turn;
                pitch=Mth.lerp(.2F,pitch,(float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance())));
                roll=Mth.lerp(.2F,roll,-turn*1.6F);
            }
        }
    }
}
