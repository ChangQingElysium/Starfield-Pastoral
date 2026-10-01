package com.stardew.craft.client.deco;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.deco.PaintbrushSelectionManager.Mode;
import com.stardew.craft.item.tool.PaintbrushItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;

/**
 * Handles all client-side input for paintbrush selection mode:
 * - Shift+scroll to cycle modes
 * - Right-click to set corners (intercepts BEFORE vanilla interaction via InteractionKeyMappingTriggered)
 * - Shift+right-click to clear selection
 * - Live preview update on tick
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public final class PaintbrushInputHandler {

    /** Maximum pick distance for region-select mode (blocks). */
    private static final double REGION_PICK_RANGE = 64.0;

    private PaintbrushInputHandler() {}

    // ---- Shift+Scroll: cycle mode ----

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (!isHoldingPaintbrush(mc.player)) return;
        if (!mc.player.isShiftKeyDown()) return;

        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
        mgr.cycleMode(event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    // ---- Right-click intercept: fires BEFORE useItemOn packet is sent ----

    @SuppressWarnings("null")
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;
        if (!isHoldingPaintbrush(player)) return;

        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
        if (mgr.getMode() != Mode.REGION_SELECT) return;

        // Region-selection input only owns wallpaper/flooring clicks. Furniture
        // (including beds) must still reach the paintbrush item's useOn path.
        HitResult hit = player.pick(REGION_PICK_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos clickPos = bhr.getBlockPos();
        BlockState clicked = mc.level.getBlockState(clickPos);
        if (!isDecoBlock(clicked.getBlock())) return;

        // Shift+right-click: clear selection
        if (player.isShiftKeyDown()) {
            mgr.clearSelection();
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        if (!mgr.hasFirstPos()) {
            // First corner — record and CANCEL so no packet is sent to server
            mgr.setFirstPos(clickPos);
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (!mgr.hasCompleteSelection()) {
            // Second corner — record, then let vanilla proceed to open the decoration screen
            mgr.setSecondPos(clickPos);
            // Do NOT cancel: PaintbrushItem.useOn will run and server opens decoration screen
        }
        // If selection is already complete, let vanilla handle re-opening screen
    }

    // ---- Live preview on tick ----

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();

        // If not holding paintbrush, reset
        if (!isHoldingPaintbrush(mc.player)) {
            if (mgr.getMode() == Mode.REGION_SELECT) {
                mgr.clearSelection();
            }
            return;
        }

        // Update live preview: when first corner is set but second is not yet confirmed,
        // chase the looked-at block position
        if (mgr.getMode() == Mode.REGION_SELECT && mgr.hasFirstPos() && !mgr.hasCompleteSelection()) {
            HitResult hit = mc.player.pick(REGION_PICK_RANGE, 1.0f, false);
            if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                mgr.updatePreviewTarget(bhr.getBlockPos());
            }
        }

        mgr.tick();
    }

    // ---- Helpers ----

    private static boolean isHoldingPaintbrush(Player player) {
        return player.getMainHandItem().getItem() instanceof PaintbrushItem
            || player.getOffhandItem().getItem() instanceof PaintbrushItem;
    }

    private static boolean isDecoBlock(Block block) {
        return block instanceof com.stardew.craft.block.utility.WallpaperBlock
            || block == ModBlocks.WALLPAPER_BLOCK.get()
            || block == ModBlocks.FLOORING_BLOCK.get();
    }
}
