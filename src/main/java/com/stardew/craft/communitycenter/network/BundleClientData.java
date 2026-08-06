package com.stardew.craft.communitycenter.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for Community Center progress data.
 * Updated by BundleSyncPayload from the server.
 * Read by BundleScreen for rendering.
 */
public final class BundleClientData {

    public static final BundleClientData INSTANCE = new BundleClientData();

    private final Map<Integer, boolean[]> bundleSlots = new HashMap<>();
    private final boolean[] areasComplete = new boolean[7];
    private final Map<Integer, Boolean> bundleRewards = new HashMap<>();
    private boolean canReadJunimoText = false;
    private int version = 0;
    /** 玩家的 CC 内部原点，由服务端同步 */
    private net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.InteriorSubspaceManager.CC_ORIGIN;

    /**
     * 星盘显示用的星星数，由 StarPlacedPayload 驱动。
     * 和 areasComplete 分离，这样区域完成时不会立刻改变星盘纹理，
     * 要等 Junimo 走到星盘放完星之后才递增。
     */
    private int displayStarCount = 0;
    private boolean displayStarsInitialized = false;
    private final boolean[] displayedStarAreas = new boolean[6];
    private boolean refurbishedInterior = false;

    private BundleClientData() {}

    public void update(Map<Integer, boolean[]> newSlots, boolean[] newAreas, Map<Integer, Boolean> newRewards, boolean canRead, net.minecraft.core.BlockPos origin) {
        update(newSlots, newAreas, newRewards, canRead);
        if (origin != null) this.ccOrigin = origin;
    }

    public void update(Map<Integer, boolean[]> newSlots, boolean[] newAreas, Map<Integer, Boolean> newRewards, boolean canRead) {
        boolean firstProgressSync = !displayStarsInitialized;
        bundleSlots.clear();
        bundleSlots.putAll(newSlots);

        System.arraycopy(newAreas, 0, areasComplete, 0, Math.min(newAreas.length, 7));

        // 首次同步（登录）：displayStarCount 追平到实际完成数
        // 后续同步（存入物品）：不动 displayStarCount，等 Junimo 放星的包来递增
        int completedAreaCount = 0;
        for (int i = 0; i < displayedStarAreas.length; i++) {
            if (areasComplete[i]) completedAreaCount++;
        }
        if (!displayStarsInitialized || displayStarCount > completedAreaCount) {
            displayStarsInitialized = true;
            for (int i = 0; i < displayedStarAreas.length; i++) {
                displayedStarAreas[i] = areasComplete[i];
            }
            displayStarCount = completedAreaCount;
        }
        boolean allMainAreasComplete = areAllMainAreasComplete();
        if (firstProgressSync || !allMainAreasComplete) {
            // Existing completed saves start refurbished immediately. During live final-area
            // completion, wait for the restore flash before changing location ambience.
            refurbishedInterior = allMainAreasComplete;
        }

        bundleRewards.clear();
        bundleRewards.putAll(newRewards);

        this.canReadJunimoText = canRead;
        version++;
    }

    public boolean isSlotComplete(int bundleId, int slotIndex) {
        boolean[] slots = bundleSlots.get(bundleId);
        if (slots == null || slotIndex < 0 || slotIndex >= slots.length) return false;
        return slots[slotIndex];
    }

    public boolean isBundleComplete(int bundleId) {
        boolean[] slots = bundleSlots.get(bundleId);
        if (slots == null) return false;
        for (boolean s : slots) {
            if (!s) return false;
        }
        return true;
    }

    public boolean isAreaComplete(int areaId) {
        if (areaId < 0 || areaId >= 7) return false;
        return areasComplete[areaId];
    }

    /** The six vanilla Community Center areas which control the refurbished interior state. */
    public boolean areAllMainAreasComplete() {
        for (int areaId = 0; areaId <= 5; areaId++) {
            if (!areasComplete[areaId]) {
                return false;
            }
        }
        return true;
    }

    public boolean isInteriorRefurbished() {
        return refurbishedInterior;
    }

    /** Called when the server's restore phase reaches the actual schematic replacement. */
    public void markInteriorRefurbishedIfComplete() {
        if (areAllMainAreasComplete()) {
            refurbishedInterior = true;
        }
    }

    public boolean isRewardAvailable(int bundleId) {
        return bundleRewards.getOrDefault(bundleId, false);
    }

    public boolean hasAnyRewardForArea(int areaId) {
        // Check if any bundle in this area has a claimable reward
        for (var entry : bundleRewards.entrySet()) {
            if (entry.getValue()) {
                // We'd need area info here; BundleDataManager provides it
                var def = com.stardew.craft.communitycenter.data.BundleDataManager.getBundle(entry.getKey());
                if (def != null && def.areaId() == areaId) {
                    return true;
                }
            }
        }
        return false;
    }

    public int countFilledSlots(int bundleId) {
        boolean[] slots = bundleSlots.get(bundleId);
        if (slots == null) return 0;
        int count = 0;
        for (boolean s : slots) {
            if (s) count++;
        }
        return count;
    }

    public boolean canReadJunimoText() {
        return canReadJunimoText;
    }

    public int getVersion() {
        return version;
    }

    /** 星盘渲染器使用: 获取当前应显示的星星数 */
    public int getDisplayStarCount() {
        return displayStarCount;
    }

    /** 获取玩家的 CC 内部原点 (用于客户端渲染) */
    public net.minecraft.core.BlockPos getCCOrigin() {
        return ccOrigin;
    }

    /** 设置玩家的 CC 内部原点 (由 CcOriginPayload 调用) */
    public void setCCOrigin(net.minecraft.core.BlockPos origin) {
        if (origin != null) this.ccOrigin = origin;
    }

    /** Junimo 放完一颗星后调用；按区域去重，重复网络包不会制造假星。 */
    public void markDisplayStarArea(int areaId) {
        if (areaId < 0 || areaId >= displayedStarAreas.length || displayedStarAreas[areaId]) {
            return;
        }
        displayedStarAreas[areaId] = true;
        displayStarCount = Math.min(displayedStarAreas.length, displayStarCount + 1);
    }

    public void clear() {
        bundleSlots.clear();
        bundleRewards.clear();
        for (int i = 0; i < 7; i++) areasComplete[i] = false;
        for (int i = 0; i < displayedStarAreas.length; i++) displayedStarAreas[i] = false;
        displayStarCount = 0;
        displayStarsInitialized = false;
        refurbishedInterior = false;
        ccOrigin = com.stardew.craft.interior.InteriorSubspaceManager.CC_ORIGIN;
        version++;
    }
}
