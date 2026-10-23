package com.stardew.craft.platform.fabric.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemActionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vanillaToolsExposeTheirCompleteActionsAndRejectOtherFamilies() {
        var fixtures = List.of(
                new Tool(Items.IRON_AXE, Set.of("axe_dig", "axe_strip", "axe_scrape", "axe_wax_off")),
                new Tool(Items.IRON_PICKAXE, Set.of("pickaxe_dig")),
                new Tool(Items.IRON_SHOVEL, Set.of("shovel_dig", "shovel_flatten", "shovel_douse")),
                new Tool(Items.IRON_HOE, Set.of("hoe_dig", "till")),
                new Tool(Items.IRON_SWORD, Set.of("sword_dig", "sword_sweep")),
                new Tool(Items.SHEARS, Set.of("shears_dig", "shears_harvest", "shears_remove_armor",
                        "shears_carve", "shears_disarm", "shears_trim")),
                new Tool(Items.SHIELD, Set.of("shield_block")),
                new Tool(Items.FISHING_ROD, Set.of("fishing_rod_cast")),
                new Tool(Items.TRIDENT, Set.of("trident_throw")),
                new Tool(Items.BRUSH, Set.of("brush_brush")),
                new Tool(Items.FLINT_AND_STEEL, Set.of("firestarter_light")),
                new Tool(Items.FIRE_CHARGE, Set.of("firestarter_light")),
                new Tool(Items.SPYGLASS, Set.of("spyglass_scope")));
        // Force all stock constants to initialize before enumerating their names.
        assertSame(ItemAbility.get("till"), ItemAbilities.HOE_TILL);
        ItemAbility.get("stardewcraft_test:unknown_action");
        for (Tool fixture : fixtures) {
            ItemStack stack = new ItemStack(fixture.item());
            for (ItemAbility ability : ItemAbility.getActions()) {
                assertEquals(fixture.actions().contains(ability.name()),
                        ItemActions.canPerformAction(stack, ability),
                        fixture.item() + " / " + ability.name());
            }
        }
    }

    @Test
    void ordinaryItemsAndEmptyStacksHaveNoAbilities() {
        ItemStack emptyAxe = new ItemStack(Items.IRON_AXE, 0);
        for (ItemStack stack : List.of(ItemStack.EMPTY, emptyAxe,
                new ItemStack(Items.STICK), new ItemStack(Items.CARROT_ON_A_STICK),
                new ItemStack(Items.MACE))) {
            assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.AXE_DIG));
            assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.SWORD_SWEEP));
            assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.FISHING_ROD_CAST));
        }
    }

    @Test
    void vanillaSubclassInheritsItsFamilyUnlessItSuppliesAnOverride() throws ReflectiveOperationException {
        ItemStack stack = new ItemStack(allocate(FixtureAxe.class));
        assertTrue(ItemActions.canPerformAction(stack, ItemAbilities.AXE_DIG));
        assertTrue(ItemActions.canPerformAction(stack, ItemAbilities.AXE_WAX_OFF));
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.HOE_TILL));
    }

    @Test
    void customProviderCanRejectInheritedActionsAndUsesTheExactStack() throws ReflectiveOperationException {
        final class ConditionalAxe extends FixtureAxe implements ItemAbilityProvider {
            private ItemStack observed;

            @Override
            public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
                observed = stack;
                return ability == ItemAbilities.HOE_TILL && stack.has(DataComponents.CUSTOM_NAME);
            }
        }
        ConditionalAxe axe = allocate(ConditionalAxe.class);
        ItemStack stack = new ItemStack(axe);
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.AXE_DIG));
        assertSame(stack, axe.observed);
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.HOE_TILL));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Component fixture"));
        assertTrue(ItemActions.canPerformAction(stack, ItemAbilities.HOE_TILL));
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.AXE_DIG));
        assertSame(stack, axe.observed);
    }

    @Test
    void customPlainItemCanExposeTheHoeAndClubQueriesUsedByTheMod() throws ReflectiveOperationException {
        final class CustomTool extends Item implements ItemAbilityProvider {
            CustomTool() {
                super(new Item.Properties());
            }

            @Override
            public DataComponentMap components() {
                return Items.STICK.components();
            }

            @Override
            public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
                return stack.getCount() == 1 && (ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability)
                        || ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(ability));
            }
        }
        ItemStack stack = new ItemStack(allocate(CustomTool.class));
        assertTrue(ItemActions.canPerformAction(stack, ItemAbilities.HOE_TILL));
        assertTrue(ItemActions.canPerformAction(stack, ItemAbilities.SWORD_DIG));
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.AXE_DIG));
        stack.setCount(2);
        assertFalse(ItemActions.canPerformAction(stack, ItemAbilities.HOE_TILL));
    }

    @Test
    void abilityNamesRemainCanonicalAcrossCodecsAndLiveEnumeration() {
        var view = ItemAbility.getActions();
        ItemAbility declared = ItemAbility.get("stardewcraft_test:component_action");
        assertTrue(view.contains(declared));
        assertSame(declared, ItemAbility.get(declared.name()));
        var encoded = ItemAbility.CODEC.encodeStart(JsonOps.INSTANCE, declared).getOrThrow();
        assertEquals(declared.name(), encoded.getAsString());
        assertSame(declared, ItemAbility.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertThrows(UnsupportedOperationException.class, () -> view.remove(declared));
    }

    private record Tool(Item item, Set<String> actions) {}

    private static class FixtureAxe extends AxeItem {
        private FixtureAxe() {
            super(Tiers.IRON, new Item.Properties());
        }

        @Override
        public DataComponentMap components() {
            return Items.IRON_AXE.components();
        }
    }

    /**
     * The native bootstrap freezes intrusive item holders. Allocate only the
     * identity/provider fixtures without constructors; their component maps come
     * from real bootstrapped items. No registry is unfrozen, replaced or mutated.
     */
    private static <T extends Item> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        return type.cast(allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, type));
    }
}
