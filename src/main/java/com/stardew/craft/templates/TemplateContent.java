package com.stardew.craft.templates;

import com.stardew.craft.StardewCraft;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredBlock;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;

public final class TemplateContent {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(StardewCraft.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(StardewCraft.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StardewCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StardewCraft.MODID);

    public static final Map<TemplateShape, DeferredBlock<Block>> TEMPLATE_BLOCKS = registerBlocks();
    public static final Map<TemplateShape, DeferredItem<BlockItem>> TEMPLATE_ITEMS = registerItems();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TemplateBlockEntity>> TEMPLATE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("material_template", () -> BlockEntityType.Builder.of(
                    TemplateBlockEntity::new,
                    TEMPLATE_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)
            ).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TEMPLATE_TAB = TABS.register(
            "building_templates",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stardewcraft.building_templates"))
                    .icon(() -> TEMPLATE_ITEMS.get(TemplateShape.SLOPE).get().getDefaultInstance())
                    .displayItems((parameters, output) -> TEMPLATE_ITEMS.forEach((shape, item) -> {
                        if (shape.visibleInCreativeTab()) output.accept(item.get());
                    }))
                    .build()
    );

    private static Map<TemplateShape, DeferredBlock<Block>> registerBlocks() {
        EnumMap<TemplateShape, DeferredBlock<Block>> blocks = new EnumMap<>(TemplateShape.class);
        for (TemplateShape shape : TemplateShape.values()) {
            blocks.put(shape, BLOCKS.register(shape.registryPath(), () -> createBlock(shape)));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Block createBlock(TemplateShape shape) {
        Block.Properties properties = propertiesFor(shape);
        if (shape == TemplateShape.GRID_WINDOW) return new GridWindowTemplateBlock(properties);
        if (shape == TemplateShape.BALCONY_RAILING) return new BalconyRailingTemplateBlock(properties);
        if (shape == TemplateShape.CHIMNEY) {
            return new ChimneyTemplateBlock(properties);
        }
        if (shape == TemplateShape.SNOW_LAYER) {
            return new SnowLayerTemplateBlock(properties);
        }
        if (shape == TemplateShape.FENCE) {
            return new VanillaTemplateFenceBlock(properties);
        }
        if (shape == TemplateShape.WALL) {
            return new VanillaTemplateWallBlock(properties);
        }
        if (shape == TemplateShape.BUTTON) {
            return new VanillaTemplateButtonBlock(properties);
        }
        if (shape == TemplateShape.DOOR) {
            return new VanillaTemplateDoorBlock(properties);
        }
        if (shape == TemplateShape.TRAPDOOR || shape == TemplateShape.IRON_TRAPDOOR) {
            return new VanillaTemplateTrapDoorBlock(shape, properties);
        }
        if (shape == TemplateShape.FENCE_GATE) {
            return new VanillaTemplateFenceGateBlock(properties);
        }
        if (shape == TemplateShape.PRESSURE_PLATE) {
            return new VanillaTemplatePressurePlateBlock(properties);
        }
        if (shape == TemplateShape.LADDER) {
            return new VanillaTemplateLadderBlock(properties);
        }
        if (shape.meshKind() == TemplateShape.MeshKind.ROOF) {
            if (shape.roofForm().connectsAsSlope()) {
                return new SmartRoofTemplateBlock(shape, properties);
            }
            if (shape.roofForm().isRidge()) {
                return new SmartRidgeTemplateBlock(shape, properties);
            }
            return new RoofTemplateBlock(shape, properties);
        }
        if (shape == TemplateShape.WALL_JUNCTION || shape.isWindow()) return new ConnectedFacadeTemplateBlock(shape, properties);
        if (shape == TemplateShape.ROUND_WINDOW) return new WallCompositeTemplateBlock(shape, properties);
        if (shape.isCompositeWall()) return new WallCompositeTemplateBlock(shape, properties);
        if (shape == TemplateShape.HALF_STAIRS) return new HalfStairsTemplateBlock(properties);
        return new MaterialTemplateBlock(shape, properties);
    }

    private static Block.Properties propertiesFor(TemplateShape shape) {
        Block reference = switch (shape) {
            case BUTTON -> net.minecraft.world.level.block.Blocks.OAK_BUTTON;
            case FENCE -> net.minecraft.world.level.block.Blocks.OAK_FENCE;
            case FENCE_GATE -> net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE;
            case WALL -> net.minecraft.world.level.block.Blocks.COBBLESTONE_WALL;
            case DOOR -> net.minecraft.world.level.block.Blocks.OAK_DOOR;
            case TRAPDOOR -> net.minecraft.world.level.block.Blocks.OAK_TRAPDOOR;
            case IRON_TRAPDOOR -> net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR;
            case PRESSURE_PLATE -> net.minecraft.world.level.block.Blocks.OAK_PRESSURE_PLATE;
            case LADDER -> net.minecraft.world.level.block.Blocks.LADDER;
            default -> null;
        };
        if (reference != null) {
            return Block.Properties.ofFullCopy(reference);
        }
        Block.Properties properties = Block.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.5F, 3.0F);
        if (!shape.canOccludeWithSolidMaterial()) {
            properties.noOcclusion();
        }
        return properties;
    }

    private static Map<TemplateShape, DeferredItem<BlockItem>> registerItems() {
        EnumMap<TemplateShape, DeferredItem<BlockItem>> items = new EnumMap<>(TemplateShape.class);
        for (TemplateShape shape : TemplateShape.values()) {
            DeferredBlock<Block> block = TEMPLATE_BLOCKS.get(shape);
            items.put(shape, ITEMS.register(shape.registryPath(), () -> new TemplateBlockItem(block.get(), new Item.Properties())));
        }
        return Collections.unmodifiableMap(items);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        TABS.register(modBus);
    }

    private TemplateContent() {
    }
}
