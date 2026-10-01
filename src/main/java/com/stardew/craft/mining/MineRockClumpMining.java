package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineRockClumpBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Optional;

/** Rock ResourceClumps, distinct from GameLocation.breakStone and its ore/ladder rolls. */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class MineRockClumpMining {
    private MineRockClumpMining() {}

    public static Optional<BlockState> stateForSource(String sourceId) {
        return switch (sourceId) {
            case "C752" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_752.get().defaultBlockState());
            case "C754" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_754.get().defaultBlockState());
            case "C756" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_756.get().defaultBlockState());
            case "C758" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_758.get().defaultBlockState());
            case "C672" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_672.get().defaultBlockState());
            case "C622" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_622.get().defaultBlockState());
            case "C148" -> Optional.of(ModBlocks.MINE_ROCK_CLUMP_148.get().defaultBlockState());
            default -> Optional.empty();
        };
    }

    public static boolean isPickaxe(ItemStack tool) { return MineStoneMining.pickaxePower(tool) > 0; }

    private static int tier(ItemStack tool) {
        return tool.getItem() instanceof StardewPickaxeItem pickaxe ? pickaxe.getStardewTier() : 0;
    }

    public static int health(int sourceId) {
        return switch (sourceId) {
            case 672 -> 10;
            case 622, 148 -> 20;
            case 752, 754, 756, 758 -> 8;
            default -> throw new IllegalArgumentException("Unknown rock clump: " + sourceId);
        };
    }

    public static int minimumTier(int sourceId) {
        return switch (sourceId) { case 672 -> 2; case 622, 148 -> 3; default -> 0; };
    }

    public static boolean canMine(int sourceId, ItemStack tool) {
        return isPickaxe(tool) && tier(tool) >= minimumTier(sourceId);
    }

    public static String failureHint(int sourceId, ItemStack tool) {
        if (canMine(sourceId, tool)) return null;
        if (!isPickaxe(tool)) return "message.stardewcraft.resource_clump.requires_pickaxe";
        return "message.stardewcraft.resource_clump." + (sourceId == 672 ? "weak_boulder" : "weak_meteorite");
    }

    public static String inspectionHint(int sourceId) {
        return switch (sourceId) {
            case 672 -> "message.stardewcraft.resource_clump.inspect_boulder";
            case 622 -> "message.stardewcraft.resource_clump.inspect_meteorite";
            default -> null;
        };
    }

    public static int requiredSwings(int sourceId, ItemStack tool) {
        // ResourceClump reads upgradeLevel, not Pickaxe.additionalPower (Powerful).
        double damage = Math.max(1, (tier(tool) + 1) * 0.75);
        return (int) Math.ceil(health(sourceId) / damage);
    }

    public static int breakTicks(int sourceId, ItemStack tool) {
        boolean swift = tool.getItem() instanceof StardewPickaxeItem
                && StardewEnchantments.has(tool, StardewEnchantments.SWIFT);
        return MineStoneMining.breakTicks(requiredSwings(sourceId, tool), 1, tier(tool), swift);
    }

    public static float energyCost(int sourceId, ItemStack tool, int miningLevel) {
        if (tool.getItem() instanceof StardewPickaxeItem
                && StardewEnchantments.has(tool, StardewEnchantments.EFFICIENT)) return 0;
        return MineStoneMining.energyCost(requiredSwings(sourceId, tool), 1, miningLevel);
    }

    // Capture after BreakEvent permission checks, while the main block still exists.
    // One synchronous break context; overwritten on the next break and consumed only by its own drop event.
    private record BreakOrigin(ServerLevel level, java.util.UUID player, BlockPos hit, BlockPos main,
                               int sourceId, long tick) {}
    private static final ThreadLocal<BreakOrigin> BREAK_ORIGIN = new ThreadLocal<>();

    public static void rememberBreakOrigin(ServerPlayer player, BlockPos hit, BlockState state) {
        BREAK_ORIGIN.remove();
        if (player.isCreative() || !(state.getBlock() instanceof MineRockClumpBlock clump)) return;
        BlockPos main = clump.findMainPos(player.level(), hit, state);
        if (main != null) BREAK_ORIGIN.set(new BreakOrigin(player.serverLevel(), player.getUUID(), hit.immutable(),
                main.immutable(), clump.sourceId(), player.level().getGameTime()));
    }

    private static BlockPos consumeBreakOrigin(BlockDropsEvent event, ServerPlayer player, int sourceId) {
        BreakOrigin origin = BREAK_ORIGIN.get();
        BREAK_ORIGIN.remove();
        return origin != null && origin.level == event.getLevel() && origin.player.equals(player.getUUID())
                && origin.hit.equals(event.getPos()) && origin.sourceId == sourceId
                && origin.tick == event.getLevel().getGameTime() ? origin.main : null;
    }

    public static boolean meteoriteHasShard(long worldSeed, BlockPos main) {
        // Source tileLocation.Y is horizontal; preserve its float multiplication before CreateRandom.
        return StardewDeterministicRandom.createFromDoubles(worldSeed, main.getX(), main.getZ() * 983728f, 0, 0)
                .nextDouble() < 0.25;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof MineRockClumpBlock clump)
                || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        var main = clump.findMainPos(player.level(), event.getPos(), event.getState());
        if (!canMine(clump.sourceId(), player.getMainHandItem()) || main == null
                || (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                && (!FarmAreaProtectionEvents.canModifyAt(player, main)
                || !FarmAreaProtectionEvents.canModifyAt(player, event.getPos())))) {
            event.setCanceled(true);
            String hint = failureHint(clump.sourceId(), player.getMainHandItem());
            if (hint != null) com.stardew.craft.network.payload.HudHintPayload.send(player, hint);
            return;
        }
        float cost = energyCost(clump.sourceId(), player.getMainHandItem(), PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING));
        if (!PlayerStardewDataAPI.canConsumeEnergy(player, cost)) {
            event.setCanceled(true);
            com.stardew.craft.network.payload.HudHintPayload.send(player, "stardewcraft.message.player.exhausted");
        }
    }

    @SubscribeEvent
    public static void afterBreak(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof MineRockClumpBlock clump)) return;
        event.getDrops().clear();
        event.setDroppedExperience(0);
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative()) return;
        ItemStack tool = event.getTool();
        if (!canMine(clump.sourceId(), tool)) return;
        float cost = energyCost(clump.sourceId(), tool, PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING));
        if (!PlayerStardewDataAPI.consumeEnergyOrNotify(player, cost)) return;
        BlockPos anchor = consumeBreakOrigin(event, player, clump.sourceId());
        int stoneCount = clump.sourceId() == 672 ? 15 : clump.sourceId() == 622 ? 8 : 10;
        Block.popResource(event.getLevel(), event.getPos(), new ItemStack(ModItems.STONE.get(), stoneCount));
        if (clump.sourceId() == 622) {
            Block.popResource(event.getLevel(), event.getPos(), new ItemStack(ModItems.IRIDIUM_ORE.get(), 10));
            Block.popResource(event.getLevel(), event.getPos(), new ItemStack(ModItems.OMNI_GEODE.get(), 2));
            if (anchor != null && meteoriteHasShard(event.getLevel().getSeed(), anchor))
                Block.popResource(event.getLevel(), event.getPos(), new ItemStack(ModItems.PRISMATIC_SHARD.get()));
        }
        ItemStack note = SecretNoteService.tryCreateFromSource(player, event.getLevel().getRandom(), 0.05F);
        if (!note.isEmpty()) Block.popResource(event.getLevel(), event.getPos(), note);
        // The source gives no mining XP, ordinary-stone bonus roll, or ladder roll for these clumps.
    }
}
