package com.stardew.craft.capability;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.AbstractTwoBlockUtilityBlock;
import com.stardew.craft.block.utility.AutoGrabberBlock;
import com.stardew.craft.block.utility.BaitMakerBlock;
import com.stardew.craft.block.utility.BeeHouseBlock;
import com.stardew.craft.block.utility.CharcoalKilnBlock;
import com.stardew.craft.block.utility.CheesePressBlock;
import com.stardew.craft.block.utility.CrystalariumBlock;
import com.stardew.craft.block.utility.DehydratorBlock;
import com.stardew.craft.block.utility.FishSmokerBlock;
import com.stardew.craft.block.utility.FurnaceBlock;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.block.utility.LightningRodBlock;
import com.stardew.craft.block.utility.MayonnaiseMachineBlock;
import com.stardew.craft.block.utility.SolarPanelBlock;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityAutomationAccess;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class UtilityAutomationCapabilities {
    private UtilityAutomationCapabilities() {
    }

    @SuppressWarnings("null")
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.KEG.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.PRESERVES_JAR.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.DEHYDRATOR.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.BAIT_MAKER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FISH_SMOKER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CRYSTALARIUM.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SEED_MAKER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FURNACE.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.HEAVY_FURNACE.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CHARCOAL_KILN.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CASK.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CHEESE_PRESS.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MAYONNAISE_MACHINE.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INCUBATOR.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.OIL_MAKER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.LOOM.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.BEE_HOUSE.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CRAB_POT.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.WORM_BIN.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FEED_TROUGH.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.AUTOFEED_TROUGH.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.AUTO_GRABBER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.DELUXE_WORM_BIN.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.LIGHTNING_ROD.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SOLAR_PANEL.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.TAPPER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MUSHROOM_BOX.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FISH_POND_BUCKET.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.GEODE_CRUSHER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.COFFEE_MAKER.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.BONE_MILL.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.DAILY_STATUE.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.GARDEN_POT.get(),
            (be, ctx) -> be.getAutomationItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.GARDEN_POT.get(),
            (be, ctx) -> be.getAutomationFluidHandler());

        event.registerBlock(Capabilities.ItemHandler.BLOCK, UtilityAutomationCapabilities::getAutomationFromMultiblock,
            ModBlocks.BEE_HOUSE.get(),
            ModBlocks.CHEESE_PRESS.get(),
            ModBlocks.DEHYDRATOR.get(),
            ModBlocks.BAIT_MAKER.get(),
            ModBlocks.FISH_SMOKER.get(),
            ModBlocks.FURNACE.get(),
            ModBlocks.HEAVY_FURNACE.get(),
            ModBlocks.CHARCOAL_KILN.get(),
            ModBlocks.MAYONNAISE_MACHINE.get(),
            ModBlocks.INCUBATOR.get(),
            ModBlocks.CRYSTALARIUM.get(),
            ModBlocks.AUTO_GRABBER.get(),
            ModBlocks.LIGHTNING_ROD.get(),
            ModBlocks.SOLAR_PANEL.get(),
            ModBlocks.GEODE_CRUSHER.get(),
            ModBlocks.COFFEE_MAKER.get(),
            ModBlocks.BONE_MILL.get());
    }

    @Nullable
    @SuppressWarnings("null")
    private static IItemHandler getAutomationFromMultiblock(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        BlockPos mainPos = resolveMainPos(pos, state);
        BlockEntity main = level.getBlockEntity(mainPos);
        if (main instanceof UtilityAutomationAccess access) {
            return access.getAutomationItemHandler();
        }
        return null;
    }

    private static BlockPos resolveMainPos(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AbstractTwoBlockUtilityBlock<?>) {
            return AbstractTwoBlockUtilityBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof BeeHouseBlock) {
            return BeeHouseBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof CheesePressBlock) {
            return CheesePressBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof DehydratorBlock) {
            return DehydratorBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof BaitMakerBlock) {
            return BaitMakerBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof FishSmokerBlock) {
            return FishSmokerBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof FurnaceBlock) {
            return FurnaceBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof CharcoalKilnBlock) {
            return CharcoalKilnBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof MayonnaiseMachineBlock) {
            return MayonnaiseMachineBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof IncubatorBlock) {
            return IncubatorBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof CrystalariumBlock) {
            return CrystalariumBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof AutoGrabberBlock) {
            return AutoGrabberBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof LightningRodBlock) {
            return LightningRodBlock.getMainPos(pos, state);
        }
        if (state.getBlock() instanceof SolarPanelBlock) {
            return SolarPanelBlock.getMainPos(pos, state);
        }
        return pos;
    }
}
