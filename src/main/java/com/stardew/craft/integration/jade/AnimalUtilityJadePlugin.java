package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.AutoPetterBlock;
import com.stardew.craft.block.utility.HeaterBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(StardewCraft.MODID)
public class AnimalUtilityJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeBlockDataProviders.register(registration, AnimalUtilityJadeProvider.INSTANCE, AutoPetterBlock.class);
        JadeBlockDataProviders.register(registration, AnimalUtilityJadeProvider.INSTANCE, HeaterBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AnimalUtilityJadeProvider.INSTANCE, AutoPetterBlock.class);
        registration.registerBlockComponent(AnimalUtilityJadeProvider.INSTANCE, HeaterBlock.class);
    }
}
