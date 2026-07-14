package com.stardew.craft.quest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Datapack definition for daily help-wanted probabilities and candidate pools. */
public record DailyQuestPoolDefinition(
        double resourceChance,
        double monsterChance,
        double emptyChance,
        double fishingChance,
        double socialChance,
        int durationDays,
        List<String> deliveryNpcs,
        List<List<ResourceLocation>> deliveryItemsBySeason,
        List<List<ResourceLocation>> fishBySeason,
        List<ResourceEntry> resources,
        List<MonsterEntry> monsters
) {
    private static final Codec<List<ResourceLocation>> ITEM_LIST = ResourceLocation.CODEC.listOf();
    private static final Codec<List<List<ResourceLocation>>> SEASONAL_ITEMS = ITEM_LIST.listOf();
    private static final Codec<DailyQuestPoolDefinition> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("resource_chance", 0.08).forGetter(DailyQuestPoolDefinition::resourceChance),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("monster_chance", 0.20).forGetter(DailyQuestPoolDefinition::monsterChance),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("empty_chance", 0.50).forGetter(DailyQuestPoolDefinition::emptyChance),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("fishing_chance", 0.60).forGetter(DailyQuestPoolDefinition::fishingChance),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("social_chance", 0.66).forGetter(DailyQuestPoolDefinition::socialChance),
            Codec.intRange(1, 28).optionalFieldOf("duration_days", 2).forGetter(DailyQuestPoolDefinition::durationDays),
            Codec.STRING.listOf().fieldOf("delivery_npcs").forGetter(DailyQuestPoolDefinition::deliveryNpcs),
            SEASONAL_ITEMS.fieldOf("delivery_items_by_season").forGetter(DailyQuestPoolDefinition::deliveryItemsBySeason),
            SEASONAL_ITEMS.fieldOf("fish_by_season").forGetter(DailyQuestPoolDefinition::fishBySeason),
            ResourceEntry.CODEC.listOf().fieldOf("resources").forGetter(DailyQuestPoolDefinition::resources),
            MonsterEntry.CODEC.listOf().fieldOf("monsters").forGetter(DailyQuestPoolDefinition::monsters)
    ).apply(instance, DailyQuestPoolDefinition::new));

    public static final Codec<DailyQuestPoolDefinition> CODEC = BASE_CODEC.validate(DailyQuestPoolDefinition::validate);

    public DailyQuestPoolDefinition {
        deliveryNpcs = List.copyOf(deliveryNpcs);
        deliveryItemsBySeason = deliveryItemsBySeason.stream().map(List::copyOf).toList();
        fishBySeason = fishBySeason.stream().map(List::copyOf).toList();
        resources = List.copyOf(resources);
        monsters = List.copyOf(monsters);
    }

    private static DataResult<DailyQuestPoolDefinition> validate(DailyQuestPoolDefinition value) {
        if (!(value.resourceChance <= value.monsterChance
                && value.monsterChance <= value.emptyChance
                && value.emptyChance <= value.fishingChance
                && value.fishingChance <= value.socialChance)) {
            return DataResult.error(() -> "Daily quest chance thresholds must be ascending");
        }
        if (value.deliveryNpcs.isEmpty() || value.resources.isEmpty() || value.monsters.isEmpty()) {
            return DataResult.error(() -> "Daily quest NPC, resource and monster pools must not be empty");
        }
        if (value.deliveryItemsBySeason.size() != 4 || value.fishBySeason.size() != 4
                || value.deliveryItemsBySeason.stream().anyMatch(List::isEmpty)
                || value.fishBySeason.stream().anyMatch(List::isEmpty)) {
            return DataResult.error(() -> "Daily quest seasonal pools must contain four non-empty seasons");
        }
        return DataResult.success(value);
    }

    public record ResourceEntry(ResourceLocation item, int amount, int reward, String targetNpc) {
        public static final Codec<ResourceEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ResourceEntry::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(ResourceEntry::amount),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("reward", 0).forGetter(ResourceEntry::reward),
                Codec.STRING.optionalFieldOf("target_npc", "clint").forGetter(ResourceEntry::targetNpc)
        ).apply(instance, ResourceEntry::new));
    }

    public record MonsterEntry(String type, int count, int reward, String targetNpc) {
        public static final Codec<MonsterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(MonsterEntry::type),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(MonsterEntry::count),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("reward", 0).forGetter(MonsterEntry::reward),
                Codec.STRING.optionalFieldOf("target_npc", "lewis").forGetter(MonsterEntry::targetNpc)
        ).apply(instance, MonsterEntry::new));
    }
}
