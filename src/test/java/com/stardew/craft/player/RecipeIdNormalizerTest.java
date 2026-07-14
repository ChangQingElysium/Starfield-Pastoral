package com.stardew.craft.player;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIdNormalizerTest {
    @Test
    void normalizesDefinitionAndShopIdsForStorage() {
        assertEquals("apple_crate", RecipeIdNormalizer.storageId("apple_crate"));
        assertEquals("apple_crate", RecipeIdNormalizer.storageId("stardewcraft:apple_crate"));
        assertEquals("myaddon:apple_crate", RecipeIdNormalizer.storageId("myaddon:apple_crate"));
        assertEquals("apple_crate", RecipeIdNormalizer.storageId("recipe:stardewcraft:apple_crate"));
        assertEquals("myaddon:apple_crate", RecipeIdNormalizer.storageId("recipe:myaddon:apple_crate"));
    }

    @Test
    void rejectsMalformedAndRepeatedShopPrefixes() {
        assertEquals("", RecipeIdNormalizer.storageId((String) null));
        assertEquals("", RecipeIdNormalizer.storageId(" "));
        assertEquals("", RecipeIdNormalizer.storageId("recipe:"));
        assertEquals("", RecipeIdNormalizer.storageId("recipe:recipe:apple_crate"));
        assertEquals("", RecipeIdNormalizer.storageId("myaddon:bad id"));
    }

    @Test
    void resourceLocationsUseTheSameStorageRule() {
        assertEquals("apple_crate", RecipeIdNormalizer.storageId(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "apple_crate")));
        assertEquals("myaddon:apple_crate", RecipeIdNormalizer.storageId(
                ResourceLocation.fromNamespaceAndPath("myaddon", "apple_crate")));
    }

    @Test
    void thirdPartyUnlocksDoNotCollideWithBuiltInRecipes() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        String addonRecipe = RecipeIdNormalizer.storageId("recipe:myaddon:apple_crate");

        assertTrue(data.unlockRecipe(addonRecipe));
        assertTrue(data.isRecipeUnlocked("myaddon:apple_crate"));
        assertFalse(data.isRecipeUnlocked("apple_crate"));
        assertFalse(data.unlockRecipe(addonRecipe));
        assertFalse(data.unlockRecipe("recipe:myaddon:apple_crate"));
    }

    @Test
    void legacyRecipeIdsNormalizeDuringNbtMigrationAndWriteBackCanonically() {
        CompoundTag legacy = new CompoundTag();
        ListTag unlocked = new ListTag();
        unlocked.add(StringTag.valueOf("recipe:stardewcraft:apple_crate"));
        unlocked.add(StringTag.valueOf("stardewcraft:apple_crate"));
        unlocked.add(StringTag.valueOf("recipe:myaddon:apple_crate"));
        unlocked.add(StringTag.valueOf("recipe:recipe:invalid"));
        legacy.put("UnlockedRecipes", unlocked);

        ListTag craftCounts = new ListTag();
        craftCounts.add(craftCount("recipe:stardewcraft:apple_crate", 3));
        craftCounts.add(craftCount("recipe:myaddon:apple_crate", 5));
        craftCounts.add(craftCount("recipe:recipe:invalid", 9));
        craftCounts.add(craftCount("recipe:myaddon:ignored", -2));
        legacy.put("RecipeCraftCounts", craftCounts);

        PlayerStardewData migrated = PlayerStardewData.fromNBT(legacy, UUID.randomUUID());
        assertEquals(Set.of("apple_crate", "myaddon:apple_crate"), migrated.getUnlockedRecipes());
        assertEquals(3, migrated.getRecipeCraftCount("apple_crate"));
        assertEquals(3, migrated.getRecipeCraftCount("recipe:stardewcraft:apple_crate"));
        assertEquals(5, migrated.getRecipeCraftCount("myaddon:apple_crate"));
        assertEquals(0, migrated.getRecipeCraftCount("myaddon:ignored"));

        CompoundTag rewritten = migrated.toNBT();
        Set<String> rewrittenUnlocks = new HashSet<>();
        ListTag rewrittenList = rewritten.getList("UnlockedRecipes", StringTag.TAG_STRING);
        for (int i = 0; i < rewrittenList.size(); i++) {
            rewrittenUnlocks.add(rewrittenList.getString(i));
        }
        assertEquals(Set.of("apple_crate", "myaddon:apple_crate"), rewrittenUnlocks);

        ListTag rewrittenCounts = rewritten.getList("RecipeCraftCounts", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < rewrittenCounts.size(); i++) {
            String recipeId = rewrittenCounts.getCompound(i).getString("Recipe");
            assertFalse(recipeId.startsWith("recipe:"));
            assertFalse(recipeId.startsWith("stardewcraft:"));
        }
    }

    private static CompoundTag craftCount(String recipeId, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Recipe", recipeId);
        tag.putInt("Count", count);
        return tag;
    }
}
