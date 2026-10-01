package com.stardew.craft.mail;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * 邮件系统事件钩子：注册数据包重载监听器。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MailSystem {
    private MailSystem() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MailRegistry.ReloadListener());
    }
}
