package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.gingerisland.GingerIslandSurfaceBlock;
import com.stardew.craft.gingerisland.GingerIslandVariantStacks;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_ginger_variants")
@PrefixGameTestTemplate(false)
public final class GingerIslandVariantGameTests {
    private GingerIslandVariantGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void catalogExposesOnlyAuthoredStatesAndStableItemOverrides(GameTestHelper h) {
        var extras = StardewItemCatalog.jeiExtraIngredientStacks();
        int assets = 0;
        int worldStates = 0;
        int functional = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!asset.kind().equals("state_decor")) continue;
            var block = (GingerIslandStateDecorBlock) GingerIslandBlocks.get(asset.id());
            assets++;
            worldStates += asset.state_models().size();
            h.assertTrue(new HashSet<>(block.visualStateValues()).equals(asset.state_models().keySet()),
                    "Hiding a hand choice removed an authored world state: " + asset.id());
            h.assertTrue(GingerIslandVariantStacks.supports(block.asItem()),
                    "Functional decor bypasses the shared catalog: " + asset.id());
            var choices = StardewItemDisplayStacks.stacksForItem(block.asItem());
            h.assertTrue(choices.size() == block.buildingStateValues().size(), "Catalog misses building choices: " + asset.id());
            if (!block.hasBuildingVariants()) {
                functional++;
                h.assertTrue(block.buildingStateValues().equals(List.of(com.stardew.craft.port.PortJava.getFirst(block.visualStateValues()))),
                        "A functional process state became a building choice: " + asset.id());
                ItemStack plain = new ItemStack(block);
                h.assertTrue(choices.size() == 1 && ItemStack.isSameItemSameTags(plain, com.stardew.craft.port.PortJava.getFirst(choices))
                                && !PortItemData.has(com.stardew.craft.port.PortJava.getFirst(choices), DataComponents.BLOCK_STATE)
                                && !PortItemData.has(com.stardew.craft.port.PortJava.getFirst(choices), DataComponents.ITEM_NAME),
                        "Functional catalog entry carries process state or a variant name: " + asset.id());
                h.assertTrue(extras.stream().noneMatch(other -> other.is(block.asItem())),
                        "JEI exposes functional process choices: " + asset.id());
                h.assertTrue(GingerIslandVariantStacks.modelValue(com.stardew.craft.port.PortJava.getFirst(choices)) == 0,
                        "Plain functional item selects an in-progress model: " + asset.id());
                continue;
            }
            h.assertTrue(block.buildingStateValues().equals(block.visualStateValues()),
                    "A real building choice was removed: " + asset.id());
            var values = new HashSet<String>();
            for (int index = 0; index < choices.size(); index++) {
                ItemStack stack = choices.get(index);
                var properties = PortItemData.get(stack, DataComponents.BLOCK_STATE);
                h.assertTrue(properties != null && properties.properties().size() == 1,
                        "Catalog exposes part/facing or an unbound choice: " + asset.id());
                String value = properties.properties().get(asset.state_property());
                h.assertTrue(values.add(value) && asset.state_models().containsKey(value),
                        "Duplicate or phantom state: " + asset.id() + " / " + value);
                h.assertTrue(block.modelForState(properties.apply(block.defaultBlockState()))
                        .equals(asset.state_models().get(value)), "State selects the wrong authored model");
                h.assertTrue(stack.is(block.asItem()) && stack.getCount() == 1,
                        "A state became another registered item");
                h.assertTrue(extras.stream().anyMatch(other -> other.is(stack.getItem())
                                && properties.equals(PortItemData.get(other, DataComponents.BLOCK_STATE))),
                        "JEI does not offer this real state stack: " + asset.id());
                float expected = choices.size() < 2 ? 0 : index / (float) (choices.size() - 1);
                h.assertTrue(Math.abs(GingerIslandVariantStacks.modelValue(stack) - expected) < 0.000001F,
                        "Item override order differs from the authored stack order");
            }
            h.assertTrue(GingerIslandVariantStacks.subtypeProperties(new ItemStack(block))
                            .equals(GingerIslandVariantStacks.subtypeProperties(com.stardew.craft.port.PortJava.getFirst(choices))),
                    "JEI duplicates the unbound default and explicit default: " + asset.id());
        }
        h.assertTrue(assets == 26 && worldStates == 69,
                "World-state coverage changed: " + assets + " assets / " + worldStates + " states");
        h.assertTrue(functional == 10, "Catalog audit changed the functional/building owner boundary: " + functional);
        var palm = (GingerIslandStateDecorBlock) GingerIslandBlocks.get("ginger_south_palm");
        h.assertTrue(palm.visualStateValues().equals(List.of("0", "1", "2", "3")),
                "Palm catalog displays unused variant 4 or 5");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_placement", template = "large_empty", timeoutTicks = 200)
    public static void forgedFunctionalStacksPlaceDefaultAndWorldProgressPicksAndDropsPlain(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Functional state stacks"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(25)));
        player.setYRot(180);
        int checked = 0;
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            level.setBlock(h.absolutePos(relative), relative.getY() == 3
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
        try {
            for (var asset : GingerIslandAssets.blocks()) {
                if (!(GingerIslandBlocks.get(asset.id()) instanceof GingerIslandStateDecorBlock block)
                        || block.hasBuildingVariants()) continue;
                ItemStack plain = new ItemStack(block);
                String defaultValue = com.stardew.craft.port.PortJava.getFirst(block.buildingStateValues());
                BlockPos main = bottom.above(block.placementAnchorYOffset());
                for (String value : block.visualStateValues()) {
                    if (value.equals(defaultValue)) continue;
                    ItemStack forged = new ItemStack(block, 2);
                    PortItemData.set(forged, DataComponents.BLOCK_STATE,
                            new BlockItemStateProperties(Map.of(asset.state_property(), value)));
                    player.setItemInHand(InteractionHand.MAIN_HAND, forged);
                    h.assertTrue(GingerIslandVariantStacks.modelValue(forged) == 0,
                            "Forged functional stack selects a progress icon: " + asset.id() + " / " + value);
                    h.assertTrue(GingerIslandVariantStacks.subtypeProperties(forged).equals(BlockItemStateProperties.EMPTY),
                            "Forged functional state becomes a JEI subtype: " + asset.id() + " / " + value);
                    h.assertTrue(forged.getItem().useOn(context(player, bottom)).consumesAction(),
                            "Functional item cannot be hand placed: " + asset.id());
                    h.assertTrue(forged.getCount() == 1, "Functional placement did not consume one item");
                    BlockState placed = level.getBlockState(main);
                    h.assertTrue(placed.is(block) && block.modelForState(placed).equals(asset.state_models().get(defaultValue)),
                            "Forged process component bypassed default placement: " + asset.id() + " / " + value);
                    var cells = block.placementPositions(main, placed.getValue(MapDecorStaticBlock.FACING));
                    BlockPos extension = null;
                    for (BlockPos cell : cells) {
                        h.assertTrue(com.stardew.craft.port.PortGameTests.getBounds(h).contains(Vec3.atCenterOf(cell)), "Functional fixture escaped its bounds");
                        var part = level.getBlockState(cell);
                        h.assertTrue(part.is(block) && block.modelForState(part).equals(asset.state_models().get(defaultValue)),
                                "Extension retained a forged process state: " + asset.id() + " / " + cell);
                        if (!cell.equals(main)) extension = cell;
                    }
                    assertPlainPickAndDrop(h, block, main, plain);
                    // Only the world/controller can advance the process. Keep EXT stale
                    // to exercise clone's owner lookup rather than its own state value.
                    BlockState advanced = new BlockItemStateProperties(Map.of(asset.state_property(), value)).apply(placed);
                    level.setBlock(main, advanced, 2 | 16);
                    h.assertTrue(block.modelForState(level.getBlockState(main)).equals(asset.state_models().get(value)),
                            "Functional world state was removed: " + asset.id() + " / " + value);
                    assertPlainPickAndDrop(h, block, main, plain);
                    if (extension != null) {
                        var part = level.getBlockState(extension);
                        h.assertTrue(ItemStack.isSameItemSameTags(plain, block.getCloneItemStack(level, extension, part)),
                                "Picking a functional EXT leaks its owner's progress: " + asset.id());
                        h.assertTrue(net.minecraft.world.level.block.Block.getDrops(part, level, extension,
                                level.getBlockEntity(extension)).isEmpty(), "Functional EXT duplicates its owner drop");
                    }
                    MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
                    for (BlockPos cell : cells) h.assertTrue(level.isEmptyBlock(cell), "Functional cleanup left an extension");
                    checked++;
                }
            }
        } finally {
            player.getInventory().clearContent();
        }
        h.assertTrue(checked == 16, "Forged functional state audit missed progress states: " + checked);
        h.succeed();
    }

    private static void assertPlainPickAndDrop(GameTestHelper h, GingerIslandStateDecorBlock block,
                                              BlockPos pos, ItemStack plain) {
        var level = h.getLevel();
        var state = level.getBlockState(pos);
        h.assertTrue(ItemStack.isSameItemSameTags(plain, block.getCloneItemStack(level, pos, state)),
                "Picking functional MAIN leaks a process state or variant name");
        var drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        h.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(plain, com.stardew.craft.port.PortJava.getFirst(drops)),
                "Dropping functional MAIN leaks a process state or variant name");
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void realHandPlacementAndExtensionPickingPreservePalmChoice(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Palm variants"));
        player.setPos(Vec3.atBottomCenterOf(pos.south(20)));
        player.setYRot(0);
        var block = (GingerIslandStateDecorBlock) GingerIslandBlocks.get("ginger_south_palm");
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        try {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, 9, 8))) {
                previous.put(p.immutable(), level.getBlockState(p));
                level.setBlock(p, p.getY() < pos.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            for (ItemStack choice : StardewItemDisplayStacks.stacksForItem(block.asItem())) {
                ItemStack hand = choice.copy();
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.getItem().useOn(context(player, pos)).consumesAction(), "Registered palm item cannot be hand placed");
                var state = level.getBlockState(pos);
                var wanted = PortItemData.get(choice, DataComponents.BLOCK_STATE).get(GingerIslandStateDecorBlock.VARIANT);
                h.assertTrue(state.is(block) && state.getValue(GingerIslandStateDecorBlock.VARIANT).equals(wanted),
                        "BlockItem validation or setPlacedBy erased the selected palm");
                h.assertTrue(ItemStack.isSameItemSameTags(choice, block.getCloneItemStack(level, pos, state)),
                        "Picking the main loses the selected palm stack");
                var drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, level.getBlockEntity(pos));
                h.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(choice, com.stardew.craft.port.PortJava.getFirst(drops)),
                        "Breaking the main loses its authored palm variant");
                BlockPos extension = null;
                for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 9, 8))) {
                    var part = level.getBlockState(p);
                    if (!part.is(block) || part.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.EXTENSION) continue;
                    h.assertTrue(part.getValue(GingerIslandStateDecorBlock.VARIANT).equals(wanted),
                            "An extension has another palm variant");
                    extension = p.immutable();
                }
                h.assertTrue(extension != null && ItemStack.isSameItemSameTags(choice,
                        block.getCloneItemStack(level, extension, level.getBlockState(extension))),
                        "Picking an extension loses the selected palm stack");
                h.assertTrue(net.minecraft.world.level.block.Block.getDrops(level.getBlockState(extension),
                        level, extension, level.getBlockEntity(extension)).isEmpty(), "Extension duplicates the drop");
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(pos, false));
            }
            ItemStack invalid = new ItemStack(block);
            PortItemData.set(invalid, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("variant", "5")));
            player.setItemInHand(InteractionHand.MAIN_HAND, invalid);
            h.assertTrue(invalid.getItem().useOn(context(player, pos)).consumesAction()
                            && level.getBlockState(pos).getValue(GingerIslandStateDecorBlock.VARIANT) == 0,
                    "Vanilla's second component application reintroduced an unauthored variant");
        } finally {
            MapDecorStaticBlock.runWithDropsSuppressed(() -> previous.forEach((p, state) -> level.setBlock(p, state, 18)));
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void fixedGroundStacksSurviveRealPlacementAndKeepNaturalEntry(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Ground variants"));
        player.setPos(Vec3.atBottomCenterOf(pos.south(5)));
        var previous = level.getBlockState(pos);
        var support = level.getBlockState(pos.below());
        try {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 18);
            for (String id : List.of("ginger_volcano_floor", "ginger_caldera_floor")) {
                var block = (GingerIslandSurfaceBlock) GingerIslandBlocks.get(id);
                var choices = StardewItemDisplayStacks.stacksForItem(block.asItem());
                h.assertTrue(choices.size() == block.variantCount() + 1 && !PortItemData.has(com.stardew.craft.port.PortJava.getFirst(choices), DataComponents.BLOCK_STATE),
                        "Natural random ground entry was replaced with a fixed texture");
                for (ItemStack choice : choices) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    var hand = choice.copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    h.assertTrue(hand.getItem().useOn(context(player, pos)).consumesAction(), "Ground item cannot be hand placed");
                    int placed = level.getBlockState(pos).getValue(GingerIslandSurfaceBlock.VARIANT);
                    h.assertTrue(placed >= 0 && placed < block.variantCount(), "Ground placement creates a phantom texture");
                    if (PortItemData.has(choice, DataComponents.BLOCK_STATE)) {
                        var wanted = PortItemData.get(choice, DataComponents.BLOCK_STATE).get(GingerIslandSurfaceBlock.VARIANT);
                        h.assertTrue(wanted != null && wanted == placed, "Fixed ground choice was randomized");
                    }
                    var picked = block.getCloneItemStack(level, pos, level.getBlockState(pos));
                    h.assertTrue(PortItemData.get(picked, DataComponents.BLOCK_STATE).get(GingerIslandSurfaceBlock.VARIANT) == placed,
                            "Picking ground loses its fixed texture");
                    var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, null);
                    h.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameTags(picked, com.stardew.craft.port.PortJava.getFirst(drops)),
                            "Breaking ground randomizes the selected surface or loses its name");
                }
                for (String value : List.of("99", "-1", "not-a-number")) {
                    assertInvalidGroundNormalizes(h, player, block, pos, value);
                }
                if (block.variantCount() < 6) assertInvalidGroundNormalizes(h, player, block, pos, "5");
            }
        } finally {
            level.setBlock(pos, previous, 18);
            level.setBlock(pos.below(), support, 18);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    private static void assertInvalidGroundNormalizes(GameTestHelper h,
            net.minecraft.server.level.ServerPlayer player, GingerIslandSurfaceBlock block, BlockPos pos, String value) {
        var level = h.getLevel();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        ItemStack forged = new ItemStack(block);
        PortItemData.set(forged, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("variant", value)));
        h.assertTrue(GingerIslandVariantStacks.modelValue(forged) == 0
                        && GingerIslandVariantStacks.subtypeProperties(forged).equals(
                        BlockItemStateProperties.EMPTY.with(GingerIslandSurfaceBlock.VARIANT, 0)),
                "Invalid ground choice exposes a phantom inventory model or JEI subtype: " + value);
        player.setItemInHand(InteractionHand.MAIN_HAND, forged);
        h.assertTrue(forged.getItem().useOn(context(player, pos)).consumesAction()
                        && level.getBlockState(pos).getValue(GingerIslandSurfaceBlock.VARIANT) == 0,
                "Ground component survived vanilla's second application: " + value);
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false));
    }
}
