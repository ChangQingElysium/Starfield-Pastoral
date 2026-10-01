package com.stardew.craft.item.weapon;

import com.stardew.craft.combat.StardewWeaponSpeedRules;
import com.stardew.craft.item.IStardewItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import com.stardew.craft.port.net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星露谷武器基类
 * 实现IStardewItem接口以继承模组的标准tooltip系统
 */
@SuppressWarnings("deprecation") // Tier 在 1.21+ 被弃用，但武器系统依赖它
public class StardewWeaponItem extends SwordItem
        implements IStardewItem, IStardewWeapon {
    private final String weaponId;
    private final WeaponData weaponData;

    public StardewWeaponItem(String weaponId, Properties properties) {
        // PORT(1.20.1): 1.20.1 SwordItem takes attack damage/speed modifiers; unused, see getDefaultAttributeModifiers.
        super(createTier(weaponId), 0, 0.0F, properties);
        this.weaponId = weaponId;
        this.weaponData = WeaponRegistry.get(weaponId);
    }

    /**
     * 根据武器数据创建Tier
     */
    private static Tier createTier(String weaponId) {
        WeaponData data = WeaponRegistry.get(weaponId);
        if (data == null) {
            return new StardewWeaponTier(1, 0, 1.6F);
        }

        // 计算平均伤害 (MC的attackDamage会+1)
        float avgDamage =
                (float) ((data.getDamageMin() + data.getDamageMax())
                        / 2.0D - 1);

        float attackSpeed = (float) StardewWeaponSpeedRules.attacksPerSecondFromRawSpeed(
                data.getWeaponType(),
                data.getRawSpeed(),
                0.0F
        ) - 4.0F;

        return new StardewWeaponTier(
                data.getLevel(),
                avgDamage,
                attackSpeed
        );
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        if (weaponData != null) {
            return Component.translatable(this.getDescriptionId())
                    .withStyle(weaponData.getRarity().getColor());
        }
        return super.getName(stack);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ensureWeaponStats(stack);
        return stack;
    }

    @SuppressWarnings("null")
    @Override
    // PORT(1.20.1): items expose default attribute modifiers per slot instead of a default component.
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot) {
        if (weaponData == null) {
            // 1.21: bare Item.Properties -> empty attribute_modifiers (1.20.1 SwordItem would add tier modifiers).
            return com.google.common.collect.ImmutableMultimap.of();
        }
        return WeaponItemSupport.createAttributeModifiers(
                weaponId,
                weaponData
        ).portModifiers(slot);
    }
    // PORT(1.20.1): NeoForge 1.21 only consults Item#isDamageable(stack) for repairability
    // (Item#isRepairable = canRepair && isDamageable); durability loss follows the MAX_DAMAGE component.
    // Forge 1.20.1 also routes ItemStack#isDamageableItem through it, so keep the 1.21 effect here only.
    @Override
    public boolean isRepairable(@SuppressWarnings("null") ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(@SuppressWarnings("null") ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(@SuppressWarnings("null") ItemStack stack) {
        return false;
    }

    @Override
    public boolean hurtEnemy(
            @SuppressWarnings("null") ItemStack stack,
            @SuppressWarnings("null") LivingEntity target,
            @SuppressWarnings("null") LivingEntity attacker
    ) {
        return true;
    }

    @Override
    public boolean mineBlock(
            @SuppressWarnings("null") ItemStack stack,
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") BlockState state,
            @SuppressWarnings("null") BlockPos pos,
            @SuppressWarnings("null") LivingEntity entityLiving
    ) {
        return true;
    }

    @Override
    public String getItemTypeKey() {
        if (weaponData == null) {
            return "stardewcraft.type.weapon";
        }
        return switch (weaponData.getWeaponType()) {
            case SWORD -> "stardewcraft.type.weapon.sword";
            case DAGGER -> "stardewcraft.type.weapon.dagger";
            case CLUB -> "stardewcraft.type.weapon.club";
            case SLINGSHOT -> "stardewcraft.type.weapon.slingshot";
        };
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return weaponData == null ? -1 : weaponData.getLevel() * 50;
    }

    @Override
    public void appendHoverText(@SuppressWarnings("null") ItemStack stack,
            @javax.annotation.Nullable Level level,
            @SuppressWarnings("null") List<Component> tooltipComponents,
            @SuppressWarnings("null") TooltipFlag tooltipFlag) {
        if (weaponData != null) {
            ensureWeaponStats(stack);
            boolean expanded =
                    net.minecraft.client.gui.screens.Screen.hasShiftDown();
            WeaponTooltipBuilder builder =
                    new WeaponTooltipBuilder(stack, weaponData, expanded);
            tooltipComponents.addAll(builder.build());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void inventoryTick(
            @SuppressWarnings("null") ItemStack stack,
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide && entity instanceof Player) {
            ensureWeaponStats(stack);
        }
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") Player player,
            @SuppressWarnings("null") InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> useSkill(
            Level level,
            Player player,
            InteractionHand hand,
            boolean majorSkill
    ) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    public String getWeaponId() {
        return weaponId;
    }

    public WeaponData getWeaponData() {
        return weaponData;
    }

    @SuppressWarnings("null")
    private void ensureWeaponStats(ItemStack stack) {
        WeaponItemSupport.ensureStats(stack, weaponData);
    }

    /**
     * 自定义武器Tier
     */
    private static class StardewWeaponTier implements Tier {
        private final int level;
        private final float attackDamage;
        private final float attackSpeed;

        private StardewWeaponTier(
                int level,
                float attackDamage,
                float attackSpeed
        ) {
            this.level = level;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
        }

        @Override
        public int getUses() {
            return Integer.MAX_VALUE;
        }

        @Override
        public float getSpeed() {
            return attackSpeed;
        }

        @Override
        public float getAttackDamageBonus() {
            return attackDamage;
        }

        // PORT(1.20.1): 1.20.5+ getIncorrectBlocksForDrops() == INCORRECT_FOR_IRON_TOOL; 1.20.1 ranks unsorted tiers by
        // mining level, and iron's is 2.
        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}
