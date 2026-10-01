package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class NpcAnimationSyncGameTests {
    private NpcAnimationSyncGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void remoteAndLateObserversAnimateWithoutServerCapabilityRegistry(GameTestHelper helper) {
        var server = npc(helper);
        server.setNpcId("robin");
        server.setWalking(true);
        var updates = server.getEntityData().packDirty();
        var first = npc(helper);
        var second = npc(helper);
        var late = npc(helper);
        var capabilities = NpcDataRegistry.capabilities();
        try {
            // A remote client never loads the server's NPC data-pack registry.
            NpcDataRegistry.replaceCapabilities(Map.of());
            receive(server, first, updates);
            receive(server, second, updates);
            assertAnimation(helper, first, "walk");
            assertAnimation(helper, second, "walk");
            receive(server, late, server.getEntityData().getNonDefaultValues());
            assertAnimation(helper, late, "walk");
            server.setWalking(false);
            updates = server.getEntityData().packDirty();
            for (var observer : List.of(first, second, late)) {
                receive(server, observer, updates);
                assertAnimation(helper, observer, "idle");
            }
            server.setWalking(true);
            receive(server, first, server.getEntityData().packDirty());
            assertAnimation(helper, first, "walk");
        } finally {
            NpcDataRegistry.replaceCapabilities(capabilities);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void serverCapabilityReloadUpdatesExistingObservers(GameTestHelper helper) {
        var server = npc(helper);
        server.setNpcId("robin");
        var observer = npc(helper);
        receive(server, observer, server.getEntityData().packDirty());
        var capabilities = NpcDataRegistry.capabilities();
        try {
            var profile = capabilities.get("robin");
            for (String animation : List.of(NpcCapabilityProfile.ANIM_IDLE_ONLY, NpcCapabilityProfile.ANIM_IDLE_WALK)) {
                var replacement = new java.util.LinkedHashMap<>(capabilities);
                replacement.put("robin", new NpcCapabilityProfile(profile.npcId(), profile.implemented(), profile.pathingEnabled(),
                        animation, profile.age(), profile.manners(), profile.socialAnxiety(), profile.optimism(), profile.gender(), profile.datable()));
                NpcDataRegistry.replaceCapabilities(replacement);
                server.tick();
                server.setWalking(true);
                var updates = server.getEntityData().packDirty();
                NpcDataRegistry.replaceCapabilities(Map.of());
                receive(server, observer, updates);
                assertAnimation(helper, observer, animation.equals(NpcCapabilityProfile.ANIM_IDLE_ONLY) ? "idle" : "walk");
            }
        } finally {
            NpcDataRegistry.replaceCapabilities(capabilities);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void walkingStateTracksDisplacementAndStopsAfterRestOrTeleport(GameTestHelper helper) {
        var server = npc(helper);
        server.setNpcId("robin");
        var pos = helper.absolutePos(new BlockPos(5, 1, 5));
        server.setPos(pos.getX(), pos.getY(), pos.getZ());
        server.tick();
        server.setPos(server.getX() + 0.1, server.getY(), server.getZ());
        server.tick();
        helper.assertTrue(server.isWalking(), "Actual NPC displacement did not enable walking");
        server.tick();
        helper.assertTrue(!server.isWalking(), "Stationary NPC kept walking");
        server.setPos(server.getX() + 5, server.getY(), server.getZ());
        server.tick();
        helper.assertTrue(!server.isWalking(), "Teleport was mistaken for walking");
        helper.succeed();
    }

    private static StardewNpcEntity npc(GameTestHelper helper) {
        var npc = new StardewNpcEntity(ModEntities.STARDEW_NPC.get(), helper.getLevel());
        npc.setNoAi(true);
        return npc;
    }

    private static void receive(StardewNpcEntity server, StardewNpcEntity observer, List<SynchedEntityData.DataValue<?>> values) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.level().registryAccess());
        try {
            // PORT(1.20.1): vanilla packets have no STREAM_CODEC; use the packet's own wire methods.
            new ClientboundSetEntityDataPacket(server.getId(), values).write(buffer);
            observer.getEntityData().assignValues(new ClientboundSetEntityDataPacket(buffer).packedItems());
        } finally {
            buffer.release();
        }
    }

    private static void assertAnimation(GameTestHelper helper, StardewNpcEntity observer, String expected) {
        helper.assertTrue(com.stardew.craft.model.ModelAnimation.loop(expected).equals(observer.modelAnimation(false, 0)),
                "Remote NPC selected the wrong animation; expected " + expected);
    }
}
