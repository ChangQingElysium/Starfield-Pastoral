package com.stardew.craft.platform.fabric.brewing;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class BrewingRecipesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void customRecipesRespectSingletonInputEmptyIngredientAndRegistrationOrder() {
        var first = recipe(Items.STICK);
        var recipes = new BrewingRecipes(List.of(first, recipe(Items.DIAMOND)));
        var input = new ItemStack(Items.POTION);
        var reagent = new ItemStack(Items.RABBIT_FOOT);
        assertTrue(recipes.isIngredient(reagent));
        assertFalse(recipes.isIngredient(ItemStack.EMPTY));
        assertTrue(recipes.output(input, reagent).is(Items.STICK));
        assertTrue(recipes.output(input.copyWithCount(2), reagent).isEmpty());
        assertTrue(recipes.output(ItemStack.EMPTY, reagent).isEmpty());
        assertTrue(recipes.output(input, ItemStack.EMPTY).isEmpty());
        assertTrue(recipes.output(new ItemStack(Items.GLASS_BOTTLE), reagent).isEmpty());
        assertTrue(recipes.output(input, new ItemStack(Items.STONE)).isEmpty());
    }

    @Test void builderSnapshotsAndOutputComponentsRemainSpecificToEachRegistry() {
        var builder = new BrewingRecipes.Builder();
        var old = builder.build();
        builder.addRecipe(recipe(Items.STICK));
        var current = builder.build();
        assertTrue(old.recipes().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> current.recipes().clear());
        var output = current.output(new ItemStack(Items.POTION), new ItemStack(Items.RABBIT_FOOT));
        assertEquals(Component.literal("custom brew"), output.get(DataComponents.CUSTOM_NAME));
        output.setCount(30);
        assertEquals(1, current.output(new ItemStack(Items.POTION), new ItemStack(Items.RABBIT_FOOT)).getCount());
        assertTrue(BrewingRecipes.EMPTY.recipes().isEmpty());
    }

    @Test void realRegistryContextReentersAndRestoresAfterExceptionsAndEventsArePerBootstrap() {
        RegistryAccess outer = new RegistryAccess.ImmutableRegistryAccess(List.of());
        RegistryAccess inner = new RegistryAccess.ImmutableRegistryAccess(List.of());
        var bus = new EventBus();
        bus.addListener(RegisterBrewingRecipesEvent.class, event -> {
            assertSame(outer, event.getRegistryAccess());
            event.getBuilder().addRecipe(recipe(Items.STICK));
        });
        assertSame(RegistryAccess.EMPTY, BrewingBootstrap.registries());
        BrewingBootstrap.withRegistries(outer, () -> {
            assertEquals(1, BrewingBootstrap.collect(bus).recipes().size());
            assertThrows(IllegalArgumentException.class, () -> BrewingBootstrap.withRegistries(inner, () -> {
                assertSame(inner, BrewingBootstrap.registries());
                throw new IllegalArgumentException("fixture");
            }));
            assertSame(outer, BrewingBootstrap.registries());
            assertEquals(1, BrewingBootstrap.collect(bus).recipes().size());
            return null;
        });
        assertSame(RegistryAccess.EMPTY, BrewingBootstrap.registries());
    }

    @Test void nativeBootstrapHasOneBuildAfterVanillaAndBothRealRegistryOwnersCallItOnce() throws Exception {
        var type = nativeType("net/minecraft/world/item/alchemy/PotionBrewing");
        var bootstrap = type.methods.stream().filter(m -> m.name.equals("bootstrap")).findFirst().orElseThrow();
        assertTrue((bootstrap.access & Opcodes.ACC_STATIC) != 0);
        int vanilla = -1, build = -1, builds = 0;
        for (int i = 0; i < bootstrap.instructions.size(); i++) {
            if (bootstrap.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.name.equals("addVanillaMixes")) vanilla = i;
                if (call.owner.endsWith("PotionBrewing$Builder") && call.name.equals("build")) { build = i; builds++; }
            }
        }
        assertEquals(1, builds);
        assertTrue(vanilla >= 0 && build > vanilla);
        for (String owner : List.of("net/minecraft/server/MinecraftServer", "net/minecraft/client/multiplayer/ClientPacketListener")) {
            long calls = nativeType(owner).methods.stream().filter(m -> m.name.equals("<init>"))
                    .flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                    .filter(i -> i instanceof MethodInsnNode call && call.owner.equals(type.name) && call.name.equals("bootstrap")
                            && call.desc.equals("(Lnet/minecraft/world/flag/FeatureFlagSet;)Lnet/minecraft/world/item/alchemy/PotionBrewing;")).count();
            assertEquals(1, calls, owner);
        }
    }

    private static IBrewingRecipe recipe(net.minecraft.world.item.Item result) {
        return new IBrewingRecipe() {
            public boolean isInput(ItemStack stack) { return stack.is(Items.POTION); }
            public boolean isIngredient(ItemStack stack) { return stack.is(Items.RABBIT_FOOT); }
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
                var output = new ItemStack(result);
                output.set(DataComponents.CUSTOM_NAME, Component.literal("custom brew"));
                return output;
            }
        };
    }

    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = BrewingRecipesTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
