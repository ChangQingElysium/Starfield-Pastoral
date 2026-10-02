package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.WizardBuildingItem;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runtime pickup boundaries, including the production Wizard ownership subscriber. */
@GameTestHolder("stardewcraft")
@PrefixGameTestTemplate(false)
public final class PortPickupGameTests {
    private PortPickupGameTests() {}

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "port-pickup"));
        player.getInventory().clearContent();
        return player;
    }

    private static ItemEntity item(GameTestHelper helper, ItemStack stack) {
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setNoPickUpDelay();
        return item;
    }

    private static void fillInventory(FakePlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stone = new ItemStack(Items.STONE);
            stone.setCount(stone.getMaxStackSize());
            player.getInventory().items.set(i, stone);
        }
    }

    private static final class Probe implements AutoCloseable {
        int preCount;
        int postCount;
        int nativePreCount;
        int nativePostCount;
        int nativePickedCount;
        int currentCount;
        boolean aliveAtPost;
        boolean originalGetterIsCopy;
        boolean currentGetterIsLive;
        ItemStack originalStack;
        Consumer<ItemEntityPickupEvent.Pre> decision = event -> {};
        Consumer<EntityItemPickupEvent> nativeDecision = event -> {};
        final Consumer<ItemEntityPickupEvent.Pre> pre;
        final Consumer<ItemEntityPickupEvent.Post> post;
        final Consumer<EntityItemPickupEvent> nativePre;
        final Consumer<PlayerEvent.ItemPickupEvent> nativePost;

        Probe(ItemEntity item) {
            pre = event -> {
                if (event.getItemEntity() != item) return;
                preCount++;
                decision.accept(event);
            };
            post = event -> {
                if (event.getItemEntity() != item) return;
                postCount++;
                originalStack = event.getOriginalStack();
                ItemStack mutation = event.getOriginalStack();
                mutation.setCount(123);
                originalGetterIsCopy = event.getOriginalStack().getCount() == originalStack.getCount();
                currentGetterIsLive = event.getCurrentStack() == item.getItem();
                currentCount = event.getCurrentStack().getCount();
                aliveAtPost = item.isAlive();
            };
            nativePre = event -> {
                if (event.getItem() != item) return;
                nativePreCount++;
                nativeDecision.accept(event);
            };
            nativePost = event -> {
                if (event.getOriginalEntity() != item) return;
                nativePostCount++;
                nativePickedCount = event.getStack().getCount();
            };
            var bus = MinecraftForge.EVENT_BUS;
            bus.addListener(EventPriority.NORMAL, false, ItemEntityPickupEvent.Pre.class, pre);
            bus.addListener(EventPriority.NORMAL, false, ItemEntityPickupEvent.Post.class, post);
            bus.addListener(EventPriority.NORMAL, false, EntityItemPickupEvent.class, nativePre);
            bus.addListener(EventPriority.NORMAL, false, PlayerEvent.ItemPickupEvent.class, nativePost);
        }

        @Override
        public void close() {
            var bus = MinecraftForge.EVENT_BUS;
            bus.unregister(pre);
            bus.unregister(post);
            bus.unregister(nativePre);
            bus.unregister(nativePost);
        }
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void fullAndPartialPickupKeepCompleteOriginalStackAndNativeEvents(GameTestHelper helper) {
        for (boolean partial : new boolean[] {false, true}) {
            FakePlayer player = player(helper);
            ItemStack apple = new ItemStack(Items.APPLE);
            int capacity = Math.min(apple.getMaxStackSize(), player.getInventory().getMaxStackSize());
            if (partial) {
                fillInventory(player);
                player.getInventory().items.set(0, new ItemStack(Items.APPLE, capacity - 1));
            }
            ItemEntity item = item(helper, new ItemStack(Items.APPLE, 8));
            try (Probe probe = new Probe(item)) {
                item.playerTouch(player);
                // Both 1.20 and 1.21 Inventory.add return false after a partial merge exhausts space.
                // Preserve that native path: partial insertion changes inventory but emits neither Post.
                int expectedPost = partial ? 0 : 1;
                helper.assertTrue(probe.preCount == 1 && probe.postCount == expectedPost
                        && probe.nativePreCount == 1 && probe.nativePostCount == expectedPost,
                        "Pickup events duplicated or missing: partial=" + partial + " pre=" + probe.preCount
                                + " post=" + probe.postCount + " nativePre=" + probe.nativePreCount
                                + " nativePost=" + probe.nativePostCount + " remaining=" + item.getItem().getCount()
                                + " capacity=" + capacity + " inventorySlot0=" + player.getInventory().items.get(0));
                if (partial) {
                    helper.assertTrue(probe.originalStack == null && item.isAlive() && item.getItem().getCount() == 7
                            && player.getInventory().items.get(0).getCount() == capacity,
                            "Partial merge must insert exactly one, leave seven, and not manufacture a Post");
                    // Check the event API separately, without pretending native partial pickup emitted it.
                    ItemEntityPickupEvent.Post api = new ItemEntityPickupEvent.Post(player, item, new ItemStack(Items.APPLE, 8));
                    ItemStack mutableCopy = api.getOriginalStack();
                    mutableCopy.setCount(1);
                    helper.assertTrue(api.getOriginalStack().getCount() == 8 && api.getCurrentStack() == item.getItem()
                            && api.getCurrentStack().getCount() == 7, "Post API lost independent original/live current stacks");
                } else {
                    helper.assertTrue(probe.originalStack.getCount() == 8 && probe.originalGetterIsCopy
                            && probe.currentGetterIsLive && probe.aliveAtPost,
                            "Post lost complete original snapshot or pre-take boundary");
                    helper.assertTrue(probe.currentCount == 0 && probe.nativePickedCount == 8
                            && item.isRemoved() && player.getInventory().items.get(0).getCount() == 8,
                            "Complete pickup must preserve native count, insertion and discard");
                }
            } finally {
                item.discard();
                player.getInventory().clearContent();
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void delayedPickupStillFiresPreButNotNativePickupOrPost(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemEntity item = item(helper, new ItemStack(Items.APPLE, 8));
        item.setPickUpDelay(20);
        try (Probe probe = new Probe(item)) {
            item.playerTouch(player);
            item.playerTouch(player);
            helper.assertTrue(probe.preCount == 2 && probe.nativePreCount == 0 && probe.postCount == 0
                    && item.getItem().getCount() == 8 && player.getInventory().isEmpty(),
                    "Delay must block inventory, not the NeoForge Pre boundary");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void portDenialAndNativeForgeCancellationBothPreventPickup(GameTestHelper helper) {
        for (boolean portDenial : new boolean[] {true, false}) {
            FakePlayer player = player(helper);
            ItemEntity item = item(helper, new ItemStack(Items.APPLE, 8));
            try (Probe probe = new Probe(item)) {
                if (portDenial) probe.decision = event -> event.setCanPickup(TriState.FALSE);
                else probe.nativeDecision = event -> event.setCanceled(true);
                item.playerTouch(player);
                helper.assertTrue(probe.preCount == 1 && probe.nativePreCount == (portDenial ? 0 : 1)
                        && probe.postCount == 0 && probe.nativePostCount == 0
                        && item.isAlive() && item.getItem().getCount() == 8 && player.getInventory().isEmpty(),
                        "Port denial or Forge cancellation was bypassed");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void forcedPickupBypassesDelayAndTargetButNeverDeletesWithoutInventorySpace(GameTestHelper helper) {
        for (boolean full : new boolean[] {false, true}) {
            FakePlayer player = player(helper);
            if (full) fillInventory(player);
            ItemEntity item = item(helper, new ItemStack(Items.APPLE, 8));
            item.setPickUpDelay(20);
            item.setTarget(UUID.randomUUID());
            try (Probe probe = new Probe(item)) {
                probe.decision = event -> event.setCanPickup(TriState.TRUE);
                item.playerTouch(player);
                helper.assertTrue(probe.preCount == 1 && probe.nativePreCount == 1
                        && probe.postCount == (full ? 0 : 1), "Forced pickup did not follow inventory insertion");
                helper.assertTrue(full ? item.isAlive() && item.getItem().getCount() == 8 : item.isRemoved(),
                        "TRUE must not behave like Forge ALLOW's already-handled shortcut");
            } finally {
                item.discard();
                player.getInventory().clearContent();
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void wizardOwnershipBindsAndDeniesDuringPickupDelay(GameTestHelper helper) {
        FakePlayer owner = player(helper);
        FakePlayer stranger = player(helper);
        ItemEntity item = item(helper, new ItemStack(ModItems.JUNIMO_HUT.get()));
        item.setPickUpDelay(20);
        try (Probe probe = new Probe(item)) {
            item.playerTouch(owner);
            helper.assertTrue(owner.getUUID().equals(WizardBuildingItem.getOwner(item.getItem()))
                    && owner.getInventory().isEmpty(), "Existing Wizard handler must bind on delayed collision");
            item.setPickUpDelay(1);
            item.playerTouch(stranger);
            for (int i = 0; i < 19; i++) item.tick();
            helper.assertTrue(item.hasPickUpDelay() && item.isAlive() && stranger.getInventory().isEmpty()
                    && owner.getUUID().equals(WizardBuildingItem.getOwner(item.getItem()))
                    && probe.preCount == 2 && probe.nativePreCount == 0 && probe.postCount == 0,
                    "Delayed foreign collision must preserve owner and reset the original 20-tick denial delay");
        } finally {
            item.discard();
        }
        helper.succeed();
    }
}
