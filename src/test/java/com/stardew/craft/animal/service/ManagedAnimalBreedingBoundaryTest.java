package com.stardew.craft.animal.service;

import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedAnimalBreedingBoundaryTest {
    @Test
    void managedAnimalHooksRemainSubclassCompatibleWhileOffspringStayAuthoritative()
            throws NoSuchMethodException {
        assertOverridableMethod("isFood", ItemStack.class);
        assertOverridableMethod("canMate", Animal.class);

        assertEquals(
                Ingredient.class,
                BaseCoopAnimalEntity.class
                        .getDeclaredMethod("getBreedIngredient")
                        .getReturnType());
        assertEquals(
                EntityType.class,
                BaseCoopAnimalEntity.class
                        .getDeclaredMethod("getOffspringType")
                        .getReturnType());

        Method offspring = assertFinalMethod(
                "getBreedOffspring",
                ServerLevel.class,
                AgeableMob.class);
        assertEquals(AgeableMob.class, offspring.getReturnType());
    }

    private static Method assertOverridableMethod(
            String name,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Method method = BaseCoopAnimalEntity.class.getDeclaredMethod(
                name,
                parameterTypes);
        assertFalse(
                Modifier.isFinal(method.getModifiers()),
                name + " must remain overridable for third-party animal subclasses");
        return method;
    }

    private static Method assertFinalMethod(
            String name,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Method method = BaseCoopAnimalEntity.class.getDeclaredMethod(
                name,
                parameterTypes);
        assertTrue(
                Modifier.isFinal(method.getModifiers()),
                name + " must remain final so reproduction only uses the "
                        + "authoritative overnight transaction");
        return method;
    }
}
