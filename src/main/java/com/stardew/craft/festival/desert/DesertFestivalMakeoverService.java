package com.stardew.craft.festival.desert;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.event.SleepVoteTracker;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
import com.stardew.craft.port.net.minecraft.world.item.component.DyedItemColor;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class DesertFestivalMakeoverService {
    private static final BlockPos AREA_MIN = new BlockPos(-209, 63, -146);
    private static final BlockPos AREA_MAX = new BlockPos(-207, 66, -144);
    private static final String FLAG_PREFIX = "Y%d_DesertMakeoverClaimed";
    private static final Set<UUID> PLAYERS_INSIDE = new HashSet<>();

    private static final List<ResourceKey<TrimPattern>> TRIM_PATTERNS = List.of(
        TrimPatterns.SENTRY,
        TrimPatterns.DUNE,
        TrimPatterns.COAST,
        TrimPatterns.WILD,
        TrimPatterns.WARD,
        TrimPatterns.EYE,
        TrimPatterns.VEX,
        TrimPatterns.TIDE,
        TrimPatterns.SNOUT,
        TrimPatterns.RIB,
        TrimPatterns.SPIRE,
        TrimPatterns.WAYFINDER,
        TrimPatterns.SHAPER,
        TrimPatterns.SILENCE,
        TrimPatterns.RAISER,
        TrimPatterns.HOST,
        TrimPatterns.FLOW,
        TrimPatterns.BOLT
    );

    private static final List<ResourceKey<TrimMaterial>> TRIM_MATERIALS = List.of(
        TrimMaterials.QUARTZ,
        TrimMaterials.IRON,
        TrimMaterials.NETHERITE,
        TrimMaterials.REDSTONE,
        TrimMaterials.COPPER,
        TrimMaterials.GOLD,
        TrimMaterials.EMERALD,
        TrimMaterials.DIAMOND,
        TrimMaterials.LAPIS,
        TrimMaterials.AMETHYST
    );

    private static final Item[] TRIM_TEMPLATES = new Item[] {
        Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
        Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    };

    private DesertFestivalMakeoverService() {
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player == null || player.isSpectator() || !SleepVoteTracker.isInStardewDimension(player)) {
            if (player != null) {
                PLAYERS_INSIDE.remove(player.getUUID());
            }
            return;
        }
        if (!DesertFestivalService.isFestivalOpen()) {
            PLAYERS_INSIDE.remove(player.getUUID());
            return;
        }

        UUID uuid = player.getUUID();
        boolean inside = isInside(player.blockPosition());
        if (!inside) {
            PLAYERS_INSIDE.remove(uuid);
            return;
        }
        if (!PLAYERS_INSIDE.add(uuid)) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        String flag = annualFlag();
        if (data.hasMailFlag(flag)) {
            sendDialogue(player, "emily", "stardewcraft.desert_festival.makeover.already_styled");
            return;
        }

        data.addMailFlag(flag);
        PlayerDataEventHandler.syncPlayerData(player, data);
        giveRewards(player, makeoverRandom(player));
        sendDialogue(player, "emily", "stardewcraft.desert_festival.makeover.emily.done");
    }

    private static boolean isInside(BlockPos pos) {
        return pos.getX() >= AREA_MIN.getX() && pos.getX() <= AREA_MAX.getX()
            && pos.getY() >= AREA_MIN.getY() && pos.getY() <= AREA_MAX.getY()
            && pos.getZ() >= AREA_MIN.getZ() && pos.getZ() <= AREA_MAX.getZ();
    }

    private static String annualFlag() {
        return FLAG_PREFIX.formatted(StardewTimeManager.get().getCurrentYear());
    }

    private static Random makeoverRandom(ServerPlayer player) {
        long seed = player.getUUID().getMostSignificantBits()
            ^ player.getUUID().getLeastSignificantBits()
            ^ (long) StardewTimeManager.get().getCurrentYear() * 0x9E3779B97F4A7C15L;
        return new Random(seed);
    }

    private static void giveRewards(ServerPlayer player, Random random) {
        giveItem(player, makeoverLeather(player, Items.LEATHER_HELMET, random, 0));
        giveItem(player, makeoverLeather(player, Items.LEATHER_CHESTPLATE, random, 1));
        giveItem(player, makeoverLeather(player, Items.LEATHER_LEGGINGS, random, 2));
        giveItem(player, makeoverLeather(player, Items.LEATHER_BOOTS, random, 3));
        giveItem(player, new ItemStack(TRIM_TEMPLATES[random.nextInt(TRIM_TEMPLATES.length)]));
        player.inventoryMenu.broadcastChanges();
    }

    private static ItemStack makeoverLeather(ServerPlayer player, Item item, Random random, int slotIndex) {
        float hue = (random.nextFloat() + slotIndex * 0.23F) % 1.0F;
        int color = Mth.hsvToRgb(hue, 0.55F + random.nextFloat() * 0.3F, 0.65F + random.nextFloat() * 0.3F) & 0xFFFFFF;
        ItemStack stack = new ItemStack(item);
        PortItemData.set(stack, DataComponents.DYED_COLOR, new DyedItemColor(color, true));

        var patternLookup = player.level().registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
        var materialLookup = player.level().registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
        Holder<TrimPattern> pattern = patternLookup.getOrThrow(randomTrimPattern(random, slotIndex));
        Holder<TrimMaterial> material = materialLookup.getOrThrow(TRIM_MATERIALS.get(random.nextInt(TRIM_MATERIALS.size())));
        PortItemData.set(stack, DataComponents.TRIM, new ArmorTrim(material, pattern));
        return stack;
    }

    private static ResourceKey<TrimPattern> randomTrimPattern(Random random, int slotIndex) {
        int index = Math.floorMod(random.nextInt(TRIM_PATTERNS.size()) + slotIndex, TRIM_PATTERNS.size());
        return TRIM_PATTERNS.get(index);
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        ItemStack hudStack = stack.copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
    }

    private static void sendDialogue(ServerPlayer player, String npcId, String key) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(npcId, key, 0));
    }
}