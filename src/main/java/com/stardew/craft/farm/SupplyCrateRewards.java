package com.stardew.craft.farm;

import com.stardew.craft.data.VanillaObjectCatalog;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** SDV Object.performToolAction, SupplyCrate (922–924). Counts are inclusive. */
public final class SupplyCrateRewards {
    private SupplyCrateRewards() {}

    public record Reward(List<String> alternatives, int min, int max) {}
    private static Reward item(String id, int min, int max) { return new Reward(List.of(id), min, max); }
    private static List<Reward> one(String id, int min, int max) { return List.of(item(id, min, max)); }

    // Each list entry is one equally likely outcome. The repeated cherry-bomb entry is intentional.
    private static final List<List<List<Reward>>> TABLES = List.of(
            List.of(one("770", 3, 5), one("371", 5, 7), one("535", 2, 4), one("241", 1, 2),
                    one("395", 1, 2), one("286", 3, 5), one("286", 3, 5)),
            List.of(one("770", 3, 5), one("371", 5, 7), one("749", 2, 4), one("253", 1, 2),
                    one("237", 1, 2), one("246", 4, 7), one("247", 2, 4), one("245", 4, 7),
                    one("287", 3, 5), one("MixedFlowerSeeds", 4, 5)),
            List.of(one("770", 3, 5), one("920", 5, 7), one("749", 2, 4), one("253", 2, 3),
                    List.of(new Reward(List.of("904", "905"), 1, 2)),
                    List.of(item("246", 4, 7), item("247", 2, 4), item("245", 4, 7)),
                    one("275", 2, 2), one("288", 3, 5), one("MixedFlowerSeeds", 5, 5)));

    public static List<List<Reward>> table(int tier) { return TABLES.get(com.stardew.craft.port.PortJava.clamp(tier, 0, 2)); }

    /** TODO farmhouse-upgrades: replace this date fallback with the breaking farmer's house level. */
    public static int tierForDate(int year, int season) {
        return year >= 2 ? 2 : season >= 2 ? 1 : 0;
    }

    public static List<ItemStack> roll(int tier, RandomSource random) {
        var outcomes = table(tier);
        var outcome = outcomes.get(random.nextInt(outcomes.size()));
        List<ItemStack> drops = new ArrayList<>();
        for (var reward : outcome) {
            var keys = reward.alternatives();
            String key = keys.get(keys.size() == 1 ? 0 : random.nextInt(keys.size()));
            ItemStack stack = VanillaObjectCatalog.stackFor(VanillaObjectCatalog.entryByKey(key));
            if (stack.isEmpty()) throw new IllegalStateException("Unresolved supply crate reward: " + key);
            stack.setCount(reward.min() + random.nextInt(reward.max() - reward.min() + 1));
            drops.add(stack);
        }
        return drops;
    }
}
