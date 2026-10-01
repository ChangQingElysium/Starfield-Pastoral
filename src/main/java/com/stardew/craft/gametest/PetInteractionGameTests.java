package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import com.stardew.craft.pet.*;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.world.interaction.InteractionHintService;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder("stardewcraft_pets")
@PrefixGameTestTemplate(false)
public final class PetInteractionGameTests {
    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void crosshairAndRepeatInteractionFollowEachPlayersPettingDay(GameTestHelper h) {
        var ownerOffers = new ArrayList<CompoundTag>(); var memberOffers = new ArrayList<CompoundTag>(); var foreignOffers = new ArrayList<CompoundTag>();
        var owner = player(h, "PetCursorOwner", ownerOffers); var member = player(h, "PetCursorMember", memberOffers); var foreign = player(h, "PetCursorOther", foreignOffers);
        var registry = FarmInstanceRegistry.get(h.getLevel().getServer());
        var farm = registry.createFarm(owner.getUUID(), "Pet cursor", "Pet cursor", FarmType.STANDARD); registry.addMember(owner.getUUID(), member.getUUID());
        var data = PetWorldData.get(owner.server); var clock = StardewTimeManager.get(); int originalDay = clock.getCurrentDay();
        var hat = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof StardewHatItem).findFirst().orElseThrow();
        try {
            for (var variant : PetVariant.values()) {
                clock.setCurrentDay(5); ownerOffers.clear(); memberOffers.clear();
                var pet = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), variant, "Cursor " + variant.id(), clock.getAbsoluteDay());
                var entity = ModEntities.PET.get().create(h.getLevel()); entity.setUUID(pet.id); entity.refresh(pet);
                h.assertTrue(InteractionHintService.resolveEntity(owner, entity).isEmpty(), "Missing pet record advertised an action");
                data.put(pet);
                hint(h, owner, entity, StardewInteractionHintType.GRAB);
                PetService.interact(owner, entity);
                h.assertTrue(ownerOffers.isEmpty() && pet.friendship == 12 && pet.timesPet == 1, "First right-click opened a menu or lost petting");
                hint(h, owner, entity, StardewInteractionHintType.LOOK);
                hint(h, member, entity, StardewInteractionHintType.GRAB);
                PetService.interact(owner, entity);
                menu(h, ownerOffers, "manage", pet.id);
                h.assertTrue(ownerOffers.getLast().getList("Pets", 10).stream().map(t -> (CompoundTag) t)
                        .anyMatch(row -> row.getUUID("Id").equals(pet.id) && row.getBoolean("Petted")), "Menu lost today's petting state");
                PetService.interact(member, entity);
                h.assertTrue(memberOffers.isEmpty(), "Another player's first petting opened a menu");
                hint(h, member, entity, StardewInteractionHintType.LOOK);
                PetService.interact(member, entity); menu(h, memberOffers, "manage", pet.id);
                h.assertTrue(pet.friendship == 12 && pet.timesPet == 1 && pet.petted.size() == 2, "Opening menus or multiplayer petting repeated rewards");
                h.assertTrue(InteractionHintService.resolveEntity(foreign, entity).isEmpty(), "Foreign player saw a pet action");
                PetService.interact(foreign, entity); h.assertTrue(foreignOffers.isEmpty() && pet.petted.size() == 2, "Foreign player interacted with pet");

                ownerOffers.clear(); owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hat));
                hint(h, owner, entity, variant.wearsHat() ? StardewInteractionHintType.GRAB : StardewInteractionHintType.LOOK);
                PetService.interact(owner, entity);
                if (variant.wearsHat()) h.assertTrue(ownerOffers.isEmpty() && !pet.hat.isEmpty(), "Menu intercepted putting on a hat");
                else menu(h, ownerOffers, "manage", pet.id);
                ownerOffers.clear(); owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BUTTERFLY_POWDER.get()));
                hint(h, owner, entity, StardewInteractionHintType.GRAB);
                PetService.interact(owner, entity); menu(h, ownerOffers, "remove", pet.id);
                h.assertTrue(data.find(pet.id) == pet && owner.getMainHandItem().getCount() == 1, "Powder skipped confirmation");
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                clock.setCurrentDay(6); hint(h, owner, entity, StardewInteractionHintType.GRAB); hint(h, member, entity, StardewInteractionHintType.GRAB);
                owner.setShiftKeyDown(true); ownerOffers.clear();
                hint(h, owner, entity, StardewInteractionHintType.LOOK);
                PetService.interact(owner, entity); menu(h, ownerOffers, "manage", pet.id);
                owner.setShiftKeyDown(false); hint(h, owner, entity, StardewInteractionHintType.GRAB);
                h.assertTrue(pet.timesPet == 1 && pet.friendship == 12, "Sneak menu consumed tomorrow's petting");
                entity.discard();
            }
        } finally {
            clock.setCurrentDay(originalDay); registry.deleteFarm(owner.getUUID());
            for (var player : List.of(owner, member, foreign)) { PetManagement.clear(player.getUUID()); player.discard(); }
        }
        h.succeed();
    }

    private static void hint(GameTestHelper h, ServerPlayer player, PetEntity entity, StardewInteractionHintType type) {
        var hint = InteractionHintService.resolveEntity(player, entity).orElseThrow();
        h.assertTrue(hint.type() == type && !hint.done(), "Wrong crosshair action for " + entity.variant() + ": " + hint);
    }
    private static void menu(GameTestHelper h, List<CompoundTag> offers, String kind, UUID pet) {
        h.assertTrue(offers.size() == 1 && offers.getLast().getString("Kind").equals(kind)
                && offers.getLast().getUUID("Selected").equals(pet), "Right-click did not open the targeted pet's " + kind + " menu");
    }
    private static ServerPlayer player(GameTestHelper h, String name, List<CompoundTag> offers) {
        var server = h.getLevel().getServer();
        var player = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (!(packet instanceof ClientboundCustomPayloadPacket custom) || !(custom.payload() instanceof PetScreenPayload pet)) return;
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
                try { PetScreenPayload.CODEC.encode(buffer, pet); offers.add(PetScreenPayload.CODEC.decode(buffer).data()); h.assertTrue(!buffer.isReadable(), "Pet menu left unread wire data"); }
                finally { buffer.release(); }
            }
        };
        return player;
    }
}
