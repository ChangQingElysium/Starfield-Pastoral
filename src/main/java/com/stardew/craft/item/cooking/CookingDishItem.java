package com.stardew.craft.item.cooking;

import com.stardew.craft.client.TooltipConstants;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Generic Stardew cooking dish item.
 * Uses vanilla edibility formula and applies configured temporary buffs.
 */
public class CookingDishItem extends Item implements IStardewItem {
    public enum BuffType {
        MAX_ENERGY,
        FISHING,
        LUCK,
        SPEED,
        FARMING,
        FORAGING,
        MINING,
        ATTACK,
        DEFENSE,
        MAGNETIC_RADIUS,
        AVOID_MONSTERS
    }

    public record DishBuff(BuffType type, int amount, int durationTicks) {}

    private static final int INEDIBLE_THRESHOLD = -300;
    private static final String DEFAULT_TYPE_KEY = "stardewcraft.type.cooking";

    private final String typeKey;
    private final int sellPrice;
    private final int edibility;
    private final int energy;
    private final int health;
    private final List<DishBuff> buffs;
    private final boolean drinkAnimation;

    @SuppressWarnings("null")
    public CookingDishItem(int sellPrice, int edibility, List<DishBuff> buffs, Item.Properties properties) {
        this(DEFAULT_TYPE_KEY, sellPrice, edibility, buffs, properties, false);
    }

    @SuppressWarnings("null")
    public CookingDishItem(int sellPrice, int edibility, List<DishBuff> buffs, Item.Properties properties, boolean drinkAnimation) {
        this(DEFAULT_TYPE_KEY, sellPrice, edibility, buffs, properties, drinkAnimation);
    }

    @SuppressWarnings("null")
    public CookingDishItem(String typeKey, int sellPrice, int edibility, List<DishBuff> buffs, Item.Properties properties) {
        this(typeKey, sellPrice, edibility, buffs, properties, false);
    }

    @SuppressWarnings("null")
    public CookingDishItem(String typeKey, int sellPrice, int edibility, List<DishBuff> buffs, Item.Properties properties, boolean drinkAnimation) {
        super(properties.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build()));
        this.typeKey = typeKey == null || typeKey.isBlank() ? DEFAULT_TYPE_KEY : typeKey;
        this.sellPrice = sellPrice;
        this.edibility = edibility;
        this.energy = computeEnergy(edibility);
        this.health = computeHealth(this.energy);
        this.buffs = List.copyOf(buffs);
        this.drinkAnimation = drinkAnimation;
    }

    private static int computeEnergy(int edibility) {
        if (edibility <= INEDIBLE_THRESHOLD) {
            return -300;
        }
        return (int) Math.ceil(edibility * 2.5D);
    }

    private static int computeHealth(int energy) {
        if (energy <= INEDIBLE_THRESHOLD) {
            return 0;
        }
        return (int) (energy * 0.45F);
    }

    @Override
    public String getItemTypeKey() {
        return typeKey;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice;
    }

    @Override
    public boolean isFood() {
        return edibility > INEDIBLE_THRESHOLD;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return energy;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return health;
    }

    public List<DishBuff> getBuffs() {
        return buffs;
    }

    @SuppressWarnings("null")
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return drinkAnimation ? UseAnim.DRINK : super.getUseAnimation(stack);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return super.useOn(context);
        }

        return PlacedFoodPlacement.place(context);
    }

    @SuppressWarnings("null")
    @Override
    public List<Component> getAfterEatTooltipLines(ItemStack stack) {
        if (buffs.isEmpty()) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        for (DishBuff buff : buffs) {
            String icon = iconFor(buff.type());
            String effectKey = effectTranslationKeyFor(buff.type());
            String valueKey = valueTranslationKeyFor(buff.type());
            ChatFormatting color = colorFor(buff.type());
            int displayAmount = displayAmountFor(buff);

            MutableComponent title = Component.literal("[")
                    .append(Component.translatable(effectKey))
                    .append("]")
                    .withStyle(color);

            lines.add(Component.literal(icon + " ")
                    .append(title)
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(valueKey, displayAmount).withStyle(ChatFormatting.GRAY)));
        }

        return lines;
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            if (health != 0) {
                int current = PlayerStardewDataAPI.getHealth(serverPlayer);
                int max = PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                PlayerStardewDataAPI.setHealth(serverPlayer, Math.max(0, Math.min(max, current + health)));
            }
            if (energy != 0) {
                if (energy > 0) {
                    PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
                } else {
                    PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
                }
            }

            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this).toString().equals("stardewcraft:squid_ink_ravioli"))
                serverPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        com.stardew.craft.effect.ModMobEffects.SQUID_INK_RAVIOLI.get(), 3600, 0));
            for (DishBuff buff : buffs) {
                applyBuff(serverPlayer, buff);
            }
        }
        return result;
    }

    @SuppressWarnings("null")
    private static void applyBuff(ServerPlayer player, DishBuff buff) {
        if (buff.amount() == 0 || buff.durationTicks() <= 0) {
            return;
        }

        switch (buff.type()) {
            case MAX_ENERGY -> PlayerStardewDataAPI.applyMaxEnergyBuff(player, buff.amount(), buff.durationTicks());
            case FISHING -> PlayerStardewDataAPI.applyFishingLevelBuff(player, buff.amount(), buff.durationTicks());
            case LUCK -> PlayerStardewDataAPI.applyLuckBuff(player, buff.amount(), buff.durationTicks());
            case SPEED -> {
                // Speed buff in dishes is level-based (+1 in vanilla cooking).
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.stardew.craft.effect.ModMobEffects.SPEED.get(),
                        buff.durationTicks(),
                        Math.max(0, buff.amount() - 1)
                ));
            }
            case FARMING -> PlayerStardewDataAPI.applyFarmingLevelBuff(player, buff.amount(), buff.durationTicks());
            case FORAGING -> PlayerStardewDataAPI.applyForagingLevelBuff(player, buff.amount(), buff.durationTicks());
            case MINING -> PlayerStardewDataAPI.applyMiningLevelBuff(player, buff.amount(), buff.durationTicks());
            case ATTACK -> PlayerStardewDataAPI.applyAttackBuff(player, buff.amount(), buff.durationTicks());
            case DEFENSE -> PlayerStardewDataAPI.applyDefenseBuff(player, buff.amount(), buff.durationTicks());
            case MAGNETIC_RADIUS -> PlayerStardewDataAPI.applyMagneticRadiusBuff(player, buff.amount(), buff.durationTicks());
            case AVOID_MONSTERS -> player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS.get(),
                    buff.durationTicks(),
                    Math.max(0, buff.amount() - 1),
                    false,
                    true,
                    true));
        }
    }

    private static String iconFor(BuffType type) {
        return switch (type) {
            case MAX_ENERGY -> TooltipConstants.ICON_BUFF_VIGOROUS;
            case FISHING -> TooltipConstants.ICON_BUFF_SEA_KING_BLESSING;
            case LUCK -> TooltipConstants.ICON_BUFF_SPIRIT_BLESSING;
            case SPEED -> TooltipConstants.ICON_BUFF_SPEED;
            case FARMING -> TooltipConstants.ICON_BUFF_FARMER_BLESSING;
            case FORAGING -> TooltipConstants.ICON_BUFF_FORAGER_BLESSING;
            case MINING -> TooltipConstants.ICON_BUFF_MINER_BLESSING;
            case ATTACK -> TooltipConstants.ICON_BUFF_WARRIOR_BLESSING;
            case DEFENSE -> TooltipConstants.ICON_BUFF_GUARDIAN_BLESSING;
            case MAGNETIC_RADIUS -> TooltipConstants.ICON_BUFF_MAGNETISM;
            case AVOID_MONSTERS -> TooltipConstants.ICON_BUFF_AVOID_MONSTERS;
        };
    }

    private static String effectTranslationKeyFor(BuffType type) {
        return switch (type) {
            case MAX_ENERGY -> "effect.stardewcraft.vigorous";
            case FISHING -> "effect.stardewcraft.sea_king_blessing";
            case LUCK -> "effect.stardewcraft.spirit_blessing";
            case SPEED -> "effect.stardewcraft.speed";
            case FARMING -> "effect.stardewcraft.farmer_blessing";
            case FORAGING -> "effect.stardewcraft.forager_blessing";
            case MINING -> "effect.stardewcraft.miner_blessing";
            case ATTACK -> "effect.stardewcraft.warrior_blessing";
            case DEFENSE -> "effect.stardewcraft.guardian_blessing";
            case MAGNETIC_RADIUS -> "effect.stardewcraft.magnetism";
            case AVOID_MONSTERS -> "effect.stardewcraft.avoid_monsters";
        };
    }

    private static String valueTranslationKeyFor(BuffType type) {
        return switch (type) {
            case MAX_ENERGY -> "stardewcraft.tooltip.buff.max_energy";
            case FISHING -> "stardewcraft.tooltip.buff.fishing_level";
            case LUCK -> "stardewcraft.tooltip.buff.luck";
            case SPEED -> "stardewcraft.tooltip.buff.speed";
            case FARMING -> "stardewcraft.tooltip.buff.farming_level";
            case FORAGING -> "stardewcraft.tooltip.buff.foraging_level";
            case MINING -> "stardewcraft.tooltip.buff.mining_level";
            case ATTACK -> "stardewcraft.tooltip.buff.attack";
            case DEFENSE -> "stardewcraft.tooltip.buff.defense";
            case MAGNETIC_RADIUS -> "stardewcraft.tooltip.buff.magnetic_radius";
            case AVOID_MONSTERS -> "stardewcraft.tooltip.buff.avoid_monsters";
        };
    }

    private static ChatFormatting colorFor(BuffType type) {
        return switch (type) {
            case FISHING -> ChatFormatting.AQUA;
            case LUCK -> ChatFormatting.GOLD;
            case SPEED -> ChatFormatting.BLUE;
            case MAX_ENERGY -> ChatFormatting.GREEN;
            case FARMING, FORAGING, MINING -> ChatFormatting.GREEN;
            case ATTACK -> ChatFormatting.RED;
            case DEFENSE -> ChatFormatting.DARK_AQUA;
            case MAGNETIC_RADIUS -> ChatFormatting.YELLOW;
            case AVOID_MONSTERS -> ChatFormatting.DARK_GREEN;
        };
    }

    private static int displayAmountFor(DishBuff buff) {
        return buff.amount();
    }
}
