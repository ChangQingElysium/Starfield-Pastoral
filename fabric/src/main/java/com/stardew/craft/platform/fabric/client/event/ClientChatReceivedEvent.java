package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientChatReceivedEvent extends Event {
    private Component message;
    @Nullable private final ChatType.Bound type;
    private final UUID sender;
    public ClientChatReceivedEvent(@Nullable ChatType.Bound type, Component message, UUID sender) {
        super(true); this.type = type; this.message = Objects.requireNonNull(message); this.sender = Objects.requireNonNull(sender);
    }
    public Component getMessage() { return message; }
    public void setMessage(Component message) { this.message = Objects.requireNonNull(message); }
    @Nullable public ChatType.Bound getBoundChatType() { return type; }
    public UUID getSender() { return sender; }
    public boolean isSystem() { return Util.NIL_UUID.equals(sender); }
    public static final class System extends ClientChatReceivedEvent {
        private final boolean overlay;
        public System(Component message, boolean overlay) { super(null, message, Util.NIL_UUID); this.overlay = overlay; }
        public boolean isOverlay() { return overlay; }
    }
}
