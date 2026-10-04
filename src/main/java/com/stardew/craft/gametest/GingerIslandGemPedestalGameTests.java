package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.GingerIslandGemPedestalBlockEntity;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandGemPedestalBlock;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.item.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

/** A pedestal's visual gem state must correspond to one real, conserved offering. */
@GameTestHolder("stardewcraft_ginger_pedestal")
@PrefixGameTestTemplate(false)
public final class GingerIslandGemPedestalGameTests {
    private static final String TEMPLATE_NS = "stardewcraft_ginger_collision";

    private GingerIslandGemPedestalGameTests() {}

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void fiveProjectGemsUseMainAndExtensionWithoutLosingComponents(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL, "Pedestal gems");
        BlockPos bottom = bottom(h);
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (var gem : gems()) {
                    player.getInventory().clearContent();
                    BlockPos main = place(h, player, bottom, facing, new ItemStack(block(), 2));
                    BlockPos extension = extension(h, main, facing);
                    ItemStack hand = namedGem(gem.item(), 3);
                    ItemStack expected = hand.copyWithCount(1);
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    useItem(h, player, extension);
                    h.assertTrue(hand.getCount() == 2, "Putting a gem did not consume exactly one: " + gem.state());
                    assertOffering(h, main, expected);
                    assertVisual(h, main, facing, gem.state());
                    for (BlockPos cell : block().placementPositions(main, facing)) {
                        h.assertTrue((h.getLevel().getBlockEntity(cell) instanceof GingerIslandGemPedestalBlockEntity)
                                        == cell.equals(main), "An extension created another offering inventory");
                    }
                    replaceHandKeepingStack(player, ItemStack.EMPTY);
                    h.assertTrue(PortBlockInteraction.stateUseWithoutItem(h.getLevel().getBlockState(main), h.getLevel(), player, hit(main))
                                    .consumesAction(), "Empty-hand retrieval failed");
                    h.assertTrue(be(h, main).offering().isEmpty() && componentCount(player, expected) == 3,
                            "Retrieved gem was lost, duplicated, or changed");
                    assertVisual(h, main, facing, GingerIslandStateDecorBlock.Gem.EMPTY);
                    cleanup(h, main, facing);
                }
            }
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void creativeDoesNotConsumeAndUnsupportedItemsCannotFillAnEmptyPedestal(GameTestHelper h) {
        var player = player(h, GameType.CREATIVE, "Pedestal creative");
        BlockPos main = place(h, player, bottom(h), Direction.NORTH, new ItemStack(block()));
        try {
            ItemStack hand = new ItemStack(ModItems.AMETHYST.get(), 7);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            useItem(h, player, main);
            h.assertTrue(hand.getCount() == 7 && be(h, main).offering().getCount() == 1,
                    "Creative offering consumed the hand or stored a whole stack");
            be(h, main).clearOffering();
            player.setGameMode(GameType.SURVIVAL);
            for (Item item : List.of(Items.STONE, Items.DIAMOND_PICKAXE, Items.EMERALD)) {
                ItemStack unsupported = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, unsupported);
                PortBlockInteraction.stateUseItemOn(h.getLevel().getBlockState(main), unsupported, h.getLevel(), player,
                        InteractionHand.MAIN_HAND, hit(main));
                h.assertTrue(unsupported.getCount() == 1 && be(h, main).offering().isEmpty(),
                        "An unsupported item was consumed or turned into a project gem: " + item);
            }
        } finally {
            cleanup(h, main, Direction.NORTH);
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void occupiedPedestalRetrievesInsteadOfExchangingTheHeldItem(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL, "Pedestal no swap");
        BlockPos main = place(h, player, bottom(h), Direction.NORTH, new ItemStack(block()));
        BlockPos extension = extension(h, main, Direction.NORTH);
        try {
            for (Item incoming : List.of(ModItems.TOPAZ.get(), Items.STICK, Items.DIAMOND_PICKAXE)) {
                player.getInventory().clearContent();
                ItemStack ruby = namedGem(ModItems.RUBY.get(), 2);
                ItemStack expected = ruby.copyWithCount(1);
                player.setItemInHand(InteractionHand.MAIN_HAND, ruby);
                useItem(h, player, main);
                ItemStack untouchedHand = new ItemStack(incoming);
                replaceHandKeepingStack(player, untouchedHand);
                useItem(h, player, extension);
                h.assertTrue(untouchedHand.getCount() == 1 && be(h, main).offering().isEmpty(),
                        "An occupied pedestal swapped or consumed the incoming hand: " + incoming);
                h.assertTrue(componentCount(player, expected) == 2, "The old offering was not returned exactly once");
                assertVisual(h, main, Direction.NORTH, GingerIslandStateDecorBlock.Gem.EMPTY);
            }
        } finally {
            cleanup(h, main, Direction.NORTH);
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void fullInventoryKeepsTheOfferingAndItsVisualStateUntilThereIsRoom(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL, "Pedestal full bag");
        BlockPos main = place(h, player, bottom(h), Direction.NORTH, new ItemStack(block()));
        BlockPos extension = extension(h, main, Direction.NORTH);
        AABB area = new AABB(main).inflate(3);
        try {
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                player.setGameMode(mode);
                player.getInventory().clearContent();
                ItemStack hand = namedGem(ModItems.EMERALD.get(), 2);
                ItemStack expected = hand.copyWithCount(1);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                useItem(h, player, main);
                player.getInventory().clearContent();
                for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                    ItemStack filler = new ItemStack(Items.STONE);
                    filler.setCount(filler.getMaxStackSize());
                    player.getInventory().setItem(slot, filler);
                }
                int fillerBefore = count(player, Items.STONE);
                useItem(h, player, extension);
                assertOffering(h, main, expected);
                assertVisual(h, main, Direction.NORTH, GingerIslandStateDecorBlock.Gem.EMERALD);
                h.assertTrue(count(player, Items.STONE) == fillerBefore
                                && h.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Full-bag retrieval consumed the hand or dropped the gem on the ground: " + mode);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                useItem(h, player, main);
                h.assertTrue(be(h, main).offering().isEmpty() && componentCount(player, expected) == 1,
                        "Freeing one slot did not allow the retained offering to be retrieved: " + mode);
                assertVisual(h, main, Direction.NORTH, GingerIslandStateDecorBlock.Gem.EMPTY);
            }
        } finally {
            cleanup(h, main, Direction.NORTH);
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void savedOfferingRetainsComponentsAndReturnedCopiesCannotModifyStorage(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL, "Pedestal reload");
        BlockPos main = place(h, player, bottom(h), Direction.NORTH, new ItemStack(block()));
        try {
            ItemStack hand = namedGem(ModItems.AQUAMARINE.get(), 4);
            ItemStack expected = hand.copyWithCount(1);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            useItem(h, player, main);
            var original = be(h, main);
            ItemStack externalCopy = original.offering();
            PortItemData.set(externalCopy, DataComponents.CUSTOM_NAME, Component.literal("Not the stored name"));
            externalCopy.setCount(0);
            assertOffering(h, main, expected);
            CompoundTag saved = original.saveWithFullMetadata();
            BlockState filledState = h.getLevel().getBlockState(main);
            original.clearOffering();
            h.getLevel().setBlock(main, filledState, 2);
            h.getLevel().removeBlockEntity(main);
            var reloaded = new GingerIslandGemPedestalBlockEntity(main, filledState);
            reloaded.load(saved);
            h.getLevel().setBlockEntity(reloaded);
            reloaded.onLoad();
            assertOffering(h, main, expected);
            assertVisual(h, main, Direction.NORTH, GingerIslandStateDecorBlock.Gem.AQUAMARINE);
            var packetCopy = new GingerIslandGemPedestalBlockEntity(main, filledState);
            packetCopy.load(reloaded.getUpdateTag());
            h.assertTrue(packetCopy.offering().getCount() == 1
                            && ItemStack.isSameItemSameTags(expected, packetCopy.offering()),
                    "The block-entity update payload lost offering metadata");
            replaceHandKeepingStack(player, ItemStack.EMPTY);
            PortBlockInteraction.stateUseWithoutItem(h.getLevel().getBlockState(extension(h, main, Direction.NORTH)), h.getLevel(), player,
                    hit(extension(h, main, Direction.NORTH)));
            h.assertTrue(reloaded.offering().isEmpty() && componentCount(player, expected) == 4,
                    "Retrieval after reload lost the name/custom data or duplicated the gem");
        } finally {
            cleanup(h, main, Direction.NORTH);
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void twoPlayersSeriallyPutAndRetrieveOnlyOneSharedOffering(GameTestHelper h) {
        var first = player(h, GameType.SURVIVAL, "Pedestal first");
        var second = player(h, GameType.SURVIVAL, "Pedestal second");
        BlockPos main = place(h, first, bottom(h), Direction.NORTH, new ItemStack(block()));
        BlockPos extension = extension(h, main, Direction.NORTH);
        try {
            ItemStack rubies = new ItemStack(ModItems.RUBY.get(), 3);
            first.setItemInHand(InteractionHand.MAIN_HAND, rubies);
            useItem(h, first, main);
            ItemStack topazes = new ItemStack(ModItems.TOPAZ.get(), 3);
            second.setItemInHand(InteractionHand.MAIN_HAND, topazes);
            useItem(h, second, extension);
            h.assertTrue(rubies.getCount() == 2 && topazes.getCount() == 3 && be(h, main).offering().isEmpty(),
                    "The second player swapped the first player's offering");
            PortBlockInteraction.stateUseWithoutItem(h.getLevel().getBlockState(main), h.getLevel(), first, hit(main));
            h.assertTrue(count(first, ModItems.RUBY.get()) + count(second, ModItems.RUBY.get()) == 3,
                    "A second retrieval duplicated the already-taken offering");
            useItem(h, second, main);
            h.assertTrue(topazes.getCount() == 2 && be(h, main).offering().is(ModItems.TOPAZ.get()),
                    "The next sequential player could not put a new offering");
            replaceHandKeepingStack(first, ItemStack.EMPTY);
            PortBlockInteraction.stateUseWithoutItem(h.getLevel().getBlockState(extension), h.getLevel(), first, hit(extension));
            PortBlockInteraction.stateUseWithoutItem(h.getLevel().getBlockState(main), h.getLevel(), second, hit(main));
            h.assertTrue(be(h, main).offering().isEmpty()
                            && count(first, ModItems.TOPAZ.get()) + count(second, ModItems.TOPAZ.get()) == 3
                            && count(first, ModItems.RUBY.get()) + count(second, ModItems.RUBY.get()) == 3,
                    "Sequential multiplayer retrieval lost or duplicated the replacement offering");
        } finally {
            cleanup(h, main, Direction.NORTH);
            first.getInventory().clearContent();
            second.getInventory().clearContent();
            first.discard();
            second.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "empty", timeoutTicks = 100)
    public static void forgedVisualStacksPlaceEmptyAndMainOrExtensionRemovalDropsContentsOnlyOnce(GameTestHelper h) {
        var player = player(h, GameType.SURVIVAL, "Pedestal break");
        BlockPos bottom = bottom(h);
        try {
            for (var gem : gems()) {
                ItemStack forged = new ItemStack(block(), 2);
                PortItemData.set(forged, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("gem", gem.state().toString())));
                BlockPos main = place(h, player, bottom, Direction.NORTH, forged);
                h.assertTrue(forged.getCount() == 1 && be(h, main).offering().isEmpty(),
                        "A forged visual stack generated an offering");
                assertVisual(h, main, Direction.NORTH, GingerIslandStateDecorBlock.Gem.EMPTY);
                cleanup(h, main, Direction.NORTH);
                player.getInventory().clearContent();
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean breakExtension : new boolean[]{false, true}) {
                    for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                        player.setGameMode(mode);
                        BlockPos main = place(h, player, bottom, facing, new ItemStack(block()));
                        BlockPos extension = extension(h, main, facing);
                        ItemStack offering = namedGem(ModItems.RUBY.get(), 2);
                        ItemStack expected = offering.copyWithCount(1);
                        player.setItemInHand(InteractionHand.MAIN_HAND, offering);
                        useItem(h, player, main);
                        assertEmptyBlockStack(h, block().getCloneItemStack(h.getLevel(), main,
                                h.getLevel().getBlockState(main)));
                        assertEmptyBlockStack(h, block().getCloneItemStack(h.getLevel(), extension,
                                h.getLevel().getBlockState(extension)));
                        var previewDrops = Block.getDrops(h.getLevel().getBlockState(main), h.getLevel(), main, be(h, main));
                        h.assertTrue(previewDrops.size() == 1, "MAIN loot did not contain exactly one empty pedestal");
                        assertEmptyBlockStack(h, com.stardew.craft.port.PortJava.getFirst(previewDrops));
                        h.assertTrue(Block.getDrops(h.getLevel().getBlockState(extension), h.getLevel(), extension,
                                h.getLevel().getBlockEntity(extension)).isEmpty(), "EXTENSION has its own duplicate block loot");
                        var storage = be(h, main);
                        Set<BlockPos> cells = block().placementPositions(main, facing);
                        AABB area = new AABB(main).inflate(3);
                        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) entity.discard();
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                        h.assertTrue(player.gameMode.destroyBlock(breakExtension ? extension : main),
                                "Player could not remove the requested pedestal part");
                        for (BlockPos cell : cells) {
                            h.assertTrue(h.getLevel().isEmptyBlock(cell) && h.getLevel().getBlockEntity(cell) == null,
                                    "Breaking a pedestal left a part or offering inventory");
                        }
                        int blocks = 0;
                        int returnedOfferings = 0;
                        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
                            ItemStack drop = entity.getItem();
                            if (drop.is(block().asItem())) {
                                assertEmptyBlockStack(h, drop);
                                blocks += drop.getCount();
                            } else {
                                h.assertTrue(ItemStack.isSameItemSameTags(expected, drop),
                                        "Removal replaced the real offering with a newly constructed gem");
                                returnedOfferings += drop.getCount();
                            }
                        }
                        h.assertTrue(blocks == (mode == GameType.CREATIVE ? 0 : 1) && returnedOfferings == 1,
                                "MAIN/EXT removal lost or duplicated block/contents: " + facing + "/" + breakExtension + "/" + mode);
                        int dropCount = h.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
                        h.assertTrue(storage.clearOffering().isEmpty(), "Removed storage still held its already-dropped offering");
                        h.getLevel().removeBlock(main, false);
                        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, area).size() == dropCount,
                                "Repeated cleanup settled the same offering twice");
                        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) entity.discard();
                        player.getInventory().clearContent();
                    }
                }
            }
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    private record GemCase(Item item, GingerIslandStateDecorBlock.Gem state) {}

    private static List<GemCase> gems() {
        return List.of(new GemCase(ModItems.AMETHYST.get(), GingerIslandStateDecorBlock.Gem.AMETHYST),
                new GemCase(ModItems.AQUAMARINE.get(), GingerIslandStateDecorBlock.Gem.AQUAMARINE),
                new GemCase(ModItems.EMERALD.get(), GingerIslandStateDecorBlock.Gem.EMERALD),
                new GemCase(ModItems.RUBY.get(), GingerIslandStateDecorBlock.Gem.RUBY),
                new GemCase(ModItems.TOPAZ.get(), GingerIslandStateDecorBlock.Gem.TOPAZ));
    }

    private static GingerIslandGemPedestalBlock block() {
        return (GingerIslandGemPedestalBlock) GingerIslandBlocks.get("ginger_gem_pedestal");
    }

    private static BlockPos bottom(GameTestHelper h) { return h.absolutePos(new BlockPos(8, 2, 8)); }

    private static FakePlayer player(GameTestHelper h, GameType mode, String name) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        player.setGameMode(mode);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 2, 4))));
        return player;
    }

    private static BlockPos place(GameTestHelper h, FakePlayer player, BlockPos bottom,
                                  Direction facing, ItemStack hand) {
        h.getLevel().setBlock(bottom.below(), Blocks.STONE.defaultBlockState(), 18);
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        BlockPos main = bottom.above(block().placementAnchorYOffset());
        for (BlockPos cell : block().placementPositions(main, facing)) {
            h.assertTrue(com.stardew.craft.port.PortGameTests.getBounds(h).contains(Vec3.atCenterOf(cell)), "Pedestal fixture is too small for a part");
        }
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(bottom), Direction.UP, bottom.below(), false));
        h.assertTrue(hand.useOn(context).consumesAction(), "Registered empty/forged pedestal item could not be hand placed");
        h.assertTrue(h.getLevel().getBlockState(main).is(block()), "Actual hand placement changed the anchor");
        be(h, main);
        return main;
    }

    private static BlockPos extension(GameTestHelper h, BlockPos main, Direction facing) {
        return block().placementPositions(main, facing).stream().filter(cell -> !cell.equals(main))
                .filter(cell -> h.getLevel().getBlockState(cell).is(block())
                        && h.getLevel().getBlockState(cell).getValue(MapDecorStaticBlock.PART)
                        == MapDecorStaticBlock.Part.EXTENSION).findFirst()
                .orElseThrow(() -> new AssertionError("The pedestal test no longer exercises a real extension"));
    }

    private static GingerIslandGemPedestalBlockEntity be(GameTestHelper h, BlockPos main) {
        h.assertTrue(h.getLevel().getBlockEntity(main) instanceof GingerIslandGemPedestalBlockEntity,
                "MAIN has no offering block entity");
        return (GingerIslandGemPedestalBlockEntity) h.getLevel().getBlockEntity(main);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
    }

    private static void useItem(GameTestHelper h, FakePlayer player, BlockPos target) {
        ItemInteractionResult result = PortBlockInteraction.stateUseItemOn(h.getLevel().getBlockState(target), player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit(target));
        h.assertTrue(result.consumesAction(), "A handled pedestal click did not consume the block interaction");
    }

    private static ItemStack namedGem(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        PortItemData.set(stack, DataComponents.CUSTOM_NAME, Component.literal("南方供物"));
        CompoundTag marker = new CompoundTag();
        marker.putString("pedestal_owner", "first-player");
        marker.putInt("kept_value", 73);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }

    private static void assertOffering(GameTestHelper h, BlockPos main, ItemStack expected) {
        ItemStack stored = be(h, main).offering();
        h.assertTrue(stored.getCount() == 1 && ItemStack.isSameItemSameTags(expected, stored),
                "Offering storage lost its count or original item components");
    }

    private static void assertVisual(GameTestHelper h, BlockPos main, Direction facing,
                                     GingerIslandStateDecorBlock.Gem gem) {
        for (BlockPos cell : block().placementPositions(main, facing)) {
            h.assertTrue(h.getLevel().getBlockState(cell).is(block())
                            && h.getLevel().getBlockState(cell).getValue(GingerIslandStateDecorBlock.GEM) == gem,
                    "MAIN and EXTENSION disagree with the actual stored offering");
        }
    }

    private static void assertEmptyBlockStack(GameTestHelper h, ItemStack stack) {
        var properties = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        var gem = properties.get(GingerIslandStateDecorBlock.GEM);
        h.assertTrue(stack.is(block().asItem()) && stack.getCount() == 1
                        && (gem == null || gem == GingerIslandStateDecorBlock.Gem.EMPTY)
                        && !PortItemData.has(stack, DataComponents.BLOCK_ENTITY_DATA),
                "Picking or block loot exposes a free filled pedestal item");
    }

    private static int count(FakePlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int componentCount(FakePlayer player, ItemStack expected) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(expected, stack)) count += stack.getCount();
        }
        return count;
    }

    private static void replaceHandKeepingStack(FakePlayer player, ItemStack replacement) {
        ItemStack current = player.getMainHandItem();
        if (!current.isEmpty()) {
            boolean stored = false;
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                if (slot == player.getInventory().selected || !player.getInventory().getItem(slot).isEmpty()) continue;
                player.getInventory().setItem(slot, current);
                stored = true;
                break;
            }
            if (!stored) throw new AssertionError("Fixture cannot change hands without discarding a held stack");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
    }

    private static void cleanup(GameTestHelper h, BlockPos main, Direction facing) {
        if (h.getLevel().getBlockEntity(main) instanceof GingerIslandGemPedestalBlockEntity storage) storage.clearOffering();
        MapDecorStaticBlock.runWithDropsSuppressed(() -> h.getLevel().removeBlock(main, false));
        for (BlockPos cell : block().placementPositions(main, facing)) {
            h.assertTrue(h.getLevel().isEmptyBlock(cell), "Cleanup left a pedestal part inside the next scenario");
        }
    }
}
