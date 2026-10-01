package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.Config;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.fishing.server.FishingSession;
import com.stardew.craft.fishing.server.FishingSessionManager;
import com.stardew.craft.fishing.server.FishingCatchProgress;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.specialorder.SpecialOrderDefinitions;
import com.stardew.craft.specialorder.SpecialOrderInstance;
import com.stardew.craft.specialorder.SpecialOrderWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder("stardewcraft_fishing_rules")
@PrefixGameTestTemplate(false)
public final class FishingOrderCollectionGameTests {
    @GameTest(templateNamespace = "stardewcraft_fishing_rules", template = "ring_utilities")
    public static void crabPotAndMiscCatchesUnlockFishingCollection(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Collection catch test"));
        Item[] catches = {
                ModItems.LOBSTER.get(), ModItems.CRAYFISH.get(), ModItems.CRAB.get(), ModItems.COCKLE.get(),
                ModItems.MUSSEL.get(), ModItems.SHRIMP.get(), ModItems.SNAIL.get(), ModItems.PERIWINKLE.get(),
                ModItems.OYSTER.get(), ModItems.CLAM.get(), ModItems.SEAWEED.get(), ModItems.GREEN_ALGAE.get(),
                ModItems.WHITE_ALGAE.get(), ModItems.SEA_JELLY.get(), ModItems.RIVER_JELLY.get(), ModItems.CAVE_JELLY.get()
        };
        for (Item item : catches) {
            ItemStack stack = new ItemStack(item);
            var source = com.stardew.craft.data.VanillaObjectCatalog.resolve(stack);
            helper.assertTrue(source != null && source.collectionTab() == 1,
                    "Catch is missing from the source fishing collection: " + item);
            FishingCatchProgress.record(player, stack, 1);
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            helper.assertTrue(PlayerStardewDataAPI.getFishCatchCount(player, id) == 1,
                    "Catch did not unlock its collection entry: " + id);
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fishing_rules", template = "ring_utilities")
    public static void linusCountsCaughtTrashWithEitherMinigameSetting(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper)) {
            // Reproduce the reported saved order: donation complete, collection still zero.
            fixture.order.objectives().get(1).add(20);
            fixture.reloadOrder();
            int count = 0;
            for (boolean enabled : new boolean[]{true, false}) {
                Config.ENABLE_FISHING_MINIGAME.set(enabled);
                for (Item item : new Item[]{ModItems.TRASH.get(), ModItems.DRIFTWOOD.get(),
                        ModItems.BROKEN_GLASSES.get(), ModItems.BROKEN_CD.get(),
                        ModItems.SOGGY_NEWSPAPER.get(), ModItems.JOJA_COLA.get()}) {
                    fixture.catchItem(new ItemStack(item), true);
                    helper.assertTrue(fixture.progress() == ++count, "Caught trash was missed or counted twice");
                }
                fixture.catchItem(new ItemStack(ModItems.SARDINE.get()), false);
                helper.assertTrue(fixture.progress() == count, "A normal fish counted as trash");
            }
            helper.assertTrue(fixture.order.objectives().get(1).progress() == 20, "Existing donation was changed");
            fixture.catchItem(new ItemStack(ModItems.TRASH.get(), 20 - count), true);
            helper.assertTrue(fixture.progress() == 20 && fixture.order.complete(), "Fully collected/donated order did not complete");
            fixture.reloadOrder();
            helper.assertTrue(fixture.progress() == 20 && fixture.order.complete(), "Fixed order progress did not survive reload");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_fishing_rules", template = "ring_utilities")
    public static void fishingOverflowCountsOnlyItemsActuallyReceived(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper)) {
            for (int slot = 0; slot < fixture.player.getInventory().items.size(); slot++) {
                fixture.player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            // Leave the equipped rod and one mergeable trash slot, but no empty storage slot.
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FISHING_ROD.get()));
            int trashCapacity = new ItemStack(ModItems.TRASH.get()).getMaxStackSize();
            fixture.player.getInventory().setItem(1, new ItemStack(ModItems.TRASH.get(), trashCapacity - 1));
            fixture.catchItem(new ItemStack(ModItems.TRASH.get(), 3), true);
            helper.assertTrue(fixture.progress() == 1, "Partial insertion counted overflow before pickup");
            helper.assertTrue(fixture.player.getInventory().countItem(ModItems.TRASH.get()) == trashCapacity, "Partial catch was duplicated");
            helper.assertTrue(fixture.droppedTrash() == 2, "Overflow was lost or duplicated");
            fixture.catchItem(new ItemStack(ModItems.TRASH.get(), 2), true);
            helper.assertTrue(fixture.progress() == 1, "A full inventory counted unreceived trash");
            helper.assertTrue(fixture.droppedTrash() == 4, "Full-inventory catch was lost or duplicated");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final FakePlayer player;
        private final FishingSessionManager manager;
        private final SpecialOrderWorldData data;
        private final Map<ResourceKey<Level>, ServerLevel> levels;
        private final ServerLevel previousMining;
        private final boolean previousMinigame = Config.ENABLE_FISHING_MINIGAME.get();
        private SpecialOrderInstance order;

        @SuppressWarnings("unchecked")
        private Fixture(GameTestHelper helper) throws Exception {
            this.helper = helper;
            ServerLevel level = helper.getLevel();
            levels = (Map<ResourceKey<Level>, ServerLevel>) field(level.getServer(), MinecraftServer.class, "levels");
            previousMining = levels.put(ModMiningDimensions.STARDEW_MINING, level);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Linus fishing test"));
            player.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FISHING_ROD.get()));
            manager = FishingSessionManager.get(level.getServer());
            data = SpecialOrderWorldData.get(level);
            var definition = SpecialOrderDefinitions.get("Linus");
            helper.assertTrue(definition != null, "Shipped Linus order is missing");
            order = SpecialOrderInstance.create(definition, 1, 7);
            order.setAccepted(true);
            order.addParticipant(player.getUUID());
            data.active().add(order);
        }

        private int progress() { return com.stardew.craft.port.PortJava.getFirst(order.objectives()).progress(); }

        private void reloadOrder() {
            data.active().remove(order);
            order = SpecialOrderInstance.load(order.save());
            data.active().add(order);
        }

        @SuppressWarnings("unchecked")
        private void catchItem(ItemStack item, boolean instant) throws Exception {
            manager.prepareUse(player, UUID.randomUUID());
            var session = new FishingSession(UUID.randomUUID(), helper.absolutePos(new BlockPos(4, 1, 4)), 5, 0);
            set(session, "state", FishingSession.State.BITE_READY);
            set(session, "plannedCatch", item.copy());
            set(session, "skipMinigame", instant);
            set(session, "difficulty", 30);
            var sessions = (Map<UUID, FishingSession>) field(manager, FishingSessionManager.class, "sessionsByPlayer");
            sessions.put(player.getUUID(), session);
            if (!instant && Config.ENABLE_FISHING_MINIGAME.get()) {
                // The interactive path reaches this state after the hooked presentation.
                session.startMinigame(-1);
                manager.handleResult(player, session.id(), true, 1, false, 1, false, session.caughtFishSize());
            } else {
                helper.assertTrue(manager.tryStartMinigame(player), "Catch did not start");
            }
            helper.assertTrue(session.state() == FishingSession.State.DONE, "Catch did not settle");
            int before = progress();
            helper.assertTrue(!manager.tryStartMinigame(player), "A settled catch started again");
            manager.handleResult(player, session.id(), true, 1, false, 1, false, 1);
            helper.assertTrue(progress() == before, "A replayed result counted again");
        }

        private int droppedTrash() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3))
                    .stream().filter(entity -> entity.getItem().is(ModItems.TRASH.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        @Override
        public void close() {
            manager.cancel(player);
            data.active().remove(order);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3))
                    .forEach(ItemEntity::discard);
            player.discard();
            Config.ENABLE_FISHING_MINIGAME.set(previousMinigame);
            if (previousMining == null) levels.remove(ModMiningDimensions.STARDEW_MINING);
            else levels.put(ModMiningDimensions.STARDEW_MINING, previousMining);
        }
    }

    private static Object field(Object object, Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(FishingSession session, String name, Object value) throws Exception {
        var field = FishingSession.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(session, value);
    }
}
