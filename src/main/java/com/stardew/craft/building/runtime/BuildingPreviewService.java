package com.stardew.craft.building.runtime;

import com.stardew.craft.network.payload.BuildingPreviewPayload;
import com.stardew.craft.network.payload.BuildingPreviewRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

public final class BuildingPreviewService {
    private static final Map<UUID, Long> LAST_REQUEST = new java.util.HashMap<>();
    private static final Map<String, Long> SENT_TEMPLATE = new java.util.HashMap<>();
    private BuildingPreviewService() {}
    public static void forget(UUID player) { LAST_REQUEST.remove(player); SENT_TEMPLATE.keySet().removeIf(key -> key.startsWith(player.toString())); }
    public static void sendTemplate(ServerPlayer player, net.minecraft.resources.ResourceLocation family, int tier) {
        if (SENT_TEMPLATE.getOrDefault(player.getUUID() + ":" + family + ":" + tier, -1L) == PrefabDefinitions.generation()) return;
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.BuildingTemplatePreviewPayload(PrefabDefinitions.previewTag(player.serverLevel(), family, tier)));
        SENT_TEMPLATE.put(player.getUUID() + ":" + family + ":" + tier, PrefabDefinitions.generation());
    }
    public static void clear() { LAST_REQUEST.clear(); SENT_TEMPLATE.clear(); BuildingMovePreview.clear(); }
    public static void request(ServerPlayer player, BuildingPreviewRequestPayload request) {
        long now = player.serverLevel().getGameTime();
        if (now - LAST_REQUEST.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < 5) return;
        LAST_REQUEST.put(player.getUUID(), now);
        if (request.facing().getAxis().isVertical()) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BuildingManagerItem) && !(stack.getItem() instanceof BuildingBlueprintItem)) stack = player.getOffhandItem();
        net.minecraft.resources.ResourceLocation family;
        if (request.self() && stack.getItem() instanceof BuildingManagerItem item) family = item.family();
        else if (!request.self() && stack.getItem() instanceof BuildingBlueprintItem item) family = item.family();
        else return;
        var moving = BuildingBlueprintItem.isMove(stack) ? BuildingBlueprintItem.moving(player.serverLevel(), stack) : null;
        if (BuildingBlueprintItem.isMove(stack) && (moving == null || !moving.family().equals(family) || moving.phase() != BuildingRecord.Phase.READY || !BuildingService.canManage(player, moving))) return;
        int tier = moving == null ? 1 : moving.tier();
        if (!request.self()) {
            if (!PrefabDefinitions.available(family) || request.facing() != BuildingBlueprintItem.facing(stack,player)) return;
            var pin = BuildingBlueprintItem.pinned(stack, player.level());
            if (pin != null) {
                if (!pin.equals(request.anchor()) || !BuildingBlueprintItem.aimsAtPinned(player, stack, family)) return;
            } else {
                var hit = BuildingBlueprintItem.target(player);
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP
                        || !BuildingBlueprintItem.targetAnchor(stack,hit.getBlockPos(),request.facing()).equals(request.anchor())) return;
            }
        } else if (request.anchor().distToCenterSqr(player.getEyePosition()) > Math.pow(player.blockInteractionRange() + 2, 2)) return;
        if (moving != null) BuildingMovePreview.send(player,moving); else if (!request.self()) sendTemplate(player, family, tier);
        var probe = BuildingPlacementService.probe(player.serverLevel(), player, request.anchor(), request.facing(), request.self() || moving != null && moving.mode() == BuildingRecord.Mode.SELF_BUILT, family, moving);
        String issue = probe.issue();
        if (probe.valid() && !request.self() && moving == null) {
            var permit = BuildingBlueprintItem.permit(stack);
            var farms = com.stardew.craft.farm.FarmInstanceRegistry.get(player.serverLevel().getServer());
            if (permit == null || !BuildingWorldData.get(player.serverLevel().getServer())
                    .permitsPlacement(permit, probe.farm().getInstanceId(), family, player.getUUID(), farms)) issue = "permit";
        }
        var outline=PrefabDefinitions.maxTier(family)==1?probe.claim():probe.structure();
        PacketDistributor.sendToPlayer(player, new BuildingPreviewPayload(request.anchor(), request.facing(), request.self(), request.sequence(),
                issue, probe.problem(), probe.claim().min(), probe.claim().maxExclusive(),
                outline.min(), outline.maxExclusive(), probe.manager(), family, tier));
    }
}
