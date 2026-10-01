package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("null")
public class FriendshipDoorBlockEntity extends BlockEntity {
    public static final int DEFAULT_REQUIRED_POINTS = 500;
    public static final String TAG_NPC_ID = "NpcId";
    public static final String TAG_NPC_IDS = "NpcIds";
    public static final String TAG_REQUIRED_POINTS = "RequiredPoints";

    private List<String> npcIds = List.of();
    private int requiredPoints = DEFAULT_REQUIRED_POINTS;

    public FriendshipDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FRIENDSHIP_DOOR.get(), pos, state);
    }

    public String getNpcId() {
        return npcIds.isEmpty() ? "" : com.stardew.craft.port.PortJava.getFirst(npcIds);
    }

    public List<String> getNpcIds() {
        return npcIds;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public int getRequiredHearts() {
        return Math.max(0, (requiredPoints + 249) / 250);
    }

    public boolean isBound() {
        return !npcIds.isEmpty();
    }

    public Component getNpcDisplayName() {
        if (!isBound()) {
            return Component.translatable("block.stardewcraft.friendship_door.unbound");
        }
        MutableComponent result = Component.empty();
        for (int i = 0; i < npcIds.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(" / "));
            }
            result.append(Component.translatable("entity.stardewcraft.npc." + npcIds.get(i)));
        }
        return result;
    }

    public void setBinding(String npcId, int requiredPoints) {
        setBinding(List.of(npcId), requiredPoints);
    }

    public void setBinding(Collection<String> npcIds, int requiredPoints) {
        this.npcIds = normalizeNpcIds(npcIds);
        this.requiredPoints = Math.max(0, requiredPoints);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static String normalizeNpcId(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> normalizeNpcIds(Collection<String> npcIds) {
        if (npcIds == null || npcIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String npcId : npcIds) {
            String value = normalizeNpcId(npcId);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    public static List<String> parseNpcIds(String npcIds) {
        if (npcIds == null || npcIds.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : npcIds.split(",")) {
            values.add(part);
        }
        return normalizeNpcIds(values);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!npcIds.isEmpty()) {
            tag.putString(TAG_NPC_ID, com.stardew.craft.port.PortJava.getFirst(npcIds));
            ListTag list = new ListTag();
            for (String npcId : npcIds) {
                list.add(StringTag.valueOf(npcId));
            }
            tag.put(TAG_NPC_IDS, list);
        }
        tag.putInt(TAG_REQUIRED_POINTS, requiredPoints);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        npcIds = readNpcIds(tag);
        requiredPoints = tag.contains(TAG_REQUIRED_POINTS) ? Math.max(0, tag.getInt(TAG_REQUIRED_POINTS)) : DEFAULT_REQUIRED_POINTS;
    }

    public static List<String> readNpcIds(CompoundTag tag) {
        if (tag.contains(TAG_NPC_IDS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_NPC_IDS, Tag.TAG_STRING);
            List<String> values = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                values.add(list.getString(i));
            }
            return normalizeNpcIds(values);
        }
        if (tag.contains(TAG_NPC_ID)) {
            return normalizeNpcIds(List.of(tag.getString(TAG_NPC_ID)));
        }
        return List.of();
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}