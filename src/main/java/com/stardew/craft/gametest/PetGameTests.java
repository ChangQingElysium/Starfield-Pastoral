package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.pet.*;
import com.stardew.craft.time.StardewTimeManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_pets")
@PrefixGameTestTemplate(false)
public final class PetGameTests {
    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void identityCareAndBowlAssignmentsSurviveRoundTrip(GameTestHelper h) {
        var data = new PetWorldData(); var farm = UUID.randomUUID(); var caretaker = UUID.randomUUID(); int i = 0;
        h.assertTrue(data.chooseInitial(farm) && !data.chooseInitial(farm), "Initial selection replay was accepted");
        for (var variant : PetVariant.values()) {
            var pet = new PetRecord(UUID.randomUUID(), farm, variant, "Mochi", 9);
            pet.friendship = 712; pet.petted.put(caretaker, 9); pet.careDay = 9; pet.timesPet = 5;
            pet.position = new Vec3(12.25 + i, 4.5, -2.75); pet.bowl = new BlockPos(12 + i++, 4, -5);
            data.bowl(new PetWorldData.Bowl(farm, pet.bowl, "stone", 9, false)); data.put(pet);
        }
        data.markLoved(farm); data.markPrepared(farm);
        var restored = PetWorldData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.forFarm(farm).size() == 12 && restored.loved(farm) && restored.prepared(farm) && !restored.chooseInitial(farm), "Save lost index or receipts");
        for (var pet : data.all()) {
            var copy = restored.find(pet.id);
            h.assertTrue(copy.save().equals(pet.save()) && restored.occupant(copy.bowl).id.equals(copy.id), "Pet identity/care/precise position changed");
            h.assertTrue(!restored.bowl(copy.bowl).outdoors(), "Indoor bowl became rain-filled after reload");
        }
        var removed = restored.forFarm(farm).getFirst(); var pos = removed.bowl; restored.removeBowl(pos);
        h.assertTrue(removed.bowl == null && restored.bowl(pos) == null, "Removed bowl retained pet assignment");
        restored.removeFarm(farm); h.assertTrue(restored.all().isEmpty() && restored.bowls().isEmpty(), "Deleted farm retained pet state"); h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void multiplayerPettingAndDailyWaterSettleOnce(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var registry = FarmInstanceRegistry.get(server); var clock = StardewTimeManager.get();
        int originalDay = clock.getCurrentDay(); var owner = UUID.randomUUID(); var member = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Pet test", "Pet test", FarmType.STANDARD); registry.addMember(owner, member);
        var data = PetWorldData.get(server);
        try {
            clock.setCurrentDay(5);
            var first = FakePlayerFactory.get(level, new GameProfile(owner, "PetOwner"));
            var second = FakePlayerFactory.get(level, new GameProfile(member, "PetMember"));
            first.getInventory().clearContent(); second.getInventory().clearContent();
            PetService.selectInitial(first, farm, "stardewcraft:cat0", "Mochi"); PetService.selectInitial(first, farm, "stardewcraft:dog0", "Duplicate");
            h.assertTrue(data.forFarm(farm.getInstanceId()).size() == 1, "Duplicate questionnaire created two pets");
            var pet = com.stardew.craft.port.PortJava.getFirst(data.forFarm(farm.getInstanceId())); var entity = ModEntities.PET.get().create(level); entity.setUUID(pet.id); entity.refresh(pet);
            PetService.interact(first, entity); PetService.interact(first, entity); PetService.interact(second, entity);
            h.assertTrue(pet.friendship == 12 && pet.timesPet == 1 && pet.petted.size() == 2, "Multiplayer granted daily friendship twice");
            var bowl = new BlockPos(2, 2, 2); data.bowl(new PetWorldData.Bowl(pet.farm, bowl, "wood", clock.getAbsoluteDay()));
            clock.setCurrentDay(6); PetService.onNewDay(level); PetService.onNewDay(level);
            h.assertTrue(pet.friendship == 18 && bowl.equals(pet.bowl), "Water or automatic bowl assignment did not settle once");
            data.removeBowl(bowl); clock.setCurrentDay(7); PetService.onNewDay(level); PetService.onNewDay(level);
            h.assertTrue(pet.friendship == 8, "Missing bowl penalty duplicated or missing");
            entity.discard();
        } finally { clock.setCurrentDay(originalDay); registry.deleteFarm(owner); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "construction_site")
    public static void invulnerabilityAndMovementMatchAuthoredFootStride(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(8, 3, 8));
        for (int x = -3; x <= 5; x++) for (int z = -2; z <= 2; z++) { level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); for (int y = 0; y < 3; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3); }
        for (var variant : PetVariant.values()) {
            var entity = ModEntities.PET.get().create(level); var pet = new PetRecord(UUID.randomUUID(), UUID.randomUUID(), variant, "Stride", 1); entity.refresh(pet);
            entity.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5); entity.setOnGround(true); entity.play("walk");
            h.assertTrue(!entity.hurt(level.damageSources().genericKill(), 10000), "Pet accepted damage"); entity.kill();
            h.assertTrue(entity.isAlive() && !entity.canBeLeashed(null) && !entity.shouldBeSaved(), "Pet protection or projection contract failed");
            double start = entity.getX();
            for (int tick = 0; tick < 14; tick++) { entity.getMoveControl().setWantedPosition(start + 4, origin.getY(), entity.getZ(), 1); entity.getMoveControl().tick(); entity.travel(Vec3.ZERO); }
            h.assertTrue(Math.abs(entity.getX() - start - variant.stride(false) / 16) < .0001, "One walk cycle does not match authored stride: " + variant);
            entity.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void nativeActionCoverageAndSourceGiftItemsResolve(GameTestHelper h) throws Exception {
        for (var variant : PetVariant.values()) {
            var species = PetBehaviors.get(variant);
            for (var state : species.states().values()) {
                h.assertTrue(species.clips().containsKey(state.clip()), "State references missing clip");
                h.assertTrue(state.entry().isEmpty() || species.clips().containsKey(state.entry()), "Missing posture entry");
                h.assertTrue(state.exit().isEmpty() || species.clips().containsKey(state.exit()), "Missing posture exit");
            }
        }
        try (var reader = new java.io.InputStreamReader(PetGameTests.class.getResourceAsStream("/data/stardewcraft/pet/gifts.json"))) {
            var tables = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var table : tables.entrySet()) for (var row : table.getValue().getAsJsonArray()) {
                String query = row.getAsJsonObject().get("query").getAsString();
                if (query.startsWith("LOCATION_FISH")) continue;
                var ids = new java.util.ArrayList<String>();
                if (query.startsWith("RANDOM_ITEMS")) { var parts = query.split(" "); for (int n = Integer.parseInt(parts[2]); n <= Integer.parseInt(parts[3]); n++) ids.add(parts[1] + n); }
                else ids.add(query);
                for (var id : ids) h.assertTrue(!com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack(id, 1).isEmpty(), "Unresolved original pet gift " + id);
            }
        }
        var pos = h.absolutePos(new BlockPos(1, 2, 1)); levelBowl(h, pos); h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void petGiftChanceAndPoolRequireFullFriendship(GameTestHelper h) {
        var level = h.getLevel(); var clock = StardewTimeManager.get(); int originalDay = clock.getCurrentDay();
        var pet = new PetRecord(UUID.randomUUID(), UUID.randomUUID(), PetVariant.DOG0, "Scout", 1);
        var entity = ModEntities.PET.get().create(level); entity.setUUID(pet.id); entity.refresh(pet);
        entity.moveTo(h.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F); level.addFreshEntity(entity);
        try {
            clock.setCurrentDay(8); int day = clock.getAbsoluteDay();
            for (int attempt = 0; attempt < 1000; attempt++) {
                if (com.stardew.craft.util.StardewDeterministicRandom.create(day, level.getSeed() / 2, attempt, 71928, pet.id.hashCode()).nextDouble() < .2) {
                    pet.timesPet = attempt; break;
                }
            }
            pet.friendship = 999; PetGifts.give(FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GiftTester")), entity, pet);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(4)).isEmpty(), "Pet gifted before full friendship");
            pet.friendship = 1000; PetGifts.give(FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GiftTester2")), entity, pet);
            var gifts = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(4));
            h.assertTrue(gifts.size() == 1 && !com.stardew.craft.port.PortJava.getFirst(gifts).getItem().isEmpty(), "Full-friendship pet gift did not spawn exactly one item stack");
            gifts.forEach(ItemEntity::discard); h.succeed();
        } finally { clock.setCurrentDay(originalDay); entity.discard(); }
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void butterflyPowderUsesStardewObjectMetadata(GameTestHelper h) {
        var stack = new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.BUTTERFLY_POWDER.get());
        var metadata = com.stardew.craft.api.v1.item.StardewItemDataApi.resolve(stack).orElse(null);
        h.assertTrue(metadata != null, "Butterfly Powder is not registered as a Stardew item");
        h.assertTrue(metadata.category().equals(new net.minecraft.resources.ResourceLocation("stardewcraft", "misc")),
                "Butterfly Powder lost its original Basic/category-0 mapping");
        h.assertTrue(metadata.baseSellPrice() == 0
                        && com.stardew.craft.api.v1.item.StardewItemDataApi.getSellPrice(stack) == -1
                        && metadata.edibility() == -300,
                "Butterfly Powder price or edibility differs from the original object data");
        h.assertTrue(stack.getMaxStackSize() == 999, "Butterfly Powder lost its original stackability");
        h.succeed();
    }
    private static void levelBowl(GameTestHelper h, BlockPos pos) {
        var level = h.getLevel(); level.setBlock(pos.below(), ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PET_BOWL_WOOD.get().defaultBlockState(), 3);
        h.assertTrue(PetBowlBlock.water(level, pos) && level.getBlockState(pos).getValue(PetBowlBlock.FULL), "Watering did not fill bowl");
        h.assertTrue(level.getBlockState(pos.below()).is(ModBlocks.GRASS_BLOCK.get()), "Bowl replaced permanent building grass");
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void managementPurchasesRejectReplayAndForeignOwnership(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var registry = FarmInstanceRegistry.get(level.getServer());
        var owner = UUID.randomUUID(); var outsider = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Pet buyer", "Pet buyer", FarmType.STANDARD);
        registry.createFarm(outsider, "Other farmer", "Other farmer", FarmType.STANDARD);
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "PetBuyer"));
        var other = FakePlayerFactory.get(level, new GameProfile(outsider, "OtherFarmer"));
        var data = PetWorldData.get(level.getServer());
        try {
            player.getInventory().clearContent();
            com.stardew.craft.player.PlayerStardewDataAPI.setMoney(player, 100000);
            data.markLoved(farm.getInstanceId());
            com.stardew.craft.player.PlayerDataManager.getPlayerData(player).addMailFlag(PetManagement.ADOPTION_MAIL);
            PetManagement.openShop(player); var nonce = nonce(player);
            var purchase = new PetActionPayload(nonce, new UUID(0, 0), "adopt", PetActionPayload.selection("stardewcraft:dog2", "豆包"), BlockPos.ZERO);
            PetManagement.submit(player, purchase); PetManagement.submit(player, purchase);
            var pets = data.forFarm(farm.getInstanceId());
            h.assertTrue(pets.size() == 1 && com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == 60000, "Purchase replay spent money or created another pet");
            var pet = com.stardew.craft.port.PortJava.getFirst(pets);
            h.assertTrue(pet.variant == PetVariant.DOG2 && pet.name.equals("豆包") && pet.bowl == null, "No-bowl adoption was blocked or assigned a nonexistent bowl");
            var bowl = farm.getOrigin().offset(4, 4, 4);
            data.bowl(new PetWorldData.Bowl(farm.getInstanceId(), bowl, "wood", -1));
            PetManagement.openShop(player); nonce = nonce(player);
            PetManagement.submit(player, new PetActionPayload(nonce, new UUID(0, 0), "adopt", PetActionPayload.selection("stardewcraft:cat0", "Miso"), BlockPos.ZERO));
            pets = data.forFarm(farm.getInstanceId());
            h.assertTrue(pets.size() == 2 && pets.stream().anyMatch(candidate -> candidate.name.equals("Miso") && bowl.equals(candidate.bowl))
                    && com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == 20000, "Free bowl was not assigned to the next adopted pet");
            PetManagement.open(other, pet.id);
            PetManagement.submit(other, new PetActionPayload(nonce(other), pet.id, "rename", "Intruder", BlockPos.ZERO));
            h.assertTrue(pet.name.equals("豆包"), "Foreign farm member renamed a pet");
            PetManagement.open(player, pet.id);
            PetManagement.submit(player, new PetActionPayload(nonce(player), pet.id, "remove", "", BlockPos.ZERO));
            h.assertTrue(data.find(pet.id) != null, "Management menu bypassed powder confirmation");

            player.moveTo(30.5, 52, -118.5);
            var wood = com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack("(O)709", 25);
            player.getInventory().add(wood);
            PetManagement.openBowls(player); nonce = nonce(player);
            var buyBowl = new PetActionPayload(nonce, new UUID(0, 0), "buy_bowl", "stone", BlockPos.ZERO);
            PetManagement.submit(player, buyBowl); PetManagement.submit(player, buyBowl);
            h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == 15000
                    && player.getInventory().countItem(com.stardew.craft.item.ModItems.PET_BOWL_STONE.get()) == 1
                    && player.getInventory().countItem(wood.getItem()) == 0, "Bowl purchase did not charge exactly 5000 and 25 hardwood once");
        } finally { registry.deleteFarm(owner); registry.deleteFarm(outsider); PetManagement.clear(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void marnieAdoptionRequiresDeliveredLetterOrSourceFallback(GameTestHelper h) {
        var level = h.getLevel(); var registry = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Adoption mail", "Adoption mail", FarmType.STANDARD);
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "AdoptionMail"));
        var pets = PetWorldData.get(level.getServer());
        try {
            var pet = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), PetVariant.CAT0, "Miso", 1);
            pets.put(pet); pets.markLoved(farm.getInstanceId());
            PetManagement.scheduleAdoptionMail(level.getServer(), farm);
            var playerData = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            h.assertTrue(playerData.getMailForTomorrow().contains(PetManagement.ADOPTION_MAIL), "Full-love farm did not queue Marnie's adoption letter");
            h.assertTrue(!PetManagement.unlocked(player), "Queued but unread adoption letter unlocked Marnie's shop early");
            playerData.addMailFlag(PetManagement.ADOPTION_MAIL);
            h.assertTrue(PetManagement.unlocked(player), "Read adoption letter did not unlock Marnie's shop");
            playerData.removeMailFlag(PetManagement.ADOPTION_MAIL);
            playerData.addMailFlag(PetManagement.REJECTED_ADOPTION_FLAG);
            h.assertTrue(PetManagement.unlocked(player), "Rejecting the initial pet did not preserve the source-game unlock fallback");
        } finally { registry.deleteFarm(owner); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty", timeoutTicks = 300)
    public static void projectionReusesIdentityAndDoesNotLoadDistantChunks(GameTestHelper h) {
        var level = h.getLevel(); var registry = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Pet projection", "Pet projection", FarmType.STANDARD); farm.markInitialized();
        var data = PetWorldData.get(level.getServer()); var pos = farm.getOrigin().offset(8, 4, 8);
        var pet = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), PetVariant.CAT3, "Snow", 1);
        var distant = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), PetVariant.TURTLE1, "Shell", 1);
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        level.setChunkForced(chunk.x, chunk.z, true);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(chunk.toLong()), "Waiting for entity-ready test chunk")).thenExecute(() -> {
        try {
            level.getChunkAt(pos);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 3; y++) level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 3);
            pet.position = Vec3.atBottomCenterOf(pos).add(.125, 0, -.125); pet.friendship = 432; data.put(pet);
            distant.position = Vec3.atBottomCenterOf(farm.getOrigin().offset(400, 4, 400)); data.put(distant);
            var farPos = BlockPos.containing(distant.position);
            h.assertTrue(!level.hasChunkAt(farPos), "Distant fixture was already loaded");
            PetService.project(level); var first = level.getEntity(pet.id);
            h.assertTrue(first instanceof PetEntity && first.position().equals(pet.position), "Visible projection lost saved identity or precise position");
            PetService.project(level); h.assertTrue(level.getEntity(pet.id) == first, "Repeated projection replaced a living pet");
            first.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            PetService.project(level); var restored = level.getEntity(pet.id);
            h.assertTrue(restored != null && restored != first && restored.getUUID().equals(pet.id) && pet.friendship == 432, "Projection recovery changed identity/care");
            h.assertTrue(!level.hasChunkAt(farPos) && level.getEntity(distant.id) == null, "Pet projection force-loaded an absent chunk");
        } finally {
            var visible = level.getEntity(pet.id); if (visible != null) visible.discard(); registry.deleteFarm(owner); level.setChunkForced(chunk.x, chunk.z, false);
        }
        }).thenSucceed();
    }

    private static UUID nonce(net.minecraft.server.level.ServerPlayer player) throws Exception {
        var field = PetManagement.class.getDeclaredField("sessions"); field.setAccessible(true);
        var session = ((java.util.Map<?, ?>) field.get(null)).get(player.getUUID());
        var accessor = session.getClass().getDeclaredMethod("nonce"); accessor.setAccessible(true);
        return (UUID) accessor.invoke(session);
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void packagedAssetChainsAreComplete(GameTestHelper h) throws Exception {
        for (var variant : PetVariant.values()) {
            try (var stream = new java.util.zip.GZIPInputStream(PetGameTests.class.getResourceAsStream("/assets/" + variant.breed().model().getNamespace() + "/" + variant.breed().model().getPath()));
                 var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                var asset = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); var rig = asset.getAsJsonObject("rig");
                int bones = rig.getAsJsonArray("bones").size();
                for (var clip : PetBehaviors.get(variant).clips().keySet()) h.assertTrue(asset.getAsJsonObject("clips").has(clip), "Packaged model is missing " + variant + ":" + clip);
                for (var f : rig.getAsJsonArray("faces")) for (var v : f.getAsJsonObject().getAsJsonArray("vertices")) {
                    var vertex = v.getAsJsonObject(); var indices = vertex.getAsJsonArray("bones"); var weights = vertex.getAsJsonArray("weights"); double total = 0;
                    for (int i = 0; i < indices.size(); i++) { h.assertTrue(indices.get(i).getAsInt() >= 0 && indices.get(i).getAsInt() < bones, "Invalid native skin joint"); total += weights.get(i).getAsDouble(); }
                    h.assertTrue(Math.abs(total - 1) < 1e-6, "Unnormalized native pet skin weights");
                }
            }
            try (var stream = PetGameTests.class.getResourceAsStream("/assets/" + variant.breed().icon().getNamespace() + "/" + variant.breed().icon().getPath())) {
                var icon = javax.imageio.ImageIO.read(stream); h.assertTrue(icon.getWidth() == 16 && icon.getHeight() == 16, "Pet icon is not native 16 px art");
            }
        }
        for (String style : java.util.List.of("wood", "stone", "hay")) {
            var states = json("/assets/stardewcraft/blockstates/pet_bowl_" + style + ".json").getAsJsonObject("variants");
            h.assertTrue(states.size() == 8, "Bowl lacks a season/water state");
            for (var state : states.entrySet()) {
                String modelId = state.getValue().getAsJsonObject().get("model").getAsString().split(":")[1];
                var model = json("/assets/stardewcraft/models/" + modelId + ".json");
                String particle = model.getAsJsonObject("textures").get("particle").getAsString().split(":")[1];
                try (var stream = PetGameTests.class.getResourceAsStream("/assets/stardewcraft/textures/" + particle + ".png")) {
                    var image = javax.imageio.ImageIO.read(stream);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) h.assertTrue(image.getRGB(x, y) >>> 24 == 255, "Bowl particle samples a transparent atlas gutter");
                }
            }
        }
        h.succeed();
    }
    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = PetGameTests.class.getResourceAsStream(path); var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @GameTest(templateNamespace = "stardewcraft_pets", template = "empty")
    public static void petCanvasAndLocalClipsAgreeWithPointerAcrossScales(GameTestHelper h) {
        for (int[] size : new int[][]{{320, 240}, {1067, 701}, {1920, 1080}, {2561, 1441}}) {
            double expected = -1;
            for (int scale = 1; scale <= 8; scale++) {
                var canvas = com.stardew.craft.client.gui.common.GuiLayoutMath.viewport(size[0], size[1], scale, 564, 360);
                double physical = canvas.scale() * scale;
                if (expected < 0) expected = physical;
                h.assertTrue(Math.abs(physical - expected) < 1e-8 && canvas.width() >= 564 && canvas.height() >= 360, "Pet canvas depends on GUI scale");
                for (int[] point : new int[][]{{18, 22}, {208, 198}, {366, 226}, {532, 302}}) {
                    double x = canvas.x() + point[0] * canvas.scale(), y = canvas.y() + point[1] * canvas.scale();
                    h.assertTrue(Math.abs(canvas.mouseX(x) - point[0]) < 1e-8 && Math.abs(canvas.mouseY(y) - point[1]) < 1e-8, "Pet control draw/click coordinates disagree");
                }
                var pose = new org.joml.Matrix4f().translate((float) canvas.x(), (float) canvas.y(), 0).scale((float) canvas.scale(), (float) canvas.scale(), 1).translate(12, 9, 0);
                var clip = com.stardew.craft.client.gui.common.GuiScissorMath.framebuffer(pose, scale, 16, 40, 176, 256);
                h.assertTrue(clip.left() <= (canvas.x() + 28 * canvas.scale()) * scale + .001 && clip.right() >= (canvas.x() + 188 * canvas.scale()) * scale - .001, "Local clip cuts its own transformed controls");
            }
        }
        h.succeed();
    }
}
