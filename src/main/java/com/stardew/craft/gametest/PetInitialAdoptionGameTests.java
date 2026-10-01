package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.pet.*;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.time.StardewTimePauseService;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder("stardewcraft_pets")
@PrefixGameTestTemplate(false)
public final class PetInitialAdoptionGameTests {
    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty", timeoutTicks = 100)
    public static void legacyLoginWaitsForFormsAndFreeClaimSurvivesReconnect(GameTestHelper h) {
        var offers = new ArrayList<CompoundTag>(); var player = player(h, "LegacyPetOwner", offers);
        var registry = FarmInstanceRegistry.get(player.server);
        var farm = registry.createFarm(player.getUUID(), "Legacy", "Legacy", FarmType.STANDARD); farm.markInitialized();
        var data = PetWorldData.get(player.server); var profile = PlayerDataManager.getPlayerData(player); profile.setMoney(0);
        PetInitialAdoption.login(new PlayerEvent.PlayerLoggedInEvent(player)); PetInitialAdoption.poll(player);
        h.assertTrue(offers.isEmpty() && PetInitialAdoption.needed(player), "Pet form replaced missing profile or consumed eligibility");
        profile.setProfile("Legacy", "Apples", 0); PetInitialAdoption.poll(player);
        h.assertTrue(offers.isEmpty(), "Pet form opened before a fresh client GUI report");
        h.runAfterDelay(1, () -> {
            StardewTimePauseService.updateClientState(player, true, true);
            PetInitialAdoption.login(new PlayerEvent.PlayerLoggedInEvent(player)); PetInitialAdoption.poll(player);
            h.assertTrue(offers.isEmpty(), "Pet form overwrote an open screen");
        });
        h.runAfterDelay(2, () -> {
            try {
                StardewTimePauseService.updateClientState(player, false, false);
                PetInitialAdoption.login(new PlayerEvent.PlayerLoggedInEvent(player)); PetInitialAdoption.poll(player);
                h.assertTrue(offers.size() == 1 && offers.getFirst().getString("Kind").equals("initial"), "Eligible old farm did not receive the pet questionnaire");
                PetInitialAdoption.poll(player); h.assertTrue(offers.size() == 1, "Login repeatedly reopened the form");
                var stale = offers.getLast().getUUID("Nonce");
                PetInitialAdoption.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                h.assertTrue(PetInitialAdoption.needed(player), "Closing without choosing lost the free pet");
                // The bowl page now delegates its initial-choice button to this entry; its full block interaction is covered separately.
                PetManagement.openInitial(player);
                h.assertTrue(offers.getLast().getString("Kind").equals("initial"), "Initial-choice entry did not reopen the questionnaire");
                PetManagement.submit(player, request(stale, PetActionPayload.selection("stardewcraft:cat0", "Replay")));
                h.assertTrue(data.forFarm(farm.getInstanceId()).isEmpty(), "Pre-disconnect nonce was accepted");
                PetManagement.submit(player, request(offers.getLast().getUUID("Nonce"), PetActionPayload.selection("stardewcraft:turtle0", "Shell")));
                h.assertTrue(PetInitialAdoption.needed(player), "Invalid starter consumed eligibility");
                PetManagement.submit(player, request(offers.getLast().getUUID("Nonce"), PetActionPayload.selection("stardewcraft:dog2", "   ")));
                h.assertTrue(PetInitialAdoption.needed(player), "Blank name consumed eligibility");
                var claim = request(offers.getLast().getUUID("Nonce"), PetActionPayload.selection("stardewcraft:dog2", "豆包"));
                PetManagement.submit(player, claim); PetManagement.submit(player, claim);
                var pets = data.forFarm(farm.getInstanceId());
                h.assertTrue(pets.size() == 1 && pets.getFirst().variant == PetVariant.DOG2 && pets.getFirst().name.equals("豆包"), "Initial claim lost breed/name or duplicated");
                h.assertTrue(profile.getMoney() == 0 && offers.getLast().getString("Kind").equals("initial_done"), "Free initial claim charged money or failed to close");
                var restored = PetWorldData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
                h.assertTrue(!restored.needsInitialChoice(farm.getInstanceId()) && restored.find(pets.getFirst().id).name.equals("豆包"), "Save roundtrip lost claim receipt or pet");
                int sent = offers.size(); PetInitialAdoption.login(new PlayerEvent.PlayerLoggedInEvent(player)); PetInitialAdoption.poll(player);
                h.assertTrue(offers.size() == sent, "Claimed farm got another login questionnaire");
                h.succeed();
            } finally { cleanup(player); registry.deleteFarm(player.getUUID()); }
        });
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void oldFarmIdentityAndChoiceReceiptsProtectSharedFarms(GameTestHelper h) {
        var owner = player(h, "PetReceiptOwner", new ArrayList<>()); var member = player(h, "PetReceiptMember", new ArrayList<>());
        var registry = FarmInstanceRegistry.get(owner.server); var farm = registry.createFarm(owner.getUUID(), "Owner", "Shared", FarmType.STANDARD);
        registry.addMember(owner.getUUID(), member.getUUID()); var data = PetWorldData.get(owner.server);
        try {
            var oldFarmTag = farm.save(); oldFarmTag.remove("InstanceId");
            var migrated = FarmInstance.load(oldFarmTag);
            h.assertTrue(migrated.getInstanceId().equals(FarmInstance.load(oldFarmTag).getInstanceId())
                    && migrated.getInstanceId().equals(FarmInstance.load(migrated.save()).getInstanceId()), "Old farm identity changed across migration/reload");
            h.assertTrue(!PetInitialAdoption.needed(member) && !PetService.selectInitial(member, farm, "stardewcraft:cat0", "Member"), "Co-farmer claimed the owner's initial pet");
            h.assertTrue(data.needsInitialChoice(farm.getInstanceId()), "Rejected member request consumed farm entitlement");
            PetService.selectInitial(owner, farm, "", "");
            h.assertTrue(!PetInitialAdoption.needed(owner), "Explicit no-pet choice from new-farm setup was treated as an old save");
            var oldPets = new PetWorldData(); var pet = new PetRecord(UUID.randomUUID(), migrated.getInstanceId(), PetVariant.CAT0, "Existing", 1);
            oldPets.put(pet); var tag = oldPets.save(new CompoundTag(), h.getLevel().registryAccess()); tag.remove("InitialChoices");
            var loaded = PetWorldData.load(tag, h.getLevel().registryAccess());
            h.assertTrue(!loaded.needsInitialChoice(pet.farm), "Existing legacy pet without receipt got a free duplicate");
            loaded.remove(pet.id);
            var afterRemoval = PetWorldData.load(loaded.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(!afterRemoval.needsInitialChoice(pet.farm), "Removing an existing pet reset initial entitlement");
            h.succeed();
        } finally { cleanup(owner); cleanup(member); registry.deleteFarm(owner.getUUID()); }
    }

    private static PetActionPayload request(UUID nonce, String value) { return new PetActionPayload(nonce, new UUID(0, 0), "initial", value, BlockPos.ZERO); }
    private static void cleanup(ServerPlayer player) {
        PetInitialAdoption.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        StardewTimePauseService.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player)); player.discard();
    }
    static ServerPlayer player(GameTestHelper h, String name, List<CompoundTag> offers) {
        var server = h.getLevel().getServer();
        var player = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (!(packet instanceof ClientboundCustomPayloadPacket custom) || !(custom.payload() instanceof PetScreenPayload pet)) return;
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
                try { PetScreenPayload.CODEC.encode(buffer, pet); offers.add(PetScreenPayload.CODEC.decode(buffer).data()); h.assertTrue(!buffer.isReadable(), "Pet questionnaire left unread wire data"); }
                finally { buffer.release(); }
            }
        };
        return player;
    }
}
