package com.stardew.craft.port.net.neoforged.neoforge.event.entity.living;

import javax.annotation.Nullable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code FinalizeSpawnEvent}, bridged from Forge {@code MobSpawnEvent.FinalizeSpawn}
 * (same firing point). Accessors delegate to the Forge event; cancelling cancels it.
 */
@Cancelable
public class FinalizeSpawnEvent extends MobSpawnEvent {
    private final MobSpawnEvent.FinalizeSpawn delegate;

    public FinalizeSpawnEvent() {
        super(null, null, 0, 0, 0);
        this.delegate = null;
    }

    public FinalizeSpawnEvent(MobSpawnEvent.FinalizeSpawn delegate) {
        super(delegate.getEntity(), delegate.getLevel(), delegate.getX(), delegate.getY(), delegate.getZ());
        this.delegate = delegate;
    }

    public DifficultyInstance getDifficulty() {
        return this.delegate.getDifficulty();
    }

    public void setDifficulty(DifficultyInstance inst) {
        this.delegate.setDifficulty(inst);
    }

    public MobSpawnType getSpawnType() {
        return this.delegate.getSpawnType();
    }

    @Nullable
    public SpawnGroupData getSpawnData() {
        return this.delegate.getSpawnData();
    }

    public void setSpawnData(@Nullable SpawnGroupData data) {
        this.delegate.setSpawnData(data);
    }

    public void setSpawnCancelled(boolean cancel) {
        this.delegate.setSpawnCancelled(cancel);
    }

    public boolean isSpawnCancelled() {
        return this.delegate.isSpawnCancelled();
    }
}
