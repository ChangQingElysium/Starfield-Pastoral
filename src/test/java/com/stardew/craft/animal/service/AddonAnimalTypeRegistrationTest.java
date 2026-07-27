package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.api.v1.agriculture.StardewAnimalIncubation;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplay;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplays;
import com.stardew.craft.api.v1.agriculture.StardewAnimalQueryDefinition;
import com.stardew.craft.api.v1.agriculture.StardewAnimalQueryDefinitions;
import com.stardew.craft.api.v1.agriculture.StardewAnimalShopEntries;
import com.stardew.craft.api.v1.agriculture.StardewAnimalShopEntry;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypeDefinition;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonAnimalTypeRegistrationTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void addonTypeFeedsCatalogAndEntityProjectionWithoutReplacingBuiltins() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_api_test:goose_" + suffix;
        ResourceLocation registrationId = ResourceLocation.fromNamespaceAndPath(
                "animal_api_test", "goose_" + suffix);
        EntityType<? extends BaseCoopAnimalEntity> entityType = ModEntities.WHITE_CHICKEN.get();
        AtomicBoolean supplierResolved = new AtomicBoolean();

        StardewAnimalTypes.register(
                registrationId,
                animalTypeId.toUpperCase(java.util.Locale.ROOT),
                "COOP",
                5,
                () -> {
                    supplierResolved.set(true);
                    return entityType;
                }
        );
        assertFalse(supplierResolved.get());

        StardewAnimalTypeDefinition definition = StardewAnimalTypes.definition(animalTypeId);
        assertEquals(registrationId, definition.registrationId());
        assertEquals(animalTypeId, definition.animalTypeId());
        assertEquals("coop", definition.family());
        assertEquals(5, definition.daysToMature());

        AnimalTypeCatalog.AnimalTypeSpec catalog = AnimalTypeCatalog.resolve(animalTypeId);
        assertEquals(animalTypeId, catalog.id());
        assertEquals("coop", catalog.family());
        assertEquals(5, catalog.daysToMature());
        assertTrue(AnimalTypeCatalog.knownTypeIds().contains(animalTypeId));
        assertSame(entityType, AnimalEntitySyncService.resolveEntityType(animalTypeId));
        assertTrue(supplierResolved.get());
    }

    @Test
    void unknownTypeNamesNeverGuessABuildingFamily() {
        assertNull(AnimalTypeCatalog.find(
                "missing_addon:very_duck_like_chicken"));
        assertThrows(IllegalArgumentException.class, () ->
                AnimalTypeCatalog.resolve(
                        "missing_addon:very_duck_like_chicken"));
        assertThrows(IllegalArgumentException.class, () ->
                AnimalTypeCatalog.require(
                        "missing_addon:very_duck_like_chicken"));
    }

    @Test
    void addonCannotReplaceBuiltInAnimalType() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation registrationId = ResourceLocation.fromNamespaceAndPath(
                "animal_api_test", "cow_override_" + suffix);

        assertThrows(IllegalArgumentException.class, () -> StardewAnimalTypes.register(
                registrationId,
                "cow",
                "barn",
                5,
                ModEntities.WHITE_CHICKEN::get
        ));
    }

    @Test
    void addonQueryAndPurchaseDisplayDefinitionsRemainSeparateFromServerShopRules() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_api_test:presented_goose_" + suffix;
        StardewAnimalTypes.register(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "presented_goose_type_" + suffix),
                animalTypeId,
                "coop",
                5,
                ModEntities.WHITE_CHICKEN::get
        );
        StardewAnimalQueryDefinitions.register(new StardewAnimalQueryDefinition(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "presented_goose_query_" + suffix),
                animalTypeId,
                12_000,
                15_600,
                false
        ));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                "animal_api_test", "textures/gui/goose_" + suffix + ".png");
        StardewAnimalPurchaseDisplays.register(new StardewAnimalPurchaseDisplay(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "presented_goose_display_" + suffix),
                animalTypeId,
                texture,
                32,
                16
        ));

        StardewAnimalQueryDefinition query =
                StardewAnimalQueryDefinitions.definition(animalTypeId);
        assertEquals(3_600, query.sellPrice(0));
        assertEquals(15_600, query.sellPrice(1_000));
        assertFalse(query.reproductionToggleAvailable());
        assertEquals(texture, StardewAnimalPurchaseDisplays.display(animalTypeId).texture());
    }

    @Test
    void addonShopEntryBecomesPurchasableOnlyAfterItsAnimalTypeExists() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_api_test:shop_goose_" + suffix;
        ResourceLocation typeRegistrationId = ResourceLocation.fromNamespaceAndPath(
                "animal_api_test", "shop_goose_type_" + suffix);
        ResourceLocation shopRegistrationId = ResourceLocation.fromNamespaceAndPath(
                "animal_api_test", "shop_goose_entry_" + suffix);

        StardewAnimalShopEntries.register(new StardewAnimalShopEntry(
                shopRegistrationId,
                animalTypeId,
                "coop",
                2,
                12_000,
                "Goose",
                "entity.animal_api_test.goose",
                "animal_api_test.animal.shop.desc.goose",
                "animal_api_test.animal.shop.lock.coop_t2",
                100
        ));
        assertNull(AnimalShopService.getRule(animalTypeId));

        StardewAnimalTypes.register(
                typeRegistrationId,
                animalTypeId,
                "coop",
                5,
                ModEntities.WHITE_CHICKEN::get
        );

        AnimalShopService.ShopAnimalRule rule = AnimalShopService.getRule(animalTypeId);
        assertNotNull(rule);
        assertEquals(animalTypeId, rule.animalTypeId());
        assertEquals("coop", rule.family());
        assertEquals(2, rule.requiredTier());
        assertEquals(12_000, rule.price());
        assertEquals("Goose", rule.defaultName());
        assertEquals(
                "animal_api_test.animal.shop.desc.goose", rule.descriptionKey());
        assertEquals(
                "animal_api_test.animal.shop.lock.coop_t2", rule.lockReasonKey());
    }

    @Test
    void addonCannotReplaceBuiltInAnimalShopEntry() {
        int suffix = IDS.incrementAndGet();
        assertThrows(IllegalArgumentException.class, () ->
                StardewAnimalShopEntries.register(new StardewAnimalShopEntry(
                        ResourceLocation.fromNamespaceAndPath(
                                "animal_api_test", "cow_shop_override_" + suffix),
                        "cow",
                        "barn",
                        1,
                        1,
                        "Cow",
                        "entity.stardewcraft.cow",
                        "stardewcraft.animal.shop.desc.cow",
                        "stardewcraft.animal.shop.lock.barn_t1",
                        0
                )));
    }

    @Test
    void addonIncubationResolversUseStablePriorityAndRegisteredTypes() {
        int suffix = IDS.incrementAndGet();
        String animalTypeId = "animal_api_test:incubated_goose_" + suffix;
        StardewAnimalTypes.register(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "incubated_goose_type_" + suffix),
                animalTypeId,
                "coop",
                5,
                ModEntities.WHITE_CHICKEN::get
        );
        AtomicInteger lowerCalls = new AtomicInteger();
        StardewAnimalIncubation.register(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "incubation_selected_" + suffix),
                100,
                stack -> stack.is(Items.FEATHER) ? animalTypeId : null
        );
        StardewAnimalIncubation.register(
                ResourceLocation.fromNamespaceAndPath(
                        "animal_api_test", "incubation_lower_" + suffix),
                -100,
                stack -> {
                    if (stack.is(Items.FEATHER)) {
                        lowerCalls.incrementAndGet();
                        return "duck";
                    }
                    return null;
                }
        );

        assertEquals(
                animalTypeId,
                IncubatorBlockEntity.resolveAnimalTypeId(new ItemStack(Items.FEATHER))
        );
        assertEquals(0, lowerCalls.get());
        assertTrue(StardewAnimalTypes.isKnown("duck"));
    }
}
