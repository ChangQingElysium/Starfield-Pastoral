package com.stardew.craft.api.v1.mastery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Ordered reward rows shown and granted for one existing core skill mastery. */
public record StardewMasteryRewardDefinition(
        ResourceLocation skill,
        int priority,
        List<Entry> entries
) {
    public static final Codec<StardewMasteryRewardDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("skill").forGetter(StardewMasteryRewardDefinition::skill),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewMasteryRewardDefinition::priority),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(StardewMasteryRewardDefinition::entries)
    ).apply(instance, StardewMasteryRewardDefinition::new));

    public StardewMasteryRewardDefinition {
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    public record Entry(
            Kind kind,
            Optional<ResourceLocation> item,
            int count,
            String recipeId,
            String nameKey,
            String descKey,
            Optional<ResourceLocation> statBonus
    ) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Entry>create(instance -> instance.group(
                Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
                ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(Entry::count),
                Codec.STRING.optionalFieldOf("recipe_id", "").forGetter(Entry::recipeId),
                Codec.STRING.fieldOf("name_key").forGetter(Entry::nameKey),
                Codec.STRING.fieldOf("desc_key").forGetter(Entry::descKey),
                ResourceLocation.CODEC.optionalFieldOf("stat_bonus").forGetter(Entry::statBonus)
        ).apply(instance, Entry::new)).validate(Entry::validate);

        public Entry {
            item = item == null ? Optional.empty() : item;
            recipeId = recipeId == null ? "" : recipeId.trim();
            statBonus = statBonus == null ? Optional.empty() : statBonus;
        }

        private static DataResult<Entry> validate(Entry entry) {
            if ((entry.kind() == Kind.ITEM || entry.kind() == Kind.RECIPE) && entry.item().isEmpty()) {
                return DataResult.error(() -> "mastery item/recipe row needs item");
            }
            if (entry.kind() == Kind.RECIPE && entry.recipeId().isBlank()) {
                return DataResult.error(() -> "mastery recipe row needs recipe_id");
            }
            if (entry.kind() == Kind.STAT && entry.statBonus().isEmpty()) {
                return DataResult.error(() -> "mastery stat row needs stat_bonus");
            }
            return DataResult.success(entry);
        }
    }

    public enum Kind {
        ITEM,
        RECIPE,
        STAT,
        PLACEHOLDER;

        public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(raw -> {
            try {
                return DataResult.<Kind>success(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.<Kind>error(() -> "unknown mastery reward kind: " + raw);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
