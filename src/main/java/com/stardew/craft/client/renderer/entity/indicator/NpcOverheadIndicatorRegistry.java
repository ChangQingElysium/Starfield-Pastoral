package com.stardew.craft.client.renderer.entity.indicator;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NpcOverheadIndicatorRegistry {
    private static final List<NpcOverheadIndicatorProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private NpcOverheadIndicatorRegistry() {
    }

    public static void register(NpcOverheadIndicatorProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static NpcOverheadIndicator resolve(StardewNpcEntity npc, LocalPlayer localPlayer) {
        for (NpcOverheadIndicatorProvider provider : PROVIDERS) {
            NpcOverheadIndicator indicator = provider.resolve(npc, localPlayer);
            if (indicator != null) {
                return indicator;
            }
        }
        return null;
    }
}
