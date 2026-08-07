package com.stardew.craft.client.render;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.client.hud.InteractionHintHud;
import com.stardew.craft.client.render.ClientStarterChestState;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.festival.FestivalOfIceService;
import com.stardew.craft.festival.desert.DesertFestivalCookService;
import com.stardew.craft.festival.desert.DesertFestivalRaceService;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService;
import com.stardew.craft.festival.desert.DesertFestivalWillyFishingService;
import com.stardew.craft.festival.fair.FairFishingGameService;
import com.stardew.craft.festival.fair.FairSlingshotGameService;
import com.stardew.craft.festival.nightmarket.NightMarketCoffeeService;
import com.stardew.craft.festival.nightmarket.NightMarketMermaidService;
import com.stardew.craft.festival.nightmarket.NightMarketPainterService;
import com.stardew.craft.festival.nightmarket.NightMarketShopService;
import com.stardew.craft.festival.nightmarket.NightMarketSubmarineService;
import com.stardew.craft.qi.MrQiQuestInteractionService;
import com.stardew.craft.qi.MrQiQuestRules;
import com.stardew.craft.quest.network.ClientQuestData;
import com.stardew.craft.secretnote.SecretNote20Service;
import com.stardew.craft.world.OldMasterCannoliService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders visual hints near portal interaction entities:
 * - A soft glowing outline around the door area
 * - A screen-space interaction card which stays readable regardless of nearby blocks
 *
 * Enter portals: warm golden glow
 * Exit portals:  cool blue-white glow
 */
@SuppressWarnings("unused")
public final class PortalHintRenderer {

    private static final double HINT_RANGE = 5.0;
    private static final double HINT_RANGE_SQ = HINT_RANGE * HINT_RANGE;
    /** Scan radius for dynamic entity hints — must be large enough to cover the longest portal area. */
    private static final double ENTITY_SCAN_RANGE = 20.0;

    // Edge half-width for the glow outline (world units)
    private static final float EDGE_HALF = 0.02f;

    // Enter style — warm amber/gold
    private static final int ENTER_R = 255, ENTER_G = 200, ENTER_B = 60;
    private static final int ENTER_EDGE_A = 180;
    private static final int ENTER_FACE_A = 25;
    // Exit style — cool blue-white
    private static final int EXIT_R = 140, EXIT_G = 200, EXIT_B = 255;
    private static final int EXIT_EDGE_A = 160;
    private static final int EXIT_FACE_A = 20;

    // Return-to-overworld style — green
    private static final int RET_R = 80, RET_G = 230, RET_B = 100;
    private static final int RET_EDGE_A = 170;
    private static final int RET_FACE_A = 22;

    // Locked style — gray（献祭未完成）
    private static final int LOCKED_R = 150, LOCKED_G = 150, LOCKED_B = 150;
    private static final int LOCKED_EDGE_A = 150;
    private static final int LOCKED_FACE_A = 20;

    // Shop style — purple
    private static final int SHOP_R = 190, SHOP_G = 120, SHOP_B = 255;
    private static final int SHOP_EDGE_A = 175;
    private static final int SHOP_FACE_A = 24;

    // Desert race style — bright gold
    private static final int RACE_R = 255, RACE_G = 205, RACE_B = 45;
    private static final int RACE_EDGE_A = 190;
    private static final int RACE_FACE_A = 28;

    // Shady guy style — black
    private static final int SHADY_R = 25, SHADY_G = 20, SHADY_B = 18;
    private static final int SHADY_EDGE_A = 210;
    private static final int SHADY_FACE_A = 36;

    // Desert cook style — orange
    private static final int COOK_R = 255, COOK_G = 145, COOK_B = 45;
    private static final int COOK_EDGE_A = 190;
    private static final int COOK_FACE_A = 28;

    // Old Master Cannoli statue style — white
    private static final int WHITE_R = 255, WHITE_G = 255, WHITE_B = 255;
    private static final int WHITE_EDGE_A = 190;
    private static final int WHITE_FACE_A = 24;

    // Bubble text — translatable keys
    private static final String ENTER_KEY = "stardewcraft.portal.hint.enter";
    private static final String EXIT_KEY = "stardewcraft.portal.hint.exit";
    private static final String RETURN_KEY = "stardewcraft.portal.hint.return";
    private static final String CLAIM_KEY = "stardewcraft.portal.hint.claim";
    private static final String LOCKED_KEY = "stardewcraft.portal.hint.locked";
    private static final String OPEN_KEY = "stardewcraft.portal.hint.open";
    private static final String INTERACT_KEY = "stardewcraft.portal.hint.interact";
    private static final String RACE_KEY = "stardewcraft.portal.hint.desert_race";
    private static final String SHADY_KEY = "stardewcraft.portal.hint.shady_guy";
    private static final String WILLY_CHALLENGE_KEY = "stardewcraft.portal.hint.willy_challenge";
    private static final String COOK_KEY = "stardewcraft.portal.hint.desert_festival_cook";
    private static final String FAIR_SLINGSHOT_KEY = "stardewcraft.portal.hint.fair_slingshot";
    private static final String FAIR_FISHING_KEY = "stardewcraft.portal.hint.fair_fishing";
    private static final String FAIR_TOKEN_PURCHASE_KEY = "stardewcraft.portal.hint.fair_token_purchase";
    private static final String FAIR_FORTUNE_KEY = "stardewcraft.portal.hint.fair_fortune";
    private static final String CASINO_QI_COIN_MACHINE_KEY =
            "stardewcraft.portal.hint.casino_qi_coin_machine";
    private static final String CASINO_QI_COIN_SHOP_KEY =
            "stardewcraft.portal.hint.casino_qi_coin_shop";

    private static final String BUY_TICKET_KEY = "stardewcraft.portal.hint.buy_ticket";
    private static final String SECRET_NOTE_20_DRIVER_KEY = "stardewcraft.secret_note.20.truck_hint";

    @SuppressWarnings("null")
    private static final RenderType QUAD_TYPE = makeQuadType("stardew_portal_hint", false);
    @SuppressWarnings("null")
    private static final RenderType QUAD_XRAY = makeQuadType("stardew_portal_hint_xr", true);

    private static final float TEXT_FADE_IN_MS = 140.0F;
    private static final float TEXT_FADE_OUT_MS = 180.0F;
    private static PortalHint activeScreenHint;
    private static boolean textHintTargeted;
    private static float textHintAlpha;
    private static long lastTextFadeUpdateMs;

    private PortalHintRenderer() {}

    // ======================== main render ========================

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            clearScreenHint();
            return;
        }

        // Only in Stardew Valley, Overworld (wizard tower), or Mining dimension
        boolean inStardew = ModDimensions.STARDEW_VALLEY.equals(mc.level.dimension());
        boolean inOverworld = Level.OVERWORLD.equals(mc.level.dimension());
        boolean inMine = ModMiningDimensions.STARDEW_MINING.equals(mc.level.dimension());

        if (!inStardew && !inOverworld && !inMine) {
            clearScreenHint();
            return;
        }

        List<PortalHint> hints = findNearbyPortals(player);
        updateTargetedHint(mc, player, hints);

        if (hints.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        // ---- Phase 1: Glow outlines (world-space, single translate(-cam)) ----
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        for (PortalHint hint : hints) {
            float alpha = calcFadeAlpha(player, hint);
            if (alpha < 0.01F) continue;
            renderGlowOutline(buf, ps, cam, hint, alpha);
        }

        ps.popPose();

        buf.endBatch();
    }

    public static void onRenderGui(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        updateTextFade();
        PortalHint hint = activeScreenHint;
        if (hint == null || textHintAlpha <= 0.01F || mc.player == null
                || mc.options.hideGui || mc.screen != null) {
            return;
        }

        Component action = actionComponent(hint);
        Component destination = isSingleLineHint(hint.targetId)
                ? Component.empty()
                : Component.translatable("stardewcraft.location." + hint.destinationKey);
        InteractionHintHud.render(graphics, StardewFonts.small(), action, destination,
                accentRgb(hint), textHintAlpha);
    }

    private static void updateTargetedHint(Minecraft mc, Player player, List<PortalHint> hints) {
        if (mc.hitResult instanceof EntityHitResult entityHit
                && SecretNote20Service.isTruckInteraction(entityHit.getEntity())) {
            for (PortalHint hint : hints) {
                if (SecretNote20Service.TARGET_ID.equals(hint.targetId)) {
                    activateScreenHint(hint);
                    return;
                }
            }
        }
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) {
            textHintTargeted = false;
            return;
        }

        BlockPos targetedBlock = blockHit.getBlockPos();
        for (PortalHint hint : hints) {
            if (containsBlock(hint, targetedBlock)) {
                activateScreenHint(hint);
                return;
            }
        }
        textHintTargeted = false;
    }

    private static void activateScreenHint(PortalHint hint) {
        if (!hint.equals(activeScreenHint)) {
            activeScreenHint = hint;
            textHintAlpha = 0.0F;
        }
        textHintTargeted = true;
    }

    private static boolean containsBlock(PortalHint hint, BlockPos block) {
        int minX = (int) Math.floor(hint.pos.x - 0.5D);
        int minY = (int) Math.floor(hint.pos.y);
        int minZ = (int) Math.floor(hint.pos.z - 0.5D);
        return block.getX() >= minX && block.getX() < minX + hint.xBlocks
                && block.getY() >= minY && block.getY() < minY + hint.heightBlocks
                && block.getZ() >= minZ && block.getZ() < minZ + hint.zBlocks;
    }

    private static void clearScreenHint() {
        activeScreenHint = null;
        textHintTargeted = false;
        textHintAlpha = 0.0F;
        lastTextFadeUpdateMs = 0L;
    }

    private static void updateTextFade() {
        long now = Util.getMillis();
        if (lastTextFadeUpdateMs == 0L) {
            lastTextFadeUpdateMs = now;
            return;
        }

        float elapsedMs = Math.min(100.0F, now - lastTextFadeUpdateMs);
        lastTextFadeUpdateMs = now;
        float durationMs = textHintTargeted ? TEXT_FADE_IN_MS : TEXT_FADE_OUT_MS;
        float delta = elapsedMs / durationMs;
        textHintAlpha = textHintTargeted
                ? Math.min(1.0F, textHintAlpha + delta)
                : Math.max(0.0F, textHintAlpha - delta);
        if (!textHintTargeted && textHintAlpha <= 0.0F) {
            activeScreenHint = null;
        }
    }

    // ======================== find nearby portals ========================

    @SuppressWarnings("null")
    private static List<PortalHint> findNearbyPortals(Player player) {
        List<PortalHint> result = new ArrayList<>();
        Vec3 playerPos = player.position();

        findPortalBlockHints(player, playerPos, result);

        if (com.stardew.craft.client.ClientPlayerDataCache.hasSeenSecretNote(SecretNote20Service.NOTE_ID)
                && !com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(SecretNote20Service.DONE_FLAG)
                && !com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(SecretNote20Service.SPECIAL_CHARM_FLAG)
                && !com.stardew.craft.client.ClientPlayerDataCache.hasSpecialItem(SecretNote20Service.SPECIAL_CHARM_SPECIAL_ITEM)) {
            Vec3 truckPos = new Vec3(122.5D, 67.0D, -20.5D);
            if (distSqToHintArea(playerPos, truckPos, 2, 1, 1) <= HINT_RANGE_SQ) {
                result.add(new PortalHint(truckPos, false, 2, 1, 1,
                        HintStyle.SHOP, "", SecretNote20Service.TARGET_ID));
            }
        }

        // Dynamic starter chest hint — 箱子模型比 1 格高，气泡上移 0.4 避免嵌入模型
        Vec3 chestVec = ClientStarterChestState.getHintVec();
        if (chestVec != null && playerPos.distanceToSqr(chestVec) <= HINT_RANGE_SQ) {
            result.add(new PortalHint(chestVec.add(0, 0.4, 0), true, 1, 1, 1,
                    HintStyle.ENTER, "starter_chest", "starter_chest"));
        }

        return result;
    }

    /**
     * Scan nearby PortalTriggerBlockEntity blocks for portal hints.
    * Groups contiguous blocks with the same targetId into separate hints.
     */
    @SuppressWarnings("null")
    private static void findPortalBlockHints(Player player, Vec3 playerPos, List<PortalHint> result) {
        Level level = player.level();
        if (level == null) return;

        BlockPos center = player.blockPosition();
        int range = (int) ENTITY_SCAN_RANGE;

        Map<String, Set<BlockPos>> portalBlocks = new LinkedHashMap<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) continue;
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof PortalTriggerBlockEntity ptbe)) continue;
                    String targetId = ptbe.getTargetId();
                    if (targetId == null || targetId.isBlank()) continue;
                    if (MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID.equals(targetId)
                            && !ClientQuestData.hasQuest(MrQiQuestRules.QUEST_SAND_DRAGON)) {
                        continue;
                    }
                    if (!canShowCasinoPortal(player, targetId)) {
                        continue;
                    }

                    portalBlocks.computeIfAbsent(targetId, k -> new LinkedHashSet<>()).add(pos.immutable());
                }
            }
        }

        for (var entry : portalBlocks.entrySet()) {
            String targetId = entry.getKey();
            boolean isEnter = isEnterTarget(targetId);
            String locKey = destinationKeyForTarget(targetId);
            HintStyle style = styleForTarget(targetId, isEnter);
            for (PortalBounds bounds : splitConnectedBounds(entry.getValue())) {
                Vec3 minPos = new Vec3(bounds.minX() + 0.5D, bounds.minY(), bounds.minZ() + 0.5D);
                int xBlocks = bounds.maxX() - bounds.minX() + 1;
                int zBlocks = bounds.maxZ() - bounds.minZ() + 1;
                int heightBlocks = bounds.maxY() - bounds.minY() + 1;
                if (distSqToHintArea(playerPos, minPos, xBlocks, heightBlocks, zBlocks) > HINT_RANGE_SQ) continue;

                result.add(new PortalHint(minPos, isEnter, xBlocks, heightBlocks, zBlocks, style, locKey, targetId));
            }
        }
    }

    private static boolean canShowCasinoPortal(Player player, String targetId) {
        if (com.stardew.craft.casino.CasinoAccessService.ENTRY_TARGET_ID.equals(targetId)) {
            return (com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.item.PowerSpecialItemService.CLUB_CARD_FLAG)
                    || com.stardew.craft.client.ClientPlayerDataCache.hasSpecialItem(
                    com.stardew.craft.item.PowerSpecialItemService.CLUB_CARD_ID))
                    && com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.casino.CasinoAccessService.BOUNCER_GONE_FLAG);
        }
        if (com.stardew.craft.casino.CasinoAccessService.EXIT_TARGET_ID.equals(targetId)) {
            return com.stardew.craft.casino.CasinoAccessService.isCasinoPosition(
                    player.getX(), player.getY(), player.getZ());
        }
        return true;
    }

    private static List<PortalBounds> splitConnectedBounds(Set<BlockPos> blocks) {
        List<PortalBounds> result = new ArrayList<>();
        Set<BlockPos> remaining = new LinkedHashSet<>(blocks);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        while (!remaining.isEmpty()) {
            BlockPos first = remaining.iterator().next();
            remaining.remove(first);
            queue.add(first);

            int minX = first.getX();
            int minY = first.getY();
            int minZ = first.getZ();
            int maxX = first.getX();
            int maxY = first.getY();
            int maxZ = first.getZ();

            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());

                addNeighborIfPresent(remaining, queue, pos.east());
                addNeighborIfPresent(remaining, queue, pos.west());
                addNeighborIfPresent(remaining, queue, pos.north());
                addNeighborIfPresent(remaining, queue, pos.south());
                addNeighborIfPresent(remaining, queue, pos.above());
                addNeighborIfPresent(remaining, queue, pos.below());
            }

            result.add(new PortalBounds(minX, minY, minZ, maxX, maxY, maxZ));
        }

        return result;
    }

    private static void addNeighborIfPresent(Set<BlockPos> remaining, ArrayDeque<BlockPos> queue, BlockPos neighbor) {
        if (remaining.remove(neighbor)) {
            queue.add(neighbor);
        }
    }

    private static boolean isEnterTarget(String targetId) {
        return targetId.endsWith("_enter")
                || targetId.endsWith("_entrance")
                || targetId.startsWith("farm_entry_")
                || "mine_entrance".equals(targetId)
                || "desert_bus".equals(targetId)
                || "desert_bus_return".equals(targetId)
                || "wizard_tower_overworld_enter".equals(targetId);
    }

    private static HintStyle styleForTarget(String targetId, boolean isEnter) {
        if (com.stardew.craft.world.MutantBugLairService.ENTRANCE_TARGET_ID.equals(targetId)) {
            return com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.sewer.SewerStoryFlags.KROBUS_UNSEAL)
                    ? HintStyle.ENTER
                    : HintStyle.LOCKED;
        }
        if ("quarry_entrance".equals(targetId)) {
            return com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.communitycenter.state.CCStoryFlags.CC_CRAFTS_ROOM)
                    ? HintStyle.ENTER
                    : HintStyle.LOCKED;
        }
        if ("desert_bus".equals(targetId)) {
            return com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.communitycenter.state.CCStoryFlags.CC_VAULT)
                    ? HintStyle.ENTER
                    : HintStyle.LOCKED;
        }
        if ("sewer_enter".equals(targetId)) {
            return com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.sewer.SewerStoryFlags.HAS_RUSTY_KEY)
                    ? HintStyle.ENTER
                    : HintStyle.LOCKED;
        }
        if (DesertFestivalService.EGG_SHOP_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (OldMasterCannoliService.TARGET_ID.equals(targetId)) {
            return HintStyle.WHITE;
        }
        if (com.stardew.craft.casino.CasinoAccessService.QI_COIN_MACHINE_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.QI_COIN_SHOP_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (DesertFestivalRaceService.RACE_MAN_TARGET_ID.equals(targetId)) {
            return HintStyle.RACE;
        }
        if (DesertFestivalRaceService.SHADY_GUY_TARGET_ID.equals(targetId)) {
            return HintStyle.SHADY;
        }
        if (DesertFestivalSpecialInteractionService.SCHOLAR_TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (DesertFestivalWillyFishingService.TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (DesertFestivalCookService.TARGET_ID.equals(targetId)) {
            return HintStyle.COOK;
        }
        if (FairSlingshotGameService.TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (FairFishingGameService.TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (FairFestivalService.STAR_TOKEN_SHOP_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (FestivalOfIceService.TRAVELING_MERCHANT_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (NightMarketPainterService.TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (NightMarketCoffeeService.TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (NightMarketShopService.DECORATION_BOAT_TARGET_ID.equals(targetId)
                || NightMarketShopService.MAGIC_BOAT_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (NightMarketSubmarineService.ENTRANCE_TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (NightMarketSubmarineService.EXIT_TARGET_ID.equals(targetId)) {
            return HintStyle.EXIT;
        }
        if (NightMarketMermaidService.TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID.equals(targetId)) {
            return HintStyle.SHOP;
        }
        if (FairFestivalService.FORTUNE_TELLER_TARGET_ID.equals(targetId)) {
            return HintStyle.INTERACT;
        }
        if (targetId.contains("return_overworld") || "skull_cavern_exit".equals(targetId)) {
            return HintStyle.RETURN_OVERWORLD;
        }
        return isEnter ? HintStyle.ENTER : HintStyle.EXIT;
    }

    private static String destinationKeyForTarget(String targetId) {
        return switch (targetId) {
            case "desert_bus" -> "desert";
            case "desert_bus_return" -> "pelican_town";
            case OldMasterCannoliService.TARGET_ID -> "old_master_cannoli";
            case MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID -> "";
            case DesertFestivalService.EGG_SHOP_TARGET_ID -> "desert_festival_egg_shop";
            case DesertFestivalRaceService.RACE_MAN_TARGET_ID -> "desert_festival_race_man";
            case DesertFestivalRaceService.SHADY_GUY_TARGET_ID -> "desert_festival_shady_guy";
            case DesertFestivalSpecialInteractionService.SCHOLAR_TARGET_ID -> "desert_festival_scholar";
            case DesertFestivalWillyFishingService.TARGET_ID -> "desert_festival_willy_challenge";
            case DesertFestivalCookService.TARGET_ID -> "desert_festival_cook";
            case FairSlingshotGameService.TARGET_ID -> "fair_slingshot_game";
            case FairFishingGameService.TARGET_ID -> "fair_fishing_game";
            case FairFestivalService.STAR_TOKEN_SHOP_TARGET_ID -> "fair_star_token_shop";
            case FestivalOfIceService.TRAVELING_MERCHANT_TARGET_ID -> "festival_of_ice_traveling_merchant";
            case NightMarketPainterService.TARGET_ID -> "night_market_lupini";
            case NightMarketCoffeeService.TARGET_ID -> "night_market_coffee";
            case NightMarketShopService.DECORATION_BOAT_TARGET_ID -> "night_market_decoration_boat";
            case NightMarketShopService.MAGIC_BOAT_TARGET_ID -> "night_market_magic_boat";
            case NightMarketSubmarineService.ENTRANCE_TARGET_ID -> "night_market_submarine";
            case NightMarketSubmarineService.EXIT_TARGET_ID -> "night_market_submarine_exit";
            case NightMarketMermaidService.TARGET_ID -> "night_market_mermaid_show";
            case FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID -> "fair_star_token_purchase";
            case FairFestivalService.FORTUNE_TELLER_TARGET_ID -> "fair_fortune_teller";
            case "quarry_entrance", "quarry_exit" -> "quarry";
            case "sewer_enter" -> "sewer";
            case "mutant_bug_lair_enter", "mutant_bug_lair_exit" -> "mutant_bug_lair";
            case "witch_hut_enter", "witch_hut_exit" -> "witch_hut";
            case "sewer_exit" -> "pelican_town";
            case "greenhouse_enter", "greenhouse_exit" -> "greenhouse";
            case "farm_cave_enter", "farm_cave_exit" -> "farm_cave";
            case "lewis_basement_exit" -> "lewis_basement";
            case "mine_entrance", "mine_exit" -> "mine";
            case "desert_mine_enter", "skull_cavern_exit" -> "desert_mine";
            case "oasis_enter", "oasis_exit" -> "oasis";
            case "casino_enter" -> "casino";
            case "casino_exit" -> "oasis";
            case "casino_qi_coin_machine" -> "casino_qi_coin_machine";
            case "casino_qi_coin_shop" -> "casino_qi_coin_shop";
            case "community_center_enter", "community_center_exit" -> "community_center";
            case "wizard_tower_return_overworld" -> "overworld";
            case "wizard_tower_overworld_enter" -> "wizard_tower";
            default -> normalizePortalDestinationKey(targetId);
        };
    }

    private static String normalizePortalDestinationKey(String targetId) {
        if (targetId.startsWith("farm_entry_")) {
            return "farm_" + targetId.substring("farm_entry_".length());
        }
        if (targetId.startsWith("farm_exit_")) {
            return targetId;
        }
        String key = targetId;
        if (key.endsWith("_entrance")) {
            key = key.substring(0, key.length() - "_entrance".length());
        } else if (key.endsWith("_enter")) {
            key = key.substring(0, key.length() - "_enter".length());
        } else if (key.endsWith("_exit")) {
            key = key.substring(0, key.length() - "_exit".length());
        }
        return switch (key) {
            case "pierre_house" -> "pierre_shop";
            case "carpenter_shop" -> "carpenter";
            default -> key;
        };
    }

    // ======================== fade based on distance ========================

    @SuppressWarnings("null")
    private static float calcFadeAlpha(Player player, PortalHint hint) {
        double distSq = distSqToHintArea(player.position(), hint.pos, hint.xBlocks, hint.heightBlocks, hint.zBlocks);
        if (distSq > HINT_RANGE_SQ) return 0.0f;

        double dist = Math.sqrt(distSq);
        if (dist > HINT_RANGE - 1.0) {
            return (float) ((HINT_RANGE - dist));
        }
        return 1.0f;
    }

    /**
     * Squared distance from a point to the nearest point on a hint's AABB.
     * pos is atBottomCenterOf(min block), so the box spans
     * [pos.x-0.5 .. pos.x-0.5+xBlocks] etc.
     */
    private static double distSqToHintArea(Vec3 point, Vec3 pos, int xBlocks, int heightBlocks, int zBlocks) {
        double minX = pos.x - 0.5, maxX = minX + xBlocks;
        double minY = pos.y,        maxY = minY + heightBlocks;
        double minZ = pos.z - 0.5, maxZ = minZ + zBlocks;
        double dx = Math.max(0, Math.max(minX - point.x, point.x - maxX));
        double dy = Math.max(0, Math.max(minY - point.y, point.y - maxY));
        double dz = Math.max(0, Math.max(minZ - point.z, point.z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static Component actionComponent(PortalHint hint) {
        String hintKey;
        if ("starter_chest".equals(hint.destinationKey)) {
            hintKey = CLAIM_KEY;
        } else if (SecretNote20Service.TARGET_ID.equals(hint.targetId)) {
            hintKey = SECRET_NOTE_20_DRIVER_KEY;
        } else if (com.stardew.craft.casino.CasinoAccessService.QI_COIN_MACHINE_TARGET_ID
                .equals(hint.targetId)) {
            hintKey = CASINO_QI_COIN_MACHINE_KEY;
        } else if (com.stardew.craft.casino.CasinoAccessService.QI_COIN_SHOP_TARGET_ID
                .equals(hint.targetId)) {
            hintKey = CASINO_QI_COIN_SHOP_KEY;
        } else if (hint.hintStyle == HintStyle.LOCKED) {
            hintKey = LOCKED_KEY;
        } else if (hint.hintStyle == HintStyle.SHOP) {
            hintKey = OPEN_KEY;
        } else if (hint.hintStyle == HintStyle.RACE) {
            hintKey = RACE_KEY;
        } else if (hint.hintStyle == HintStyle.SHADY) {
            hintKey = SHADY_KEY;
        } else if (DesertFestivalWillyFishingService.TARGET_ID.equals(hint.targetId)) {
            hintKey = WILLY_CHALLENGE_KEY;
        } else if (DesertFestivalCookService.TARGET_ID.equals(hint.targetId)) {
            hintKey = COOK_KEY;
        } else if (FairSlingshotGameService.TARGET_ID.equals(hint.targetId)) {
            hintKey = FAIR_SLINGSHOT_KEY;
        } else if (FairFishingGameService.TARGET_ID.equals(hint.targetId)) {
            hintKey = FAIR_FISHING_KEY;
        } else if (FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID.equals(hint.targetId)) {
            hintKey = FAIR_TOKEN_PURCHASE_KEY;
        } else if (FairFestivalService.FORTUNE_TELLER_TARGET_ID.equals(hint.targetId)) {
            hintKey = FAIR_FORTUNE_KEY;
        } else if (hint.hintStyle == HintStyle.INTERACT || hint.hintStyle == HintStyle.WHITE) {
            hintKey = INTERACT_KEY;
        } else if ("desert_bus".equals(hint.targetId)) {
            hintKey = BUY_TICKET_KEY;
        } else if (hint.hintStyle == HintStyle.RETURN_OVERWORLD || "desert_bus_return".equals(hint.targetId)) {
            hintKey = RETURN_KEY;
        } else if (hint.isEnter) {
            hintKey = ENTER_KEY;
        } else {
            hintKey = EXIT_KEY;
        }
        return Component.translatable(hintKey);
    }

    private static boolean isSingleLineHint(String targetId) {
        return DesertFestivalWillyFishingService.TARGET_ID.equals(targetId)
                || SecretNote20Service.TARGET_ID.equals(targetId)
                || MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID.equals(targetId)
                || DesertFestivalCookService.TARGET_ID.equals(targetId)
                || FairSlingshotGameService.TARGET_ID.equals(targetId)
                || FairFishingGameService.TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.QI_COIN_MACHINE_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.QI_COIN_SHOP_TARGET_ID.equals(targetId)
                || FairFestivalService.STAR_TOKEN_PURCHASE_TARGET_ID.equals(targetId)
                || FairFestivalService.FORTUNE_TELLER_TARGET_ID.equals(targetId);
    }

    private static int accentRgb(PortalHint hint) {
        return switch (hint.hintStyle) {
            case RETURN_OVERWORLD -> (RET_R << 16) | (RET_G << 8) | RET_B;
            case LOCKED -> (LOCKED_R << 16) | (LOCKED_G << 8) | LOCKED_B;
            case SHOP -> (SHOP_R << 16) | (SHOP_G << 8) | SHOP_B;
            case RACE -> (RACE_R << 16) | (RACE_G << 8) | RACE_B;
            case SHADY -> 0x50483F;
            case COOK -> (COOK_R << 16) | (COOK_G << 8) | COOK_B;
            case WHITE -> (WHITE_R << 16) | (WHITE_G << 8) | WHITE_B;
            case EXIT -> (EXIT_R << 16) | (EXIT_G << 8) | EXIT_B;
            case ENTER, INTERACT -> (ENTER_R << 16) | (ENTER_G << 8) | ENTER_B;
        };
    }

    // ======================== glow outline ========================

    @SuppressWarnings("null")
    private static void renderGlowOutline(MultiBufferSource.BufferSource buf, PoseStack ps, Vec3 cam,
                                           PortalHint hint, float alpha) {
        double x = hint.pos.x - 0.5;
        double y = hint.pos.y;
        double z = hint.pos.z - 0.5;
        AABB box = new AABB(x, y, z,
                            x + hint.xBlocks, y + hint.heightBlocks, z + hint.zBlocks).inflate(0.02);

        int r, g, b, edgeA, faceA;
        if (hint.hintStyle == HintStyle.RETURN_OVERWORLD) {
            r = RET_R; g = RET_G; b = RET_B;
            edgeA = (int) (RET_EDGE_A * alpha);
            faceA = (int) (RET_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.LOCKED) {
            r = LOCKED_R; g = LOCKED_G; b = LOCKED_B;
            edgeA = (int) (LOCKED_EDGE_A * alpha);
            faceA = (int) (LOCKED_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.SHOP) {
            r = SHOP_R; g = SHOP_G; b = SHOP_B;
            edgeA = (int) (SHOP_EDGE_A * alpha);
            faceA = (int) (SHOP_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.RACE) {
            r = RACE_R; g = RACE_G; b = RACE_B;
            edgeA = (int) (RACE_EDGE_A * alpha);
            faceA = (int) (RACE_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.SHADY) {
            r = SHADY_R; g = SHADY_G; b = SHADY_B;
            edgeA = (int) (SHADY_EDGE_A * alpha);
            faceA = (int) (SHADY_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.COOK) {
            r = COOK_R; g = COOK_G; b = COOK_B;
            edgeA = (int) (COOK_EDGE_A * alpha);
            faceA = (int) (COOK_FACE_A * alpha);
        } else if (hint.hintStyle == HintStyle.WHITE) {
            r = WHITE_R; g = WHITE_G; b = WHITE_B;
            edgeA = (int) (WHITE_EDGE_A * alpha);
            faceA = (int) (WHITE_FACE_A * alpha);
        } else if (hint.isEnter) {
            r = ENTER_R; g = ENTER_G; b = ENTER_B;
            edgeA = (int) (ENTER_EDGE_A * alpha);
            faceA = (int) (ENTER_FACE_A * alpha);
        } else {
            r = EXIT_R; g = EXIT_G; b = EXIT_B;
            edgeA = (int) (EXIT_EDGE_A * alpha);
            faceA = (int) (EXIT_FACE_A * alpha);
        }

        // X-ray layer
        VertexConsumer xr = buf.getBuffer(QUAD_XRAY);
        renderFaces(ps, xr, box, r, g, b, faceA / 2);
        renderEdgeQuads(ps, xr, box, cam, r, g, b, edgeA / 3, EDGE_HALF * 0.6f);
        buf.endBatch(QUAD_XRAY);

        // Depth-tested layer
        VertexConsumer vc = buf.getBuffer(QUAD_TYPE);
        renderFaces(ps, vc, box, r, g, b, faceA);
        renderEdgeQuads(ps, vc, box, cam, r, g, b, edgeA, EDGE_HALF);
        buf.endBatch(QUAD_TYPE);
    }

    // ======================== 3D box rendering ========================

    @SuppressWarnings("null")
    private static void renderFaces(PoseStack ps, VertexConsumer vc, AABB b, int r, int g, int bl, int a) {
        if (a <= 0) return;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        PoseStack.Pose p = ps.last();
        q(p, vc, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,bl,a);
        q(p, vc, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, r,g,bl,a);
        q(p, vc, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,bl,a);
        q(p, vc, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, r,g,bl,a);
        q(p, vc, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, r,g,bl,a);
        q(p, vc, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,bl,a);
    }

    @SuppressWarnings("null")
    private static void q(PoseStack.Pose p, VertexConsumer v,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          int r, int g, int b, int a) {
        v.addVertex(p, ax, ay, az).setColor(r, g, b, a);
        v.addVertex(p, bx, by, bz).setColor(r, g, b, a);
        v.addVertex(p, cx, cy, cz).setColor(r, g, b, a);
        v.addVertex(p, dx, dy, dz).setColor(r, g, b, a);
    }

    @SuppressWarnings("null")
    private static void renderEdgeQuads(PoseStack ps, VertexConsumer vc, AABB b, Vec3 cam,
                                         int r, int g, int bl, int a, float halfW) {
        if (a <= 0) return;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        // bottom
        edgeQuad(ps, vc, x0,y0,z0, x1,y0,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z0, x1,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z1, x0,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y0,z1, x0,y0,z0, halfW, cam, r,g,bl,a);
        // top
        edgeQuad(ps, vc, x0,y1,z0, x1,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z0, x1,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z1, x0,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y1,z1, x0,y1,z0, halfW, cam, r,g,bl,a);
        // verticals
        edgeQuad(ps, vc, x0,y0,z0, x0,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z0, x1,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z1, x1,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y0,z1, x0,y1,z1, halfW, cam, r,g,bl,a);
    }

    @SuppressWarnings("null")
    private static void edgeQuad(PoseStack ps, VertexConsumer vc,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float halfW, Vec3 cam,
                                  int r, int g, int b, int a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float ex = dx / len, ey = dy / len, ez = dz / len;

        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float cx = (float) cam.x - mx, cy = (float) cam.y - my, cz = (float) cam.z - mz;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6f) return;
        cx /= cl; cy /= cl; cz /= cl;

        float px = ey * cz - ez * cy;
        float py = ez * cx - ex * cz;
        float pz = ex * cy - ey * cx;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1e-6f) {
            if (Math.abs(ex) < 0.9f) { px = 0; py = -ez; pz = ey; }
            else                     { px = ez; py = 0; pz = -ex; }
            pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        }
        px = px / pl * halfW;
        py = py / pl * halfW;
        pz = pz / pl * halfW;

        PoseStack.Pose pose = ps.last();
        vc.addVertex(pose, ax - px, ay - py, az - pz).setColor(r, g, b, a);
        vc.addVertex(pose, ax + px, ay + py, az + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx + px, by + py, bz + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx - px, by - py, bz - pz).setColor(r, g, b, a);
    }

    // ======================== RenderType ========================

    @SuppressWarnings("null")
    private static RenderType makeQuadType(String name, boolean xray) {
        return RenderType.create(name,
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(new RenderType.TransparencyStateShard("translucent", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }, RenderSystem::disableBlend))
                .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
                .setCullState(new RenderType.CullStateShard(false))
                .setDepthTestState(xray
                    ? new RenderType.DepthTestStateShard("always", 519)
                    : RenderType.LEQUAL_DEPTH_TEST)
                .createCompositeState(false));
    }

    // ======================== data ========================

    private enum HintStyle {
        ENTER,
        EXIT,
        RETURN_OVERWORLD,
        LOCKED,
        SHOP,
        RACE,
        SHADY,
        COOK,
        WHITE,
        INTERACT
    }

    private record PortalHint(Vec3 pos, boolean isEnter, int xBlocks, int heightBlocks, int zBlocks,
                               HintStyle hintStyle, String destinationKey, String targetId) {}

    private record PortalBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
}
