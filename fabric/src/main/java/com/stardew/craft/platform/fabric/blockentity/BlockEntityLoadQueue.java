package com.stardew.craft.platform.fabric.blockentity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One level's pending load callbacks, populated by real chunk registration. */
public final class BlockEntityLoadQueue {
    private List<Pending> ordered = new ArrayList<>();
    private final Map<FabricBlockEntity, Pending> identities = new IdentityHashMap<>();

    public void enqueue(BlockEntity blockEntity, Map<BlockPos, BlockEntity> residents) {
        if (!(blockEntity instanceof FabricBlockEntity target)) {
            return;
        }
        Pending existing = identities.get(target);
        if (existing != null) {
            existing.residents = residents;
            return;
        }
        Pending pending = new Pending(target, residents);
        identities.put(target, pending);
        ordered.add(pending);
    }

    public void drain(Level owner) {
        if (ordered.isEmpty()) {
            return;
        }
        // Registrations from an onLoad callback or a normal BE tick belong to
        // the next pass, matching the upstream fresh/pending-fresh distinction.
        List<Pending> current = ordered;
        ordered = new ArrayList<>();
        identities.clear();
        for (Pending pending : current) {
            FabricBlockEntity target = pending.target;
            if (!target.isRemoved() && target.hasLevel() && target.getLevel() == owner
                    && pending.residents.get(target.getBlockPos()) == target) {
                target.onLoad();
            }
        }
    }

    private static final class Pending {
        private final FabricBlockEntity target;
        private Map<BlockPos, BlockEntity> residents;

        private Pending(FabricBlockEntity target, Map<BlockPos, BlockEntity> residents) {
            this.target = target;
            this.residents = residents;
        }
    }
}
