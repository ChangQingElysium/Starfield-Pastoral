package com.stardew.craft.templates.client;

import com.stardew.craft.templates.TemplateBlock;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateContent;
import com.stardew.craft.templates.TemplateShape;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class TemplateClientEvents {
    @SubscribeEvent
    public static void modifyModels(ModelEvent.ModifyBakingResult event) {
        // PORT(1.20.1): Forge keys baked models by ResourceLocation (ModelResourceLocation is a subclass).
        Map<net.minecraft.resources.ResourceLocation, BakedModel> models = event.getModels();
        int wrappedBlockStates = 0;
        int wrappedItems = 0;
        for (TemplateShape shape : TemplateShape.values()) {
            Block block = TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            if (!(block instanceof TemplateBlock)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel original = models.get(location);
                if (original != null) {
                    models.put(location, new TemplateBakedModel(original, shape, state, false));
                    wrappedBlockStates++;
                }
            }

            var blockId = BuiltInRegistries.BLOCK.getKey(block);
            ModelResourceLocation itemLocation = new ModelResourceLocation(blockId, "inventory"); // PORT(1.20.1): no inventory() factory
            BakedModel itemModel = models.get(itemLocation);
            if (itemModel != null) {
                models.put(itemLocation, new TemplateBakedModel(itemModel, shape, block.defaultBlockState(), true));
                wrappedItems++;
            }
        }
        StardewCraft.LOGGER.info("Wrapped {} template block-state models and {} template item models",
                wrappedBlockStates, wrappedItems);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        Block[] blocks = TemplateContent.TEMPLATE_BLOCKS.values().stream()
                .map(java.util.function.Supplier::get)
                .toArray(Block[]::new);
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) {
                return -1;
            }
            if (level.getBlockEntity(pos) instanceof TemplateBlockEntity template) {
                boolean fill = tintIndex >= TemplateBakedModel.FILL_TINT_OFFSET;
                BlockState material = fill ? template.effectiveFillMaterial() : template.material();
                if (material != null) return Minecraft.getInstance().getBlockColors()
                        .getColor(material, level, pos, fill ? tintIndex-TemplateBakedModel.FILL_TINT_OFFSET : tintIndex);
            }
            return -1;
        }, blocks);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        var items = TemplateContent.TEMPLATE_ITEMS.entrySet().stream()
                .map(e -> e.getValue().get()).toArray(net.minecraft.world.item.Item[]::new);
        event.register((stack, tint) -> {
            var data = TemplateBlockEntity.itemMaterials(stack);
            boolean fill = tint >= TemplateBakedModel.FILL_TINT_OFFSET;
            BlockState material = data.get(fill ? TemplateBlockEntity.FILL_MATERIAL_PROPERTY : TemplateBlockEntity.MATERIAL_PROPERTY);
            return material == null ? -1 : Minecraft.getInstance().getItemColors().getColor(
                    new net.minecraft.world.item.ItemStack(material.getBlock()), fill ? tint-TemplateBakedModel.FILL_TINT_OFFSET : tint);
        }, items);
    }

    private TemplateClientEvents() {
    }
}
