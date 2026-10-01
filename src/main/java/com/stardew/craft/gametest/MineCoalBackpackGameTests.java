package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mine.MineCoalBackpackBlock;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_backpacks")
@PrefixGameTestTemplate(false)
public final class MineCoalBackpackGameTests {
    @GameTest(templateNamespace = "stardewcraft_backpacks", template = "ring_utilities", timeoutTicks = 100)
    public static void twoCellsShareOnePersistentCoalClaim(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.MINE_COAL_BACKPACK.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.getMinecraft(level);
        for (int theme = 0; theme < 3; theme++) for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            }
            var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing)
                    .setValue(MineCoalBackpackBlock.DARK, theme == 1)
                    .setValue(MineCoalBackpackBlock.DESERT, theme == 2);
            level.setBlock(pos, state, 3);
            helper.assertTrue(block.placeExtensions(level, pos, state), "Backpack did not reserve strap cell");
            var strap = pos.relative(facing.getClockWise());
            var second = level.getBlockState(strap);
            helper.assertTrue(pos.equals(block.findMainPos(level, strap, second)), "Strap lost anchor");
            var whole = state.getShape(level, pos).bounds();
            var fromStrap = second.getShape(level, strap).bounds().move(strap.getX()-pos.getX(), 0, strap.getZ()-pos.getZ());
            helper.assertTrue(whole.equals(fromStrap), "Parts do not share the full outline");
            PortBlockInteraction.stateUseWithoutItem(second, level, player, new BlockHitResult(Vec3.atCenterOf(strap), Direction.UP, strap, false));
            PortBlockInteraction.stateUseWithoutItem(state, level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            var opened = level.getBlockState(pos);
            helper.assertTrue(opened.getValue(MineCoalBackpackBlock.OPEN)
                    && level.getBlockState(strap).getValue(MineCoalBackpackBlock.OPEN), "Opening did not update both cells");
            var restored = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(opened));
            helper.assertTrue(restored.equals(opened), "NBT lost facing, theme or depleted state");
            level.setBlock(pos, restored, 3);
            PortBlockInteraction.stateUseWithoutItem(restored, level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            var drops = level.getEntitiesOfClass(ItemEntity.class, whole.move(pos).inflate(2));
            helper.assertTrue(drops.stream().allMatch(e -> e.getItem().is(ModItems.COAL.get())), "Non-mod coal dropped");
            helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 6, "Coal claim duplicated or default quantity changed");
            drops.forEach(ItemEntity::discard);
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(strap, false));
            helper.assertTrue(level.getBlockState(pos).isAir(), "Removing strap left orphan bag");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_backpacks", template = "ring_utilities", timeoutTicks = 100)
    public static void importedStrapMayArriveBeforeItsBag(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.MINE_COAL_BACKPACK.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, Direction.SOUTH)
                .setValue(MineCoalBackpackBlock.OPEN, true);
        level.setBlock(pos.west(), state.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION), 3);
        level.setBlock(pos, state, 3);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getBlockState(pos.west()).is(block), "Structure import removed strap prematurely");
            helper.assertTrue(state.getDestroySpeed(level, pos) < 0, "Mine backpack is breakable");
            helper.succeed();
        });
    }
}
