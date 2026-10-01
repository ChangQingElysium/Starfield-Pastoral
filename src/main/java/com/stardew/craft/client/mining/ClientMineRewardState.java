package com.stardew.craft.client.mining;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

@EventBusSubscriber(modid="stardewcraft",value=Dist.CLIENT)
public final class ClientMineRewardState {
    private static final Set<Integer> opened=new HashSet<>();
    public static void receive(String floors) {
        opened.clear(); for(String f:floors.split(",")) if(!f.isBlank()) opened.add(Integer.parseInt(f));
    }
    public static boolean isOpen(int floor) { return opened.contains(floor); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { opened.clear(); }
}
