package com.stardew.craft.monster;
import com.stardew.craft.entity.monster.BatFlight;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WingedFlightTest {
    @Test void sourceHitRecoveryKeepsAcceptedKnockbackWithoutPoweredSteering(){
        for(var flight:new MonsterFlightMotion[]{new SerpentFlightMotion(),new BatFlight(),new FlySteering()}){
            var state=flight.save();state.putInt("Slipperiness",24);flight.load(state);
            flight.steer(new Vec3(0,0,-4),true,true);
            flight.knockback(0,8);flight.hit();
            var expected=flight.velocity();
            for(int step=0;step<9;step++){
                flight.elapsed(step%3==2?18:16);
                expected=expected.scale(1-1.0/24);
                flight.steer(new Vec3(0,0,-4),true,false);
                assertEquals(expected,flight.velocity(),"Recovery accelerated or clipped the accepted hit trajectory");
                assertTrue(flight.velocity().z>0,"Chasing erased the reverse trajectory during recovery");
            }
        }
    }
    @Test void flyWaitsForEmergenceAndHitLocksAllThreeAxes(){
        var fly=new FlySteering();fly.initialize(RandomSource.create(3));
        for(int i=0;i<60;i++){fly.advance(i%3==2?18:16,new Vec3(3,5,2),true,true);assertEquals(Vec3.ZERO,fly.velocity());}
        for(int i=0;i<40;i++)fly.advance(16,new Vec3(3,5,2),true,true);
        assertTrue(fly.velocity().y>0);fly.hit();var heading=fly.heading();
        for(int i=0;i<30;i++){fly.advance(16,new Vec3(-2,-5,-3),true,true);assertEquals(heading,fly.heading());}
        fly.advance(18,new Vec3(-2,-5,-3),true,true);assertEquals(heading,fly.heading());
        fly.advance(16,new Vec3(-2,-5,-3),true,true);assertNotEquals(heading,fly.heading());
    }
    @Test void batVariantsKeepDifferentAccelerationAndSourceSpeedCaps(){
        var ordinary=new BatFlight();var iridium=new BatFlight();var deep=new BatFlight();
        ordinary.variant(false,false);iridium.variant(true,false);deep.variant(true,true);
        for(var flight:new BatFlight[]{ordinary,iridium,deep})flight.initialize(RandomSource.create(5));
        var goal=new Vec3(0,0,-10);ordinary.steer(goal,true,true);iridium.steer(goal,true,true);
        assertTrue(iridium.velocity().length()>ordinary.velocity().length());
        assertEquals(5,iridium.speedLimit());assertEquals(8,deep.speedLimit());
        for(int i=0;i<1000;i++)for(var f:new BatFlight[]{ordinary,iridium,deep}){f.steer(new Vec3(2,4,3),true,true);assertTrue(f.velocity().length()<=f.speedLimit()+1e-10);}
    }
    @Test void savedFlightKeepsEmergenceAndVerticalKnockbackState(){
        var fly=new FlySteering();fly.initialize(RandomSource.create(9));
        for(int i=0;i<9;i++)fly.advance(16,null,false,true);
        var copy=new FlySteering();copy.load(fly.save());assertEquals(fly.save(),copy.save());
        for(int i=0;i<150;i++){fly.advance(16,new Vec3(0,5,-2),true,true);copy.advance(16,new Vec3(0,5,-2),true,true);assertEquals(fly.save(),copy.save());}
        fly.hit();fly.knockback(3,-2);copy.load(fly.save());assertEquals(fly.save(),copy.save());
    }
}
