package com.stardew.craft.shop;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.*;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reloadable tool conversions. A pending order stores its result so later reloads cannot rewrite it. */
public final class ToolUpgradeData {
    private ToolUpgradeData() {}
    private static volatile Map<ResourceLocation, Definition> definitions = Map.of();
    public static Map<ResourceLocation, Definition> snapshot() { return definitions; }
    public record Definition(ResourceLocation family, int tier, Optional<ResourceLocation> input, ResourceLocation output,
            int price, ResourceLocation material, int materialCount, int days, boolean copyComponents,
            boolean resetDamage, int trashCanLevel, List<StardewCondition> availableWhen) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("family").forGetter(Definition::family),
                Codec.intRange(1, 100).fieldOf("tier").forGetter(Definition::tier),
                ResourceLocation.CODEC.optionalFieldOf("input").forGetter(Definition::input),
                ResourceLocation.CODEC.fieldOf("output").forGetter(Definition::output),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("price").forGetter(Definition::price),
                ResourceLocation.CODEC.fieldOf("material").forGetter(Definition::material),
                Codec.intRange(0, 4096).optionalFieldOf("material_count", 5).forGetter(Definition::materialCount),
                Codec.intRange(0, 365).optionalFieldOf("days", 2).forGetter(Definition::days),
                Codec.BOOL.optionalFieldOf("copy_components", true).forGetter(Definition::copyComponents),
                Codec.BOOL.optionalFieldOf("reset_damage", true).forGetter(Definition::resetDamage),
                Codec.intRange(0, 4).optionalFieldOf("trash_can_level", 0).forGetter(Definition::trashCanLevel),
                StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of()).forGetter(Definition::availableWhen)
        ).apply(i, Definition::new));
        public Definition { availableWhen = List.copyOf(availableWhen); }
        public ShopItemEntry shopEntry() {
            return new ShopItemEntry(output.toString(), "", "", price, 1, material.toString(), materialCount,
                    Set.of(), 1, 0, null, -1, 0, 1);
        }
        public boolean available(ServerPlayer player) {
            return availableWhen.stream().allMatch(c -> StardewConditions.test(c, StardewConditionContext.forPlayer(player)).result().orElse(false));
        }
        public int inputSlot(ServerPlayer player) {
            if (input.isEmpty()) return -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                if (BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(i).getItem()).equals(input.get())) return i;
            return -1;
        }
        public ItemStack result(ItemStack old) {
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(output));
            if (copyComponents && !old.isEmpty()) result.applyComponents(old.getComponentsPatch());
            if (resetDamage && result.isDamageableItem()) result.setDamageValue(0);
            return result;
        }
    }
    public static List<Map.Entry<ResourceLocation, Definition>> offers(ServerPlayer player) {
        var current = definitions;
        var highest = new HashMap<ResourceLocation, Integer>();
        for (var d : current.values()) {
            if (d.inputSlot(player) >= 0) highest.merge(d.family(), d.tier() - 1, Math::max);
            if (player.getInventory().countItem(BuiltInRegistries.ITEM.get(d.output())) > 0)
                highest.merge(d.family(), d.tier(), Math::max);
        }
        return current.entrySet().stream().filter(e -> {
            var d = e.getValue();
            return d.available(player) && (d.trashCanLevel() > 0
                    ? PlayerDataManager.getPlayerData(player).getTrashCanLevel() == d.trashCanLevel() - 1
                    : d.inputSlot(player) >= 0 && highest.getOrDefault(d.family(), -1) == d.tier() - 1);
        }).toList();
    }
    public static boolean hasTool(ServerPlayer player) {
        return definitions.values().stream().anyMatch(d -> d.inputSlot(player) >= 0
                || player.getInventory().countItem(BuiltInRegistries.ITEM.get(d.output())) > 0);
    }
    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() { super(new Gson(), "tool_upgrades"); }
        @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
            var next = new LinkedHashMap<ResourceLocation, Definition>();
            try {
                var outputs = new HashSet<ResourceLocation>();
                for (var e : json.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList()) {
                    var d = Definition.CODEC.parse(JsonOps.INSTANCE, e.getValue()).getOrThrow();
                    if (d.input().isEmpty() && d.trashCanLevel() == 0) throw new IllegalArgumentException("Missing upgrade input: " + e.getKey());
                    if (d.input().isPresent() && !BuiltInRegistries.ITEM.containsKey(d.input().get())
                            || !BuiltInRegistries.ITEM.containsKey(d.output()) || !BuiltInRegistries.ITEM.containsKey(d.material()))
                        throw new IllegalArgumentException("Unknown upgrade item: " + e.getKey());
                    if (d.output().equals(new ResourceLocation("minecraft:air"))
                            || d.input().filter(id -> id.equals(new ResourceLocation("minecraft:air"))).isPresent())
                        throw new IllegalArgumentException("Upgrade input/output cannot be air: " + e.getKey());
                    if (!outputs.add(d.output())) throw new IllegalArgumentException("Duplicate upgrade output: " + d.output());
                    if (d.trashCanLevel() > 0 && com.stardew.craft.inventory.TrashCanTier.fromUpgradeItemId(d.output().toString())
                            .map(t -> t.level() != d.trashCanLevel()).orElse(true)) throw new IllegalArgumentException("Trash-can output does not match target level");
                    next.put(e.getKey(), d);
                }
                definitions = Collections.unmodifiableMap(next);
            } catch (RuntimeException ex) {
                com.stardew.craft.StardewCraft.LOGGER.error("[Tool upgrade] Rejected reload; keeping previous definitions", ex);
            }
        }
    }
}
