package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineSerpentPillarBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import javax.annotation.Nullable;
import java.util.Objects;

/** Native item models follow the same variant as the placed/picked pillar. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MineSerpentPillarModels {
    private static ModelResourceLocation id(String color) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "item/mine_" + color + "_serpent_pillar"), "standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        event.register(id("purple")); event.register(id("green"));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        BakedModel[] variants = {Objects.requireNonNull(event.getModels().get(id("purple"))),
                Objects.requireNonNull(event.getModels().get(id("green")))};
        var inventory = new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "mine_serpent_pillar"), "inventory");
        event.getModels().put(inventory, new PillarItem(variants));
    }
    private static final class PillarItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        PillarItem(BakedModel[] variants) {
            super(variants[0]);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                        @Nullable LivingEntity entity, int seed) {
                    Integer variant = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                            .get(MineSerpentPillarBlock.VARIANT);
                    return variants[variant == null ? 0 : variant];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
