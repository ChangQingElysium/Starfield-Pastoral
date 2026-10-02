package com.stardew.craft.mixin;

import java.util.List;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only login metadata: replies may complete only their own still-pending Forge login request. */
@Mixin(value = HandshakeHandler.class, remap = false)
public interface PortHandshakeHandlerAccessor {
    @Accessor("messageList")
    List<NetworkRegistry.LoginPayload> stardewcraft$loginMessages();

    @Accessor("sentMessages")
    List<Integer> stardewcraft$pendingLoginIndices();
}
