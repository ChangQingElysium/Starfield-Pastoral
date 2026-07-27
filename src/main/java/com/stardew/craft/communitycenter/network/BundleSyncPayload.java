package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * S→C: Full sync of Community Center progress + bundle definitions to the client.
 * <p>
 * Wire format:
 *   1) Bundle definitions: defCount, then for each: bundleId, areaId, internalName, displayNameKey,
 *      rewardString, color, requiredCount, ingredientCount, then for each ingredient: itemId, sdvId, category, stack, quality
 *   2) Area definitions: areaCount, then for each: areaId, name, displayNameKey
 *   3) Progress: bundleCount, then for each: bundleId, slotCount, slotBits
 *   4) 7 area completion booleans
 *   5) Rewards: rewardCount, then bundleId for each claimable
 *   6) canReadJunimoText boolean
 */
@SuppressWarnings("null")
public record BundleSyncPayload(
        Map<Integer, boolean[]> bundleSlots,
        boolean[] areasComplete,
        Map<Integer, Boolean> bundleRewards,
        boolean canReadJunimoText,
        java.util.List<BundleDefinition> definitions,
        Map<Integer, String> areaNames,
        Map<Integer, String> areaDisplayKeys
) implements CustomPacketPayload {

    public static final Type<BundleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_sync")
    );

    public static final StreamCodec<ByteBuf, BundleSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BundleSyncPayload decode(ByteBuf buf) {
            // 1) Bundle definitions
            int defCount = ByteBufCodecs.VAR_INT.decode(buf);
            java.util.List<BundleDefinition> defs = new java.util.ArrayList<>(defCount);
            for (int i = 0; i < defCount; i++) {
                int bId = ByteBufCodecs.VAR_INT.decode(buf);
                int aId = ByteBufCodecs.VAR_INT.decode(buf);
                String iName = ByteBufCodecs.STRING_UTF8.decode(buf);
                String dName = ByteBufCodecs.STRING_UTF8.decode(buf);
                String reward = ByteBufCodecs.STRING_UTF8.decode(buf);
                int color = ByteBufCodecs.VAR_INT.decode(buf);
                int reqCount = ByteBufCodecs.VAR_INT.decode(buf);
                int ingCount = ByteBufCodecs.VAR_INT.decode(buf);
                java.util.List<com.stardew.craft.communitycenter.data.BundleIngredient> ings = new java.util.ArrayList<>(ingCount);
                for (int j = 0; j < ingCount; j++) {
                    boolean hasItemId = buf.readBoolean();
                    String itemId = hasItemId ? ByteBufCodecs.STRING_UTF8.decode(buf) : null;
                    String sdvId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int cat = ByteBufCodecs.VAR_INT.decode(buf);
                    int stack = ByteBufCodecs.VAR_INT.decode(buf);
                    int quality = ByteBufCodecs.VAR_INT.decode(buf);
                    ings.add(new com.stardew.craft.communitycenter.data.BundleIngredient(itemId, sdvId, cat, stack, quality));
                }
                defs.add(new BundleDefinition(bId, aId, iName, dName, reward,
                        java.util.Collections.unmodifiableList(ings), color, reqCount));
            }

            // 2) Area definitions
            int areaCount = ByteBufCodecs.VAR_INT.decode(buf);
            Map<Integer, String> areaNms = new HashMap<>(areaCount);
            Map<Integer, String> areaDks = new HashMap<>(areaCount);
            for (int i = 0; i < areaCount; i++) {
                int aId = ByteBufCodecs.VAR_INT.decode(buf);
                areaNms.put(aId, ByteBufCodecs.STRING_UTF8.decode(buf));
                areaDks.put(aId, ByteBufCodecs.STRING_UTF8.decode(buf));
            }

            // 3) Progress
            int bundleCount = ByteBufCodecs.VAR_INT.decode(buf);
            Map<Integer, boolean[]> slots = new HashMap<>();
            for (int i = 0; i < bundleCount; i++) {
                int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                int slotCount = ByteBufCodecs.VAR_INT.decode(buf);
                boolean[] slotArr = new boolean[slotCount];
                for (int s = 0; s < slotCount; s++) {
                    slotArr[s] = buf.readBoolean();
                }
                slots.put(bundleId, slotArr);
            }

            // 4) Area completion
            boolean[] areas = new boolean[7];
            for (int i = 0; i < 7; i++) {
                areas[i] = buf.readBoolean();
            }

            // 5) Rewards
            int rewardCount = ByteBufCodecs.VAR_INT.decode(buf);
            Map<Integer, Boolean> rewards = new HashMap<>();
            for (int i = 0; i < rewardCount; i++) {
                int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                rewards.put(bundleId, true);
            }

            // 6) canRead
            boolean canRead = buf.readBoolean();
            return new BundleSyncPayload(slots, areas, rewards, canRead, defs, areaNms, areaDks);
        }

        @Override
        public void encode(ByteBuf buf, BundleSyncPayload payload) {
            // 1) Bundle definitions
            ByteBufCodecs.VAR_INT.encode(buf, payload.definitions.size());
            for (BundleDefinition def : payload.definitions) {
                ByteBufCodecs.VAR_INT.encode(buf, def.bundleId());
                ByteBufCodecs.VAR_INT.encode(buf, def.areaId());
                ByteBufCodecs.STRING_UTF8.encode(buf, def.internalName());
                ByteBufCodecs.STRING_UTF8.encode(buf, def.displayNameKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, def.rewardString());
                ByteBufCodecs.VAR_INT.encode(buf, def.color());
                ByteBufCodecs.VAR_INT.encode(buf, def.requiredCount());
                ByteBufCodecs.VAR_INT.encode(buf, def.ingredients().size());
                for (var ing : def.ingredients()) {
                    buf.writeBoolean(ing.itemId() != null);
                    if (ing.itemId() != null) ByteBufCodecs.STRING_UTF8.encode(buf, ing.itemId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ing.sdvId());
                    ByteBufCodecs.VAR_INT.encode(buf, ing.category());
                    ByteBufCodecs.VAR_INT.encode(buf, ing.stack());
                    ByteBufCodecs.VAR_INT.encode(buf, ing.quality());
                }
            }

            // 2) Area definitions
            ByteBufCodecs.VAR_INT.encode(buf, payload.areaNames.size());
            for (var entry : payload.areaNames.entrySet()) {
                ByteBufCodecs.VAR_INT.encode(buf, entry.getKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.areaDisplayKeys.getOrDefault(entry.getKey(), ""));
            }

            // 3) Progress
            ByteBufCodecs.VAR_INT.encode(buf, payload.bundleSlots.size());
            for (var entry : payload.bundleSlots.entrySet()) {
                ByteBufCodecs.VAR_INT.encode(buf, entry.getKey());
                boolean[] slots = entry.getValue();
                ByteBufCodecs.VAR_INT.encode(buf, slots.length);
                for (boolean slot : slots) {
                    buf.writeBoolean(slot);
                }
            }

            // 4) Area completion
            for (int i = 0; i < 7; i++) {
                buf.writeBoolean(i < payload.areasComplete.length && payload.areasComplete[i]);
            }

            // 5) Rewards
            int rewardCount = 0;
            for (var entry : payload.bundleRewards.entrySet()) {
                if (entry.getValue()) rewardCount++;
            }
            ByteBufCodecs.VAR_INT.encode(buf, rewardCount);
            for (var entry : payload.bundleRewards.entrySet()) {
                if (entry.getValue()) {
                    ByteBufCodecs.VAR_INT.encode(buf, entry.getKey());
                }
            }

            // 6) canRead
            buf.writeBoolean(payload.canReadJunimoText);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler: store the synced data in a client-side cache.
     */
    public static void handle(BundleSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 先填充 BundleDataManager（专用服务器客户端需要这步）
            if (!payload.definitions.isEmpty()) {
                BundleDataManager.applyFromNetwork(payload.definitions, payload.areaNames, payload.areaDisplayKeys);
            }
            BundleClientData.INSTANCE.update(payload.bundleSlots, payload.areasComplete, payload.bundleRewards, payload.canReadJunimoText);
        });
    }

    /**
     * Send full sync from server to the given player.
     */
    public static void sendFullSync(ServerPlayer player) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = player.getUUID();
        java.util.Collection<BundleDefinition> resolvedDefinitions =
                StardewCommunityCenterVariantRegistry.all(uuid);

        Map<Integer, boolean[]> allSlots = data.getBundleSlotsView(uuid);
        Map<Integer, boolean[]> slots = new HashMap<>();
        for (BundleDefinition def : resolvedDefinitions) {
            boolean[] bundleSlots = allSlots.get(def.bundleId());
            if (bundleSlots != null) {
                slots.put(def.bundleId(), bundleSlots.clone());
            }
        }

        boolean[] areas = new boolean[7];
        for (int i = 0; i < 7; i++) {
            areas[i] = data.isAreaComplete(uuid, i);
        }

        Map<Integer, Boolean> rewards = new HashMap<>();
        for (BundleDefinition def : resolvedDefinitions) {
            if (data.isRewardAvailable(uuid, def.bundleId())) {
                rewards.put(def.bundleId(), true);
            }
        }

        boolean canRead = CCStoryFlags.canReadJunimoText(player);

        // 获取该玩家的 CC 原点
        net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.InteriorSubspaceManager.CC_ORIGIN;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            ccOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(sl).getCCOrigin(uuid);
        }

        // 收集 bundle 定义 + area 名称（专用服务器客户端需要）
        java.util.List<BundleDefinition> defs =
                new java.util.ArrayList<>(resolvedDefinitions);
        Map<Integer, String> aNames = new HashMap<>();
        Map<Integer, String> aDKeys = new HashMap<>();
        for (int i = 0; i <= 6; i++) {
            String n = BundleDataManager.getAreaName(i);
            String dk = BundleDataManager.getAreaDisplayNameKey(i);
            if (n != null) aNames.put(i, n);
            if (dk != null) aDKeys.put(i, dk);
        }

        PacketDistributor.sendToPlayer(player, new BundleSyncPayload(slots, areas, rewards, canRead, defs, aNames, aDKeys));

        // 同步 CC 原点到客户端（通过 BundleClientData）
        final net.minecraft.core.BlockPos origin = ccOrigin;
        // 用一个额外的轻量 payload 或直接在 handle 中设置
        // 这里直接通过在 BundleSyncPayload handle 后设置 — 需要额外的同步机制
        // 暂时方案：让 sendFullSync 后发送一个 CcOriginPayload
        // TODO: 可以在 BundleSyncPayload record 添加 ccOrigin 字段来优化
        PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.communitycenter.network.CcOriginPayload(origin));
    }
}
