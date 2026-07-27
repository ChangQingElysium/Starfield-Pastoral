package com.stardew.craft.client;

import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import com.stardew.craft.api.v1.npc.StardewNpcDisplays;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

public final class NpcDisplayNames {
    private NpcDisplayNames() {
    }

    public static String translated(String npcId) {
        StardewNpcDisplay display = StardewNpcDisplays.resolve(npcId);
        if (display == null) {
            return ClientDisplayFallbacks.readableId(npcId);
        }
        String key = display.nameTranslationKey();
        return I18n.exists(key)
                ? I18n.get(key)
                : ClientDisplayFallbacks.readableId(
                        display.npcId().toString());
    }

    public static String sortKey(String npcId) {
        return translated(npcId).toLowerCase(Locale.ROOT);
    }

}
