package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.WoodSignBlock;
import com.stardew.craft.blockentity.WoodSignBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.player.StardewCraftingRecipeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_signs")
@PrefixGameTestTemplate(false)
public final class WoodSignGameTests {
    private static FakePlayer player(GameTestHelper h) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SignTest"));
    }

    private static BlockPos stand(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var state = ModBlocks.WOOD_SIGN.get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos, state);
        h.assertTrue(((WoodSignBlock) state.getBlock()).placeExtensions(h.getLevel(), pos, state), "Upper cell was not reserved");
        return pos;
    }

    @GameTest(templateNamespace = "stardewcraft_signs", template = "empty")
    public static void recipeIsDefaultAndCostsExactly25Wood(GameTestHelper h) {
        var recipe = StardewCraftingRecipeData.getRecipe("wood_sign").orElseThrow();
        h.assertTrue(recipe.output().item().equals("stardewcraft:wood_sign") && recipe.output().count() == 1,
                "Incorrect output");
        h.assertTrue(recipe.ingredients().size() == 1 && recipe.ingredients().getFirst().count() == 25
                && recipe.ingredients().getFirst().item().equals("stardewcraft:wood_normal"), "Incorrect material cost");
        h.assertTrue(StardewCraftingRecipeData.isBigCraftable("wood_sign"), "Missing big craftable metadata");
        h.assertTrue("default".equals(recipe.unlockCondition()) && recipe.unlockWhen().isEmpty(), "Recipe needs a purchase or skill level");
        h.assertTrue(StardewItemCatalog.typeKey(ModItems.WOOD_SIGN.get()).equals("stardewcraft.type.utility"), "Incorrect item category");
        h.assertTrue(ModBlocks.WOOD_WALL_SIGN.get().asItem() == ModItems.WOOD_SIGN.get(), "Wall form has no matching item");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_signs", template = "empty")
    public static void upperFaceCopiesItemsAndReloadPreservesComponents(GameTestHelper h) {
        BlockPos pos = stand(h);
        var player = player(h);
        var item = new ItemStack(Items.DIAMOND, 17);
        PortItemData.set(item, DataComponents.CUSTOM_NAME, Component.literal("Display specimen"));
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var result = PortBlockInteraction.stateUseItemOn(h.getLevel().getBlockState(pos.above()), item, h.getLevel(), player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos.above()), Direction.NORTH, pos.above(), false));
        h.assertTrue(result.consumesAction() && item.getCount() == 17, "Display interaction failed or consumed items");
        var sign = (WoodSignBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(sign.getDisplayItem().getCount() == 1, "Display copy was not normalized");
        PortItemData.set(item, DataComponents.CUSTOM_NAME, Component.literal("Changed in hand"));
        h.assertTrue(sign.getDisplayItem().getHoverName().getString().equals("Display specimen"), "Display aliases the held stack");
        var reloaded = new WoodSignBlockEntity(pos, sign.getBlockState());
        reloaded.load(sign.saveWithFullMetadata());
        h.assertTrue(ItemStack.matches(sign.getDisplayItem(), reloaded.getDisplayItem()), "NBT reload lost display components");
        var clientCopy = new WoodSignBlockEntity(pos, sign.getBlockState());
        clientCopy.load(sign.getUpdateTag());
        h.assertTrue(ItemStack.matches(sign.getDisplayItem(), clientCopy.getDisplayItem()), "Chunk/update sync lost display components");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_signs", template = "empty")
    public static void replacementAndBreakingNeverDropDisplayCopies(GameTestHelper h) {
        BlockPos pos = stand(h);
        var sign = (WoodSignBlockEntity) h.getLevel().getBlockEntity(pos);
        sign.setDisplayItem(new ItemStack(Items.DIAMOND, 64));
        sign.setDisplayItem(new ItemStack(Items.NETHERITE_INGOT, 64));
        h.getLevel().destroyBlock(pos, true);
        h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "Breaking the base left an upper part");
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        h.assertTrue(items.stream().allMatch(e -> e.getItem().is(ModItems.WOOD_SIGN.get())), "Display copies leaked as drops");
        h.assertTrue(items.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1, "Breaking a sign duplicated or lost the sign");
        items.forEach(ItemEntity::discard);
        stand(h);
        h.getLevel().destroyBlock(pos.above(), true);
        h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Breaking the upper part left the base");
        items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        h.assertTrue(items.size() == 1 && items.getFirst().getItem().is(ModItems.WOOD_SIGN.get())
                && items.getFirst().getItem().getCount() == 1, "Upper-part destruction duplicated or lost the sign");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_signs", template = "empty")
    public static void wallPlacementFacesOutwardAndDropsTheSameSign(GameTestHelper h) {
        var player = player(h);
        BlockPos support = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.Plane.HORIZONTAL) {
            var stack = new ItemStack(ModItems.WOOD_SIGN.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setPos(Vec3.atCenterOf(support.relative(face, 3)));
            var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, support, false);
            h.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Wall placement failed: " + face);
            BlockPos pos = support.relative(face);
            var state = h.getLevel().getBlockState(pos);
            h.assertTrue(state.is(ModBlocks.WOOD_WALL_SIGN.get()) && state.getValue(WoodSignBlock.FACING) == face,
                    "Wall face points inward: " + face);
            h.assertTrue(stack.getCount() == 1 && h.getLevel().getBlockEntity(pos) instanceof WoodSignBlockEntity, "Wall placement did not consume exactly one sign/create its entity");
            h.getLevel().destroyBlock(pos, true);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.WOOD_SIGN.get()), "Wall form dropped the wrong item");
            drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_signs", template = "empty")
    public static void standingPlacementRejectsOccupiedHeadroom(GameTestHelper h) {
        var player = player(h);
        BlockPos support = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(support.above(2), Blocks.STONE.defaultBlockState());
        var stack = new ItemStack(ModItems.WOOD_SIGN.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        h.assertTrue(ModBlocks.WOOD_SIGN.get().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)) == null,
                "Standing sign overlaps a block above it");
        h.assertTrue(stack.getCount() == 2 && h.getLevel().getBlockState(support.above()).isAir(), "Failed preview modified the world/inventory");
        h.succeed();
    }
}
