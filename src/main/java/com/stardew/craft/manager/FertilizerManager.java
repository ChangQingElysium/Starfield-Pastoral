package com.stardew.craft.manager;

import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.network.FertilizerSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 肥料管理器
 * 记录耕地上施加的肥料类型，允许农作物与肥料共存
 */
public class FertilizerManager extends SavedData {
    private static final String DATA_NAME = "stardew_fertilizer_manager";

    // SavedData and crop simulation are both owned by the server thread.
    private final Map<GlobalPos, FertilizerType> fertilizerMap = new HashMap<>();

    public FertilizerManager() {}

    /**
     * 获取服务器级别的肥料管理器实例
     */
    public static FertilizerManager get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                FertilizerManager::new,
                                FertilizerManager::load
                        ),
                        DATA_NAME
                );
    }

    /** Adds fertilizer without replacing an existing type. */
    public boolean tryApplyFertilizer(ServerLevel level, BlockPos pos, FertilizerType type) {
        if (!(level.getBlockState(pos).getBlock() instanceof FarmBlock)) {
            return false;
        }
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        if (fertilizerMap.putIfAbsent(globalPos, type) != null) {
            return false;
        }
        setDirty();
        syncChangeToTrackingPlayers(level, pos, type);
        return true;
    }

    /**
     * 获取某个位置的肥料类型
     */
    @SuppressWarnings("null")
    @Nullable
    public FertilizerType getFertilizer(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return fertilizerMap.get(GlobalPos.of(serverLevel.dimension(), pos));
    }

    /**
     * 移除某个位置的肥料记录
     */
    @SuppressWarnings("null")
    public boolean removeFertilizer(ServerLevel level, BlockPos pos) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        if (fertilizerMap.remove(globalPos) == null) {
            return false;
        }
        setDirty();
        syncChangeToTrackingPlayers(level, pos, null);
        return true;
    }

    /**
     * 检查某个位置是否有肥料
     */
    public boolean hasFertilizer(Level level, BlockPos pos) {
        return getFertilizer(level, pos) != null;
    }
    
    /** Syncs fertilizer within the player's configured chunk view distance. */
    public void syncAllFertilizersToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ResourceKey<Level> dimKey = level.dimension();
        ChunkPos playerChunk = player.chunkPosition();
        int viewDistance = level.getServer().getPlayerList().getViewDistance() + 1;

        Map<Long, List<FertilizerSyncPacket.Entry>> entriesByChunk = new HashMap<>();
        for (Map.Entry<GlobalPos, FertilizerType> entry : fertilizerMap.entrySet()) {
            GlobalPos gpos = entry.getKey();
            if (!gpos.dimension().equals(dimKey)) {
                continue;
            }
            ChunkPos fertilizerChunk = new ChunkPos(gpos.pos());
            if (Math.abs((long) fertilizerChunk.x - playerChunk.x) > viewDistance
                    || Math.abs((long) fertilizerChunk.z - playerChunk.z) > viewDistance) {
                continue;
            }
            entriesByChunk
                    .computeIfAbsent(fertilizerChunk.toLong(), ignored -> new ArrayList<>())
                    .add(syncEntry(gpos.pos(), entry.getValue()));
        }
        for (Map.Entry<Long, List<FertilizerSyncPacket.Entry>> chunk : entriesByChunk.entrySet()) {
            sortSyncEntries(chunk.getValue());
            PacketDistributor.sendToPlayer(
                    player,
                    FertilizerSyncPacket.chunkSnapshot(
                            dimKey.location(),
                            new ChunkPos(chunk.getKey()),
                            chunk.getValue()));
        }
    }

    /**
     * Sync fertilizer data for a chunk after Minecraft has sent that chunk to the client.
     */
    public void syncFertilizersInChunkToPlayer(
            ServerPlayer player,
            ServerLevel level,
            ChunkPos chunkPos
    ) {
        ResourceKey<Level> dimKey = level.dimension();
        List<BlockPos> invalidPositions = new ArrayList<>();
        List<FertilizerSyncPacket.Entry> snapshot = new ArrayList<>();
        for (Map.Entry<GlobalPos, FertilizerType> entry
                : new ArrayList<>(fertilizerMap.entrySet())) {
            GlobalPos gpos = entry.getKey();
            if (!gpos.dimension().equals(dimKey) || !new ChunkPos(gpos.pos()).equals(chunkPos)) {
                continue;
            }
            if (level.isLoaded(gpos.pos()) && !(level.getBlockState(gpos.pos()).getBlock() instanceof FarmBlock)) {
                invalidPositions.add(gpos.pos());
                continue;
            }
            snapshot.add(syncEntry(gpos.pos(), entry.getValue()));
        }
        invalidPositions.forEach(pos -> removeFertilizer(level, pos));
        sortSyncEntries(snapshot);
        PacketDistributor.sendToPlayer(
                player,
                FertilizerSyncPacket.chunkSnapshot(dimKey.location(), chunkPos, snapshot));
    }

    /** Releases client cache for a chunk which is no longer tracked. */
    public void clearFertilizersInChunkForPlayer(
            ServerPlayer player,
            ServerLevel level,
            ChunkPos chunkPos
    ) {
        PacketDistributor.sendToPlayer(
                player,
                FertilizerSyncPacket.chunkSnapshot(
                        level.dimension().location(), chunkPos, List.of()));
    }

    /**
     * 清理无效肥料：当对应方块不再是耕地时，移除肥料记录并同步客户端。
     * 这是为了解决耕地被破坏/踩坏后，肥料残留导致无法再次施肥的问题。
     */
    @SuppressWarnings("null")
    public void cleanupInvalidEntries(MinecraftServer server) {
        List<GlobalPos> toRemove = new ArrayList<>();
        for (Map.Entry<GlobalPos, FertilizerType> entry : fertilizerMap.entrySet()) {
            GlobalPos gpos = entry.getKey();
            @SuppressWarnings("null")
            ServerLevel level = server.getLevel(gpos.dimension());
            if (level == null) {
                continue;
            }

            // 不要因为清理逻辑去强制加载区块
            if (!level.isLoaded(gpos.pos())) {
                continue;
            }

            if (!(level.getBlockState(gpos.pos()).getBlock() instanceof FarmBlock)) {
                toRemove.add(gpos);
            }
        }
        for (GlobalPos gpos : toRemove) {
            @SuppressWarnings("null")
            ServerLevel level = server.getLevel(gpos.dimension());
            if (level != null) {
                removeFertilizer(level, gpos.pos());
            }
        }
    }

    /**
     * 获取肥料的生长速度加成
     */
    public float getSpeedBoost(Level level, BlockPos pos) {
        FertilizerType type = getFertilizer(level, pos);
        return type != null ? type.getSpeedBoost() : 0f;
    }

    /**
     * 获取肥料的保湿概率
     */
    public float getWaterRetention(Level level, BlockPos pos) {
        FertilizerType type = getFertilizer(level, pos);
        return type != null ? type.getWaterRetention() : 0f;
    }

    /**
     * 获取肥料的品质提升等级
     */
    public int getQualityLevel(Level level, BlockPos pos) {
        FertilizerType type = getFertilizer(level, pos);
        return type != null ? type.getQualityLevel() : 0;
    }

    private static void syncChangeToTrackingPlayers(
            ServerLevel level,
            BlockPos pos,
            @Nullable FertilizerType type
    ) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                new ChunkPos(pos),
                FertilizerSyncPacket.update(level.dimension().location(), pos, type));
    }

    private static FertilizerSyncPacket.Entry syncEntry(
            BlockPos pos,
            FertilizerType type
    ) {
        return new FertilizerSyncPacket.Entry(pos, type.getSerializedName());
    }

    private static void sortSyncEntries(List<FertilizerSyncPacket.Entry> entries) {
        entries.sort(Comparator
                .comparingInt((FertilizerSyncPacket.Entry entry) -> entry.pos().getX())
                .thenComparingInt(entry -> entry.pos().getY())
                .thenComparingInt(entry -> entry.pos().getZ()));
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
        ListTag listTag = new ListTag();
        
        List<Map.Entry<GlobalPos, FertilizerType>> entries =
                new ArrayList<>(fertilizerMap.entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<GlobalPos, FertilizerType> entry) ->
                        entry.getKey().dimension().location().toString())
                .thenComparingInt(entry -> entry.getKey().pos().getX())
                .thenComparingInt(entry -> entry.getKey().pos().getY())
                .thenComparingInt(entry -> entry.getKey().pos().getZ()));
        for (Map.Entry<GlobalPos, FertilizerType> entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            
            GlobalPos gPos = entry.getKey();
            entryTag.putString("dimension", gPos.dimension().location().toString());
            entryTag.put("pos", NbtUtils.writeBlockPos(gPos.pos()));
            entryTag.putString("type", entry.getValue().getSerializedName());
            
            listTag.add(entryTag);
        }
        
        tag.put("fertilizers", listTag);
        return tag;
    }

    public static FertilizerManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        FertilizerManager manager = new FertilizerManager();
        
        if (tag.contains("fertilizers", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("fertilizers", Tag.TAG_COMPOUND);
            
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag entryTag = listTag.getCompound(i);
                
                try {
                    @SuppressWarnings("null")
                    ResourceLocation dimLoc = ResourceLocation.parse(entryTag.getString("dimension"));
                    @SuppressWarnings("null")
                    ResourceKey<Level> dimKey = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            dimLoc
                    );
                    BlockPos pos = NbtUtils.readBlockPos(entryTag, "pos").orElseThrow();
                    String typeName = entryTag.getString("type");
                    
                    FertilizerType type = FertilizerType.bySerializedName(typeName);
                    if (type == null) {
                        continue;
                    }
                    
                    @SuppressWarnings("null")
                    GlobalPos globalPos = GlobalPos.of(dimKey, pos);
                    manager.fertilizerMap.put(globalPos, type);
                } catch (Exception e) {
                    // 跳过损坏的数据
                }
            }
        }
        
        return manager;
    }
}
