package com.stardew.craft.combat.skill;

import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponSkillDispatcherTest {
    @Test
    void builtInWeaponDefinitionsResolveThroughTheSharedDispatcher() {
        WeaponData data = WeaponData.builder("test_blade")
                .skill1(WeaponSkillData.builder("primary_test").build())
                .skill2(WeaponSkillData.builder("othermod:secondary_test").build())
                .build();
        IStardewWeapon weapon = new TestWeapon(data);

        assertEquals(
                Optional.of(id("stardewcraft", "primary_test")),
                WeaponSkillDispatcher.configuredSkillId(Optional.empty(), weapon, false)
        );
        assertEquals(
                Optional.of(id("othermod", "secondary_test")),
                WeaponSkillDispatcher.configuredSkillId(Optional.empty(), weapon, true)
        );
    }

    @Test
    void missingConfiguredSlotDoesNotInventASkill() {
        WeaponData data = WeaponData.builder("test_blade")
                .skill1(WeaponSkillData.builder("primary_test").build())
                .build();
        IStardewWeapon weapon = new TestWeapon(data);

        assertEquals(
                Optional.empty(),
                WeaponSkillDispatcher.configuredSkillId(Optional.empty(), weapon, true)
        );
    }

    @Test
    void stackAwarePublicDefinitionKeepsPriorityOverBuiltInDefinition() {
        WeaponData data = WeaponData.builder("test_blade")
                .skill1(WeaponSkillData.builder("primary_test").build())
                .build();
        IStardewWeapon weapon = new TestWeapon(data);
        ResourceLocation override = id("othermod", "override");

        assertEquals(
                Optional.of(override),
                WeaponSkillDispatcher.configuredSkillId(Optional.of(override), weapon, false)
        );
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static final class TestWeapon implements IStardewWeapon {
        private final WeaponData data;

        private TestWeapon(WeaponData data) {
            this.data = data;
        }

        @Override
        public String getWeaponId() {
            return data.getId();
        }

        @Override
        public WeaponData getWeaponData() {
            return data;
        }

        @Override
        public InteractionResultHolder<ItemStack> useSkill(
                Level level,
                Player player,
                InteractionHand hand,
                boolean majorSkill
        ) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
    }
}
