package com.stardew.craft.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端独立类，包含 {@link ShaftConfirmPacket} 的实际客户端处理逻辑。
 * 拆分到独立类后，专用服务器在注册阶段不会触发 {@code net.minecraft.client.*} 类加载。
 */
@OnlyIn(Dist.CLIENT)
public final class ShaftConfirmPacketClient {

    private ShaftConfirmPacketClient() {}

    public static void open(ShaftConfirmPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(com.stardew.craft.client.gui.ShaftConfirmScreen.create(packet.shaftPos()));
        }
    }
}
