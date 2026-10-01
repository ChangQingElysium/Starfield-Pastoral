package com.stardew.craft.gametest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.api.v1.world.StardewWorldAnchor;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcSocialRules;
import com.stardew.craft.npc.runtime.*;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.world.WorldAnchorRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcContentRecoveryGameTests {
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",batch="npc_content_recovery")
    public static void sandyFirstContactDoesNotRequirePersonalVaultFlag(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"SandyContact"));
        var data=PlayerStardewDataAPI.getData(player);
        data.removeMailFlag(CCStoryFlags.CC_VAULT);
        player.setPos(0,70,0);
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),h.getLevel());
        npc.setNpcId("sandy");npc.setPos(1,70,0);
        try {
            h.assertTrue(NpcSocialRules.canSocialize("sandy",player),"Meeting Sandy silently requires personal Vault progress");
            h.assertTrue(NpcInteractionService.probeInteractionHint(player,npc).visible(),"Sandy has no interaction hint");
            NpcInteractionService.onInteract(player,npc,InteractionHand.MAIN_HAND);
            h.assertTrue(npc.isFacingOverrideActive(),"Right click did not enter the dialogue-facing flow");
            h.assertTrue(!data.hasMailFlag(CCStoryFlags.CC_VAULT),"Talking incorrectly unlocked the bus");
        } finally { npc.cancelAutonomousActions();NpcExecutionCoordinator.cancel(npc); }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",batch="npc_content_recovery")
    public static void retiredPlaceholderFloorsCannotWinOverOfficialPoints(GameTestHelper h) throws Exception {
        var original=new LinkedHashMap<net.minecraft.resources.ResourceLocation,StardewWorldAnchor>();
        var local=new LinkedHashMap<net.minecraft.resources.ResourceLocation,StardewWorldAnchor>();
        for(var a:WorldAnchorRegistry.all()) {
            original.put(a.id(),a);
            local.put(a.id(),new StardewWorldAnchor(a.id(),h.getLevel().dimension().location(),a.position(),a.yaw(),a.indoor(),a.useGroundHeight(),a.locationId(),a.roles()));
        }
        var runtime=NpcRuntimeDataManager.get(h.getLevel());
        var old=new HashMap<String,NpcRuntimeState>();
        var spawns=NpcDataRegistry.events().get("default_spawns").getAsJsonObject("spawns");
        try {
            WorldAnchorRegistry.replaceLegacyNpcAnchors(local);
            for(String id:List.of("sebastian","marnie")) {
                old.put(id,runtime.states().get(id));
                var config=spawns.getAsJsonObject(id);
                var state=new NpcRuntimeState(id);
                state.setNamedPointId(config.get("point").getAsString());
                state.setLocationName(id.equals("sebastian")?"sebastianroom":"animalshop");
                runtime.states().put(id,state);
                Vec3 target=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null).position();
                h.assertTrue(target.y==(id.equals("sebastian")?46:34),"Official floor changed");
                Vec3 home=(Vec3)call("resolveConfiguredHome",new Class<?>[]{ServerLevel.class,JsonObject.class},h.getLevel(),config);
                h.assertTrue(home.equals(target),"Bootstrap and schedule point diverge");
                // The named point must remain authoritative even if legacy XYZ is stale.
                var stale=config.deepCopy();stale.addProperty("y",80);
                h.assertTrue(target.equals(call("resolveConfiguredHome",new Class<?>[]{ServerLevel.class,JsonObject.class},h.getLevel(),stale)),"Home reference lost to stale raw coordinates");
                for(var entry:config.getAsJsonArray("retired_positions")) {
                    var p=entry.getAsJsonObject();
                    state.rememberPosition(new NpcRuntimeState.ActualPosition(h.getLevel().dimension().location().toString(),p.get("x").getAsDouble(),p.get("y").getAsDouble(),p.get("z").getAsDouble(),0,StardewTimeManager.get().getAbsoluteDay()));
                    var recovered=call("resolveRuntimeScheduleSpawn",new Class<?>[]{ServerLevel.class,String.class},h.getLevel(),id);
                    h.assertTrue(target.equals(recovered),"Retired same-day position restored for "+id);
                }
                var travelling=target.add(3,0,0);
                state.rememberPosition(new NpcRuntimeState.ActualPosition(h.getLevel().dimension().location().toString(),travelling.x,travelling.y,travelling.z,0,StardewTimeManager.get().getAbsoluteDay()));
                h.assertTrue(travelling.equals(call("resolveRuntimeScheduleSpawn",new Class<?>[]{ServerLevel.class,String.class},h.getLevel(),id)),"Ordinary movement progress was discarded");
            }
        } finally {
            WorldAnchorRegistry.replaceLegacyNpcAnchors(original);
            old.forEach((id,state)->{if(state==null)runtime.states().remove(id);else runtime.states().put(id,state);});
        }
        h.succeed();
    }
    private static Object call(String name,Class<?>[] types,Object...args) throws Exception {
        var method=NpcSpawnManager.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);
    }
}
