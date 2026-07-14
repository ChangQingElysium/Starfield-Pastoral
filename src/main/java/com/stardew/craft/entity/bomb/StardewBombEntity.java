package com.stardew.craft.entity.bomb;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.bomb.BombType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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

/**
 * SDV 炸弹实体 — 放置在地面上，引信倒数后爆炸。
 *
 * <p>完全复刻 SDV 原版逻辑：</p>
 * <ul>
 *   <li>引信时间：48 ticks (2400ms)，引信期间模型加速颤抖</li>
 *   <li>音效：放置 thudStep → 引信 fuse (循环) → 爆炸 explosion</li>
 *   <li>颤抖：shakeIntensity=0.5 + 0.002/tick 加速，完全匹配 SDV</li>
 *   <li>爆炸：MC 3D 球形破坏，方块破坏半径按炸弹类型单独平衡</li>
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

    @Nullable
    private Player owner;

    public StardewBombEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    /* ── 初始化 ─────────────────────────────────────────── */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOMB_TYPE, 0);
        builder.define(DATA_FUSE, 48);
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

    public void setOwner(@Nullable Player player) {
        this.owner = player;
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

    private void spawnFuseParticles(int fuse) {
        if (fuse <= 0) return;

        BombType type = getBombType();
        double sparkX = this.getX();
        double sparkY = this.getY() + getModelHeight(type);
        double sparkZ = this.getZ();

        // SDV: 53ms/frame × 5 frames × 9 loops = ~2385ms
        // MC: 基础火花每 tick 生成
        level().addParticle(ParticleTypes.FLAME,
            sparkX + (random.nextDouble() - 0.5) * 0.15,
            sparkY + random.nextDouble() * 0.1,
            sparkZ + (random.nextDouble() - 0.5) * 0.15,
            0, 0.02, 0);

        // 黄色/橙色层 — 每 2 tick 交替
        if (fuse % 2 == 0) {
            level().addParticle(ParticleTypes.LAVA,
                sparkX + (random.nextDouble() - 0.5) * 0.1,
                sparkY,
                sparkZ + (random.nextDouble() - 0.5) * 0.1,
                0, 0.01, 0);
        }

        // SDV 白色烟雾层 — 概率生成
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SMOKE,
                sparkX, sparkY + 0.1, sparkZ,
                0, 0.01, 0);
        }
    }

    private float getModelHeight(BombType type) {
        return switch (type) {
            case CHERRY_BOMB -> 0.375f;  // 6/16
            case BOMB -> 0.8125f;        // 13/16
            case MEGA_BOMB -> 0.875f;    // 14/16
        };
    }

    /* ── 爆炸 ──────────────────────────────────────────── */

    private void explode() {
        if (level().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level();
        BombType type = getBombType();
        BlockPos center = this.blockPosition();
        float scaledRadius = type.getScaledRadius();

        // 1. SDV: 停止 fuse 音效 → 播放 explosion
        serverLevel.playSound(null, center, ModSounds.EXPLOSION.get(),
            SoundSource.BLOCKS, 1.5f, 0.9f + random.nextFloat() * 0.2f);

        // 2. MC 3D 球形破坏方块，半径由炸弹类型单独平衡
        destroyBlocksInCircle(serverLevel, center, scaledRadius);

        // 3. 伤害范围内实体（SDV 伤害数值 + MC 3D 空间判定）
        damageEntitiesInRadius(serverLevel, type);

        // 4. SDV 爆炸粒子（按炸弹类型区分规模）
        spawnExplosionParticles(serverLevel, center, type, scaledRadius);

        // 5. 移除实体
        this.discard();
    }

    /**
     * SDV 使用 getCircleOutlineGrid(radius) 生成填充圆形图案。
     * 本实现在 XZ 平面上做 2D 圆形检测，Y 方向扩展 ±scaledRadius（球形）。
     */
    private void destroyBlocksInCircle(ServerLevel level, BlockPos center, float radius) {
        int r = (int) Math.ceil(radius);
        float radiusSq = radius * radius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // SDV 2D 圆形检测
                if (dx * dx + dz * dz > radiusSq) continue;

                for (int dy = -r; dy <= r; dy++) {
                    // 3D 球形检测（自然的 MC 3D 扩展）
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) continue;
                    if (!canBombDestroy(level, pos, state)) continue;

                    // SDV 炸弹掉落逻辑：矿石掉产物，其余掉自身
                    dropBlockForBomb(level, pos, state);
                    level.removeBlock(pos, false);

                    // 炸弹炸石头会递减 stonesLeft，但梯子概率低于手挖。
                    if (owner instanceof net.minecraft.server.level.ServerPlayer sp
                            && level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
                        com.stardew.craft.event.MiningBlockBreakHandler.handleStoneBreakFromBomb(level, sp, pos, state);
                    }
                }
            }
        }
    }

    private boolean canBombDestroy(ServerLevel level, BlockPos pos, BlockState state) {
        // 不破坏不可破坏方块（基岩 / 屏障 / 命令方块 / 末地传送门框 / 强化深板岩 等）
        if (isIndestructible(level, pos, state)) return false;
        // 不破坏矿井梯子和 portal trigger 这类功能方块。
        if (isBombProtectedBlock(state)) return false;
        // 多格家具/机器的 extension 格子只是占位。炸它们会触发每格各掉一份的安全网。
        if (isExtensionPart(state)) return false;
        // SDV parity：炸弹不破坏树（树干 / 树枝 / 树苗 / 树叶 等任何树的部分），
        // 砍树必须用斧头。
        if (com.stardew.craft.tree.WildTrees.isAnyWildTreePart(state)) return false;
        // 采石场：只允许炸掉每日/初始生成的石头、矿物等资源块，原始结构不允许被炸毁。
        if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(pos)
                && !com.stardew.craft.manager.QuarrySpawnService.canBombDestroyInQuarry(state)) {
            return false;
        }
        // 不破坏小镇区域和非权限农场的方块。
        if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && owner instanceof net.minecraft.server.level.ServerPlayer sp
                && !sp.isCreative()
                && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(sp, pos)) {
            return false;
        }
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
        if (getOreDropItem(state) != null) {
            return true;
        }
        if (state.is(ModBlocks.ARTIFACT_SPOT_DIRT.get())) {
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
    private void dropBlockForBomb(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (isExtensionPart(state)) {
            return;
        }

        // 0. 木桶：onRemove 已经会调用 dropBarrelLoot，这里不要再 popResource(barrelItem)
        //    否则玩家会同时拿到一个可放置的木桶方块物品。
        if (block instanceof com.stardew.craft.block.mine.MineBarrelBlock) {
            return;
        }

        // 1. SDV parity：让炸弹完全等价于"玩家挖矿"——吃 Miner / Geologist / Excavator /
        //    Prospector 职业、采矿等级、每日幸运、铱矿 3.5% 五彩碎片、120 层后普通石头 0.005% 五彩碎片、
        //    并给予挖矿经验。当 owner 是 ServerPlayer 时走这条路径。
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp && !sp.isCreative()) {
            net.minecraft.world.item.Item oreProduct =
                com.stardew.craft.event.MinePickaxeEvents.applyPlayerStyleBombDrops(level, sp, pos, state);
            if (oreProduct != null) {
                // 矿石已由 helper 投放，跳过自身/产物重复掉落
                return;
            }
            // 非矿石：继续走下面的"掉落自身"逻辑（额外晶洞/煤等已由 helper 处理）
        } else {
            // 非玩家所有者（极少见，例如指令召唤）：保留旧的简化矿石产物路径
            net.minecraft.world.item.Item oreProduct = getOreDropItem(state);
            if (oreProduct != null) {
                Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(oreProduct));
                if (state.is(com.stardew.craft.core.ModTags.Blocks.IRIDIUM_ORES)
                        && level.getRandom().nextDouble() < 0.035) {
                    Block.popResource(level, pos,
                        new net.minecraft.world.item.ItemStack(ModItems.PRISMATIC_SHARD.get(), 1));
                }
                return;
            }
        }

        // 1.5 远古斑点：被炸时不出古物，仅掉落普通黄土方块
        if (state.is(ModBlocks.ARTIFACT_SPOT_DIRT.get())) {
            Block.popResource(level, pos,
                new net.minecraft.world.item.ItemStack(ModBlocks.YELLOW_DIRT.get()));
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
        // 模组植物
        if (b instanceof com.stardew.craft.block.crop.StardewCropBlock) return true;
        if (b instanceof com.stardew.craft.block.nature.WildWeedsBlock) return true;
        if (b instanceof com.stardew.craft.block.tree.WildOakBranchBlock) return true;
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
     * SDV 矿石 → 产物映射（与 MinePickaxeEvents.getOreDropItem 一致）。
     * 返回 null 表示不是矿石。
     */
    @Nullable
    private static net.minecraft.world.item.Item getOreDropItem(BlockState state) {
        @SuppressWarnings("null")
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null || !com.stardew.craft.StardewCraft.MODID.equals(key.getNamespace())) {
            return null;
        }
        String path = key.getPath();
        if (path.contains("coal_ore"))     return ModItems.COAL.get();
        if (path.contains("copper_ore"))   return ModItems.COPPER_ORE.get();
        if (path.contains("iron_ore"))     return ModItems.IRON_ORE.get();
        if (path.contains("gold_ore"))     return ModItems.GOLD_ORE.get();
        if (path.contains("iridium_ore"))  return ModItems.IRIDIUM_ORE.get();
        return switch (path) {
            case "amethyst_ore"   -> ModItems.AMETHYST.get();
            case "aquamarine_ore" -> ModItems.AQUAMARINE.get();
            case "diamond_ore"    -> ModItems.DIAMOND.get();
            case "emerald_ore"    -> ModItems.EMERALD.get();
            case "jade_ore"       -> ModItems.JADE.get();
            case "ruby_ore"       -> ModItems.RUBY.get();
            case "topaz_ore"      -> ModItems.TOPAZ.get();
            default -> null;
        };
    }

    /**
     * SDV 伤害公式：怪物 r*6 ~ r*8，玩家自伤 r*3。
     * 范围判定改为 MC 3D 友好的水平圆柱：水平半径跟随可见爆炸半径，垂直半径随炸弹等级增长但保持克制。
     */
    private void damageEntitiesInRadius(ServerLevel level, BombType type) {
        double horizontalRadius = type.getScaledRadius();
        double verticalRadius = Math.max(1.25D, type.getRadius() * 0.75D);
        Vec3 center = new Vec3(this.getX(), this.getY() + 0.5D, this.getZ());
        AABB damageBox = new AABB(center, center).inflate(horizontalRadius, verticalRadius, horizontalRadius);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, damageBox);

        for (LivingEntity entity : entities) {
            if (!isInsideBombDamageVolume(entity, center, horizontalRadius, verticalRadius)) {
                continue;
            }

            DamageSource source = level.damageSources().explosion(this, owner);

            if (entity instanceof Player) {
                if (entity.hasEffect(ModMobEffects.DWARF_STATUE_3)) {
                    continue;
                }
                entity.hurt(source, type.getPlayerDamage());
            } else if (entity instanceof Mob) {
                int damage = type.getMinDamage()
                    + random.nextInt(type.getMaxDamage() - type.getMinDamage() + 1);
                entity.hurt(source, damage);
            }
        }
    }

    private boolean isInsideBombDamageVolume(Entity entity, Vec3 center, double horizontalRadius, double verticalRadius) {
        AABB box = entity.getBoundingBox().inflate(0.3D);
        double closestX = Mth.clamp(center.x, box.minX, box.maxX);
        double closestY = Mth.clamp(center.y, box.minY, box.maxY);
        double closestZ = Mth.clamp(center.z, box.minZ, box.maxZ);
        double dx = closestX - center.x;
        double dy = Math.abs(closestY - center.y);
        double dz = closestZ - center.z;
        return dx * dx + dz * dz <= horizontalRadius * horizontalRadius && dy <= verticalRadius;
    }

    /**
     * SDV: 爆炸粒子按炸弹类型区分。
     * Cherry Bomb: 小型爆炸，少量烟雾
     * Bomb: 中型爆炸 + 火焰+烟雾
     * Mega Bomb: 大型爆炸 + 大量火焰+烟雾+余烬
     */
    private void spawnExplosionParticles(ServerLevel level, BlockPos center,
                                         BombType type, float radius) {
        int r = (int) Math.ceil(radius);
        float radiusSq = radius * radius;

        // 核心爆炸大小取决于炸弹类型
        int emitterCount = switch (type) {
            case CHERRY_BOMB -> 1;
            case BOMB -> 2;
            case MEGA_BOMB -> 3;
        };
        for (int i = 0; i < emitterCount; i++) {
            double ox = (random.nextDouble() - 0.5) * radius * 0.5;
            double oz = (random.nextDouble() - 0.5) * radius * 0.5;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.getX() + 0.5 + ox, center.getY() + 0.5, center.getZ() + 0.5 + oz,
                1, 0, 0, 0, 0);
        }

        // SDV: 45% 概率/格，随机碎片或粉尘
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                if (random.nextFloat() > 0.45f) continue;

                double px = center.getX() + dx + 0.5 + (random.nextDouble() - 0.5) * 0.5;
                double py = center.getY() + 0.5 + random.nextDouble() * 1.5;
                double pz = center.getZ() + dz + 0.5 + (random.nextDouble() - 0.5) * 0.5;

                if (random.nextBoolean()) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        px, py, pz, 1,
                        0.2, 0.3, 0.2, 0.01);
                } else {
                    level.sendParticles(ParticleTypes.POOF,
                        px, py - 0.3, pz, 1,
                        0.1, 0.2, 0.1, 0.02);
                }
            }
        }

        // Bomb / Mega Bomb: 额外火焰粒子（模拟 SDV 大规模爆炸火焰）
        if (type == BombType.BOMB || type == BombType.MEGA_BOMB) {
            int fireCount = type == BombType.MEGA_BOMB ? 20 : 8;
            for (int i = 0; i < fireCount; i++) {
                double fx = center.getX() + 0.5 + (random.nextDouble() - 0.5) * radius * 2;
                double fy = center.getY() + 0.5 + random.nextDouble() * 2.0;
                double fz = center.getZ() + 0.5 + (random.nextDouble() - 0.5) * radius * 2;
                level.sendParticles(ParticleTypes.FLAME,
                    fx, fy, fz, 1,
                    0.05, 0.15, 0.05, 0.03);
            }
        }

        // Mega Bomb: 额外大型烟柱 + 余烬
        if (type == BombType.MEGA_BOMB) {
            for (int i = 0; i < 12; i++) {
                double sx = center.getX() + 0.5 + (random.nextDouble() - 0.5) * radius * 1.5;
                double sz = center.getZ() + 0.5 + (random.nextDouble() - 0.5) * radius * 1.5;
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    sx, center.getY() + 1.0, sz, 1,
                    0.1, 0.5, 0.1, 0.02);
            }
            for (int i = 0; i < 8; i++) {
                double lx = center.getX() + 0.5 + (random.nextDouble() - 0.5) * radius * 2;
                double lz = center.getZ() + 0.5 + (random.nextDouble() - 0.5) * radius * 2;
                level.sendParticles(ParticleTypes.LAVA,
                    lx, center.getY() + 0.5, lz, 1,
                    0, 0.05, 0, 0);
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BombType", getBombType().ordinal());
        tag.putInt("Fuse", getFuse());
    }

    /* ── 杂项 ──────────────────────────────────────────── */

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }
}
