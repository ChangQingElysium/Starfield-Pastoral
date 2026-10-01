package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Authoritative identity and state. World blocks and animal entities are projections, not identity. */
public record BuildingRecord(UUID id, UUID farmId, int farmSlot, ResourceLocation family,
                             Mode mode, ResourceLocation dimension, BlockPos anchor,
                             BlockPos manager, Direction facing, BuildingBounds claim,
                             Phase phase, int tier, Residence residence, long revision, String displayName) {
    public enum Mode { SELF_BUILT, PREFAB }
    public enum Phase { WAITING, CONSTRUCTING, READY, UPGRADING, MISSING }
    public enum Residence { UNCHECKED, VALID, INVALID }
    public enum Action { START_CONSTRUCTION, FINISH_CONSTRUCTION, START_UPGRADE, FINISH_UPGRADE }

    public BuildingRecord {
        Objects.requireNonNull(id);
        displayName = Objects.requireNonNull(displayName);
        if (displayName.length() > 32 || displayName.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) throw new IllegalArgumentException("Invalid building name");
        Objects.requireNonNull(farmId);
        Objects.requireNonNull(family);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(facing);
        Objects.requireNonNull(claim);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(residence);
        anchor = anchor.immutable();
        manager = manager.immutable();
        if (farmSlot < 0 || tier < 1 || revision < 0
                || facing.getAxis().isVertical() || !claim.contains(manager)) {
            throw new IllegalArgumentException("Invalid building identity or geometry");
        }
        if ((mode == Mode.SELF_BUILT && (phase == Phase.CONSTRUCTING || phase == Phase.UPGRADING))

                || ((phase == Phase.WAITING || phase == Phase.CONSTRUCTING) && tier != 1)
                || ((phase == Phase.WAITING || phase == Phase.CONSTRUCTING) && residence == Residence.VALID)) {
            throw new IllegalArgumentException("Invalid building lifecycle");
        }
    }

    public static BuildingRecord waiting(UUID farmId, int farmSlot, ResourceLocation family,
                                         Mode mode, ResourceLocation dimension, BlockPos anchor,
                                         BlockPos manager, Direction facing, BuildingBounds claim) {
        return new BuildingRecord(UUID.randomUUID(), farmId, farmSlot, family, mode, dimension,
                anchor, manager, facing, claim, Phase.WAITING, 1, Residence.UNCHECKED, 0, "");
    }

    /** Called only by server construction orders; player upgrades never enter this state machine. */
    BuildingRecord advance(Action action) {
        if (mode != Mode.PREFAB) throw new IllegalStateException("Self-built buildings have no Robin order");
        return switch (action) {
            case START_CONSTRUCTION -> {
                requirePhase(Phase.WAITING);
                yield state(Phase.CONSTRUCTING, tier, Residence.UNCHECKED);
            }
            case FINISH_CONSTRUCTION -> {
                requirePhase(Phase.CONSTRUCTING);
                yield state(Phase.READY, tier, Residence.UNCHECKED);
            }
            case START_UPGRADE -> {
                requirePhase(Phase.READY);
                if (tier >= PrefabDefinitions.maxTier(family)) throw new IllegalStateException("Already at maximum tier");
                yield state(Phase.UPGRADING, tier, residence);
            }
            case FINISH_UPGRADE -> {
                requirePhase(Phase.UPGRADING);
                yield state(Phase.READY, tier + 1, Residence.UNCHECKED);
            }
        };
    }

    /** eligibleTier is a server facility assessment (0 means not a valid residence). */
    BuildingRecord assessResidence(int eligibleTier) {
        if (eligibleTier < 0 || eligibleTier > PrefabDefinitions.maxTier(family)) throw new IllegalArgumentException("Invalid eligible tier");
        if (mode == Mode.SELF_BUILT) {
            if (phase == Phase.WAITING || phase == Phase.MISSING) return state(phase, tier, Residence.INVALID);
            return state(phase, tier, eligibleTier >= tier ? Residence.VALID : Residence.INVALID);
        }
        if (phase == Phase.WAITING || phase == Phase.CONSTRUCTING) {
            throw new IllegalStateException("Unfinished prefab cannot be a residence");
        }
        return state(phase, tier, eligibleTier >= tier ? Residence.VALID : Residence.INVALID);
    }

    public net.minecraft.network.chat.Component title() {
        return displayName.isBlank() ? com.stardew.craft.api.v1.building.StardewBuildingFamilies.title(family,tier) : net.minecraft.network.chat.Component.literal(displayName);
    }
    BuildingRecord acceptSelf(int eligibleTier) {
        if (mode != Mode.SELF_BUILT || phase != Phase.WAITING && phase != Phase.READY) throw new IllegalStateException("Unavailable residence");
        int target = phase == Phase.WAITING ? 1 : tier + 1;
        if (target > eligibleTier || target > PrefabDefinitions.maxTier(family)) throw new IllegalStateException("Facilities not ready");
        return state(Phase.READY, target, Residence.VALID);
    }
    BuildingRecord rename(String name) {
        return new BuildingRecord(id, farmId, farmSlot, family, mode, dimension, anchor, manager, facing, claim, phase, tier, residence, revision + 1, name.strip());
    }
    BuildingRecord missing() { return state(Phase.MISSING, tier, phase == Phase.WAITING ? Residence.UNCHECKED : Residence.INVALID); }

    private void requirePhase(Phase expected) {
        if (phase != expected) throw new IllegalStateException("Expected " + expected + ", got " + phase);
    }

    private BuildingRecord state(Phase nextPhase, int nextTier, Residence nextResidence) {
        return new BuildingRecord(id, farmId, farmSlot, family, mode, dimension, anchor, manager,
                facing, claim, nextPhase, nextTier, nextResidence, Math.addExact(revision, 1), displayName);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("FarmId", farmId);
        tag.putInt("FarmSlot", farmSlot);
        tag.putString("Family", family.toString());
        tag.putString("Mode", mode.name());
        tag.putString("Dimension", dimension.toString());
        putPos(tag, "Anchor", anchor);
        putPos(tag, "Manager", manager);
        tag.putString("Facing", facing.getName());
        putPos(tag, "ClaimMin", claim.min());
        putPos(tag, "ClaimMaxExclusive", claim.maxExclusive());
        tag.putString("Phase", phase.name());
        tag.putInt("Tier", tier);
        tag.putString("Residence", residence.name());
        tag.putLong("Revision", revision);
        tag.putString("DisplayName", displayName);
        return tag;
    }

    public static BuildingRecord load(CompoundTag tag) {
        return new BuildingRecord(tag.getUUID("Id"), tag.getUUID("FarmId"), tag.getInt("FarmSlot"),
                new ResourceLocation(tag.getString("Family")), Mode.valueOf(tag.getString("Mode")),
                new ResourceLocation(tag.getString("Dimension")), getPos(tag, "Anchor"),
                getPos(tag, "Manager"), Objects.requireNonNull(Direction.byName(tag.getString("Facing"))),
                new BuildingBounds(getPos(tag, "ClaimMin"), getPos(tag, "ClaimMaxExclusive")),
                Phase.valueOf(tag.getString("Phase")), tag.getInt("Tier"),
                Residence.valueOf(tag.getString("Residence")), tag.getLong("Revision"), tag.getString("DisplayName"));
    }

    private static void putPos(CompoundTag tag, String key, BlockPos pos) {
        tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private static BlockPos getPos(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) throw new IllegalArgumentException("Invalid position: " + key);
        return new BlockPos(values[0], values[1], values[2]);
    }
}
