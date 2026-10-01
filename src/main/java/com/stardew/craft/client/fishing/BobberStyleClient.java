package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.BobberStyles;
import com.stardew.craft.fishing.network.BobberStyleStatePayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-selected ordinary style; purple shorts override it without changing the saved preference. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class BobberStyleClient {
    private static final Map<UUID,Integer> STYLES=new HashMap<>();
    public static void receive(BobberStyleStatePayload p){if(p.style()>=0&&p.style()<BobberStyles.COUNT)STYLES.put(p.actor(),p.style());}
    public static int style(UUID actor){return STYLES.getOrDefault(actor,0);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){STYLES.clear();}
}
