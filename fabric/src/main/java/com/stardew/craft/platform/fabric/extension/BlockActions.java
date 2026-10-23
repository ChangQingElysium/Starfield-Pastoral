package com.stardew.craft.platform.fabric.extension;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/** Native block action rules and the original cancellable, mutable tool-use event. */
public final class BlockActions {
    @Nullable
    public static BlockState getModifiedState(BlockState state, UseOnContext context,
            ItemAbility ability, boolean simulate) {
        return getModifiedState(FabricEvents.GAME, state, context, ability, simulate);
    }

    @Nullable
    static BlockState getModifiedState(IEventBus bus, BlockState state, UseOnContext context,
            ItemAbility ability, boolean simulate) {
        BlockEvent.BlockToolModificationEvent event = bus.post(
                new BlockEvent.BlockToolModificationEvent(state, context, ability, simulate));
        if (event.isCanceled()) return null;
        if (event.getFinalState() != state) return event.getFinalState();
        return state.getBlock() instanceof ToolModificationProvider provider
                ? provider.getToolModifiedState(state, context, ability, simulate)
                : defaultModifiedState(state, context, ability, simulate);
    }

    /** Super/block-level dispatch does not post a second event. */
    @Nullable
    public static BlockState defaultModifiedState(BlockState state, UseOnContext context,
            ItemAbility ability, boolean simulate) {
        if (!ItemActions.canPerformAction(context.getItemInHand(), ability)) return null;
        if (ability == ItemAbilities.AXE_STRIP) {
            Block stripped = AxeItem.STRIPPABLES.get(state.getBlock());
            return stripped == null ? null : stripped.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
        }
        if (ability == ItemAbilities.AXE_SCRAPE) return WeatheringCopper.getPrevious(state).orElse(null);
        if (ability == ItemAbilities.AXE_WAX_OFF) {
            Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
            return unwaxed == null ? null : unwaxed.withPropertiesOf(state);
        }
        if (ability == ItemAbilities.SHOVEL_FLATTEN) return ShovelItem.FLATTENABLES.get(state.getBlock());
        if (ability == ItemAbilities.HOE_TILL) {
            Block block = state.getBlock();
            if (block == Blocks.ROOTED_DIRT) {
                if (!simulate && !context.getLevel().isClientSide) {
                    Block.popResourceFromFace(context.getLevel(), context.getClickedPos(), context.getClickedFace(),
                            new ItemStack(Items.HANGING_ROOTS));
                }
                return Blocks.DIRT.defaultBlockState();
            }
            if ((block == Blocks.GRASS_BLOCK || block == Blocks.DIRT_PATH || block == Blocks.DIRT
                    || block == Blocks.COARSE_DIRT)
                    && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
                return (block == Blocks.COARSE_DIRT ? Blocks.DIRT : Blocks.FARMLAND).defaultBlockState();
            }
        } else if (ability == ItemAbilities.SHEARS_TRIM) {
            if (state.getBlock() instanceof GrowingPlantHeadBlock plant && !plant.isMaxAge(state)) {
                if (!simulate) context.getLevel().playSound(context.getPlayer(), context.getClickedPos(),
                        SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
                return plant.getMaxAgeState(state);
            }
        } else if (ability == ItemAbilities.SHOVEL_DOUSE) {
            if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                if (!simulate) CampfireBlock.dowse(context.getPlayer(), context.getLevel(), context.getClickedPos(), state);
                return state.setValue(CampfireBlock.LIT, false);
            }
        } else if (ability == ItemAbilities.FIRESTARTER_LIGHT
                && (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state))) {
            return state.setValue(BlockStateProperties.LIT, true);
        }
        return null;
    }

    /** Called at the native axe evaluation point, retaining its shield/advancement/durability logic. */
    public static Optional<BlockState> evaluateAxe(UseOnContext context, BlockState state) {
        var level = context.getLevel();
        var position = context.getClickedPos();
        var player = context.getPlayer();
        BlockState stripped = getModifiedState(state, context, ItemAbilities.AXE_STRIP, false);
        if (stripped != null) {
            level.playSound(player, position, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1, 1);
            return Optional.of(stripped);
        }
        BlockState scraped = getModifiedState(state, context, ItemAbilities.AXE_SCRAPE, false);
        if (scraped != null) {
            level.playSound(player, position, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1, 1);
            level.levelEvent(player, 3005, position, 0);
            return Optional.of(scraped);
        }
        BlockState unwaxed = getModifiedState(state, context, ItemAbilities.AXE_WAX_OFF, false);
        if (unwaxed != null) {
            level.playSound(player, position, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1, 1);
            level.levelEvent(player, 3004, position, 0);
            return Optional.of(unwaxed);
        }
        return Optional.empty();
    }

    private BlockActions() {}
}
