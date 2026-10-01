package com.stardew.craft.gametest;

import com.stardew.craft.cutscene.server.CombatRescuePoints;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_combat_rescue")
@PrefixGameTestTemplate(false)
public final class CombatRescuePlacementGameTests {
    @GameTest(template="hospital_room",timeoutTicks=100)
    public static void clinicBedsideAndCameraFitTheSavedRoom(GameTestHelper h) {
        // Frozen block-state-only clinic excerpt, origin (20,42,-18).
        // GameTest places template Y=0 one block above the helper origin.
        // No dependency on a developer's save and no writes to that save.
        h.assertTrue(h.getBlockState(new net.minecraft.core.BlockPos(2,2,3)).is(
                com.stardew.craft.block.ModBlocks.BED_1.get()),"Clinic bed fixture is missing: "+h.getBlockState(new net.minecraft.core.BlockPos(2,2,3)));
        var level=h.getLevel();
        for(var point:new CombatRescuePoints.Point[]{CombatRescuePoints.H01,CombatRescuePoints.H02}) {
            Vec3 p=h.absoluteVec(new Vec3(point.x()-20,point.y()-41,point.z()+18));
            h.assertTrue(level.noCollision(new AABB(p.x()-.3,p.y()+.001,p.z()-.3,p.x()+.3,p.y()+1.9,p.z()+.3)),"Bedside actor intersects furniture");
            h.assertTrue(!level.noCollision(new AABB(p.x()-.2,p.y()-.1,p.z()-.2,p.x()+.2,p.y(),p.z()+.2)),"Bedside actor has no floor");
        }
        var c=CombatRescuePoints.H03;
        Vec3 camera=h.absoluteVec(new Vec3(c.x()-20,c.y()-41,c.z()+18));
        h.assertTrue(level.noCollision(new AABB(camera.subtract(.15,.15,.15),camera.add(.15,.15,.15))),"Clinic camera is inside architecture");
        for(var local:new Vec3[]{new Vec3(2.5,2.95,2.55),new Vec3(4.8,3.6,2.35),new Vec3(3.65,3.5,3.375)}) {
            Vec3 target=h.absoluteVec(local);
            h.assertTrue(level.clip(new ClipContext(camera,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()==HitResult.Type.MISS,"Clinic camera cannot see a character");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void authoredLobbyHasSupportedActorsExitAndClearCamera(GameTestHelper h) {
        // GameTest creates only vanilla dimensions. Place the same authored lobby
        // at its production coordinates in this disposable server's overworld.
        var level=h.getLevel();
        OrdinaryMineRuntime.ensure(level,0);
        var p=CombatRescuePoints.M01;
        h.assertTrue(level.noCollision(new AABB(p.x()-.72,p.y()+.001,p.z()-1.45,p.x()+.72,p.y()+.7,p.z()+.8)),"Prone player intersects lobby architecture");
        h.assertTrue(level.noCollision(new AABB(p.x()-.3,p.y()+.001,p.z()-.3,p.x()+.3,p.y()+1.85,p.z()+.3)),"Player cannot stand up in rescue position");
        for(double z=CombatRescuePoints.M02.z();z<=CombatRescuePoints.M03.z();z+=.125) {
            double x=CombatRescuePoints.M02.x(),y=CombatRescuePoints.M02.y();
            h.assertTrue(level.noCollision(new AABB(x-.3,y+.001,z-.3,x+.3,y+1.95,z+.3)),"Rescuer path enters a wall at "+z);
            h.assertTrue(!level.noCollision(new AABB(x-.2,y-.1,z-.2,x+.2,y,z+.2)),"Rescuer has no supporting floor");
        }
        var c=CombatRescuePoints.M04;var camera=new Vec3(c.x(),c.y(),c.z());
        h.assertTrue(level.noCollision(new AABB(camera.subtract(.15,.15,.15),camera.add(.15,.15,.15))),"Camera inside architecture");
        for(var target:new Vec3[]{new Vec3(p.x(),p.y()+.3,p.z()),new Vec3(.5,67.4,-1.5)})
            h.assertTrue(level.clip(new ClipContext(camera,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()==HitResult.Type.MISS,"Camera view is blocked");
        h.succeed();
    }
}
