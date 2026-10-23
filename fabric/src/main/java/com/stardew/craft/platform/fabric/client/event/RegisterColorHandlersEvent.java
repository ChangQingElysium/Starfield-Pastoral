package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.mixin.client.event.mixin.MinecraftColorsAccessor;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.level.ItemLike;

/** Registration may precede Minecraft's construction of the underlying color maps. */
@Environment(EnvType.CLIENT)
public abstract class RegisterColorHandlersEvent extends Event implements ModBusEvent {
    private RegisterColorHandlersEvent() {}

    public static final class Block extends RegisterColorHandlersEvent {
        public void register(BlockColor color, net.minecraft.world.level.block.Block... blocks) {
            ColorProviderRegistry.BLOCK.register(color, blocks);
        }

        public BlockColors getBlockColors() {
            return blockColors();
        }
    }

    public static final class Item extends RegisterColorHandlersEvent {
        public void register(ItemColor color, ItemLike... items) {
            ColorProviderRegistry.ITEM.register(color, items);
        }

        public BlockColors getBlockColors() {
            return blockColors();
        }

        public ItemColors getItemColors() {
            return Objects.requireNonNull(((MinecraftColorsAccessor) Minecraft.getInstance()).stardewcraft$getItemColors(),
                    "Item colors are not constructed yet; defer lookup until the provider is queried");
        }
    }

    private static BlockColors blockColors() {
        return Objects.requireNonNull(Minecraft.getInstance().getBlockColors(),
                "Block colors are not constructed yet; defer lookup until the provider is queried");
    }
}
