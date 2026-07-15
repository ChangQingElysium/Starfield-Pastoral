package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import com.stardew.craft.network.payload.OpenSecretNote20QuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla secret note 20: trade a rabbit's foot to the truck driver for the Special Charm. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNote20Service {
    public static final String NOTE_ID = "stardewcraft:20";
    public static final String DONE_FLAG = "SecretNote20_done";
    public static final String SPECIAL_CHARM_FLAG = "HasSpecialCharm";
    public static final String SPECIAL_CHARM_SPECIAL_ITEM = "stardewcraft:special_charm";
    public static final String INTERACTION_TAG = "stardewcraft_secret_note_20_truck";
    public static final String TARGET_ID = "secret_note_20_truck_driver";
    public static final double DAILY_LUCK_BONUS = 0.025D;

    public static final BlockPos TRUCK_MIN = new BlockPos(122, 67, -21);
    public static final BlockPos TRUCK_MAX = new BlockPos(123, 67, -21);
    public static final AABB TRUCK_BOUNDS = new AABB(122, 67, -21, 124, 68, -20);

    private static final TagKey<Item> RABBIT_FEET = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "rabbit_feet"));

    private SecretNote20Service() {
    }

    public static boolean hasReadNote(PlayerStardewData data) {
        return data != null && data.hasSeenSecretNote(NOTE_ID);
    }

    public static boolean canUseTruck(PlayerStardewData data) {
        return hasReadNote(data)
                && !data.hasMailFlag(DONE_FLAG)
                && !data.hasMailFlag(SPECIAL_CHARM_FLAG)
                && !data.hasSpecialItem(SPECIAL_CHARM_SPECIAL_ITEM);
    }

    public static boolean hasSpecialCharm(PlayerStardewData data) {
        return data != null && (data.hasMailFlag(SPECIAL_CHARM_FLAG)
                || data.hasSpecialItem(SPECIAL_CHARM_SPECIAL_ITEM));
    }

    public static double applyDailyLuckBonus(PlayerStardewData data, double baseDailyLuck) {
        double value = baseDailyLuck + (hasSpecialCharm(data) ? DAILY_LUCK_BONUS : 0.0D);
        return Math.max(-0.2D, Math.min(0.2D, value));
    }

    public static boolean isTruckBlock(BlockPos pos) {
        return pos.getX() >= TRUCK_MIN.getX() && pos.getX() <= TRUCK_MAX.getX()
                && pos.getY() == TRUCK_MIN.getY()
                && pos.getZ() == TRUCK_MIN.getZ();
    }

    public static boolean isTruckInteraction(Entity entity) {
        return entity instanceof Interaction
                && (entity.getTags().contains(INTERACTION_TAG)
                || TRUCK_BOUNDS.inflate(0.05D).intersects(entity.getBoundingBox()));
    }

    public static void ensureInteractions(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        ensureInteraction(level, TRUCK_MIN);
        ensureInteraction(level, TRUCK_MAX);
    }

    private static void ensureInteraction(ServerLevel level, BlockPos pos) {
        AABB cell = new AABB(pos).inflate(0.05D);
        boolean exists = level.getEntitiesOfClass(Interaction.class, cell,
                entity -> entity.getTags().contains(INTERACTION_TAG)).stream().findAny().isPresent();
        if (exists) return;

        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:interaction");
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 1.0F);
        tag.putBoolean("response", true);

        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(pos.getX() + 0.5D));
        position.add(DoubleTag.valueOf(pos.getY()));
        position.add(DoubleTag.valueOf(pos.getZ() + 0.5D));
        tag.put("Pos", position);

        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(INTERACTION_TAG));
        tag.put("Tags", tags);

        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
        if (entity != null) level.addFreshEntity(entity);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) ensureInteractions(level);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ensureInteractions(player.serverLevel());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ensureInteractions(player.serverLevel());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (handleInteraction(event.getEntity(), event.getHand(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (handleInteraction(event.getEntity(), event.getHand(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean handleInteraction(net.minecraft.world.entity.player.Player rawPlayer,
                                             InteractionHand hand, Entity target) {
        if (!(rawPlayer instanceof ServerPlayer player) || hand != InteractionHand.MAIN_HAND) return false;
        if (!ModDimensions.STARDEW_VALLEY.equals(player.level().dimension()) || !isTruckInteraction(target)) return false;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (canUseTruck(data)) {
            PacketDistributor.sendToPlayer(player, new OpenSecretNote20QuestionPayload());
        }
        return true;
    }

    public static void handleQuestionResponse(ServerPlayer player, boolean accepted) {
        if (!accepted || !ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) return;
        if (player.distanceToSqr(TRUCK_BOUNDS.getCenter()) > 64.0D) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!canUseTruck(data)) return;
        if (!consumeRabbitFoot(player)) {
            PacketDistributor.sendToPlayer(player, new OpenObjectDialoguePayload(
                    Component.translatable("stardewcraft.secret_note.20.driver.no_foot")));
            return;
        }

        data.addMailFlag(DONE_FLAG);
        grantSpecialCharm(player, data);
        ItemStack charm = new ItemStack(ModItems.SPECIAL_CHARM.get());
        if (!player.getInventory().add(charm)) player.drop(charm, false);
        player.getInventory().setChanged();
        player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0F, 1.12F);
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                "", "stardewcraft.item.special_charm.obtained", 0,
                SPECIAL_CHARM_SPECIAL_ITEM, false));
    }

    public static boolean grantSpecialCharm(ServerPlayer player, PlayerStardewData data) {
        boolean changed = false;
        if (!data.hasMailFlag(SPECIAL_CHARM_FLAG)) {
            data.addMailFlag(SPECIAL_CHARM_FLAG);
            changed = true;
        }
        if (!data.hasSpecialItem(SPECIAL_CHARM_SPECIAL_ITEM)) {
            data.addSpecialItem(SPECIAL_CHARM_SPECIAL_ITEM);
            changed = true;
        }
        if (changed) {
            PlayerDataManager.get().savePlayerData(player.getUUID(), data);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    private static boolean consumeRabbitFoot(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(RABBIT_FEET)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
