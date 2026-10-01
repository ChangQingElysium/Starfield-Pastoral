package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.GreenSlimeEntity;
import com.stardew.craft.entity.monster.MineBugEntity;
import com.stardew.craft.mining.*;
import com.stardew.craft.monster.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.*;
import java.util.function.Consumer;

/** Native monster insertion only. Unfinished species have no substitute entity. */
@EventBusSubscriber(modid=StardewCraft.MODID)
@SuppressWarnings("null")
public final class MineMonsterSpawnHandler {
    private static final List<String> IDS=List.of("green_slime","frost_jelly","sludge","bat","frost_bat","lava_bat","iridium_bat","rock_crab","lava_crab","iridium_crab","bug","armored_bug","mummy","pepper_rex","serpent","big_slime","grub","fly","duggy","dust_sprite","ghost","carbon_ghost","skeleton","rock_golem","wilderness_golem","iridium_golem","metal_head","shadow_brute","shadow_shaman","squid_kid");
    private static final Set<String> prismaticSlimeFloors=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean profilesRegistered;
    private MineMonsterSpawnHandler() {}
    public static List<String> getSummonableMonsterIds() { return IDS; }
    public static boolean isImplemented(String id) { return IDS.contains(id); }
    public static int inferFloor(ServerPlayer player) {
        return player!=null&&player.level().dimension()==ModMiningDimensions.STARDEW_MINING?Math.max(1,OrdinaryMineRuntime.floorAt(player.blockPosition())):1;
    }
    public static synchronized void ensureProfilesRegistered() {
        if(profilesRegistered)return;
        for(String id:IDS)StardewMineMonsterProfiles.register(new ResourceLocation(StardewCraft.MODID,id),type(id),
                MineMonsterNames.translationKey(id),progressTags(id),(mob,context)->{
                    var nativeMob=(StardewMonsterEntity)mob;
                    nativeMob.initialize(MonsterSpawnContext.capture(context.level(),MonsterSpawnContext.Source.WORLD,context.floor()));
                });
        profilesRegistered=true;
    }
    private static Set<String> progressTags(String id) {
        return switch(id) {
            case "pepper_rex"->Set.of("sd_mob_dino");case "serpent"->Set.of("sd_mob_serpent");case "mummy"->Set.of("sd_mob_mummy");case "big_slime"->Set.of("sd_mob_big_slime");
            case "green_slime"->Set.of("sd_mob_slime");
            case "frost_jelly"->Set.of("sd_mob_slime","sd_tier_2");
            case "sludge"->Set.of("sd_mob_slime","sd_tier_3");
            case "bat"->Set.of("sd_mob_bat");
            case "frost_bat"->Set.of("sd_mob_bat","sd_tier_2");
            case "lava_bat"->Set.of("sd_mob_bat","sd_tier_3");
            case "iridium_bat"->Set.of("sd_mob_bat","sd_tier_4");
            case "rock_crab","lava_crab","iridium_crab"->Set.of("sd_mob_crab");
            case "bug"->Set.of("sd_mob_bug");case "armored_bug"->Set.of("sd_mob_bug","sd_mob_armored_bug");
            case "squid_kid"->Set.of("sd_mob_squid_kid");case "shadow_shaman"->Set.of("sd_mob_shadow_shaman");case "shadow_brute"->Set.of("sd_mob_shadow_brute");case "metal_head"->Set.of("sd_mob_metal_head");case "rock_golem"->Set.of("sd_mob_rock_golem");case "wilderness_golem"->Set.of("sd_mob_wilderness_golem");case "iridium_golem"->Set.of("sd_mob_iridium_golem");case "skeleton"->Set.of("sd_mob_skeleton");case "ghost"->Set.of("sd_mob_ghost");case "carbon_ghost"->Set.of("sd_mob_ghost","sd_mob_carbon_ghost");case "dust_sprite"->Set.of("sd_mob_dust_sprite");case "duggy"->Set.of("sd_mob_duggy");case "grub"->Set.of("sd_mob_grub");case "fly"->Set.of("sd_mob_fly");
            default->Set.of();
        };
    }
    private static EntityType<? extends StardewMonsterEntity> type(String id) {
        return switch(id) {
            case "pepper_rex"->ModEntities.PEPPER_REX.get();case "serpent"->ModEntities.SERPENT.get();case "mummy"->ModEntities.MUMMY.get();case "big_slime"->ModEntities.BIG_SLIME.get();
            case "green_slime"->ModEntities.GREEN_SLIME.get();case "frost_jelly"->ModEntities.FROST_JELLY.get();case "sludge"->ModEntities.SLUDGE.get();
            case "bat"->ModEntities.BAT.get();case "frost_bat"->ModEntities.FROST_BAT.get();case "lava_bat"->ModEntities.LAVA_BAT.get();case "iridium_bat"->ModEntities.IRIDIUM_BAT.get();
            case "rock_crab"->ModEntities.ROCK_CRAB.get();case "lava_crab"->ModEntities.LAVA_CRAB.get();case "iridium_crab"->ModEntities.IRIDIUM_CRAB.get();case "bug"->ModEntities.BUG.get();case "armored_bug"->ModEntities.ARMORED_BUG.get();
            case "grub"->ModEntities.GRUB.get();case "fly"->ModEntities.FLY.get();case "duggy"->ModEntities.DUGGY.get();case "dust_sprite"->ModEntities.DUST_SPIRIT.get();case "squid_kid"->ModEntities.SQUID_KID.get();case "shadow_shaman"->ModEntities.SHADOW_SHAMAN.get();case "shadow_brute"->ModEntities.SHADOW_BRUTE.get();case "metal_head"->ModEntities.METAL_HEAD.get();case "rock_golem"->ModEntities.ROCK_GOLEM.get();case "wilderness_golem"->ModEntities.WILDERNESS_GOLEM.get();case "iridium_golem"->ModEntities.IRIDIUM_GOLEM.get();case "skeleton"->ModEntities.SKELETON.get();case "ghost"->ModEntities.GHOST.get();case "carbon_ghost"->ModEntities.CARBON_GHOST.get();default->null;
        };
    }
    public static Mob spawnConfiguredMonster(ServerLevel level,String id,Vec3 position,float yaw,int floor) {
        if(level==null)return null;
        return spawnConfiguredMonster(level,id,position,yaw,MonsterSpawnContext.capture(level,MonsterSpawnContext.Source.COMMAND,floor),m->{});
    }
    public static Mob spawnConfiguredMonster(ServerLevel level,String id,Vec3 position,float yaw,int floor,Consumer<Mob> configure) {
        if(level==null)return null;
        return spawnConfiguredMonster(level,id,position,yaw,MonsterSpawnContext.capture(level,MonsterSpawnContext.Source.WORLD,floor),configure);
    }
    public static Mob spawnConfiguredMonster(ServerLevel level,String id,Vec3 position,float yaw,MonsterSpawnContext context,Consumer<Mob> configure) {
        return spawnConfiguredMonster(level,id,position,yaw,context,configure,m->{});
    }
    public static Mob spawnConfiguredMonster(ServerLevel level,String id,Vec3 position,float yaw,MonsterSpawnContext context,Consumer<Mob> configure,Consumer<Mob> beforeInitialize) {
        if(level==null||id==null||position==null||context==null)return null;
        id=switch(id.toLowerCase(Locale.ROOT)){case "slime"->"green_slime";case "crab"->"rock_crab";default->id.toLowerCase(Locale.ROOT);};
        // Original area 121 selects Armored Bug in the Bug constructor.
        if(id.equals("bug")&&(context.source()==MonsterSpawnContext.Source.SKULL_CAVERN||context.generation()!=null&&context.floor()>120))id="armored_bug";
        var type=type(id);if(type==null)return null;
        var mob=type.create(level);if(mob==null)return null;
        mob.moveTo(position.x,position.y,position.z,yaw,0);
        if(context.generation()==null) {
            int floor=switch(id){
                case "green_slime","bat"->Math.clamp(context.floor(),1,39);
                case "frost_jelly","frost_bat"->Math.clamp(context.floor(),40,79);
                case "lava_bat"->Math.clamp(context.floor(),80,170);
                case "iridium_bat"->Math.max(171,context.floor());case "sludge"->Math.max(80,context.floor());default->context.floor();};
            context=new MonsterSpawnContext(context.source(),floor,context.bottomReached(),null);
        }
        if(beforeInitialize!=null)beforeInitialize.accept(mob);
        mob.initialize(context);
        if(mob instanceof GreenSlimeEntity)tryMakePrismaticSlime(mob,context.floor());
        if(configure!=null)configure.accept(mob);
        // Bugs resolve their 1.3-block patrol hull after the complete floor population is
        // installed; every other physical species must be clear before insertion.
        if(!(mob instanceof MineBugEntity)&&!MonsterSpawnPlacement.ensureClear(mob))return null;
        mob.setCustomName(null);
        return MonsterFactory.add(level,mob)?mob:null;
    }
    /** No drops, XP or kill/ladder rolls when removing retired Minecraft stand-ins. */
    public static boolean isRetiredMineMob(Mob mob) {
        return !(mob instanceof StardewMonsterEntity)
                && BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft")
                && (mob.getType().getCategory()==MobCategory.MONSTER||mob.getTags().stream().anyMatch(t->t.startsWith("sd_mob_")));
    }
    private static void removePopulation(ServerLevel level,Mob mob) {
        int floor=OrdinaryMineRuntime.floorAt(mob.blockPosition());var manager=MineFloorDataManager.get(level);var data=manager.getFloorData(floor);
        if(data!=null&&data.removeGeneratedMonster(mob.getUUID()))manager.setFloorData(floor,data);
    }
    @SubscribeEvent public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=ModMiningDimensions.STARDEW_MINING||!(event.getEntity() instanceof Mob mob))return;
        if(isRetiredMineMob(mob)){removePopulation(level,mob);event.setCanceled(true);return;}
        if(mob instanceof StardewMonsterEntity)mob.setCustomName(null);
    }
    @SubscribeEvent public static void cleanupLoaded(EntityTickEvent.Post event) {
        if(event.getEntity() instanceof Mob mob&&mob.level() instanceof ServerLevel level&&level.dimension()==ModMiningDimensions.STARDEW_MINING&&isRetiredMineMob(mob)) {
            removePopulation(level,mob);mob.discard();
        }
    }

    private static void tryMakePrismaticSlime(Mob mob, int floor) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String key = serverLevel.dimension().location() + ":" + com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay() + ":" + floor;
        if (prismaticSlimeFloors.contains(key)) {
            return;
        }
        if (!com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(serverLevel, "Wizard2")) {
            return;
        }
        double chance = prismaticSlimeChance(averageDailyLuckForMineArea(serverLevel, floor));
        if (mob.getRandom().nextDouble() > chance) {
            return;
        }
        prismaticSlimeFloors.add(key);
        mob.addTag("sd_mob_prismatic_slime");
        var slime=(StardewMonsterEntity)mob;
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        var stats=slime.monsterState().stats();
        slime.replaceCombatStats(com.stardew.craft.combat.MonsterStats.builder().damage(35)
                .resilience(stats.getResilience()).missChance(stats.getMissChance()).experience(stats.getExperience()).build());
    }

    static double prismaticSlimeChance(double averageDailyLuck) {
        return Math.max(0.01D, 0.012D + averageDailyLuck / 10.0D);
    }

    private static double averageDailyLuckForMineArea(ServerLevel level, int floor) {
        boolean skullCavern = floor > 120;
        double total = 0.0D;
        int count = 0;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!player.serverLevel().dimension().equals(ModMiningDimensions.STARDEW_MINING)) {
                continue;
            }
            if ((inferFloor(player) > 120) != skullCavern) {
                continue;
            }
            total += com.stardew.craft.player.PlayerStardewDataAPI.getDailyLuck(player);
            count++;
        }
        return count == 0 ? 0.0D : total / count;
    }

}
