package com.stardew.craft.book;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BookPowerEffects {
    private static final String BOOK_DEFENSE = "Book_Defense";
    private static final String BOOK_BOMBS = "Book_Bombs";
    private static final String BOOK_TRASH = "Book_Trash";
    private static final String BOOK_ARTIFACT = "Book_Artifact";
    private static final String BOOK_MARLON = "Book_Marlon";
    private static final String BOOK_WILD_SEEDS = "Book_WildSeeds";
    private static final String BOOK_WOODCUTTING = "Book_Woodcutting";
    private static final String BOOK_DIAMONDS = "Book_Diamonds";
    private static final String BOOK_CRABBING = "Book_Crabbing";
    private static final String BOOK_ROE = "Book_Roe";
    private static final String BOOK_FRIENDSHIP = "Book_Friendship";
    private static final String BOOK_VOID = "Book_Void";
    private static final String BOOK_MYSTERY = "Book_Mystery";
    private static final String BOOK_GRASS = "Book_Grass";
    private static final String BOOK_SPEED = "Book_Speed";
    private static final String BOOK_SPEED_2 = "Book_Speed2";
    private static final String BOOK_HORSE = "Book_Horse";

    private static final ResourceLocation PLAYER_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "book_speed"
    );
    private static final ResourceLocation GRASS_PLAYER_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "book_grass_speed"
    );
    private static final ResourceLocation HORSE_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "book_horse_speed"
    );
    private static final double PLAYER_SPEED_STEP = 0.05D;
    private static final double GRASS_PLAYER_SPEED_BONUS = 0.12D;
    private static final double HORSE_SPEED_BONUS = 0.052083333333333336D;
    private static final double GRASS_SPEED_FACTOR = 0.900D;
    private static final double GRASS_BOOK_SPEED_FACTOR = 1.030D;

    private static final Map<UUID, Integer> LAST_HORSE_BY_PLAYER = new HashMap<>();

    private BookPowerEffects() {
    }

    public static int getDefenseBonus(PlayerStardewData data) {
        return hasPower(data, BOOK_DEFENSE) ? 1 : 0;
    }

    public static float applyBombDamageReduction(PlayerStardewData data, float damage) {
        return hasPower(data, BOOK_BOMBS) ? damage * 0.75F : damage;
    }

    public static float getTrashCanChanceBonus(PlayerStardewData data) {
        return hasPower(data, BOOK_TRASH) ? 0.2F : 0.0F;
    }

    public static int applyArtifactSellPrice(PlayerStardewData data, String itemTypeKey, int basePrice) {
        if (!hasPower(data, BOOK_ARTIFACT)) {
            return basePrice;
        }
        if (!"stardewcraft.type.artifact".equals(itemTypeKey)
                && !"stardewcraft.type.artifact_quality".equals(itemTypeKey)) {
            return basePrice;
        }
        return basePrice * 3;
    }

    public static int applyMarlonRecoveryPrice(PlayerStardewData data, int price) {
        if (!hasPower(data, BOOK_MARLON)) {
            return price;
        }
        return Math.max(1, (int) Math.floor(price * 0.5D));
    }

    public static double getWildSeedsChance(PlayerStardewData data) {
        return hasPower(data, BOOK_WILD_SEEDS) ? 0.09D : 0.05D;
    }

    public static int applyWoodcuttingDouble(PlayerStardewData data, int count, RandomSource random) {
        if (count <= 0 || !hasPower(data, BOOK_WOODCUTTING)) {
            return count;
        }
        return random.nextDouble() < 0.05D ? count * 2 : count;
    }

    public static boolean shouldDropDiamondFromStone(PlayerStardewData data, RandomSource random) {
        return hasPower(data, BOOK_DIAMONDS) && random.nextDouble() < 0.0066D;
    }

    public static void applyCrabbingDouble(PlayerStardewData data, ItemStack stack, RandomSource random) {
        if (stack.isEmpty() || !hasPower(data, BOOK_CRABBING)) {
            return;
        }
        if (random.nextDouble() < 0.25D) {
            stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * 2));
        }
    }

    public static void tryAddRoeTreasure(PlayerStardewData data, List<ItemStack> treasures, ItemStack caughtFish, RandomSource random) {
        if (!hasPower(data, BOOK_ROE) || caughtFish.isEmpty() || random.nextDouble() >= 0.25D) {
            return;
        }
        if (!isFishWithRoe(caughtFish)) {
            return;
        }
        ItemStack roe = new ItemStack(ModItems.ROE.get());
        PreservesItem.createFlavored(PreserveType.ROE, caughtFish, roe);
        treasures.add(roe);
    }

    public static int applyFriendshipGain(PlayerStardewData data, int delta) {
        if (delta <= 0 || !hasPower(data, BOOK_FRIENDSHIP)) {
            return delta;
        }
        return (int) (delta * 1.1F);
    }

    public static void applyVoidMonsterDropDuplicate(PlayerStardewData data, Collection<ItemEntity> drops,
                                                     LivingEntity entity, RandomSource random) {
        if (drops.isEmpty() || !hasPower(data, BOOK_VOID) || random.nextDouble() >= 0.03D) {
            return;
        }
        List<ItemEntity> copies = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                copies.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack.copy()));
            }
        }
        drops.addAll(copies);
    }

    public static double applyMysteryBoxChance(PlayerStardewData data, double baseChance) {
        return baseChance * (hasPower(data, BOOK_MYSTERY) ? 0.88D : 0.66D);
    }

    public static double getGrassSpeedFactor(PlayerStardewData data) {
        return getGrassSpeedFactor(hasPower(data, BOOK_GRASS));
    }

    public static double getGrassSpeedFactor(boolean hasGrassPower) {
        return hasGrassPower ? GRASS_BOOK_SPEED_FACTOR : GRASS_SPEED_FACTOR;
    }

    @OnlyIn(Dist.CLIENT)
    public static double getClientGrassSpeedFactor() {
        return getGrassSpeedFactor(com.stardew.craft.client.ClientPlayerDataCache.hasStat(BOOK_GRASS));
    }

    public static void tickMovement(ServerPlayer player, PlayerStardewData data) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AbstractHorse horse) {
            setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), PLAYER_SPEED_MODIFIER, 0.0D);
            setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), GRASS_PLAYER_SPEED_MODIFIER, 0.0D);
            removePreviousHorseModifier(player, horse.getId());
            setModifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), HORSE_SPEED_MODIFIER,
                    hasPower(data, BOOK_HORSE) ? HORSE_SPEED_BONUS : 0.0D);
            LAST_HORSE_BY_PLAYER.put(player.getUUID(), horse.getId());
            return;
        }

        removePreviousHorseModifier(player, -1);
        double speedBonus = 0.0D;
        if (hasPower(data, BOOK_SPEED)) {
            speedBonus += PLAYER_SPEED_STEP;
        }
        if (hasPower(data, BOOK_SPEED_2)) {
            speedBonus += PLAYER_SPEED_STEP;
        }
        setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), PLAYER_SPEED_MODIFIER, speedBonus);
        setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), GRASS_PLAYER_SPEED_MODIFIER,
                hasPower(data, BOOK_GRASS) && isInGrassSpeedBlock(player) ? GRASS_PLAYER_SPEED_BONUS : 0.0D);
    }

    private static boolean isInGrassSpeedBlock(ServerPlayer player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        if (isGrassSpeedBlock(feet)) {
            return true;
        }
        BlockState below = player.level().getBlockState(player.blockPosition().below());
        return isGrassSpeedBlock(below);
    }

    private static boolean isGrassSpeedBlock(BlockState state) {
        return state.getBlock() instanceof PastureGrassBlock
                || state.getBlock() instanceof WildWeedsBlock
                || state.getBlock() instanceof StardewCropBlock;
    }

    private static boolean hasPower(PlayerStardewData data, String statKey) {
        return data != null && data.getStat(statKey) > 0;
    }

    private static boolean isFishWithRoe(ItemStack stack) {
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        return "stardewcraft.type.fish".equals(typeKey)
                || "stardewcraft.type.legendary_fish".equals(typeKey);
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (amount > 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(
                    id,
                    amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }

    private static void removePreviousHorseModifier(ServerPlayer player, int currentHorseId) {
        Integer previousHorseId = LAST_HORSE_BY_PLAYER.get(player.getUUID());
        if (previousHorseId == null || previousHorseId == currentHorseId) {
            return;
        }

        Entity previous = player.level().getEntity(previousHorseId);
        if (previous instanceof AbstractHorse horse) {
            setModifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), HORSE_SPEED_MODIFIER, 0.0D);
        }
        LAST_HORSE_BY_PLAYER.remove(player.getUUID());
    }
}
