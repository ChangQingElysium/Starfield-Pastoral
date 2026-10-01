package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponType;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.item.weapon.WeaponRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Checks bound registries and the real creative inventory path, not source-text registrations. */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponCatalogGameTests {
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void clubsAreRegisteredAndVisible(GameTestHelper helper) {
        var visible = StardewItemCatalog.visibleItems();
        int clubs = 0;
        for (var weapon : WeaponRegistry.getAll()) {
            if (weapon.getWeaponType() != WeaponType.CLUB) continue;
            var id = new ResourceLocation(StardewCraft.MODID, weapon.getId());
            var item = BuiltInRegistries.ITEM.get(id);
            helper.assertTrue(item != Items.AIR, "Missing bound item: " + id);
            helper.assertTrue(visible.contains(item), "Hidden catalog item: " + id);
            helper.assertTrue(StardewItemCatalog.tabForItem(item) == StardewCatalogTab.COMBAT, "Wrong creative tab: " + id);
            helper.assertTrue(!StardewItemDisplayStacks.stacksForItem(item).isEmpty(), "Empty creative stacks: " + id);
            helper.assertTrue(!item.builtInRegistryHolder().is(com.stardew.craft.core.ModTags.Items.HIDDEN), "JEI-hidden item: " + id);
            clubs++;
        }
        helper.assertTrue(clubs == 16, "Expected all 16 clubs, found " + clubs);
        StardewCraft.LOGGER.info("Weapon catalog runtime check: all {} clubs registered and visible in Combat", clubs);
        helper.succeed();
    }
}
