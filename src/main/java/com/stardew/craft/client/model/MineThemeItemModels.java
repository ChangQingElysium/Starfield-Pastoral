package com.stardew.craft.client.model;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MinePlanksBlock;
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
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import javax.annotation.Nullable;
import java.util.Objects;

/** The picked/creative material is also the material shown in hand and inventory. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MineThemeItemModels {
    private static final String[] ITEMS = {"mine_planks", "mine_masonry", "mine_blocked_entry", "elevator", "mine_barrel", "mine_crate", "mine_timber_support"};
    private static int variants(String item) { return item.equals("mine_planks") || item.equals("mine_masonry") || item.equals("mine_timber_support") ? 2 : 1; }
    private static ModelResourceLocation id(String item, MineBuildingTheme theme, int variant) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "item/mine_themes/" + item + "/" + theme.id() + "_" + variant), "standalone");
    }

    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (String item : ITEMS) for (var theme : MineBuildingTheme.values()) for (int v = 0; v < variants(item); v++) {
            event.register(id(item, theme, v));
        }
    }

    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        for (String item : ITEMS) {
            BakedModel[][] models = new BakedModel[MineBuildingTheme.values().length][variants(item)];
            for (var theme : MineBuildingTheme.values()) for (int v = 0; v < variants(item); v++) {
                models[theme.ordinal()][v] = Objects.requireNonNull(event.getModels().get(id(item, theme, v)));
            }
            var inventory = new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, item), "inventory");
            event.getModels().put(inventory, new Themed(Objects.requireNonNull(event.getModels().get(inventory)), models));
        }
    }

    private static final class Themed extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        Themed(BakedModel original, BakedModel[][] models) {
            super(original);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                                    @Nullable LivingEntity entity, int seed) {
                    var saved = PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
                    var theme = saved.get(MineBuildingTheme.PROPERTY);
                    Integer v = saved.get(MinePlanksBlock.VARIANT);
                    BakedModel[] row = models[theme == null ? 0 : theme.ordinal()];
                    return row[v == null ? 0 : Math.min(v, row.length - 1)];
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
