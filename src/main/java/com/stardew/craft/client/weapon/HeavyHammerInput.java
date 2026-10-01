package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.HeavyHammerInputPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.network.PacketDistributor;

/** Normal attack input is held to pound. No second key, click-speed requirement or target lock. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class HeavyHammerInput {
    private static boolean sentHeld;
    private static long lastSent;
    private HeavyHammerInput() {}
    private static boolean ownsAttack(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.player.getMainHandItem().getItem() instanceof IStardewWeapon w && "infinity_gavel".equals(w.getWeaponId())
                && HeavyHammerVisuals.empowered(mc.player.getId());
    }
    private static boolean acceptsInput(Minecraft mc) {
        return mc.screen == null && !mc.isPaused()
                && !com.stardew.craft.client.combat.CombatCollapseClientState.isActive()
                && !com.stardew.craft.network.overnight.OvernightCollapseClientState.isActive()
                && !com.stardew.craft.cutscene.runtime.EventPlayer.get().isPlayerFrozen()
                && !com.stardew.craft.client.ritual.GalaxySwordRitualClientState.isPlayerFrozen();
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void input(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(!e.isAttack() || !ownsAttack(mc)) return;
        e.setCanceled(true); e.setSwingHand(false);
        if(acceptsInput(mc)) send(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) {sentHeld=false;lastSent=0;return;}
        boolean held=ownsAttack(mc) && acceptsInput(mc) && mc.options.keyAttack.isDown();
        if(held!=sentHeld || held && mc.level.getGameTime()-lastSent>=3) send(held);
    }
    private static void send(boolean held) {
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null) return;
        PacketDistributor.sendToServer(new HeavyHammerInputPayload(held));
        sentHeld=held;lastSent=mc.level.getGameTime();
    }
}
