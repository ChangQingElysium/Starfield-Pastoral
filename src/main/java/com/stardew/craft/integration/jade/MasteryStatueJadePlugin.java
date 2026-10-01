package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mastery.StatueOfBlessingsBlock;
import com.stardew.craft.block.mastery.StatueOfDwarfKingBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(StardewCraft.MODID)
public class MasteryStatueJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeBlockDataProviders.register(registration, MasteryStatueJadeProvider.INSTANCE, StatueOfBlessingsBlock.class);
        JadeBlockDataProviders.register(registration, MasteryStatueJadeProvider.INSTANCE, StatueOfDwarfKingBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MasteryStatueJadeProvider.INSTANCE, StatueOfBlessingsBlock.class);
        registration.registerBlockComponent(MasteryStatueJadeProvider.INSTANCE, StatueOfDwarfKingBlock.class);
    }
}