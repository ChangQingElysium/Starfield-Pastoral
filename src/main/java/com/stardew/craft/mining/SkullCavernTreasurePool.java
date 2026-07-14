package com.stardew.craft.mining;

import com.stardew.craft.api.v1.world.StardewWorldLootPools;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.world.data.WorldLootPoolData;

import java.util.List;

/**
 * 骷髅矿井宝箱奖励池 (SDV MineShaft.getTreasureRoomItem())
 * <p>
 * 26 选 1 均匀分布，与原版 1:1 对齐。
 * 原版中暂未移植到本模组的物品（家具、帽子、饰品盒等）回滚到 {@code Omni Geode ×5}
 * 以保留权重结构——这是 SDV default 分支本身的掉落。
 */
public final class SkullCavernTreasurePool {

    private SkullCavernTreasurePool() {}

    /**
     * 从 26 选 1 池中摇出一份奖励。
     * @return 非空 ItemStack；若某 case 对应的物品未注册，回滚到 Omni Geode ×5。
     */
    public static ItemStack roll(RandomSource random) {
        return roll(random, null);
    }

    public static ItemStack roll(RandomSource random, ServerPlayer player) {
        if (player != null
                && PlayerDataManager.getPlayerData(player).hasMastery(SkillType.FARMING)
                && random.nextDouble() < 0.02) {
            return stack(ModItems.GOLDEN_ANIMAL_CRACKER, 1);
        }
        if (StardewTrinketItem.canSpawnFor(player) && random.nextDouble() < 0.045) {
            ItemStack trinket = StardewTrinketItem.createRandomNaturalTrinket(random, player);
            if (!trinket.isEmpty()) {
                return trinket;
            }
        }
        if (player == null) {
            return omniGeodeFallback();
        }
        List<ItemStack> rewards = WorldLootPoolData.resolve(
                StardewWorldLootPools.SKULL_CAVERN_TREASURE,
                "default",
                player.serverLevel(),
                player,
                random);
        return rewards.isEmpty() ? omniGeodeFallback() : rewards.getFirst();
    }

    private static ItemStack stack(java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike> supplier,
                                   int count) {
        return new ItemStack(supplier.get(), count);
    }

    private static ItemStack omniGeodeFallback() {
        return new ItemStack(ModItems.OMNI_GEODE.get(), 5);
    }
}
