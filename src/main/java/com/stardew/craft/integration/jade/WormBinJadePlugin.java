package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.DeluxeWormBinBlock;
import com.stardew.craft.block.utility.WormBinBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(StardewCraft.MODID)
public class WormBinJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeBlockDataProviders.register(registration, WormBinJadeProvider.INSTANCE, WormBinBlock.class);
        JadeBlockDataProviders.register(registration, WormBinJadeProvider.INSTANCE, DeluxeWormBinBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(WormBinJadeProvider.INSTANCE, WormBinBlock.class);
        registration.registerBlockComponent(WormBinJadeProvider.INSTANCE, DeluxeWormBinBlock.class);
    }
}
