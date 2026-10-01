package com.stardew.craft.gametest;

import com.stardew.craft.api.v1.pet.StardewPetBehavior;
import com.stardew.craft.api.v1.pet.StardewPetBreedDefinition;
import com.stardew.craft.api.v1.pet.StardewPetSpeciesDefinition;
import com.stardew.craft.api.v1.pet.StardewPets;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.payload.FarmSelectionSubmitPayload;
import com.stardew.craft.pet.*;
import com.stardew.craft.player.PlayerStardewDataAPI;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("stardewcraft_pets")
@PrefixGameTestTemplate(false)
public final class PetAddonApiGameTests {
    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void addonCatalogSelectionCareAndConfigurationUseThePublicApi(GameTestHelper h) throws Exception {
        int originalCount = StardewPets.breeds().size(); String originalFingerprint = StardewPets.fingerprint();
        var offers = new ArrayList<CompoundTag>(); var owner = PetInitialAdoptionGameTests.player(h, "PetAddonOwner", offers);
        var farms = FarmInstanceRegistry.get(owner.server); var farm = farms.createFarm(owner.getUUID(), "Addon pet", "Addon pet", FarmType.STANDARD);
        var data = PetWorldData.get(owner.server);
        try (var scope = catalogScope()) {
            var cat = StardewPets.species(id("stardewcraft:cat")).orElseThrow();
            StardewPetBehavior graph = new StardewPetBehavior(cat.behavior().clips(), cat.behavior().states(), cat.behavior().postureExits());
            var species = new StardewPetSpeciesDefinition(id("pet_api_test:woodland"), .72f, .78f, false, .2, graph, 0, List.of(), null, null, .6f, 1);
            StardewPets.registerSpecies(species);
            var base = StardewPets.breed(id("stardewcraft:cat0")).orElseThrow();
            for (int i = 0; i < 3; i++) StardewPets.registerBreed(new StardewPetBreedDefinition(id("pet_api_test:woodland_companion_" + i), species.id(),
                    "pet.api_test.woodland", base.model(), base.texture(), base.icon(), true, i != 0, 137, 3.5, 3.5));
            var first = PetVariant.find("pet_api_test:woodland_companion_0").orElseThrow();
            h.assertTrue(PetVariant.find("cat0").isEmpty(), "Unnamespaced breed IDs were still accepted");
            h.assertTrue(StardewPets.breeds().size() == originalCount + 3 && PetVariant.initialChoices().contains(first), "Addon absent from new-farm choices");
            var lastPage = PetChoicePage.of(true, 99);
            h.assertTrue(lastPage.page() == 1 && lastPage.pages() == 2 && lastPage.entries().size() == 1
                    && lastPage.entries().getFirst().id().equals("pet_api_test:woodland_companion_2"), "Thirteenth starter choice was clipped or misindexed");
            h.assertTrue(PetChoicePage.of(false, 0).entries().stream().noneMatch(v -> v.id().equals(first.id()))
                    && PetChoicePage.of(false, 1).entries().size() == 2, "Shop ignored per-breed availability or paging");
            reject(h, () -> StardewPets.registerBreed(first.breed()), "Duplicate breed replaced existing content");
            reject(h, () -> StardewPets.registerBreed(new StardewPetBreedDefinition(id("pet_api_test:invalid"), id("pet_api_test:missing"),
                    "pet.api_test.invalid", base.model(), base.texture(), base.icon(), true, true, 1, 4, 4)), "Unknown species was registered");
            var brokenClips = new HashMap<>(graph.clips()); brokenClips.remove("walk");
            reject(h, () -> new StardewPetBehavior(brokenClips, graph.states(), graph.postureExits()), "Missing locomotion clip was accepted");
            reject(h, () -> new StardewPetBehavior.State("custom_walk", "", "", -1, -1, -1, -1, -1,
                    0, "", true, List.of(), List.of(), List.of(), List.of()), "Unrecognized navigation clip was accepted");

            var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), owner.server.registryAccess(), ConnectionType.NEOFORGE);
            try {
                var form = new FarmSelectionSubmitPayload("stardewcraft:standard", "Addon pet", false, "Owner", "Acorn", true, Map.of(), first.id(), "团:团");
                FarmSelectionSubmitPayload.STREAM_CODEC.encode(wire, form);
                h.assertTrue(FarmSelectionSubmitPayload.STREAM_CODEC.decode(wire).equals(form), "Namespaced ID was truncated in the new-farm form");
                PetManagement.openInitial(owner);
                var request = new PetActionPayload(offers.getLast().getUUID("Nonce"), new UUID(0, 0), "initial", PetActionPayload.selection(first.id(), "团:团"), BlockPos.ZERO);
                PetActionPayload.CODEC.encode(wire, request); var decoded = PetActionPayload.CODEC.decode(wire);
                h.assertTrue(decoded.selection().variant().equals(first.id()) && decoded.selection().name().equals("团:团"), "Namespace/name delimiters collided");
                PetManagement.submit(owner, decoded); PetManagement.submit(owner, decoded);
                h.assertTrue(data.forFarm(farm.getInstanceId()).size() == 1, "Addon initial choice failed or replay duplicated it");
                var pet = data.forFarm(farm.getInstanceId()).getFirst();
                h.assertTrue(pet.variant.id().equals(first.id()) && pet.name.equals("团:团"), "Wrong addon breed or name granted");
                var restored = PetRecord.load(pet.save()); h.assertTrue(restored.save().equals(pet.save()), "New namespaced pet did not persist");
                var entity = ModEntities.PET.get().create(h.getLevel()); entity.setUUID(pet.id); entity.refresh(pet);
                h.assertTrue(Math.abs(entity.getBbWidth() - .72) < 1e-5 && Math.abs(entity.getBbHeight() - .78) < 1e-5, "Addon dimensions were replaced by cat/dog defaults");
                offers.clear(); PetService.interact(owner, entity); PetService.interact(owner, entity);
                h.assertTrue(pet.friendship == 12 && pet.timesPet == 1 && offers.size() == 1 && offers.getFirst().getString("Kind").equals("manage")
                        && offers.getFirst().getUUID("Selected").equals(pet.id), "Addon did not inherit petting and direct management");
                entity.discard();

                data.markLoved(farm.getInstanceId());
                com.stardew.craft.player.PlayerDataManager.getPlayerData(owner).addMailFlag(PetManagement.ADOPTION_MAIL);
                PlayerStardewDataAPI.setMoney(owner, 1000);
                data.bowl(new PetWorldData.Bowl(farm.getInstanceId(), farm.getOrigin().offset(3, 4, 3), "wood", -1));
                offers.clear(); PetManagement.openShop(owner);
                PetManagement.submit(owner, new PetActionPayload(offers.getLast().getUUID("Nonce"), new UUID(0, 0), "adopt", PetActionPayload.selection(first.id(), "Blocked"), BlockPos.ZERO));
                h.assertTrue(data.forFarm(farm.getInstanceId()).size() == 1 && PlayerStardewDataAPI.getMoney(owner) == 1000, "Client bypassed addon shop eligibility");
                var purchase = new PetActionPayload(offers.getLast().getUUID("Nonce"), new UUID(0, 0), "adopt", PetActionPayload.selection("pet_api_test:woodland_companion_1", "Purchased"), BlockPos.ZERO);
                PetManagement.submit(owner, purchase); PetManagement.submit(owner, purchase);
                h.assertTrue(data.forFarm(farm.getInstanceId()).size() == 2 && PlayerStardewDataAPI.getMoney(owner) == 863, "Addon price or replay protection failed");
                h.assertTrue(!wire.isReadable(), "Pet API codecs left unread bytes");

                String fingerprint = StardewPets.fingerprint(); h.assertTrue(!fingerprint.equals(originalFingerprint), "Addon was omitted from catalog negotiation");
                var replies = new ArrayList<CustomPacketPayload>(); var disconnected = new ArrayList<Object>(); var finished = new ArrayList<Object>();
                var context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    switch (method.getName()) { case "reply" -> replies.add((CustomPacketPayload) args[0]); case "disconnect" -> disconnected.add(args[0]); case "finishCurrentTask" -> finished.add(args[0]); default -> throw new AssertionError(method.getName()); }
                    return null;
                });
                PetCatalogHandshake.Offer.CODEC.encode(wire, new PetCatalogHandshake.Offer(fingerprint));
                PetCatalogHandshake.Offer.handle(PetCatalogHandshake.Offer.CODEC.decode(wire), context);
                h.assertTrue(replies.size() == 1 && disconnected.isEmpty(), "Matching addon catalog did not acknowledge");
                PetCatalogHandshake.Ack.CODEC.encode(wire, (PetCatalogHandshake.Ack) replies.getFirst());
                PetCatalogHandshake.Ack.handle(PetCatalogHandshake.Ack.CODEC.decode(wire), context);
                h.assertTrue(finished.equals(List.of(PetCatalogHandshake.TYPE)), "Catalog task did not finish");
                PetCatalogHandshake.Offer.handle(new PetCatalogHandshake.Offer(originalFingerprint), context);
                PetCatalogHandshake.Ack.handle(new PetCatalogHandshake.Ack(originalFingerprint), context);
                h.assertTrue(disconnected.size() == 2 && finished.size() == 1, "Mismatched addon catalog was accepted");
                reject(h, () -> StardewPets.registerSpecies(species), "Registration remained open after configuration");
            } finally { wire.release(); }
        } finally { farms.deleteFarm(owner.getUUID()); PetManagement.clear(owner.getUUID()); owner.discard(); }
        h.assertTrue(StardewPets.breeds().size() == originalCount && StardewPets.fingerprint().equals(originalFingerprint), "Test addon leaked into the base catalog");
        h.succeed();
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static void reject(GameTestHelper h, Runnable action, String message) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, message);
    }
    // Tests run in the same JVM as other farm tests; restore registrations, diagnostics and freeze state.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AutoCloseable catalogScope() throws Exception {
        var restore = new ArrayList<AutoCloseable>();
        for (String name : List.of("species", "breeds")) { var field = field(StardewPets.class, name); var value = field.get(null); restore.add(() -> field.set(null, value)); }
        for (String name : List.of("SPECIES", "BREEDS")) {
            Object registry = field(StardewPets.class, name).get(null);
            for (String member : List.of("registrations", "metrics")) {
                Map map = (Map) field(registry.getClass(), member).get(registry); var copy = new HashMap(map);
                restore.add(() -> { map.clear(); map.putAll(copy); });
            }
            List issues = (List) field(registry.getClass(), "issues").get(registry); var copy = new ArrayList(issues);
            restore.add(() -> { issues.clear(); issues.addAll(copy); });
            for (String member : List.of("snapshot", "lifecycle")) { var field = field(registry.getClass(), member); var value = field.get(registry); restore.add(() -> field.set(registry, value)); }
        }
        return () -> { for (var action : restore) action.close(); };
    }
    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
}
