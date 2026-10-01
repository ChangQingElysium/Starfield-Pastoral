package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.network.payload.CompassTargetPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 法师塔指南针 — 在主世界中指向最近的法师塔结构。
 * <p>
 * 服务端每 40 tick 为手持玩家查找最近结构，通过 {@link CompassTargetPayload} 同步给客户端。
 * 客户端通过 {@code angle} ItemProperty 驱动模型旋转。
 */
public class WizardTowerCompassItem extends Item implements IStardewItem {

    /** 主世界法师塔结构 ID */
    private static final ResourceLocation STRUCTURE_ID =
            new ResourceLocation("stardewcraft", "wizard_tower_overworld");

    // ── 客户端缓存（由网络包更新） ──
    private static volatile boolean hasTarget = false;
    private static volatile int targetX = 0;
    private static volatile int targetZ = 0;

    // ── 服务端查找节流 ──
    private static final Map<UUID, Long> lastSearchTick = new WeakHashMap<>();
    private static final Map<UUID, BlockPos> serverCache = new WeakHashMap<>();
    private static final int SEARCH_INTERVAL = 40; // 2 秒

    public WizardTowerCompassItem(Properties properties) {
        super(properties);
    }

    // ── 客户端接口 ──

    public static void setClientTarget(int x, int z) {
        targetX = x;
        targetZ = z;
        hasTarget = true;
    }

    public static void clearClientTarget() {
        hasTarget = false;
    }

    public static boolean hasClientTarget() {
        return hasTarget;
    }

    public static int getClientTargetX() {
        return targetX;
    }

    public static int getClientTargetZ() {
        return targetZ;
    }

    // ── 服务端结构查找 ──

    /**
     * 由服务端 tick 事件调用，为手持指南针的玩家查找最近的法师塔。
     */
    @SuppressWarnings("null")
    public static void serverTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        // 只在主世界生效
        if (serverLevel.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            // 非主世界：清除缓存，发送"未找到"
            if (serverCache.remove(player.getUUID()) != null || lastSearchTick.containsKey(player.getUUID())) {
                lastSearchTick.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, new CompassTargetPayload(false, 0, 0));
            }
            return;
        }

        long tick = serverLevel.getGameTime();
        Long last = lastSearchTick.get(player.getUUID());
        if (last != null && tick - last < SEARCH_INTERVAL) return;
        lastSearchTick.put(player.getUUID(), tick);

        // 查找最近法师塔结构
        var registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structureHolder = registry.getHolder(STRUCTURE_ID).orElse(null);
        if (structureHolder == null) {
            PacketDistributor.sendToPlayer(player, new CompassTargetPayload(false, 0, 0));
            return;
        }

        BlockPos playerPos = player.blockPosition();
        @Nullable
        var pair = serverLevel.getChunkSource().getGenerator()
                .findNearestMapStructure(serverLevel, HolderSet.direct(structureHolder), playerPos, 100, false);
        if (pair != null) {
            BlockPos found = pair.getFirst();
            serverCache.put(player.getUUID(), found);
            PacketDistributor.sendToPlayer(player, new CompassTargetPayload(true, found.getX(), found.getZ()));
        } else {
            serverCache.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new CompassTargetPayload(false, 0, 0));
        }
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.tool";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1; // 不可出售
    }

    @Override
    @SuppressWarnings("null")
    public boolean isFoil(ItemStack stack) {
        return true; // 附魔光泽效果
    }
}
