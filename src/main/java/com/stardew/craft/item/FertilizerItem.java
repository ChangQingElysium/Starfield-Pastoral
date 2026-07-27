package com.stardew.craft.item;

import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 肥料物品 - 可以在耕地上施加肥料
 */
public class FertilizerItem extends SimpleStardewItem {
    private final FertilizerType fertilizerType;

    public FertilizerItem(FertilizerType fertilizerType, int sellPrice, Properties properties) {
        super("stardewcraft.type.fertilizer", sellPrice, properties);
        this.fertilizerType = fertilizerType;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        @SuppressWarnings("null")
        BlockState clickedState = level.getBlockState(pos);

        com.stardew.craft.StardewCraft.LOGGER.info("FertilizerItem.useOn called - Side: {}, Pos: {}, Block: {}", 
            level.isClientSide ? "CLIENT" : "SERVER", pos, clickedState.getBlock());

        BlockPos farmPos = pos;

        // 检查是否点击的是作物（原版或星露谷作物），如果是则尝试对下方的耕地施肥
        if (clickedState.getBlock() instanceof net.minecraft.world.level.block.CropBlock
            || clickedState.getBlock() instanceof com.stardew.craft.block.crop.StardewCropBlock) {
            farmPos = pos.below();
            clickedState = level.getBlockState(farmPos);
            com.stardew.craft.StardewCraft.LOGGER.info("Clicked on crop, checking farmland below at {}", farmPos);
        }

        // 检查是否是耕地
        if (!(clickedState.getBlock() instanceof FarmBlock)) {
            com.stardew.craft.StardewCraft.LOGGER.info("Not farmland, returning PASS");
            return InteractionResult.PASS;
        }

        // 服务端处理
        if (!level.isClientSide) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.FAIL;
            }
            
            FertilizerManager manager = FertilizerManager.get(serverLevel);
            
            // 检查是否已有肥料（不允许覆盖施肥）
            if (manager.hasFertilizer(level, farmPos)) {
                com.stardew.craft.StardewCraft.LOGGER.info("Farmland already has fertilizer, returning FAIL");
                return InteractionResult.FAIL;
            }
            
            // 检查耕地上方是否有作物（作物已发芽则不能施肥质量肥料）
            BlockPos abovePos = farmPos.above();
            @SuppressWarnings("null")
            BlockState aboveState = level.getBlockState(abovePos);

            boolean hasSproutedCrop = false;
            if (!aboveState.isAir()) {
                if (aboveState.getBlock() instanceof net.minecraft.world.level.block.CropBlock vanillaCrop) {
                    // age>0 视为已发芽
                    hasSproutedCrop = vanillaCrop.getAge(aboveState) > 0;
                } else {
                    var crop = StardewCropRuntime.inspect(level, abovePos);
                    if (crop != null) {
                        hasSproutedCrop = crop.visualStage() > 0;
                    }
                }
            }
            
            // 质量肥料不能在作物发芽后施加
            if (hasSproutedCrop && fertilizerType.isQualityFertilizer()) {
                com.stardew.craft.StardewCraft.LOGGER.info("Cannot apply quality fertilizer to sprouted crop");
                return InteractionResult.FAIL;
            }
            
            // 保存肥料数据到Manager
            manager.setFertilizer(serverLevel, farmPos, fertilizerType);
            
            com.stardew.craft.StardewCraft.LOGGER.info("Applied fertilizer {} at position {}", fertilizerType.getSerializedName(), farmPos);
            
            level.playSound(null, farmPos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

            // 消耗物品
            if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }

            return InteractionResult.CONSUME;
        }
        
        // 客户端返回SUCCESS以显示手臂挥动动画
        com.stardew.craft.StardewCraft.LOGGER.info("Client side, returning SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
