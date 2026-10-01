package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class TemplateMaterials {
    public static final ResourceLocation DEFAULT_MATERIAL_ID =
            new ResourceLocation("stardewcraft", "oak_planks");

    public static BlockState effectiveMaterial(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TemplateBlockEntity template && template.material() != null) {
            return template.material();
        }
        return defaultMaterial();
    }

    public static BlockState defaultMaterial() {
        return BuiltInRegistries.BLOCK.getOptional(DEFAULT_MATERIAL_ID)
                .orElse(Blocks.OAK_PLANKS)
                .defaultBlockState();
    }

    public static boolean isValid(@Nullable BlockState state) {
        return state != null
                && !state.isAir()
                && !(state.getBlock() instanceof TemplateBlock)
                && state.getRenderShape() == RenderShape.MODEL;
    }

    public static boolean isValidFill(@Nullable BlockState state) {
        return isValid(state) && !state.hasBlockEntity()
                && net.minecraft.world.level.block.Block.isShapeFullBlock(
                        state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** Keep painted timber grain along straight structural members after template rotation. */
    public static BlockState orientTimber(BlockState material, TemplateShape shape, BlockState template) {
        if (!material.is(com.stardew.craft.block.ModBlocks.BLUE_GRAY_TIMBER.get())
                && !material.is(com.stardew.craft.block.ModBlocks.TEAL_PAINTED_TIMBER.get())) return material;
        net.minecraft.core.Direction.Axis axis = switch (shape) {
            case WALL_BEAM, ROOF_EAVE, HORIZONTAL_COLUMN, HORIZONTAL_POST,
                    HORIZONTAL_STICK, HORIZONTAL_POLE, FRAME_TOP, FRAME_BOTTOM ->
                    template.getValue(MaterialTemplateBlock.FACING).getClockWise().getAxis();
            default -> material.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS);
        };
        return material.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis);
    }

    private TemplateMaterials() {
    }
}
