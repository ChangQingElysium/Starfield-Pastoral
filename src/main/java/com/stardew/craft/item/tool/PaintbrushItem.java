package com.stardew.craft.item.tool;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.BedDecorBlock;
import com.stardew.craft.block.utility.OakTableBlock;
import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.deco.DecorationService;
import com.stardew.craft.deco.DecorationType;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.network.payload.OpenSofaColorScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class PaintbrushItem extends Item implements IStardewItem {
    public PaintbrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.tool";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    @Override
    @SuppressWarnings("null")
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);

        DecorationType type = null;
        if (clicked.getBlock() instanceof WallpaperBlock || clicked.is(ModBlocks.WALLPAPER_BLOCK.get())) {
            type = DecorationType.WALLPAPER;
        } else if (clicked.getBlock() == ModBlocks.FLOORING_BLOCK.get()) {
            type = DecorationType.FLOORING;
        }

        if (type == null) {
            if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                && clicked.getBlock() instanceof OakTableBlock
                && clicked.hasProperty(OakTableBlock.HAS_CLOTH)
                && clicked.hasProperty(OakTableBlock.CLOTH_STYLE)
                && clicked.getValue(OakTableBlock.HAS_CLOTH)
                && OakTableBlock.isDyeableClothStyle(clicked.getValue(OakTableBlock.CLOTH_STYLE))) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe) {
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(pos, tableBe.getClothColor()));
                    return InteractionResult.CONSUME;
                }
            }
            if (clicked.getBlock() == ModBlocks.SOFA.get()) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                    int current = clicked.getValue(com.stardew.craft.block.utility.SofaBlock.COLOR);
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(pos, current));
                    return InteractionResult.CONSUME;
                }
            }
            if (clicked.getBlock() == ModBlocks.CUSHION.get()) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer
                    && clicked.hasProperty(com.stardew.craft.block.utility.CushionBlock.COLOR)) {
                    int current = clicked.getValue(com.stardew.craft.block.utility.CushionBlock.COLOR);
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(pos, current));
                    return InteractionResult.CONSUME;
                }
            }
            if (clicked.getBlock() == ModBlocks.OFFICE_STOOL.get()) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer
                    && clicked.hasProperty(com.stardew.craft.block.utility.OfficeStoolBlock.COLOR)) {
                    int current = clicked.getValue(com.stardew.craft.block.utility.OfficeStoolBlock.COLOR);
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(pos, current));
                    return InteractionResult.CONSUME;
                }
            }
            if (clicked.getBlock() == ModBlocks.OFFICE_CHAIR_2.get()) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer
                    && clicked.hasProperty(com.stardew.craft.block.utility.OfficeChair2Block.COLOR)) {
                    int current = clicked.getValue(com.stardew.craft.block.utility.OfficeChair2Block.COLOR);
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(pos, current));
                    return InteractionResult.CONSUME;
                }
            }
            if (clicked.getBlock() instanceof com.stardew.craft.block.utility.DyeableChairBlock) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer
                    && clicked.hasProperty(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)) {
                    com.stardew.craft.block.utility.DyeableChairBlock chairBlock =
                        (com.stardew.craft.block.utility.DyeableChairBlock) clicked.getBlock();
                    BlockPos targetPos = chairBlock.resolveMainPos(level, pos, clicked);
                    BlockState targetState = level.getBlockState(targetPos);
                    int current = targetState.hasProperty(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)
                        ? targetState.getValue(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)
                        : clicked.getValue(com.stardew.craft.block.utility.DyeableChairBlock.COLOR);
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenSofaColorScreenPayload(targetPos, current));
                    return InteractionResult.CONSUME;
                }
            }
            if (context.getPlayer() != null
                && context.getPlayer().isShiftKeyDown()
                && clicked.getBlock() instanceof BedDecorBlock bedBlock) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                    BlockPos targetPos = bedBlock.resolveMainPos(level, pos, clicked);
                    int current = bedBlock.currentColor(level, targetPos, level.getBlockState(targetPos));
                    PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new OpenSofaColorScreenPayload(targetPos, current)
                    );
                    return InteractionResult.CONSUME;
                }
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            DecorationService.openSelection(serverPlayer, pos, type);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }
}
