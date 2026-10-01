package com.stardew.craft.entity.trinket;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.item.trinket.TrinketType;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public class FairyCompanionEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FairyCompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private Vec3 motion = Vec3.ZERO;
    private int flapTimer;

    public FairyCompanionEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public FairyCompanionEntity(Level level, ServerPlayer owner) {
        this(ModEntities.FAIRY_COMPANION.get(), level);
        setOwner(owner.getUUID());
        setPos(owner.getX(), owner.getY() + 1.7D, owner.getZ());
        this.flapTimer = 1 + random.nextInt(40);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER, Optional.empty());
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER);
    }

    public boolean isOwnedBy(UUID uuid) {
        return getOwnerUuid().map(uuid::equals).orElse(false);
    }

    private void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickParticles();
            return;
        }

        ServerPlayer owner = getOwnerPlayer();
        if (owner == null || !hasFairyBox(owner)) {
            discard();
            return;
        }

        Vec3 look = owner.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.001D) {
            horizontalLook = Vec3.directionFromRotation(0.0F, owner.getYRot());
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 right = new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x);
        double bob = Math.sin((tickCount + owner.getId()) * 0.12D) * 0.16D;
        double sideDrift = Math.sin((tickCount + owner.getId() * 31) * 0.035D) * 0.45D;
        double peekPhase = ((tickCount + owner.getId() * 13) % 180) / 180.0D;
        double sidePeek = peekPhase < 0.18D ? Math.sin((peekPhase / 0.18D) * Math.PI) : 0.0D;
        double peekSide = ((owner.getId() + tickCount / 180) & 1) == 0 ? 1.0D : -1.0D;
        Vec3 target = owner.position()
            .add(horizontalLook.scale(-1.35D + sidePeek * 1.45D))
            .add(right.scale(sideDrift + sidePeek * peekSide * 1.25D))
            .add(0.0D, 1.38D + bob, 0.0D);
        if (distanceToSqr(owner) > 144.0D || owner.level() != level()) {
            setPos(target.x, target.y, target.z);
            motion = Vec3.ZERO;
            return;
        }

        flapTimer--;
        if (flapTimer <= 0) {
            motion = motion.add((random.nextDouble() - 0.5D) * 0.05D,
                    (random.nextDouble() - 0.45D) * 0.05D,
                    (random.nextDouble() - 0.5D) * 0.05D);
            flapTimer = 8 + random.nextInt(16);
        }
        Vec3 pull = target.subtract(position()).scale(0.075D);
        Vec3 ownerCenter = owner.position().add(0.0D, 1.0D, 0.0D);
        Vec3 awayFromOwner = position().subtract(ownerCenter);
        if (awayFromOwner.lengthSqr() < 0.81D && awayFromOwner.lengthSqr() > 0.0001D) {
            pull = pull.add(awayFromOwner.normalize().scale(0.06D));
        }
        motion = motion.scale(0.88D).add(pull);
        if (motion.lengthSqr() > 0.09D) {
            motion = motion.normalize().scale(0.30D);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    private void tickParticles() {
        if (random.nextFloat() < 0.06F) {
            level().addParticle(ParticleTypes.END_ROD,
                    getX() + (random.nextDouble() - 0.5D) * 0.35D,
                    getY() + (random.nextDouble() - 0.3D) * 0.35D,
                    getZ() + (random.nextDouble() - 0.5D) * 0.35D,
                    (random.nextDouble() - 0.5D) * 0.01D,
                    0.012D,
                    (random.nextDouble() - 0.5D) * 0.01D);
        }
                if (random.nextFloat() < 0.015F) {
            level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + 0.08D, getZ(), 0.0D, 0.02D, 0.0D);
        }
    }

    private boolean hasFairyBox(ServerPlayer player) {
        ItemStack stack = PlayerDataManager.getPlayerData(player).getEquippedTrinket();
        return StardewTrinketItem.getType(stack) == TrinketType.FAIRY_BOX;
    }

    private ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return getOwnerUuid()
            .map(serverLevel::getPlayerByUUID)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .orElse(null);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            setOwner(tag.getUUID("Owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUuid().ifPresent(uuid -> tag.putUUID("Owner", uuid));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}