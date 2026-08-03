package com.stardew.craft.communitycenter;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.cutscene.AreaRestoreCutscene;
import com.stardew.craft.communitycenter.cutscene.GoodbyeDanceCutscene;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.junimo.JunimoSpawner;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CommunityCenterSystem {

    private CommunityCenterSystem() {}

    /** Track players currently inside CC to detect enter/leave */
    private static final Set<UUID> playersInsideCC = new HashSet<>();

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BundleDataManager.ReloadListener());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AreaRestoreCutscene.tick();
        GoodbyeDanceCutscene.tick();
    }

    /**
     * SDV parity: CommunityCenter.resetSharedState() spawns idle Junimos.
     * We detect when a player enters the CC interior and spawn idle Junimos.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(sp.level() instanceof ServerLevel serverLevel)) return;

        // Only check every 20 ticks (1 second) for performance
        if (sp.tickCount % 20 != 0) return;

        boolean insideNow = false;
        // 检查玩家是否在自己的 CC 内（通过 PlayerInteriorAllocator）
        PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(serverLevel);
        if (alloc.isInsideAnyCC(sp.blockPosition())) {
            insideNow = true;
        }
        boolean wasInside = playersInsideCC.contains(sp.getUUID());

        if (insideNow && !wasInside) {
            // Player just entered CC — spawn idle Junimos (SDV resetSharedState parity)
            playersInsideCC.add(sp.getUUID());
            net.minecraft.core.BlockPos ccOrigin = alloc.getCCOrigin(sp.getUUID());
            // Also runs when a save is loaded while the player is already inside the CC. Register
            // and restore the exit before any story-specific early return can run.
            InteriorSubspaceManager.ensureCommunityCenterExitPortal(serverLevel, ccOrigin);
            // SDV parity (CommunityCenter.cs:540): Joja 会员进入 CC 不再生成 Junimo / JunimoNote —
            // 仓库外观、Junimo 已撤走的语义。
            if (CCStoryFlags.isJojaMember(sp)) {
                return;
            }
            boolean friendly = CCStoryFlags.canReadJunimoText(sp);
            JunimoSpawner.spawnIdleJunimos(serverLevel, friendly, sp.getUUID(), ccOrigin);
            // Also ensure JunimoNotes are placed for this player's CC
            JunimoNotePlacer.ensureJunimoNotes(serverLevel, sp.getUUID(), ccOrigin);
        } else if (!insideNow && wasInside) {
            // Player left CC
            playersInsideCC.remove(sp.getUUID());
        }
    }
}
