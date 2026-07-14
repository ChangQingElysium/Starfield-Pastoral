package com.example.stardewaddon.client;

import com.example.stardewaddon.ExampleStardewAddon;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneCommands;
import com.stardew.craft.cutscene.command.EventCommand;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = ExampleStardewAddon.MOD_ID, value = Dist.CLIENT)
public final class ExampleClientCommands {
    private ExampleClientCommands() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> StardewCutsceneCommands.register(
                ResourceLocation.fromNamespaceAndPath(ExampleStardewAddon.MOD_ID, "toast"),
                data -> new ToastCommand(data.get("text").getAsString())));
    }

    private static final class ToastCommand implements EventCommand {
        private final String text;
        private boolean complete;

        private ToastCommand(String text) {
            this.text = text;
        }

        @Override
        public void start(EventPlayer player) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(text), true);
            }
            complete = true;
        }

        @Override public void tick(EventPlayer player) { }
        @Override public boolean isComplete() { return complete; }
    }
}
