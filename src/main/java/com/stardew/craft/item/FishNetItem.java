package com.stardew.craft.item;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

public class FishNetItem extends StardewBlockItem {
    public FishNetItem(Block block, Properties properties) {
        super(block, "stardewcraft.type.utility", -1, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final com.stardew.craft.client.render.FishNetItemRenderer renderer =
                    new com.stardew.craft.client.render.FishNetItemRenderer();

            @Override
            public com.stardew.craft.client.render.FishNetItemRenderer getCustomRenderer() {
                return renderer;
            }
        });
    }
}
