package com.stardew.craft.entity.bomb;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.bomb.BombType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * SDV 炸弹实体 — 放置在地面上，引信倒数后爆炸。
 *
 * <p>原版格子爆炸映射到 MC 的当前地面层：</p>
 * <ul>
 *   <li>引信时间：48 ticks (2400ms)，引信期间模型加速颤抖</li>
 *   <li>音效：放置 thudStep → 引信 fuse (循环) → 爆炸 explosion</li>
 *   <li>颤抖：随引信时间逐渐增强的轻微颤抖</li>
 *   <li>爆炸：当前层的原版格子圆，脚下地面仅尝试锄地</li>
 *   <li>伤害：怪物 r*6~r*8，玩家自伤 r*3（r 为 SDV 原版半径）</li>
 *   <li>粒子：45% 概率每格生成碎片或粉尘</li>
 * </ul>
 */
@SuppressWarnings("null")
public class StardewBombEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_BOMB_TYPE =
        SynchedEntityData.defineId(StardewBombEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_FUSE =
        SynchedEntityData.defineId(StardewBombEntity.class, EntityDataSerializers.INT);

    /** 引信循环音效的播放间隔（ticks）。fuse.ogg ≈ 0.16s，每 3 tick 播放一次保持连续 */
    private static final int FUSE_SOUND_INTERVAL = 3;

    private static final String OWNER_TAG = "Owner";

    @Nullable
    private LivingEntity owner;

    @Nullable
    private UUID ownerUuid;

    public StardewBombEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    /* ── 初始化 ─────────────────────────────────────────── */

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BOMB_TYPE, 0);
        this.entityData.define(DATA_FUSE, 48);
    }

    public void setBombType(BombType type) {
        this.entityData.set(DATA_BOMB_TYPE, type.ordinal());
        this.entityData.set(DATA_FUSE, type.getFuseTicks());
    }

    public BombType getBombType() {
        return BombType.fromOrdinal(this.entityData.get(DATA_BOMB_TYPE));
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = owner;
        this.ownerUuid = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.owner != null && !this.owner.isRemoved()) {
            return this.owner;
        }
        if (this.ownerUuid == null
                || !(this.level() instanceof ServerLevel serverLevel)) {
            this.owner = null;
            return null;
        }

        ServerPlayer player = serverLevel.getServer()
                .getPlayerList()
                .getPlayer(this.ownerUuid);
        if (player != null) {
            this.owner = player;
            return player;
        }

        Entity entity = serverLevel.getEntity(this.ownerUuid);
        if (entity instanceof LivingEntity livingOwner) {
            this.owner = livingOwner;
            return livingOwner;
        }

        this.owner = null;
        return null;
    }

    /* ── tick ────────────────────────────────────────────── */

    @Override
    public void tick() {
        super.tick();

        // 简单重力
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());

        int fuse = getFuse();

        if (!level().isClientSide()) {
            // SDV: fuse 音效循环播放（每 FUSE_SOUND_INTERVAL ticks 播一次）
            if (fuse > 0 && fuse % FUSE_SOUND_INTERVAL == 0) {
                level().playSound(null, this.blockPosition(),
                    ModSounds.FUSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            }

            if (fuse <= 0) {
                explode();
                return;
            }
            entityData.set(DATA_FUSE, fuse - 1);
        }

        // 客户端：引信火花粒子
        if (level().isClientSide()) {
            spawnFuseParticles(fuse);
        }
    }

    /* ── 引信火花粒子（SDV: 3 层 spark，黄/橙/白交错） ──── */

    /** Source intensity grows by 0.002 per millisecond; sampling never consumes gameplay RNG. */
    public Vec3 getVisualShake(float partialTick) {
        float elapsed = Mth.clamp(getBombType().getFuseTicks() - getFuse() + partialTick,
                0, getBombType().getFuseTicks());
        float amplitude = (0.5f + elapsed * 50 * 0.002f) / 64;
        float phase = (tickCount + partialTick) * 2.4f + getId() * 0.73f;
        return new Vec3(Mth.sin(phase) * amplitude, 0, Mth.sin(phase * 1.37f) * amplitude);
    }

    private void spawnFuseParticles(int fuse) {
        if (fuse <= 0) return;
        int phase = tickCount % 5;
        if (phase != 0 && phase != 2 && phase != 4) return;

        // Model-space cord tips, including the normal bomb's 45-degree bend.
        Vec3 tip = switch (getBombType()) {
            case CHERRY_BOMB -> new Vec3(0, 9.0 / 16, 0);
            case BOMB -> new Vec3((5.5 - 4 / Math.sqrt(2) - 8) / 16,
                    (13.5 + 4 / Math.sqrt(2)) / 16, -0.5 / 16);
            case MEGA_BOMB -> new Vec3(0, 16.5 / 16, 0);
        };
        Vec3 shake = getVisualShake(0);
        level().addParticle(ModParticles.BOMB_FUSE.get(),
                getX() + tip.x + shake.x, getY() + tip.y + 0.04, getZ() + tip.z + shake.z,
                phase, 0, 0);
    }

    /* ── 爆炸 ──────────────────────────────────────────── */

    /** BasicProjectile.explodeOnImpact: the same blast rules, radius two, no fuse. */
    public static void explodeSlingshotAmmo(ServerLevel level, Vec3 impact, @Nullable LivingEntity owner) {
        var blast = new StardewBombEntity(com.stardew.craft.entity.ModEntities.STARDEW_BOMB.get(), level);
        BlockPos floor = BlockPos.containing(impact);
        // Project a 3D impact onto its local floor, as the source explosion is a tile circle.
        for (int i = 0; i < 4 && level.getBlockState(floor.below()).getCollisionShape(level, floor.below()).isEmpty(); i++)
            floor = floor.below();
        int planeY = level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING
                ? com.stardew.craft.mining.MiningCoordinates.FIXED_Y : floor.getY();
        blast.setPos(impact.x, planeY, impact.z);
        blast.setOwner(owner);
        blast.explode(2);
    }

    private void explode() { explode(getBombType().getRadius()); }

    private void explode(int radius) {
        if (level().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level();
        BlockPos center = new BlockPos(this.getBlockX(), Mth.ceil(this.getY() - 1.0E-4D), this.getBlockZ());
        float scaledRadius = radius;
        LivingEntity resolvedOwner = getOwner();

        // 1. SDV: 停止 fuse 音效 → 播放 explosion
        serverLevel.playSound(null, center, ModSounds.EXPLOSION.get(),
            SoundSource.BLOCKS, 1.5f, 0.9f + random.nextFloat() * 0.2f);

        // 2. 只处理当前层的物件；地面由独立的锄地阶段处理。
        destroyBlocksInCircle(
                serverLevel,
                center,
                scaledRadius,
                resolvedOwner
        );

        tillSoilInCircle(serverLevel, center, radius / 2, resolvedOwner);

        // 3. 原版矩形伤害范围，限定在同一地面层。
        damageEntitiesInRadius(serverLevel, radius, resolvedOwner);

        // 4. 地面爆闪和尘圈，范围跟随这次爆炸（含弹弓爆炸弹药）。
        spawnExplosionParticles(serverLevel, center, scaledRadius);

        // 5. 移除实体
        this.discard();
    }

    private void destroyBlocksInCircle(
            ServerLevel level, BlockPos center, float radius, @Nullable LivingEntity resolvedOwner
    ) {
        var trees = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(level);
        for (BombBlastPattern.Tile tile : BombBlastPattern.circle((int) radius)) {
            BlockPos pos = center.offset(tile.x(), 0, tile.z());
            BlockState state = level.getBlockState(pos);
            var tree = trees.getByMember(pos);
            if (tree != null) {
                if (pos.equals(tree.root()) && resolvedOwner instanceof ServerPlayer player
                        && tree.members().stream().allMatch(member -> canModify(level, member, player))) {
                    com.stardew.craft.tree.prefab.PrefabTreeChopHandler.explode(level, tree, player, (int) radius / 2);
                }
                continue;
            }
            if (state.isAir() || !canBombDestroy(level, pos, state, resolvedOwner)) continue;
            if (state.getBlock() instanceof com.stardew.craft.block.mine.MineBarrelBlock) {
                com.stardew.craft.block.mine.MineBarrelBlock.breakByExplosion(level, pos,
                        resolvedOwner instanceof ServerPlayer sp ? sp : null);
                continue;
            }
            if (state.getBlock() instanceof com.stardew.craft.block.mine.MineGroundWeedsBlock) {
                com.stardew.craft.block.mine.MineGroundWeedsBlock.breakBy(level, pos,
                        resolvedOwner instanceof ServerPlayer sp ? sp : null, false);
                continue;
            }
            dropBlockForBomb(level, pos, state, resolvedOwner);
            level.removeBlock(pos, false);
        }
    }

    private static boolean canModify(ServerLevel level, BlockPos pos, @Nullable LivingEntity owner) {
        return level.dimension() != com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                || owner instanceof ServerPlayer player && (player.isCreative()
                || com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(player, pos));
    }

    private void tillSoilInCircle(ServerLevel level, BlockPos center, int radius,
                                  @Nullable LivingEntity resolvedOwner) {
        for (BombBlastPattern.Tile tile : BombBlastPattern.circle(radius)) {
            BlockPos pos = center.offset(tile.x(), -1, tile.z());
            if (!level.isEmptyBlock(pos.above()) || random.nextDouble() >= 0.9D
                    || !canModify(level, pos, resolvedOwner) || !canModify(level, pos.above(), resolvedOwner)) continue;
            BlockState state = level.getBlockState(pos);
            BlockState mineSoil = BombMineSoil.tilled(state);
            if (mineSoil != null) {
                if (level.setBlock(pos, mineSoil, 11)) {
                    level.levelEvent(2001, pos, Block.getId(state));
                    BombMineSoil.drop(level, pos, resolvedOwner instanceof ServerPlayer player ? player : null);
                }
                continue;
            }
            if (state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock
                    || state.getBlock() instanceof com.stardew.craft.block.terrain.TerrainGrassBlock
                    || com.stardew.craft.mining.OrdinaryMineRuntime.isArchitecture(level, pos)) continue;
            var context = new net.minecraft.world.item.context.UseOnContext(level,
                    resolvedOwner instanceof Player player ? player : null,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_HOE),
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atBottomCenterOf(pos.above()),
                            net.minecraft.core.Direction.UP, pos, false));
            BlockState tilled = state.getToolModifiedState(context,
                    net.minecraftforge.common.ToolActions.HOE_TILL, false);
            if (tilled == null || !(tilled.getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) continue;
            if (level.isRainingAt(pos.above())) {
                tilled = tilled.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7);
            }
            if (!level.setBlock(pos, tilled, 11)) continue;
            if (com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, pos)
                    && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
                com.stardew.craft.manager.CropGrowthManager.get(level).trackPublicTilledChunk(level, pos);
            }
            level.levelEvent(2001, pos, Block.getId(state));
            // GameLocation.checkForBuriedItem(explosion=true) excludes the outdoor
            // clay/winter-forage rolls. Do not call the ordinary hoe drop path here.
        }
    }

    private boolean canBombDestroy(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity resolvedOwner
    ) {
        if (!canModify(level, pos, resolvedOwner)) return false;
        // 不破坏不可破坏方块（基岩 / 屏障 / 命令方块 / 末地传送门框 / 强化深板岩 等）
        if (isIndestructible(level, pos, state)) return false;
        // 不破坏矿井梯子和 portal trigger 这类功能方块。
        if (isBombProtectedBlock(state) || com.stardew.craft.mining.OrdinaryMineRuntime.isArchitecture(level,pos)) return false;
        if (state.getBlock() instanceof com.stardew.craft.block.mine.MineIceDebrisBlock
                || state.getBlock() instanceof com.stardew.craft.block.mine.MineGroundWeedsBlock) return true;
        // 多格家具/机器的 extension 格子只是占位。炸它们会触发每格各掉一份的安全网。
        if (isExtensionPart(state) && !(state.getBlock() instanceof com.stardew.craft.block.mine.MineBarrelBlock)) return false;
        if (com.stardew.craft.tree.WildTrees.findBySapling(state) != null) return true;
        // 登记的整树按树根受伤处理；其余树体不可逐块炸散。
        if (com.stardew.craft.tree.WildTrees.isAnyWildTreePart(state)) return false;
        // 采石场：只允许炸掉每日/初始生成的石头、矿物等资源块，原始结构不允许被炸毁。
        if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(pos)
                && !com.stardew.craft.manager.QuarrySpawnService.canBombDestroyInQuarry(state)) {
            return false;
        }
        // 爆炸毁坏作物和草丛，不触发收获产物。
        if (state.getBlock() instanceof com.stardew.craft.block.crop.StardewCropBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                || state.is(net.minecraft.tags.BlockTags.CROPS)
                || state.getBlock() instanceof net.minecraft.world.level.block.TallGrassBlock) return true;
        // 无掉落表或没有任何炸弹合法结果的方块不应被破坏。
        if (state.getBlock().getLootTable() == net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY) {
            return false;
        }
        return hasMeaningfulBombDrop(state);
    }

    private static boolean isBombProtectedBlock(BlockState state) {
        Block block = state.getBlock();
        return state.is(ModBlocks.MINE_LADDER.get())
            || state.is(ModBlocks.MINE_EXIT.get())
            || state.is(ModBlocks.MINE_CHEST.get())
            || state.is(ModBlocks.MINE_BARRIER.get())
            || state.is(ModBlocks.ELEVATOR.get())
            || block instanceof com.stardew.craft.block.mine.MineLadderBlock
            || block instanceof com.stardew.craft.block.mine.ElevatorBlock
            || block instanceof com.stardew.craft.block.mine.MineExitBlock
            || block instanceof com.stardew.craft.block.mine.MineChestBlock
            || block instanceof com.stardew.craft.block.utility.FishPondManagerBlock
            || block instanceof com.stardew.craft.block.utility.CoopManagerBlock
            || block instanceof com.stardew.craft.block.utility.BarnManagerBlock
            || block instanceof com.stardew.craft.block.utility.SiloManagerBlock
            || block instanceof com.stardew.craft.block.portal.PortalTriggerBlock;
    }

    private static boolean hasMeaningfulBombDrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof com.stardew.craft.block.mine.MineBarrelBlock) {
            return true;
        }
        if (block instanceof com.stardew.craft.block.mine.MineStoneBlock) {
            return true;
        }
        if (isPlantLikeBlock(state)) {
            return false;
        }
        if (isExtensionPart(state)) {
            return false;
        }
        return block.asItem() != net.minecraft.world.item.Items.AIR;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isExtensionPart(BlockState state) {
        for (Property property : state.getProperties()) {
            if (!"part".equals(property.getName())) {
                continue;
            }
            Object value = state.getValue(property);
            if (value instanceof StringRepresentable named) {
                return "extension".equals(named.getSerializedName());
            }
            return "extension".equals(String.valueOf(value));
        }
        return false;
    }

    /**
     * 是否为生存模式下不可破坏的方块——炸弹一律不破坏。
     * 涵盖基岩、屏障、命令方块、末地传送门框、强化深板岩、光源方块等。
     */
    @SuppressWarnings("deprecation")
    private static boolean isIndestructible(ServerLevel level, BlockPos pos, BlockState state) {
        // 1. 硬度 < 0 = 永远不可破坏（基岩、屏障、命令方块、jigsaw、末地传送门、light）
        if (state.getDestroySpeed(level, pos) < 0) return true;
        // 2. WITHER_IMMUNE tag —— 涵盖凋灵都炸不动的方块（含 end_portal_frame、reinforced_deepslate 等）
        if (state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE)) return true;
        // 3. 爆炸抗性极高的方块兜底（vanilla 用 3600000F 表示"无穷大"）
        if (state.getBlock().getExplosionResistance() >= 3600000.0F) return true;
        return false;
    }

    /**
     * SDV 炸弹方块掉落：矿石 → 产物物品；植物/作物/草丛/树枝类 → 不掉落；其余方块 → 方块自身。
     *
     * SDV 原版炸弹会清理草丛、杂草、枯萎作物等，但**不会**让玩家拿到完整的草/作物方块物品；
     * 同时 SDV 原版炸弹也不会破坏成长中的作物变成种子（默认无掉落）。
     */
    private void dropBlockForBomb(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity resolvedOwner
    ) {
        Block block = state.getBlock();

        if (isExtensionPart(state)) {
            return;
        }

        if (block instanceof com.stardew.craft.block.mine.MineStoneBlock) {
            com.stardew.craft.mining.MineStoneMining.finishDrops(level,
                    resolvedOwner instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, pos, state, true);
            return;
        }

        // 0. 木桶：onRemove 已经会调用 dropBarrelLoot，这里不要再 popResource(barrelItem)
        //    否则玩家会同时拿到一个可放置的木桶方块物品。
        if (block instanceof com.stardew.craft.block.mine.MineBarrelBlock) {
            return;
        }

        if (isPlantLikeBlock(state)) {
            return;
        }
        net.minecraft.world.item.Item blockItem = block.asItem();
        if (blockItem != net.minecraft.world.item.Items.AIR) {
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(blockItem));
        }
    }

    /**
     * 判断该方块是否属于"植物类"——这些方块在被炸弹清除时不应掉落自身物品，
     * 否则玩家就能从草丛/作物里得到完整方块。
     */
    private static boolean isPlantLikeBlock(BlockState state) {
        Block b = state.getBlock();
        if (com.stardew.craft.tree.WildTrees.findBySapling(state) != null) return true;
        // 模组植物
        if (b instanceof com.stardew.craft.block.crop.StardewCropBlock) return true;
        if (b instanceof com.stardew.craft.block.nature.WildWeedsBlock) return true;
        // 香草/模组通用植物：BushBlock 覆盖 PastureGrassBlock、DeadCropBlock 以及香草花/树苗
        if (b instanceof net.minecraft.world.level.block.BushBlock) return true;
        if (b instanceof net.minecraft.world.level.block.CropBlock) return true;
        if (b instanceof net.minecraft.world.level.block.TallGrassBlock) return true;
        if (b instanceof net.minecraft.world.level.block.DoublePlantBlock) return true;
        if (b instanceof net.minecraft.world.level.block.SaplingBlock) return true;
        if (b instanceof net.minecraft.world.level.block.StemBlock) return true;
        if (b instanceof net.minecraft.world.level.block.AttachedStemBlock) return true;
        if (b instanceof net.minecraft.world.level.block.SugarCaneBlock) return true;
        if (b instanceof net.minecraft.world.level.block.MushroomBlock) return true;
        // 通用 tag 兜底
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.SAPLINGS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.FLOWERS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.TALL_FLOWERS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.CROPS)) return true;
        if (state.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)) return true;
        return false;
    }



    /**
     * SDV 伤害公式：怪物 r*6 ~ r*8，玩家自伤 r*3。
     * 原版伤害使用 (2r+1) 格的矩形；MC 只选择同层的实体。
     */
    private void damageEntitiesInRadius(
            ServerLevel level,
            int radius,
            @Nullable LivingEntity resolvedOwner
    ) {
        int planeY = Mth.ceil(this.getY() - 1.0E-4D);
        AABB damageBox = new AABB(getBlockX() - radius, planeY - 1, getBlockZ() - radius,
                getBlockX() + radius + 1, planeY + 2, getBlockZ() + radius + 1);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, damageBox);

        for (LivingEntity entity : entities) {
            if (Mth.ceil(entity.getY() - 1.0E-4D) != planeY) continue;

            DamageSource source = level.damageSources().explosion(
                    this,
                    resolvedOwner
            );

            if (entity instanceof Player) {
                if (entity.hasEffect(ModMobEffects.DWARF_STATUE_3.get())) {
                    continue;
                }
                entity.hurt(source, radius * 3);
            } else if (entity instanceof Mob) {
                int damage = radius * 6 + random.nextInt(radius * 2 + 1);
                entity.hurt(source, damage);
            }
        }
    }

    /** Source-style ground bursts and short dust rings across the affected tile circle. */
    private void spawnExplosionParticles(ServerLevel level, BlockPos center, float radius) {
        for (BombBlastPattern.Tile tile : BombBlastPattern.circle((int) Math.ceil(radius))) {
            double x = center.getX() + tile.x() + 0.5;
            double z = center.getZ() + tile.z() + 0.5;
            // SDV animations row 6: eight frames, distance * 20 ms per frame (not a start delay).
            double frameMillis = Math.max(20, Math.hypot(tile.x(), tile.z()) * 20);
            level.sendParticles(ModParticles.BOMB_BURST.get(), x, center.getY() + 0.5, z,
                    0, frameMillis, 0, 1, 1);
            if (random.nextFloat() < 0.45f) {
                // The source's row-5 puff runs at 50 ms/frame and starts up to 200 ms later.
                float size = 0.5f + random.nextInt(10) / 10.0f;
                level.sendParticles(ModParticles.BOMB_DUST.get(), x, center.getY() + size / 2, z,
                        0, 50, random.nextInt(200), size, 1);
            }
        }
    }

    /* ── 序列化 ─────────────────────────────────────────── */

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BombType")) {
            setBombType(BombType.fromOrdinal(tag.getInt("BombType")));
        }
        if (tag.contains("Fuse")) {
            entityData.set(DATA_FUSE, tag.getInt("Fuse"));
        }
        this.owner = null;
        this.ownerUuid = readOwnerUuid(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BombType", getBombType().ordinal());
        tag.putInt("Fuse", getFuse());
        if (this.owner != null) {
            this.ownerUuid = this.owner.getUUID();
        }
        writeOwnerUuid(tag, this.ownerUuid);
    }

    @Nullable
    static UUID readOwnerUuid(CompoundTag tag) {
        return tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
    }

    static void writeOwnerUuid(CompoundTag tag, @Nullable UUID ownerUuid) {
        if (ownerUuid != null) {
            tag.putUUID(OWNER_TAG, ownerUuid);
        }
    }

    /* ── 杂项 ──────────────────────────────────────────── */

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }
}
