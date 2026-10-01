package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredRegister;
import com.stardew.craft.port.net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/** Upgrades existing built-in farm soil without regenerating terrain or loading remote chunks. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LegacyFarmSoilMigration {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StardewCraft.MODID);
    // Saved with the changed chunk, so a crash cannot persist completion before its block changes.
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<List<UUID>>> COMPLETED =
            ATTACHMENTS.register("farm_soil_migration_v1", () -> AttachmentType
                    .builder(() -> List.<UUID>of()).serialize(UUIDUtil.CODEC.listOf()).build());
    private static final int CHUNKS_PER_TICK = 2;
    private static final Map<ServerLevel, LinkedHashMap<Long, LevelChunk>> PENDING = new IdentityHashMap<>();

    private LegacyFarmSoilMigration() {}

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                && event.getChunk() instanceof LevelChunk chunk) {
            if (chunk.getPos().getMaxBlockX() < FarmInstanceAllocator.FARM_REGION_START
                    || chunk.getPos().getMaxBlockZ() < FarmInstanceAllocator.FARM_REGION_START) return;
            PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), chunk);
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending == null) return;
        pending.remove(event.getChunk().getPos().toLong(), event.getChunk());
        if (pending.isEmpty()) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending == null) return;
        // Detach first: block updates must never invalidate the pending iterator.
        List<LevelChunk> batch = new ArrayList<>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext() && batch.size() < CHUNKS_PER_TICK) {
            batch.add(iterator.next());
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);
        for (LevelChunk chunk : batch) {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) {
                migrate(level, chunk);
            }
        }
    }

    private static void migrate(ServerLevel level, LevelChunk chunk) {
        var registry = FarmInstanceRegistry.get(level.getServer());
        var candidates = new LinkedHashSet<FarmInstance>();
        // Slot boundaries need not align with chunk boundaries. Check all four corners.
        for (int x : new int[]{chunk.getPos().getMinBlockX(), chunk.getPos().getMaxBlockX()}) {
            for (int z : new int[]{chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockZ()}) {
                UUID owner = registry.getOwnerAt(new BlockPos(x, 0, z));
                FarmInstance farm = owner == null ? null : registry.getFarm(owner);
                if (farm != null && farm.isInitialized() && isBuiltin(farm)) candidates.add(farm);
            }
        }
        if (candidates.isEmpty()) return;
        List<UUID> completed = new ArrayList<>(com.stardew.craft.port.PortAttachments.getData(chunk, COMPLETED));
        for (FarmInstance farm : candidates) {
            if (completed.contains(farm.getInstanceId())) continue;
            BlockPos min = farm.getFarmBoundsMin(), max = farm.getFarmBoundsMax();
            if (max.getX() < chunk.getPos().getMinBlockX() || min.getX() > chunk.getPos().getMaxBlockX()
                    || max.getZ() < chunk.getPos().getMinBlockZ() || min.getZ() > chunk.getPos().getMaxBlockZ()) continue;
            int changed = 0;
            for (int i = 0; i < chunk.getSections().length; i++) {
                BlockPos sectionOrigin = new BlockPos(chunk.getPos().getMinBlockX(),
                        chunk.getSectionYFromSectionIndex(i) << 4, chunk.getPos().getMinBlockZ());
                changed += migrateSection(chunk.getSections()[i], sectionOrigin, min, max,
                        state -> replacement(state, ModBlocks.YELLOW_DIRT.get(),
                                ModBlocks.DIRT.get().defaultBlockState(), ModBlocks.FARMLAND.get().defaultBlockState()),
                        (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            }
            completed.add(farm.getInstanceId());
            com.stardew.craft.port.PortAttachments.setData(chunk, COMPLETED, List.copyOf(completed));
            chunk.setUnsaved(true);
            if (changed > 0) StardewCraft.LOGGER.debug("[FARM_SOIL] Migrated {} blocks in farm {} chunk {}",
                    changed, farm.getInstanceId(), chunk.getPos());
        }
    }

    private static boolean isBuiltin(FarmInstance farm) {
        for (FarmType type : FarmType.values()) {
            if (farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(type))) return true;
        }
        return false;
    }

    static BlockState replacement(BlockState state, Block yellowDirt, BlockState dirt, BlockState farmland) {
        if (state.is(yellowDirt)) return dirt;
        if (state.is(Blocks.FARMLAND)) return farmland.setValue(FarmBlock.MOISTURE, state.getValue(FarmBlock.MOISTURE));
        return state;
    }

    static int migrateSection(LevelChunkSection section, BlockPos origin, BlockPos min, BlockPos max,
                              UnaryOperator<BlockState> replacement, BiConsumer<BlockPos, BlockState> write) {
        if (origin.getY() > max.getY() || origin.getY() + 15 < min.getY() || section.hasOnlyAir()
                || !section.getStates().maybeHas(state -> replacement.apply(state) != state)) return 0;
        int changed = 0;
        for (int y = Math.max(0, min.getY() - origin.getY()); y <= Math.min(15, max.getY() - origin.getY()); y++) {
            for (int x = Math.max(0, min.getX() - origin.getX()); x <= Math.min(15, max.getX() - origin.getX()); x++) {
                for (int z = Math.max(0, min.getZ() - origin.getZ()); z <= Math.min(15, max.getZ() - origin.getZ()); z++) {
                    BlockState before = section.getBlockState(x, y, z), after = replacement.apply(before);
                    if (before != after) {
                        write.accept(origin.offset(x, y, z), after);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
