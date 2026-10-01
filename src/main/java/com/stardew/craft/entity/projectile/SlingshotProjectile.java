package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.*;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;

/** Straight, swept 3D projectile. Appearance is the existing ammunition item sprite. */
public final class SlingshotProjectile extends net.minecraft.world.entity.projectile.Projectile implements net.minecraft.world.entity.projectile.ItemSupplier {
    private static final net.minecraft.network.syncher.EntityDataAccessor<ItemStack> AMMO =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SlingshotProjectile.class,
                    net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SPIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SlingshotProjectile.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private int damage;
    private WeaponDamageSnapshot weapon;
    public SlingshotProjectile(EntityType<? extends SlingshotProjectile> type, Level level) { super(type, level); setNoGravity(true); }
    public SlingshotProjectile(Level level, Player owner, ItemStack bow, ItemStack ammo, int damage) {
        super(ModEntities.SLINGSHOT_PROJECTILE.get(), level);
        setOwner(owner); setPos(owner.getX(), owner.getEyeY() - .1, owner.getZ());
        entityData.set(SPIN, 540f / (64 + random.nextInt(127) - 63));
        setNoGravity(true);setItem(ammo);this.damage=damage;
        weapon=WeaponDamageSnapshot.capture(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bow.getItem()), bow);
    }
    public int releasedDamage() { return damage; }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(AMMO, ItemStack.EMPTY); builder.define(SPIN, 0f);
    }
    public void setItem(ItemStack item) { entityData.set(AMMO, item.copyWithCount(1)); }
    @Override public ItemStack getItem() { return entityData.get(AMMO); }
    public float spinDegreesPerTick() { return entityData.get(SPIN); }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.isInvisible()
                && (entity instanceof net.minecraft.world.entity.monster.Enemy
                    || entity instanceof com.stardew.craft.entity.npc.StardewNpcEntity);
    }
    @Override public void tick() {
        super.tick();
        Vec3 from = position(), to = from.add(getDeltaMovement());
        // SDV ignores collisions for the first 100ms. Ray sweep then handles full 3D motion.
        if (!level().isClientSide && tickCount > 2) {
            var blockHit = level().clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this) {
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(
                        net.minecraft.world.level.block.state.BlockState state,
                        net.minecraft.world.level.BlockGetter world, net.minecraft.core.BlockPos pos) {
                    var block = state.getBlock();
                    // SDV glider/projectile collision skips loose Objects, but keeps map walls.
                    if (block instanceof com.stardew.craft.block.mine.MineStoneBlock
                            || block instanceof com.stardew.craft.block.mine.MineRockClumpBlock
                            || block instanceof com.stardew.craft.block.mine.MineralNodeBlock
                            || block instanceof com.stardew.craft.block.mine.MineBarrelBlock
                            || block instanceof com.stardew.craft.block.mine.MineIceDebrisBlock)
                        return net.minecraft.world.phys.shapes.Shapes.empty();
                    return super.getBlockShape(state, world, pos);
                }
            });
            Vec3 end = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
            var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level(), this,
                    from, end, getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), this::canHitEntity, 29/128f);
            HitResult hit = entityHit == null ? blockHit : new EntityHitResult(entityHit.getEntity(),
                    entityHit.getEntity().getBoundingBox().inflate(29/128d).clip(from, end).orElse(end));
            if (hit.getType() != HitResult.Type.MISS
                    && !com.stardew.craft.port.net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                if (hit instanceof EntityHitResult e) onHitEntity(e);
                else onHitBlock((BlockHitResult) hit);
            }
        }
        if (!isRemoved()) { setPos(to); updateRotation(); }
        if (!level().isClientSide && tickCount > 200) discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if(level().isClientSide)return;
        explodeAtImpact();
        if (hit.getEntity() instanceof com.stardew.craft.entity.npc.StardewNpcEntity npc
                && getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.stardew.craft.npc.runtime.NpcInteractionService.onSlingshotHit(player, npc);
        } else if(hit.getEntity() instanceof LivingEntity target && getOwner() instanceof Player owner && weapon!=null) {
            long now=level().getGameTime();
            WeaponSkillContextStore.setPending(owner, SkillContext.builder().skillId("slingshot").damageMultiplier(1).build(), weapon, now+1);
            try { target.hurt(HitCooldownDamageSource.bypassVanillaCooldown(damageSources().mobProjectile(this, owner)),damage); }
            finally { if(WeaponSkillContextStore.hasPending(owner,now))WeaponSkillContextStore.consume(owner,now); }
        }
        finish();
    }
    @Override protected void onHitBlock(BlockHitResult hit) { if(!level().isClientSide){explodeAtImpact();finish();} }
    private void explodeAtImpact() {
        if (com.stardew.craft.item.weapon.SlingshotAmmo.explosive(getItem())
                && level() instanceof net.minecraft.server.level.ServerLevel server) {
            com.stardew.craft.entity.bomb.StardewBombEntity.explodeSlingshotAmmo(server, position().subtract(getDeltaMovement().normalize().scale(.05)),
                    getOwner() instanceof LivingEntity living ? living : null);
        }
    }
    private void finish() {
        var ammo = getItem();
        if (!com.stardew.craft.item.weapon.SlingshotAmmo.explosive(ammo)) {
            var sound = com.stardew.craft.item.weapon.SlingshotAmmo.egg(ammo)
                    ? com.stardew.craft.sound.ModSounds.SLIMEDEAD.get() : com.stardew.craft.sound.ModSounds.HAMMER.get();
            level().playSound(null, blockPosition(), sound, net.minecraft.sounds.SoundSource.PLAYERS, 1, 1);
        }
        level().broadcastEntityEvent(this, (byte) 3); discard();
    }
    @Override public void handleEntityEvent(byte event) {
        if(event==3)for(int i=0;i<6;i++)level().addParticle(new ItemParticleOption(ParticleTypes.ITEM,getItem()),getX(),getY(),getZ(),
                (random.nextDouble()-.5)*.08,random.nextDouble()*.08,(random.nextDouble()-.5)*.08);
        else super.handleEntityEvent(event);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("SlingshotDamage",damage);tag.putFloat("SlingshotSpin",spinDegreesPerTick());if(!getItem().isEmpty())tag.put("Ammo",getItem().save(registryAccess()));MeowmereProjectileEntity.writeReleaseWeaponSnapshot(tag,weapon,registryAccess());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);damage=tag.getInt("SlingshotDamage");entityData.set(SPIN,tag.getFloat("SlingshotSpin"));setItem(ItemStack.parseOptional(registryAccess(),tag.getCompound("Ammo")));weapon=MeowmereProjectileEntity.readReleaseWeaponSnapshot(tag,registryAccess());setNoGravity(true);}
}
