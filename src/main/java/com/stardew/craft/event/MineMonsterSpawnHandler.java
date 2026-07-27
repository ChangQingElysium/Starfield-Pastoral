package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.network.payload.MummyCollapsePayload;
import net.minecraft.network.chat.Component;
import com.stardew.craft.enchantment.StardewEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;
import java.util.Set;

/**
 * 矿井怪物刷怪事件处理器
 *
 * 当生物加入矿井维度时：
 * 1. 仅允许指定MC原版生物类型
 * 2. 根据楼层分配 sd_mob_* / sd_tier_* 标签
 * 3. 覆写 HP / 攻击力 / 护甲 / 移速 以匹配SDV怪物属性
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class MineMonsterSpawnHandler {

    private static final ResourceLocation MOD_HP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mine_mob_hp");
    private static final ResourceLocation MOD_ATK = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mine_mob_atk");
    private static final ResourceLocation MOD_ARMOR = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mine_mob_armor");
    private static final ResourceLocation MOD_SPEED = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mine_mob_speed");
    private static final ResourceLocation MOD_MUMMY_COLLAPSED_HP = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mummy_collapsed_hp");

    /** 每层楼最大怪物数量（房间 80-120 格，面积比 SDV 大 5-10 倍） */
    private static final int MAX_MONSTERS_PER_FLOOR = 30;

    /** Per-floor mob count cache to avoid expensive AABB scans on every spawn. */
    private static final java.util.Map<Integer, Integer> floorMobCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> prismaticSlimeFloors = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static long lastCountRefreshTick = 0;
    private static final long COUNT_REFRESH_INTERVAL = 60; // refresh every 3 seconds

        private static final java.util.List<String> SUMMONABLE_MONSTER_IDS = java.util.List.of(
            "mummy",
            "serpent",
            "royal_serpent",
            "pepper_rex",
            "big_slime",
            "green_slime",
            "frost_jelly",
            "sludge",
            "bat",
            "frost_bat",
            "lava_bat",
            "iridium_bat",
            "rock_crab",
            "truffle_crab",
            "lava_crab",
            "iridium_crab",
            "duggy",
            "grub",
            "dust_sprite",
            "bug",
            "fly",
            "ghost",
            "carbon_ghost",
            "skeleton",
            "rock_golem",
            "metal_head",
            "shadow_brute",
            "shadow_shaman",
            "squid_kid"
        );

    static {
        registerBuiltinProfiles();
    }

    /** Ensures the built-in profiles are initialized before catalog projection. */
    public static void ensureProfilesRegistered() {
    }

    private static void registerBuiltinProfiles() {
        for (String monsterId : SUMMONABLE_MONSTER_IDS) {
            EntityType<? extends Mob> entityType =
                    getSummonEntityType(monsterId);
            if (entityType == null) {
                continue;
            }
            StardewMineMonsterProfiles.register(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, monsterId),
                    entityType,
                    builtinProgressTags(monsterId),
                    (mob, context) -> {
                        if (!applySummonProfile(
                                mob, monsterId, context.floor())) {
                            throw new IllegalStateException(
                                    "Unknown built-in monster profile "
                                            + monsterId);
                        }
                    });
        }
    }

    private static Set<String> builtinProgressTags(String monsterId) {
        return switch (monsterId) {
            case "green_slime" ->
                    Set.of("sd_mob_slime");
            case "frost_jelly" ->
                    Set.of("sd_mob_slime", "sd_tier_2");
            case "sludge" ->
                    Set.of("sd_mob_slime", "sd_tier_3");
            case "bat" ->
                    Set.of("sd_mob_bat");
            case "frost_bat" ->
                    Set.of("sd_mob_bat", "sd_tier_2");
            case "lava_bat" ->
                    Set.of("sd_mob_bat", "sd_tier_3");
            case "iridium_bat" ->
                    Set.of("sd_mob_bat", "sd_tier_4");
            case "rock_crab" ->
                    Set.of("sd_mob_crab");
            case "truffle_crab" ->
                    Set.of("sd_mob_crab", "sd_truffle_crab");
            case "lava_crab" ->
                    Set.of("sd_mob_crab", "sd_tier_2");
            case "iridium_crab" ->
                    Set.of("sd_mob_crab", "sd_tier_4",
                            "sd_tier_skull");
            case "duggy" ->
                    Set.of("sd_mob_duggy");
            case "grub" ->
                    Set.of("sd_mob_grub");
            case "dust_sprite" ->
                    Set.of("sd_mob_dust_sprite");
            case "bug" ->
                    Set.of("sd_mob_bug");
            case "fly" ->
                    Set.of("sd_mob_fly");
            case "ghost" ->
                    Set.of("sd_mob_ghost");
            case "carbon_ghost" ->
                    Set.of("sd_mob_ghost", "sd_tier_skull");
            case "skeleton" ->
                    Set.of("sd_mob_skeleton");
            case "rock_golem" ->
                    Set.of("sd_mob_golem");
            case "metal_head" ->
                    Set.of("sd_mob_metal_head");
            case "shadow_brute" ->
                    Set.of("sd_mob_shadow");
            case "shadow_shaman" ->
                    Set.of("sd_mob_shadow", "sd_tier_2");
            case "squid_kid" ->
                    Set.of("sd_mob_squid");
            case "mummy" ->
                    Set.of("sd_mob_mummy", "sd_tier_skull");
            case "serpent" ->
                    Set.of("sd_mob_serpent", "sd_tier_skull");
            case "royal_serpent" ->
                    Set.of("sd_mob_royal_serpent", "sd_tier_skull");
            case "pepper_rex" ->
                    Set.of("sd_mob_dino", "sd_tier_skull");
            case "big_slime" ->
                    Set.of("sd_mob_bigslime_skull", "sd_tier_skull");
            default -> Set.of();
        };
    }

    public static void invalidateFloorMobCount(int floor) {
        floorMobCounts.remove(floor);
    }

    public static java.util.List<String> getSummonableMonsterIds() {
        return SUMMONABLE_MONSTER_IDS;
    }

    public static int inferFloor(ServerPlayer player) {
        if (player == null) {
            return 1;
        }
        if (!player.serverLevel().dimension().equals(ModMiningDimensions.STARDEW_MINING)) {
            return 1;
        }
        return Math.max(1, Math.round(player.blockPosition().getZ() / (float) MiningCoordinates.FLOOR_SPACING));
    }

    public static Mob spawnConfiguredMonster(ServerLevel level, String monsterId, Vec3 position, float yaw, int floor) {
        return spawnConfiguredMonster(level, monsterId, position, yaw, floor, ignored -> {
        });
    }

    /** Configures caller-owned marker tags before EntityJoinLevelEvent filters inspect the mob. */
    public static Mob spawnConfiguredMonster(ServerLevel level, String monsterId, Vec3 position, float yaw, int floor,
                                             Consumer<Mob> configureBeforeSpawn) {
        if (level == null || monsterId == null || monsterId.isBlank() || position == null) {
            return null;
        }

        String normalizedId = normalizeMonsterId(monsterId);
        EntityType<? extends Mob> entityType = getSummonEntityType(normalizedId);
        if (entityType == null) {
            return null;
        }

        Mob mob = entityType.create(level);
        if (mob == null) {
            return null;
        }

        mob.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        if (!applySummonProfile(mob, normalizedId, floor)) {
            return null;
        }
        if (configureBeforeSpawn != null) {
            configureBeforeSpawn.accept(mob);
        }
        if (!level.addFreshEntity(mob)) {
            return null;
        }

        invalidateFloorMobCount(floor);
        return mob;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModMiningDimensions.STARDEW_MINING)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        // StardewNpcEntity (e.g. Dwarf) is a Mob subclass — never filter it
        if (mob instanceof com.stardew.craft.entity.npc.StardewNpcEntity) return;

        // 已经标记过的不再处理
        if (mob.getTags().stream().anyMatch(t -> t.startsWith("sd_mob_"))) return;

        // 怪物数量上限检查：使用缓存计数而非每次 AABB 扫描
        int floor = getFloorFromPos(mob);

        // Periodically refresh counts (cheap: just reset, actual AABB scan only if needed)
        long currentTick = serverLevel.getGameTime();
        if (currentTick - lastCountRefreshTick > COUNT_REFRESH_INTERVAL) {
            floorMobCounts.clear();
            lastCountRefreshTick = currentTick;
        }

        int cachedCount = floorMobCounts.getOrDefault(floor, -1);
        if (cachedCount == -1) {
            // First check for this floor in this refresh window — do actual count
            int floorZ = floor * MiningCoordinates.FLOOR_SPACING;
            AABB floorBounds = new AABB(
                    mob.getX() - 150, 0, floorZ - 10,
                    mob.getX() + 150, 256, floorZ + 150);
            cachedCount = serverLevel.getEntitiesOfClass(Mob.class, floorBounds,
                    m -> m.getTags().stream().anyMatch(t -> t.startsWith("sd_mob_"))).size();
            floorMobCounts.put(floor, cachedCount);
        }

        if (cachedCount >= MAX_MONSTERS_PER_FLOOR) {
            event.setCanceled(true);
            return;
        }

        // Increment cached count for this floor
        floorMobCounts.merge(floor, 1, Integer::sum);
        if (StardewMineMonsterProfiles.hasMarkedProfile(mob)) {
            if (!StardewMineMonsterProfiles.applyMarkedProfile(
                    mob, floor)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!applyDefaultProfile(mob, floor)) {
            // 不属于矿井怪物映射表：取消生成
            event.setCanceled(true);
        }
    }

    // ======================== 怪物分配 ========================
    // 数值基于 SDV Monsters.json，加入楼层缩放（zone 起始 0.8x → zone 末尾 1.0x）

    /**
     * 楼层难度缩放：每个 zone 内部从 0.8 逐渐升到 1.0
     * 让同一 zone 前几层更轻松，后几层接近 SDV 原值
     */
    private static float getFloorScaling(int floor) {
        float progress;
        if (floor <= 40) {
            progress = Math.max(0f, floor / 40f);
        } else if (floor <= 79) {
            progress = (floor - 40) / 40f;
        } else if (floor <= 119) {
            progress = (floor - 80) / 40f;
        } else if (floor > 120) {
            // 骷髅矿：1.0 + (floor-120) * 0.008，上限 2.5
            float skullScaling = 1.0f + (floor - 120) * 0.008f;
            return Math.min(2.5f, skullScaling);
        } else {
            progress = 1.0f;
        }
        return 0.8f + 0.2f * progress;
    }

    private static void assignSlime(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_slime");
        if (floor >= 80) {
            mob.addTag("sd_tier_3");
            setStats(mob, 205 * s, 16 * s, 0, 0.25);
            setSDVName(mob, "Sludge");
        } else if (floor >= 40) {
            mob.addTag("sd_tier_2");
            setStats(mob, 106 * s, 7 * s, 0, 0.25);
            setSDVName(mob, "Frost Jelly");
        } else {
            setStats(mob, 24 * s, 5 * s, 0, 0.25);
            setSDVName(mob, "Green Slime");
        }
        tryMakePrismaticSlime(mob, floor);
    }

    private static void tryMakePrismaticSlime(Mob mob, int floor) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String key = serverLevel.dimension().location() + ":" + com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay() + ":" + floor;
        if (prismaticSlimeFloors.contains(key)) {
            return;
        }
        boolean activeForFloor = serverLevel.getPlayers(player ->
            getFloorFromPlayer(player) == floor
                && com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(player, "Wizard2")
                && !com.stardew.craft.specialorder.SpecialOrderManager.hasSpecialDropFlag(player, "prismaticJellyDrop")
        ).size() > 0;
        if (!activeForFloor) {
            return;
        }
        if (mob.getRandom().nextDouble() > 0.012D) {
            return;
        }
        prismaticSlimeFloors.add(key);
        mob.addTag("sd_mob_prismatic_slime");
        setSDVName(mob, "Prismatic Slime");
    }

    private static void assignBat(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_bat");
        if (floor >= 120) {
            mob.addTag("sd_tier_4");
            setStats(mob, 300 * s, 25 * s, 0, 0.40); // Iridium Bat — 按楼层缩放
            setSDVName(mob, "Iridium Bat");
        } else if (floor >= 80) {
            mob.addTag("sd_tier_3");
            setStats(mob, 80 * s, 15 * s, 0, 0.35);
            setSDVName(mob, "Lava Bat");
        } else if (floor >= 40) {
            mob.addTag("sd_tier_2");
            setStats(mob, 36 * s, 7 * s, 0, 0.30);
            setSDVName(mob, "Frost Bat");
        } else {
            setStats(mob, 24 * s, 6 * s, 0, 0.30);
            setSDVName(mob, "Bat");
        }
    }

    private static void assignSilverfish(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        if (floor > 120) {
            // 骷髅矿：Iridium Crab
            mob.addTag("sd_mob_crab");
            mob.addTag("sd_tier_4");
            mob.addTag("sd_tier_skull");
            setStats(mob, 300 * s, 28 * s, 16, 0.20);
            setSDVName(mob, "Iridium Crab");
        } else if (floor >= 80) {
            mob.addTag("sd_mob_crab");
            mob.addTag("sd_tier_2");
            setStats(mob, 120 * s, 15 * s, 12, 0.25);
            setSDVName(mob, "Lava Crab");
        } else if (floor >= 40) {
            mob.addTag("sd_mob_duggy");
            setStats(mob, 40 * s, 6 * s, 0, 0.20);
            setSDVName(mob, "Duggy");
        } else {
            // 1-39: Rock Crab 或 Duggy（随机）
            if (mob.getRandom().nextBoolean()) {
                mob.addTag("sd_mob_crab");
                setStats(mob, 30 * s, 5 * s, 8, 0.20);
                setSDVName(mob, "Rock Crab");
            } else {
                mob.addTag("sd_mob_duggy");
                setStats(mob, 40 * s, 6 * s, 0, 0.20);
                setSDVName(mob, "Duggy");
            }
        }
    }

    private static void assignEndermite(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        if (floor >= 40) {
            mob.addTag("sd_mob_dust_sprite");
            setStats(mob, 40 * s, 6 * s, 2, 0.35);
            setSDVName(mob, "Dust Spirit");
        } else {
            mob.addTag("sd_mob_grub");
            setStats(mob, 20 * s, 4 * s, 0, 0.15);
            setSDVName(mob, "Grub");
        }
    }

    /** Bug（SDV 装甲飞虫）— MC Spider 映射 */
    private static void assignBug(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_bug");
        setStats(mob, 1, 8 * s, 0, 0.30);  // SDV Bug: 1HP 但 8ATK，一碰就死
        setSDVName(mob, "Bug");
    }

    /** Fly（SDV 苍蝇）— MC Cave Spider 映射 */
    private static void assignFly(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_fly");
        setStats(mob, 22 * s, 6 * s, 0, 0.30);
        setSDVName(mob, "Fly");
    }

    /** Ghost — MC Husk 映射（缓慢肉搏型，每层限 1 只） */
    private static void assignGhost(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        if (floor > 120) {
            // 骷髅矿：Carbon Ghost
            mob.addTag("sd_mob_ghost");
            mob.addTag("sd_tier_skull");
            setStats(mob, 190 * s, 25 * s, 4, 0.30);
            setSDVName(mob, "Carbon Ghost");
        } else {
            mob.addTag("sd_mob_ghost");
            setStats(mob, 96 * s, 10 * s, 3, 0.25);
            setSDVName(mob, "Ghost");
        }
    }

    private static void assignSkeleton(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_skeleton");
        setStats(mob, 140 * s, 10 * s, 2, 0.25);
        setSDVName(mob, "Skeleton");
    }

    private static void assignZombie(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        if (floor >= 80) {
            mob.addTag("sd_mob_metal_head");
            setStats(mob, 40 * s, 15 * s, 16, 0.20);  // SDV Metal Head: 40HP 高甲
            setSDVName(mob, "Metal Head");
        } else {
            mob.addTag("sd_mob_golem");
            setStats(mob, 45 * s, 5 * s, 10, 0.18);
            setSDVName(mob, "Rock Golem");
        }
    }

    private static void assignWitherSkeleton(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_shadow");
        setStats(mob, 160 * s, 18 * s, 4, 0.30);
        setSDVName(mob, "Shadow Brute");
    }

    /** Shadow Shaman — MC Stray 映射（远程骷髅，替代 Evoker 避免召唤恼鬼） */
    private static void assignStray(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_shadow");
        mob.addTag("sd_tier_2");
        setStats(mob, 80 * s, 17 * s, 2, 0.25);
        setSDVName(mob, "Shadow Shaman");
    }

    private static void assignBlaze(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_squid");
        setStats(mob, 50 * s, 18 * s, 2, 0.25);
        setSDVName(mob, "Squid Kid");
    }

    // ──────── 骷髅矿洞新增怪物映射 ────────

    /** Mummy — MC Drowned 映射（骷髅矿核心怪物，被击倒后可复活） */
    private static void assignMummy(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_mummy");
        mob.addTag("sd_tier_skull");
        setStats(mob, 260 * s, 30 * s, 4, 0.20);
        setSDVName(mob, "Mummy");
    }

    /** Serpent — MC Vex 映射（飞行型，速度快） */
    private static void assignSerpent(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        // 深层变为 Royal Serpent
        if (floor >= 200 && s > 1.5f) {
            mob.addTag("sd_mob_royal_serpent");
            mob.addTag("sd_tier_skull");
            setStats(mob, 300 * s, 30 * s, 3, 0.40);
            setSDVName(mob, "Royal Serpent");
        } else {
            mob.addTag("sd_mob_serpent");
            mob.addTag("sd_tier_skull");
            setStats(mob, 150 * s, 23 * s, 2, 0.35);
            setSDVName(mob, "Serpent");
        }
    }

    /** DinoMonster — MC Hoglin 映射（稀有地面大型怪） */
    private static void assignDino(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_dino");
        mob.addTag("sd_tier_skull");
        setStats(mob, 300 * s, 15 * s, 6, 0.25);
        setSDVName(mob, "Pepper Rex");
    }

    /** BigSlime (Skull) — MC MagmaCube 映射 */
    private static void assignBigSlime(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_bigslime_skull");
        mob.addTag("sd_tier_skull");
        setStats(mob, 200 * s, 20 * s, 2, 0.20);
        setSDVName(mob, "Big Slime");
    }

    private static boolean applyDefaultProfile(Mob mob, int floor) {
        EntityType<?> type = mob.getType();
        if (type == EntityType.SLIME) {
            assignSlime(mob, floor);
            return true;
        }
        if (type == EntityType.PHANTOM) {
            assignBat(mob, floor);
            return true;
        }
        if (type == EntityType.SILVERFISH) {
            assignSilverfish(mob, floor);
            return true;
        }
        if (type == EntityType.ENDERMITE) {
            assignEndermite(mob, floor);
            return true;
        }
        if (type == EntityType.SPIDER) {
            assignBug(mob, floor);
            return true;
        }
        if (type == EntityType.CAVE_SPIDER) {
            assignFly(mob, floor);
            return true;
        }
        if (type == EntityType.HUSK) {
            assignGhost(mob, floor);
            return true;
        }
        if (type == EntityType.SKELETON) {
            assignSkeleton(mob, floor);
            return true;
        }
        if (type == EntityType.ZOMBIE) {
            assignZombie(mob, floor);
            return true;
        }
        if (type == EntityType.WITHER_SKELETON) {
            assignWitherSkeleton(mob, floor);
            return true;
        }
        if (type == EntityType.STRAY) {
            assignStray(mob, floor);
            return true;
        }
        if (type == EntityType.BLAZE) {
            assignBlaze(mob, floor);
            return true;
        }
        if (type == EntityType.DROWNED) {
            assignMummy(mob, floor);
            return true;
        }
        if (type == EntityType.VEX) {
            assignSerpent(mob, floor);
            return true;
        }
        if (type == EntityType.HOGLIN) {
            assignDino(mob, floor);
            return true;
        }
        if (type == EntityType.MAGMA_CUBE) {
            assignBigSlime(mob, floor);
            return true;
        }
        return false;
    }

    private static boolean applySummonProfile(Mob mob, String monsterId, int floor) {
        int resolvedFloor = Math.max(1, floor);
        switch (monsterId) {
            case "mummy" -> assignMummy(mob, Math.max(121, resolvedFloor));
            case "serpent" -> assignSerpent(mob, clampFloor(resolvedFloor, 121, 199));
            case "royal_serpent" -> assignSerpent(mob, Math.max(200, resolvedFloor));
            case "pepper_rex" -> assignDino(mob, Math.max(121, resolvedFloor));
            case "big_slime" -> assignBigSlime(mob, Math.max(121, resolvedFloor));
            case "green_slime" -> assignSlime(mob, clampFloor(resolvedFloor, 1, 39));
            case "frost_jelly" -> assignSlime(mob, clampFloor(resolvedFloor, 40, 79));
            case "sludge" -> assignSlime(mob, clampFloor(resolvedFloor, 80, 119));
            case "bat" -> assignBat(mob, clampFloor(resolvedFloor, 1, 39));
            case "frost_bat" -> assignBat(mob, clampFloor(resolvedFloor, 40, 79));
            case "lava_bat" -> assignBat(mob, clampFloor(resolvedFloor, 80, 119));
            case "iridium_bat" -> assignBat(mob, Math.max(120, resolvedFloor));
            case "rock_crab" -> assignRockCrab(mob, clampFloor(resolvedFloor, 1, 39));
            case "truffle_crab" -> assignTruffleCrab(mob);
            case "lava_crab" -> assignLavaCrab(mob, clampFloor(resolvedFloor, 80, 119));
            case "iridium_crab" -> assignIridiumCrab(mob, Math.max(121, resolvedFloor));
            case "duggy" -> assignDuggy(mob, clampFloor(resolvedFloor, 1, 79));
            case "grub" -> assignEndermite(mob, clampFloor(resolvedFloor, 1, 39));
            case "dust_sprite" -> assignEndermite(mob, clampFloor(resolvedFloor, 40, 119));
            case "bug" -> assignBug(mob, resolvedFloor);
            case "fly" -> assignFly(mob, resolvedFloor);
            case "ghost" -> assignGhost(mob, clampFloor(resolvedFloor, 1, 120));
            case "carbon_ghost" -> assignGhost(mob, Math.max(121, resolvedFloor));
            case "skeleton" -> assignSkeleton(mob, resolvedFloor);
            case "rock_golem" -> assignZombie(mob, clampFloor(resolvedFloor, 1, 79));
            case "metal_head" -> assignZombie(mob, clampFloor(resolvedFloor, 80, 120));
            case "shadow_brute" -> assignWitherSkeleton(mob, resolvedFloor);
            case "shadow_shaman" -> assignStray(mob, resolvedFloor);
            case "squid_kid" -> assignBlaze(mob, resolvedFloor);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static EntityType<? extends Mob> getSummonEntityType(String monsterId) {
        return switch (monsterId) {
            case "mummy" -> EntityType.DROWNED;
            case "serpent", "royal_serpent" -> EntityType.VEX;
            case "pepper_rex" -> EntityType.HOGLIN;
            case "big_slime" -> EntityType.MAGMA_CUBE;
            case "green_slime", "frost_jelly", "sludge" -> EntityType.SLIME;
            case "bat", "frost_bat", "lava_bat", "iridium_bat" -> EntityType.PHANTOM;
            case "rock_crab", "truffle_crab", "lava_crab", "iridium_crab", "duggy" -> EntityType.SILVERFISH;
            case "grub", "dust_sprite" -> EntityType.ENDERMITE;
            case "bug" -> EntityType.SPIDER;
            case "fly" -> EntityType.CAVE_SPIDER;
            case "ghost", "carbon_ghost" -> EntityType.HUSK;
            case "skeleton" -> EntityType.SKELETON;
            case "rock_golem", "metal_head" -> EntityType.ZOMBIE;
            case "shadow_brute" -> EntityType.WITHER_SKELETON;
            case "shadow_shaman" -> EntityType.STRAY;
            case "squid_kid" -> EntityType.BLAZE;
            default -> null;
        };
    }

    private static String normalizeMonsterId(String monsterId) {
        return switch (monsterId.toLowerCase(java.util.Locale.ROOT)) {
            case "slime" -> "green_slime";
            case "bigslime" -> "big_slime";
            case "crab" -> "rock_crab";
            case "dino" -> "pepper_rex";
            case "golem" -> "rock_golem";
            case "shadow" -> "shadow_brute";
            default -> monsterId.toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static int clampFloor(int floor, int min, int max) {
        return Math.max(min, Math.min(max, floor));
    }

    private static void assignRockCrab(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_crab");
        setStats(mob, 30 * s, 5 * s, 8, 0.20);
        setSDVName(mob, "Rock Crab");
    }

    private static void assignTruffleCrab(Mob mob) {
        mob.addTag("sd_mob_crab");
        mob.addTag("sd_truffle_crab");
        setStats(mob, 30, 5, 8, 0.20);
        setSDVName(mob, "Truffle Crab");
    }

    private static void assignLavaCrab(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_crab");
        mob.addTag("sd_tier_2");
        setStats(mob, 120 * s, 15 * s, 12, 0.25);
        setSDVName(mob, "Lava Crab");
    }

    private static void assignIridiumCrab(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_crab");
        mob.addTag("sd_tier_4");
        mob.addTag("sd_tier_skull");
        setStats(mob, 300 * s, 28 * s, 16, 0.20);
        setSDVName(mob, "Iridium Crab");
    }

    private static void assignDuggy(Mob mob, int floor) {
        float s = getFloorScaling(floor);
        mob.addTag("sd_mob_duggy");
        setStats(mob, 40 * s, 6 * s, 0, 0.20);
        setSDVName(mob, "Duggy");
    }

    // ======================== 属性设置 ========================

    @SuppressWarnings("null")
    private static void setStats(Mob mob, double hp, double atk, double armor, double speed) {
        // HP
        AttributeInstance maxHpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            double base = maxHpAttr.getBaseValue();
            double diff = hp - base;
            maxHpAttr.removeModifier(MOD_HP);
            maxHpAttr.addPermanentModifier(new AttributeModifier(
                    MOD_HP, diff, AttributeModifier.Operation.ADD_VALUE));
            mob.setHealth((float) hp);
        }

        // Attack
        AttributeInstance atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            double base = atkAttr.getBaseValue();
            double diff = atk - base;
            atkAttr.removeModifier(MOD_ATK);
            atkAttr.addPermanentModifier(new AttributeModifier(
                    MOD_ATK, diff, AttributeModifier.Operation.ADD_VALUE));
        }

        // Armor
        AttributeInstance armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            double base = armorAttr.getBaseValue();
            double diff = armor - base;
            armorAttr.removeModifier(MOD_ARMOR);
            armorAttr.addPermanentModifier(new AttributeModifier(
                    MOD_ARMOR, diff, AttributeModifier.Operation.ADD_VALUE));
        }

        // Speed
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double base = speedAttr.getBaseValue();
            double diff = speed - base;
            speedAttr.removeModifier(MOD_SPEED);
            speedAttr.addPermanentModifier(new AttributeModifier(
                    MOD_SPEED, diff, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Applies the original hard-mode BugLand values after using the shared monster factory. */
    public static void applyMutantBugLairProfile(Mob mob, String monsterId) {
        if (mob == null || monsterId == null) {
            return;
        }
        if ("grub".equals(monsterId)) {
            mob.addTag("sd_mob_mutant_grub");
            setStats(mob, 100, 12, 0, 0.15);
            setSDVName(mob, "Mutant Grub");
        } else if ("fly".equals(monsterId)) {
            mob.addTag("sd_mob_mutant_fly");
            setStats(mob, 66, 12, 0, 0.30);
            setSDVName(mob, "Mutant Fly");
        }
    }

    /**
     * 设置 SDV 显示名（同步到客户端），隐藏原版 nametag。
     */
    private static void setSDVName(Mob mob, String name) {
        mob.setCustomName(Component.literal(name));
        mob.setCustomNameVisible(false);
    }

    private static int getFloorFromPos(Mob mob) {
        return Math.round(mob.blockPosition().getZ() / (float) MiningCoordinates.FLOOR_SPACING);
    }

    private static int getFloorFromPlayer(ServerPlayer player) {
        if (player == null || !player.serverLevel().dimension().equals(ModMiningDimensions.STARDEW_MINING)) {
            return 1;
        }
        return Math.max(1, Math.round(player.blockPosition().getZ() / (float) MiningCoordinates.FLOOR_SPACING));
    }

    // ═══════════ Mummy 复活机制 ═══════════
    // SDV: 非爆炸击杀 → 倒地 10 秒后原地复活满血；只有炸弹能永久击杀

    /** Mummy 倒地标记 tag */
    private static final String MUMMY_COLLAPSED_TAG = "sd_mummy_collapsed";
    private static final String MUMMY_REVIVE_TICK_TAG = "stardewcraft_mummy_revive_tick";
    private static final int MUMMY_COLLAPSE_TICKS = 200;

    private static boolean isMummyPermanentKillSource(DamageSource source) {
        return source != null && (
                source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || (source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                    && StardewEnchantments.has(player.getMainHandItem(), StardewEnchantments.CRUSADER)));
    }

    public static boolean isCollapsedMummy(LivingEntity entity) {
        return entity instanceof Mob mob
                && mob.getTags().contains("sd_mob_mummy")
                && mob.getTags().contains(MUMMY_COLLAPSED_TAG);
    }

    @SubscribeEvent
    public static void onMummyIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getTags().contains("sd_mob_mummy")) return;
        if (!mob.getTags().contains(MUMMY_COLLAPSED_TAG)) {
            if (!isMummyPermanentKillSource(event.getSource()) && event.getAmount() >= mob.getHealth()) {
                StardewCraft.LOGGER.info(
                        "[MUMMY] incoming fatal hit intercepted entityId={} hp={} dmg={} src={} pos={} tags={}",
                        mob.getId(),
                        mob.getHealth(),
                        event.getAmount(),
                        describeDamageSource(event.getSource()),
                        mob.blockPosition(),
                        mob.getTags());
                event.setAmount(0.0F);
                enterMummyCollapseState(mob);
            }
            return;
        }

        // SDV parity: 倒地后的木乃伊不会被普通武器继续打死，
        // 但炸弹/管理员 kill 仍然可以永久处决。
        if (!isMummyPermanentKillSource(event.getSource())) {
            StardewCraft.LOGGER.info(
                    "[MUMMY] blocked follow-up damage on collapsed mummy entityId={} dmg={} src={} pos={}",
                    mob.getId(),
                    event.getAmount(),
                    describeDamageSource(event.getSource()),
                    mob.blockPosition());
            event.setAmount(0.0F);
        } else {
            StardewCraft.LOGGER.info(
                    "[MUMMY] allowing permanent kill damage on collapsed mummy entityId={} dmg={} src={} pos={}",
                    mob.getId(),
                    event.getAmount(),
                    describeDamageSource(event.getSource()),
                    mob.blockPosition());
            clearCollapsedStateForPermanentDeath(mob);
        }
    }

    @SubscribeEvent
    public static void onMummyDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getTags().contains("sd_mob_mummy")) return;
        // 已经在倒地状态：只有炸弹/管理员 kill 才能永久击杀。
        if (mob.getTags().contains(MUMMY_COLLAPSED_TAG)) {
            if (isMummyPermanentKillSource(event.getSource())) {
                StardewCraft.LOGGER.info(
                        "[MUMMY] collapsed mummy is dying permanently entityId={} src={} pos={}",
                        mob.getId(),
                        describeDamageSource(event.getSource()),
                        mob.blockPosition());
                clearCollapsedStateForPermanentDeath(mob);
                return;
            }
            StardewCraft.LOGGER.warn(
                    "[MUMMY] LivingDeathEvent fired for already-collapsed mummy without permanent source entityId={} src={} hp={} pos={}",
                    mob.getId(),
                    describeDamageSource(event.getSource()),
                    mob.getHealth(),
                    mob.blockPosition());
            event.setCanceled(true);
            mob.setHealth(Math.max(1.0F, mob.getHealth()));
            return;
        }

        if (isMummyPermanentKillSource(event.getSource())) {
            StardewCraft.LOGGER.info(
                    "[MUMMY] non-collapsed mummy received permanent kill source entityId={} src={} pos={}",
                    mob.getId(),
                    describeDamageSource(event.getSource()),
                    mob.blockPosition());
            return;
        }

        // 正常武器击杀现在应在 LivingIncomingDamageEvent 阶段被前置截断；
        // 这里保留兜底，防止有别的伤害路径绕过前置事件直接进入死亡流程。
        StardewCraft.LOGGER.warn(
                "[MUMMY] fallback LivingDeathEvent collapse path entityId={} src={} hp={} pos={} tags={}",
                mob.getId(),
                describeDamageSource(event.getSource()),
                mob.getHealth(),
                mob.blockPosition(),
                mob.getTags());
        event.setCanceled(true);
        enterMummyCollapseState(mob);
    }

    @SubscribeEvent
    public static void onMummyTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!mob.getTags().contains("sd_mob_mummy")) return;
        if (!mob.getTags().contains(MUMMY_COLLAPSED_TAG)) return;
        if (mob.isDeadOrDying()) return;

        mob.setNoAi(true);
        mob.setPose(Pose.STANDING);
        mob.setSilent(true);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setXRot(0.0F);
        mob.xRotO = 0.0F;
        mob.invulnerableTime = 0;
        mob.hurtTime = 0;
        mob.deathTime = 0;
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }

        long reviveTick = mob.getPersistentData().getLong(MUMMY_REVIVE_TICK_TAG);
        long nowTick = level.getGameTime();
        if (reviveTick > 0L && nowTick >= reviveTick) {
            reviveMummy(mob);
        }
    }

    private static void syncMummyCollapseState(Mob mob, int durationTicks) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        StardewCraft.LOGGER.info(
                "[MUMMY] sync collapse state entityId={} durationTicks={} players={} pos={}",
                mob.getId(),
                durationTicks,
                level.players().size(),
                mob.blockPosition());
        MummyCollapsePayload payload = new MummyCollapsePayload(mob.getId(), durationTicks);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void enterMummyCollapseState(Mob mob) {
        if (mob.getTags().contains(MUMMY_COLLAPSED_TAG)) {
            StardewCraft.LOGGER.warn(
                    "[MUMMY] enter collapse requested but tag already present entityId={} hp={} pos={}",
                    mob.getId(),
                    mob.getHealth(),
                    mob.blockPosition());
            return;
        }

        StardewCraft.LOGGER.info(
                "[MUMMY] entering collapse entityId={} hpBefore={} maxHp={} pose={} noAi={} pos={} tags={}",
                mob.getId(),
                mob.getHealth(),
                mob.getAttribute(Attributes.MAX_HEALTH) != null ? mob.getAttribute(Attributes.MAX_HEALTH).getValue() : -1.0,
                mob.getPose(),
                mob.isNoAi(),
                mob.blockPosition(),
                mob.getTags());
        mob.setHealth(1.0F); // 保留 1 HP 避免被 MC 判定为已死
        mob.addTag(MUMMY_COLLAPSED_TAG);
        mob.setNoAi(true);
        mob.setPose(Pose.STANDING);
        mob.setInvulnerable(false);
        mob.setSilent(true);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setXRot(0.0F);
        mob.xRotO = 0.0F;
        mob.invulnerableTime = 0;
        mob.hurtTime = 0;
        mob.deathTime = 0;
        long reviveTick = mob.level().getGameTime() + MUMMY_COLLAPSE_TICKS;
        mob.getPersistentData().putLong(MUMMY_REVIVE_TICK_TAG, reviveTick);
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        AttributeInstance maxHpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            double currentMaxHp = maxHpAttr.getValue();
            double collapseDiff = 1.0D - currentMaxHp;
            maxHpAttr.removeModifier(MOD_MUMMY_COLLAPSED_HP);
            maxHpAttr.addPermanentModifier(new AttributeModifier(
                    MOD_MUMMY_COLLAPSED_HP,
                    collapseDiff,
                    AttributeModifier.Operation.ADD_VALUE));
            mob.setHealth(1.0F);
        }
        StardewCraft.LOGGER.info(
                "[MUMMY] collapse state applied entityId={} hpAfter={} pose={} noAi={} xRot={} reviveTick={} pos={} tags={}",
                mob.getId(),
                mob.getHealth(),
                mob.getPose(),
                mob.isNoAi(),
                mob.getXRot(),
            reviveTick,
                mob.blockPosition(),
                mob.getTags());
        syncMummyCollapseState(mob, MUMMY_COLLAPSE_TICKS);
        StardewCraft.LOGGER.info(
            "[MUMMY] scheduled revive entityId={} atLevelTick={} currentLevelTick={}",
            mob.getId(),
            reviveTick,
            mob.level().getGameTime());
    }

    private static void clearCollapsedStateForPermanentDeath(Mob mob) {
        mob.removeTag(MUMMY_COLLAPSED_TAG);
        mob.getPersistentData().remove(MUMMY_REVIVE_TICK_TAG);
        mob.setNoAi(false);
        mob.setPose(Pose.STANDING);
        mob.setSilent(false);
        mob.setXRot(0.0F);
        mob.xRotO = 0.0F;
        mob.invulnerableTime = 0;
        mob.hurtTime = 0;
        mob.deathTime = 0;
        AttributeInstance maxHp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.removeModifier(MOD_MUMMY_COLLAPSED_HP);
        }
        syncMummyCollapseState(mob, 0);
        StardewCraft.LOGGER.info(
                "[MUMMY] cleared collapse state for permanent death entityId={} hp={} pose={} noAi={} pos={} tags={}",
                mob.getId(),
                mob.getHealth(),
                mob.getPose(),
                mob.isNoAi(),
                mob.blockPosition(),
                mob.getTags());
    }

    private static void reviveMummy(Mob mob) {
        StardewCraft.LOGGER.info(
            "[MUMMY] revive tick firing entityId={} hpBefore={} pose={} noAi={} levelTick={} pos={}",
            mob.getId(),
            mob.getHealth(),
            mob.getPose(),
            mob.isNoAi(),
            mob.level().getGameTime(),
            mob.blockPosition());

        mob.removeTag(MUMMY_COLLAPSED_TAG);
        mob.getPersistentData().remove(MUMMY_REVIVE_TICK_TAG);
        mob.setNoAi(false);
        mob.setPose(Pose.STANDING);
        mob.setSilent(false);
        mob.setXRot(0.0F);
        mob.xRotO = 0.0F;
        mob.invulnerableTime = 0;
        mob.hurtTime = 0;
        mob.deathTime = 0;
        AttributeInstance maxHp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.removeModifier(MOD_MUMMY_COLLAPSED_HP);
        }
        syncMummyCollapseState(mob, 0);
        if (maxHp != null) {
            mob.setHealth((float) maxHp.getValue());
        }
        StardewCraft.LOGGER.info(
            "[MUMMY] revive completed entityId={} hpAfter={} pose={} noAi={} levelTick={} pos={} tags={}",
            mob.getId(),
            mob.getHealth(),
            mob.getPose(),
            mob.isNoAi(),
            mob.level().getGameTime(),
            mob.blockPosition(),
            mob.getTags());
        }

    private static String describeDamageSource(DamageSource source) {
        if (source == null) {
            return "<null>";
        }
        String direct = source.getDirectEntity() == null ? "null" : source.getDirectEntity().getType().toString();
        String attacker = source.getEntity() == null ? "null" : source.getEntity().getType().toString();
        return source.type().msgId() + "[direct=" + direct + ", attacker=" + attacker + "]";
    }
}
