package com.stardew.craft.integration.jei;

import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.UnlockSourceData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Converts internal unlock IDs into localized player-facing descriptions. */
public final class JeiUnlockText {
    private JeiUnlockText() {
    }

    public static Optional<Component> crafting(StardewCraftingRecipeData.RecipeEntry recipe) {
        if (recipe == null) return Optional.empty();
        String condition = recipe.unlockCondition();
        if (condition == null || condition.isBlank() || "null".equalsIgnoreCase(condition.trim())) {
            return knownSource(recipe.id());
        }
        String normalized = condition.trim();
        if ("default".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }

        String[] parts = normalized.split("\\s+");
        int skillOffset = parts.length >= 3 && "s".equalsIgnoreCase(parts[0]) ? 1 : 0;
        if (parts.length >= skillOffset + 2) {
            SkillType skill = SkillType.fromName(parts[skillOffset]);
            Integer level = parsePositiveInt(parts[skillOffset + 1]);
            if (skill != null && level != null) {
                return Optional.of(skillLevel(skill, level));
            }
        }
        if (parts.length >= 2 && "l".equalsIgnoreCase(parts[0])) {
            return Optional.of(Component.translatable("stardewcraft.jei.unlock.event"));
        }
        if (normalized.toLowerCase(Locale.ROOT).startsWith("festival_")) {
            return Optional.of(Component.translatable("stardewcraft.jei.unlock.festival_shop"));
        }
        if (!recipe.unlockWhen().isEmpty()) {
            return Optional.of(Component.translatable("stardewcraft.jei.unlock.conditional"));
        }
        return knownSource(recipe.id()).or(() -> Optional.of(
                Component.translatable("stardewcraft.jei.unlock.other")));
    }

    public static Optional<Component> knownSource(String recipeId) {
        List<ResourceLocation> sourceIds = UnlockSourceData.getSourceIdsForRecipe(recipeId);
        if (sourceIds.isEmpty()) return Optional.empty();
        return Optional.of(source(sourceIds.get(0)));
    }

    static Component source(ResourceLocation sourceId) {
        String[] path = sourceId.getPath().split("/");
        if (path.length >= 3 && "skill".equals(path[0])) {
            SkillType skill = SkillType.fromName(path[1]);
            Integer level = parsePositiveInt(path[2]);
            if (skill != null && level != null) return skillLevel(skill, level);
        }
        if (path.length > 0 && "shop".equals(path[0])) {
            return Component.translatable("stardewcraft.jei.unlock.shop");
        }
        if (path.length > 0 && "museum".equals(path[0])) {
            return Component.translatable("stardewcraft.jei.unlock.museum");
        }
        return Component.translatable("stardewcraft.jei.unlock.other");
    }

    private static Component skillLevel(SkillType skill, int level) {
        return Component.translatable("stardewcraft.jei.unlock.skill", skill.getDisplayName(), level);
    }

    private static Integer parsePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
