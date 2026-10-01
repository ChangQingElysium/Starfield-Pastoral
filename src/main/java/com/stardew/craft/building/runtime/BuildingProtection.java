package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reservation is distinct from the immutable blocks supplied by a finished template. */
public final class BuildingProtection {
    private static final ThreadLocal<Integer> WRITES = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> TRANSFER = ThreadLocal.withInitial(() -> false);
    public static boolean transferring() { return TRANSFER.get(); }
    public static void transfer(Runnable action) { TRANSFER.set(true); try { internal(action); } finally { TRANSFER.remove(); } }
    private static final Map<UUID, Set<BlockPos>> masks = new HashMap<>();
    private BuildingProtection() {}
    public static void clearMasks() { masks.clear(); }
    public static void internal(Runnable action) {
        WRITES.set(WRITES.get() + 1);
        try { action.run(); } finally { WRITES.set(WRITES.get() - 1); }
    }
    public static boolean protects(ServerLevel level, BlockPos pos) {
        if (WRITES.get() > 0 || !level.getServer().isSameThread()) return false;
        var data = BuildingWorldData.peek(level.getServer());
        if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) {
            var pets = com.stardew.craft.pet.PetWorldData.peek(level.getServer());
            // During placement, onPlace has not yet committed bowl identity. Protect saved bowls only,
            // so MinecraftForge can accept or roll back the item transaction before creating its building.
            if (pets != null && pets.bowl(pos) != null) return true;
            if (pets != null) for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) {
                var manager = pos.offset(x, 1, z);
                if (pets.bowl(manager) != null && (data == null || data.occupying(level.dimension().location(), manager) == null)) return true;
            }
        }
        if (data == null) return false;
        UUID id = data.occupying(level.dimension().location(), pos);
        BuildingRecord record = id == null ? null : data.find(id);
        if (record == null) {
            UUID aboveId = data.occupying(level.dimension().location(), pos.above());
            var above = aboveId == null ? null : data.find(aboveId);
            if (above != null && com.stardew.craft.pet.PetBowlBuildings.isBowl(above.family())) return true;
            if (above != null && above.mode() == BuildingRecord.Mode.PREFAB) {
                if(!PrefabDefinitions.available(above))return true;
                if (above.phase() == BuildingRecord.Phase.CONSTRUCTING) return true;
                if (above.phase() == BuildingRecord.Phase.UPGRADING && PrefabDefinitions.transform(
                        PrefabDefinitions.get(above.family()).tier(above.tier() + 1).bounds(), above.anchor(),
                        PrefabDefinitions.rotation(above.facing())).contains(pos.above())) return true;
            }
        }
        if(record != null && (data.transfer(id)!=null || BuildingRemovalJournal.get(level.getServer()).contains(id)))return true;
        if(record != null && com.stardew.craft.greenhouse.GreenhouseBuildings.protectsWholeClaim(record)) return true;
        if (record != null && com.stardew.craft.pet.PetBowlBuildings.isBowl(record.family())) return pos.equals(record.manager());
        if (record == null || record.mode() != BuildingRecord.Mode.PREFAB) return false;
        if (data.transfer(id) != null || !PrefabDefinitions.available(record)) return true;
        if (record.phase() == BuildingRecord.Phase.CONSTRUCTING) return true;
        if (record.phase() == BuildingRecord.Phase.UPGRADING && PrefabDefinitions.transform(
                PrefabDefinitions.get(record.family()).tier(record.tier() + 1).bounds(), record.anchor(),
                PrefabDefinitions.rotation(record.facing())).contains(pos)) return true;
        if (level.getBlockState(pos).is(com.stardew.craft.block.ModBlocks.UPGRADE_NOTICE.get())) return true;
        return masks.computeIfAbsent(id, ignored -> {
            var tier = PrefabDefinitions.get(record.family()).tier(record.tier());
            var rotation = PrefabDefinitions.rotation(record.facing());
            Set<BlockPos> result = new HashSet<>();
            for (var cell : PrefabDefinitions.template(level, tier).cells()) {
                if (!cell.state().isAir()) result.add(PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation));
            }
            result.addAll(FishPondPrefabs.supports(level,record));
            return Set.copyOf(result);
        }).contains(pos);
    }
    public static boolean deniesReplacement(ServerLevel level, BlockPos pos, BlockState next) {
        if (!protects(level, pos)) return false;
        // Door toggles and facility state updates remain available; replacing their block does not.
        return level.getBlockState(pos).getBlock() != next.getBlock();
    }

    /** Stop extraction or consumption before a protected decoration awards items or food effects. */
    public static boolean deniesInteraction(ServerLevel level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return (block instanceof net.minecraft.world.level.block.FlowerPotBlock
                || block instanceof com.stardew.craft.block.cooking.CookingPlacedFoodBlock
                || block instanceof net.minecraft.world.level.block.CakeBlock
                || block instanceof net.minecraft.world.level.block.CandleCakeBlock
                || block instanceof com.stardew.craft.templates.TemplateBlock) && protects(level, pos);
    }

    public static boolean blockInteraction(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (FishPondPrefabs.interact(event)) return true;
        if (event.getItemStack().getItem() instanceof BuildingBlueprintItem
                || event.getItemStack().getItem() instanceof BuildingUpgradePermitItem) {
            event.setUseBlock(com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState.FALSE.toResult());
            return false;
        }
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !deniesInteraction(player.serverLevel(), event.getPos())) return false;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        // Vanilla pots predict flower removal locally. Restore both the block and inventory;
        // the server must never invoke useWithoutItem, even in creative or with the off hand.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(player.serverLevel(), event.getPos()));
        var blockEntity = player.serverLevel().getBlockEntity(event.getPos());
        if (blockEntity != null && blockEntity.getUpdatePacket() != null)
            player.connection.send(blockEntity.getUpdatePacket());
        player.inventoryMenu.broadcastFullState();
        return true;
    }
}
