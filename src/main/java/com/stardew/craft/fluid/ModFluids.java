package com.stardew.craft.fluid;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import com.stardew.craft.port.net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredRegister;
import com.stardew.craft.port.net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, StardewCraft.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, StardewCraft.MODID);

    public static final DeferredHolder<FluidType, FluidType> FISH_POND_WATER_TYPE = FLUID_TYPES.register("fish_pond_water", () -> new FluidType(
        FluidType.Properties.create()
            .descriptionId("block.stardewcraft.fish_pond_water")
            .fallDistanceModifier(0F)
            .canExtinguish(true)
            .canConvertToSource(true)
            .supportsBoating(true)
            .canHydrate(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
            .addDripstoneDripping(
                PointedDripstoneBlock.WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK,
                ParticleTypes.DRIPPING_DRIPSTONE_WATER,
                Blocks.WATER_CAULDRON,
                SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON
            )
    ) {
        @Override
        @SuppressWarnings("removal")
        public void initializeClient(@Nonnull Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public int getTintColor() {
                    return 0xFF000000 | (resolveTint(null, null) & 0xFFFFFF);
                }

                @Override
                public int getTintColor(@Nonnull FluidStack stack) {
                    return getTintColor();
                }

                @Override
                public int getTintColor(@Nonnull FluidState state, @Nonnull BlockAndTintGetter getter, @Nonnull BlockPos pos) {
                    return 0xFF000000 | (resolveTint(getter, pos) & 0xFFFFFF);
                }

                @Override
                public ResourceLocation getStillTexture() {
                    return new ResourceLocation("block/water_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return new ResourceLocation("block/water_flow");
                }

                @Override
                public ResourceLocation getOverlayTexture() {
                    return new ResourceLocation("block/water_overlay");
                }

                @Override
                public ResourceLocation getRenderOverlayTexture(@Nonnull Minecraft mc) {
                    return new ResourceLocation("textures/misc/underwater.png");
                }

                @Override
                public Vector3f modifyFogColor(@Nonnull Camera camera, float partialTick, @Nonnull ClientLevel level, int renderDistance, float darkenWorldAmount, @Nonnull Vector3f fluidFogColor) {
                    int color = resolveTint(level, camera.getBlockPosition());
                    return new Vector3f(
                        ((color >> 16) & 0xFF) / 255.0F,
                        ((color >> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F
                    );
                }

            });
        }
    });

    public static final DeferredHolder<Fluid, FlowingFluid> FISH_POND_WATER = FLUIDS.register("fish_pond_water", () -> new BaseFlowingFluid.Source(fishPondWaterProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_FISH_POND_WATER = FLUIDS.register("flowing_fish_pond_water", () -> new BaseFlowingFluid.Flowing(fishPondWaterProperties()));

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties fishPondWaterProperties() {
        return new BaseFlowingFluid.Properties(FISH_POND_WATER_TYPE::value, FISH_POND_WATER, FLOWING_FISH_POND_WATER)
            .block(() -> (LiquidBlock) ModBlocks.FISH_POND_WATER.get())
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .tickRate(5)
            .explosionResistance(100.0F);
    }

    // Stardew's ordinary spring water hue; texture alpha supplies transparency in Minecraft.
    public static final int DEFAULT_POND_WATER_COLOR = 0x78C8FF;

    private static int resolveTint(BlockAndTintGetter getter, BlockPos pos) {
        Integer overrideColor = ClientFishPondWaterColorCache.get(getter, pos);
        // Vanilla water sprites already contain grayscale shading and alpha.
        // Neutral source pixels still need a display tint when no species overrides it.
        return overrideColor == null ? DEFAULT_POND_WATER_COLOR : overrideColor & 0xFFFFFF;
    }
}
