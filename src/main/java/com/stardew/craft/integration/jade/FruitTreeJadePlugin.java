package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeExtensionBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(StardewCraft.MODID)
public class FruitTreeJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeBlockDataProviders.register(registration, FruitTreeJadeProvider.INSTANCE, FruitTreeSaplingBlock.class);
        JadeBlockDataProviders.register(registration, FruitTreeJadeProvider.INSTANCE, FruitTreeBlock.class);
        JadeBlockDataProviders.register(registration, FruitTreeJadeProvider.INSTANCE, FruitTreeExtensionBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FruitTreeJadeProvider.INSTANCE, FruitTreeSaplingBlock.class);
        registration.registerBlockComponent(FruitTreeJadeProvider.INSTANCE, FruitTreeBlock.class);
        registration.registerBlockComponent(FruitTreeJadeProvider.INSTANCE, FruitTreeExtensionBlock.class);
    }
}
