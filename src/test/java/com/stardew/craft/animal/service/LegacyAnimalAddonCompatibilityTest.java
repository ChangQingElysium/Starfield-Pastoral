package com.stardew.craft.animal.service;

import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.manager.AnimalGrowthManager;
import com.stardew.craft.menu.AnimalQueryMenu;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyAnimalAddonCompatibilityTest {
    @Test
    void legacyShopRuleConstructorUsesSafeModernDefaults() {
        AnimalShopService.ShopAnimalRule rule =
                new AnimalShopService.ShopAnimalRule(
                        "goose",
                        "coop",
                        3,
                        12_000,
                        "Goose",
                        "addon.goose.description",
                        "addon.goose.locked"
                );

        assertEquals("Goose", rule.displayNameKey());
        assertNull(rule.unlockCondition());
        assertNull(rule.shopTextureId());
        assertEquals(32, rule.shopTextureWidth());
        assertEquals(16, rule.shopTextureHeight());
        assertNull(rule.soundEventId());
    }

    @Test
    void legacyMixinTargetsRemainPresentWithExpectedShapes()
            throws ReflectiveOperationException {
        var priceHook = AnimalQueryMenu.class
                .getDeclaredMethod("getBaseAnimalSellPrice");
        assertEquals(int.class, priceHook.getReturnType());
        assertTrue(Modifier.isPrivate(priceHook.getModifiers()));
        assertFalse(Modifier.isStatic(priceHook.getModifiers()));

        var variantHook = BaseCoopAnimalEntity.class
                .getDeclaredMethod("lambda$openAnimalQueryMenu$3");
        assertEquals(int.class, variantHook.getReturnType());
        assertTrue(Modifier.isPrivate(variantHook.getModifiers()));
        assertFalse(Modifier.isStatic(variantHook.getModifiers()));

        var rules = AnimalShopService.class
                .getDeclaredField("SHOP_RULES");
        assertEquals(Map.class, rules.getType());
        assertTrue(Modifier.isPrivate(rules.getModifiers()));
        assertTrue(Modifier.isStatic(rules.getModifiers()));
        assertTrue(Modifier.isFinal(rules.getModifiers()));

        var order = AnimalShopService.class
                .getDeclaredField("SHOP_ORDER");
        assertEquals(List.class, order.getType());
        assertTrue(Modifier.isPrivate(order.getModifiers()));
        assertTrue(Modifier.isStatic(order.getModifiers()));
        assertTrue(Modifier.isFinal(order.getModifiers()));

        var dailyUpdate = AnimalGrowthManager.class
                .getDeclaredMethod(
                        "applyDayUpdate",
                        ServerLevel.class,
                        AnimalWorldData.class,
                        FarmAnimalRecord.class,
                        int.class,
                        boolean.class);
        assertEquals(boolean.class, dailyUpdate.getReturnType());
        assertTrue(Modifier.isPrivate(dailyUpdate.getModifiers()));
        assertFalse(Modifier.isStatic(dailyUpdate.getModifiers()));
    }
}
