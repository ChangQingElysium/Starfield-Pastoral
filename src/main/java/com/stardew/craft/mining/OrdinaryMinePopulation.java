package com.stardew.craft.mining;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.*;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Ordinary, non-infestation MineShaft.populateLevel branches, in source tile order. */
public final class OrdinaryMinePopulation {
    private final ServerLevel level;
    private final int floor;
    private final OrdinaryMineLayout layout;
    private final MineFloorData data;
    private final RandomSource random;
    private final Map<BlockPos,BlockState> stones = new LinkedHashMap<>();
    private final Set<BlockPos> occupied = new HashSet<>();
    private final Set<BlockPos> objects = new LinkedHashSet<>();
    private final double luck, mining;
    private final boolean bottom;
    private final MineBuildingTheme theme;
    private final boolean sourceDark;
    private final boolean loadedDark;
    private boolean ghostAdded;
    private int selectedDustConstructor=-1;

    public OrdinaryMinePopulation(ServerLevel level,int floor,OrdinaryMineLayout layout,MineFloorData data) {
        this.level=level; this.floor=floor; this.layout=layout; this.data=data; random=level.random;
        theme=layout.metadata.has("building_theme")?MineBuildingTheme.valueOf(layout.metadata.get("building_theme").getAsString().toUpperCase(Locale.ROOT)):OrdinaryMineRuntime.theme(floor);
        // MineShaft.loadedDarkArea: skull caverns derive it from the loaded map number (%40>=30, or the dark 45 map),
        // not from the floor number; isDarkArea() additionally accepts mineLevel%40>30.
        if(floor>120) {
            int map=layout.metadata.has("source_layout_number")?layout.metadata.get("source_layout_number").getAsInt():-1;
            loadedDark=map%40>=30 || map==45;
            sourceDark=loadedDark || floor%40>30;
        } else {
            sourceDark=!(floor>=40 && floor<80) && (floor%40>30 || theme.id().endsWith("dark"));
            loadedDark=sourceDark;
        }
        var players=level.getServer().getPlayerList().getPlayers();
        luck=players.stream().mapToDouble(PlayerStardewDataAPI::getDailyLuck).average().orElse(0);
        mining=players.stream().mapToInt(p->PlayerStardewDataAPI.getSkillLevel(p,SkillType.MINING)).average().orElse(0);
        bottom=players.stream().anyMatch(p->MiningDataManager.getPlayerData(p).getMaxFloorReached()>=120);
    }
    private boolean clear(int x,int z,boolean stoneOnly) {
        var c=layout.cell(x,z);
        if(c==null || !c.reachable() || !c.solid() || (stoneOnly && !c.candidate())) return false;
        BlockPos p=layout.position(floor,x,z);
        return !occupied.contains(p) && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }
    private BlockPos pos(int x,int z) { return layout.position(floor,x,z); }
    private void stone(int x,int z,Node node) { stone(x,z,node,true); }
    private void stone(int x,int z,Node node,boolean initialPopulation) {
        BlockPos p=pos(x,z); BlockState state=MineStoneMining.stateForSource(node.source(),node.health()).orElseThrow();
        // Presentation uses a coordinate hash, not the gameplay RNG stream.
        state=state.setValue(MineStoneBlock.FACING,net.minecraft.core.Direction.from2DDataValue(Math.floorMod(x*31+z*17,4)));
        if(level.setBlock(p,state,2)) { stones.put(p,state); occupied.add(p); objects.add(p); data.addGeneratedStone(p,initialPopulation); }
    }
    public record Node(String source,int health) {}
    public static boolean dark(int f) { return f < 120 && f%40>=30; }
    public static Node chooseStone(int f,double luck,double mining,boolean bottom,RandomSource r) {
        return chooseStone(f,luck,mining,bottom,r,dark(f));
    }
    private static Node chooseStone(int f,double luck,double mining,boolean bottom,RandomSource r,boolean sourceDark) {
        int which,hp=1;
        if(f>120) {
            hp=5;
            which=r.nextBoolean()?(r.nextBoolean()?32:38):(r.nextBoolean()?40:42);
            int depth=f-120;
            double ore=.02+depth*.0005;
            double boost=0;
            if(f>=130) {
                ore+=.01*((Math.min(100,depth)-10)/10.0);
                boost=Math.min(.004,.001*((depth-10)/10.0));
            }
            if(depth>100)boost+=depth/1000000.0;
            if(r.nextDouble()<ore) {
                if(r.nextDouble()<Math.min(100,depth)*(.0003+boost))return new Node("765",16);
                if(r.nextDouble()<.01+(f-Math.min(150,depth))*.0005)return new Node("764",8);
                if(r.nextDouble()<Math.min(.5,.1+(f-Math.min(200,depth))*.005))return new Node("290",4);
                return new Node("751",2);
            }
        } else if(f<40) {
            which=31+r.nextInt(11);
            if(f%40>=30) which=r.nextBoolean()?34:36;
            else if(which>=33 && which<38) which=r.nextBoolean()?32:38;
            if(f!=1 && f%5!=0 && r.nextDouble()<.029) return new Node("751",3);
        } else if(f<80) {
            which=47+r.nextInt(7); hp=3;
            if(f%5!=0 && r.nextDouble()<.029) return new Node("290",4);
        } else {
            hp=4;
            which=r.nextDouble()<.3 && !sourceDark ? (r.nextBoolean()?38:32)
                    : r.nextDouble()<.3 ? 55+r.nextInt(3) : (r.nextBoolean()?760:762);
            if(f%5!=0 && r.nextDouble()<.029) return new Node("764",8);
        }
        double modifier=luck+mining*.005;
        double gem=f==1 || (f<=120 && f%5==0) ? 0 : .0015;
        if(f>50 && r.nextDouble()<.00025+f/120000.0+.0005*modifier/2) { which=2;hp=10; }
        else if(gem!=0 && r.nextDouble()<gem+gem*modifier+f/24000.0) return new Node(gem(f,bottom,r),5);
        if(r.nextDouble()<.001/2+.001*mining*.008+.001*(luck/2)) which=44;
        if(f>100 && r.nextDouble()<.00005+.00005*mining*.008+.00005*(luck/2)) which=46;
        which+=which%2;
        if(r.nextDouble()<.1 && !(f>=40 && f<80)) {
            String source=r.nextBoolean()?"668":"670"; r.nextBoolean(); return new Node(source,2);
        }
        return new Node(Integer.toString(which),hp);
    }
    private static String gem(int f,boolean bottom,RandomSource r) {
        int g=59+r.nextInt(11);g+=g%2;
        if(!bottom) {
            if(f<40 && g!=66 && g!=68) g=r.nextBoolean()?66:68;
            else if(f<80 && (g==64 || g==60)) g=new int[]{66,70,68,62}[r.nextInt(4)];
        }
        return switch(g) { case 66->"8";case 68->"10";case 60->"12";case 70->"6";case 64->"4";case 62->"14";default->"40"; };
    }
    public void populate() {
        if(floor<=0 || (floor<=120 && floor%10==0) || data.isTreasureRoom()) return;
        double stoneChance=(10+random.nextInt(20))/100.0;
        double monsterChance=.002+random.nextInt(200)/10000.0;
        double itemChance=floor==1 || (floor<=120 && floor%5==0) ? 0 : .0025;
        if(floor==1) monsterChance=0;
        // Monster musk and oil of garlic are source-wide party modifiers.
        boolean musk=level.getServer().getPlayerList().getPlayers().stream().anyMatch(p->p.hasEffect(com.stardew.craft.effect.ModMobEffects.MONSTER_MUSK));
        boolean garlic=level.getServer().getPlayerList().getPlayers().stream().anyMatch(p->p.hasEffect(com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS));
        if(garlic && !musk && floor<=120) monsterChance=0; else if(musk && (!garlic || floor>120)) monsterChance*=2;
        edgeBarrels();
        int platforms=0; var progress=OrdinaryMineProgress.get(level); int limit=progress.platformLimit(floor);
        for(var c:layout.cells) {
            int x=c.x(),z=c.z();
            if(clear(x,z,true)) {
                if(random.nextDouble()<=stoneChance) {
                    if(floor>=40 && floor<80 && random.nextDouble()<.15) {
                        placeDebris(x,z,random.nextInt(3));
                    } else stone(x,z,chooseStone(floor,luck,mining,bottom,random,sourceDark));
                } else if(random.nextDouble()<=monsterChance && layout.distanceFromEntry(x,z)>5) {
                    spawnGroup(x,z);
                } else if(random.nextDouble()<=itemChance) forage(x,z);
                else if(random.nextDouble()<=.005 && !sourceDark && clear(x+1,z,true) && clear(x,z+1,true) && clear(x+1,z+1,true)) {
                    String id=(floor>=40 && floor<80) ? (random.nextBoolean()?"C756":"C758") : (random.nextBoolean()?"C752":"C754");
                    var state=MineRockClumpMining.stateForSource(id).orElseThrow();
                    var block=(MineRockClumpBlock)state.getBlock();
                    if(level.setBlock(pos(x,z),state,2) && block.placeExtensions(level,pos(x,z),state))
                        for(int dx=0;dx<2;dx++) for(int dz=0;dz<2;dz++) occupied.add(pos(x+dx,z+dz));
                }
            } else if(c.back()==257 && clear(x,z,false) && random.nextDouble()<.4 && (limit<0 || platforms<limit)) {
                if(barrel(x,z)) platforms++;
            } else if(random.nextDouble()<=monsterChance && clear(x,z,false) && layout.distanceFromEntry(x,z)>5) {
                String monster=chooseMonster(x,z);spawn(x,z,monster,random.nextDouble()<.01);
            }
        }
        progress.initializePlatforms(floor,platforms);
        clearings();
        areaDebrisClusters();
        if(random.nextDouble()<.95 && floor>1 && floor%5!=0) {
            int x=random.nextInt(layout.width),z=random.nextInt(layout.depth);
            if(clear(x,z,true)) OrdinaryMineRuntime.placeLadder(level,floor,pos(x,z),data);
        }
        // MineShaft L1715: no ore clumps on any multiple-of-5 level, skull caverns included.
        if(floor%5!=0 && floor>2) oreClusters();
        // Source stonesLeft counts the initial stone pass; recursively added ores are tracked
        // for one-time removal but do not inflate the source discovery budget.
        MineFloorDataManager.get(level).setFloorData(floor,data);
    }
    private boolean barrel(int x,int z) {
        if(MineBarrelBlock.placeRandom(level,pos(x,z),level.random,theme)) { occupied.add(pos(x,z));objects.add(pos(x,z));return true; }
        return false;
    }
    private void edgeBarrels() {
        if(floor<=1 || (floor<=120 && floor%5==0) || !random.nextBoolean()) return;
        int count=random.nextInt(5)+(int)(luck*20);
        for(int i=0;i<count;i++) {
            int x,z,dx,dz;
            if(random.nextDouble()<.33) { x=random.nextInt(layout.width);z=0;dx=0;dz=1; }
            else if(random.nextBoolean()) { x=0;z=random.nextInt(layout.depth);dx=1;dz=0; }
            else { x=layout.width-1;z=random.nextInt(layout.depth);dx=-1;dz=0; }
            while(x>=0 && x<layout.width && z>=0 && z<layout.depth) { x+=dx;z+=dz;if(clear(x,z,true)) {barrel(x,z);break;} }
        }
    }
    private void forage(int x,int z) {
        Block block;
        if(random.nextDouble()<.05 && floor>80) block=ModBlocks.FORAGE_PURPLE_MUSHROOM.get();
        else if(random.nextDouble()<.1 && floor>20 && !(floor>=40 && floor<80)) block=ModBlocks.FORAGE_RED_MUSHROOM.get();
        else if(random.nextDouble()<.25) block=floor<40?ModBlocks.EARTH_CRYSTAL.get():floor<80?ModBlocks.FROZEN_TEAR.get():ModBlocks.FIRE_QUARTZ.get();
        else block=ModBlocks.QUARTZ.get();
        level.setBlock(pos(x,z),block.defaultBlockState(),2);occupied.add(pos(x,z));objects.add(pos(x,z));
    }
    private void placeDebris(int x,int z,int variant) {
        // Occupancy of the source's 319..321 ice objects is kept independent of counted stones.
        level.setBlock(pos(x,z),ModBlocks.MINE_ICE_DEBRIS.get().defaultBlockState().setValue(MineIceDebrisBlock.VARIANT,variant),2);occupied.add(pos(x,z));objects.add(pos(x,z));
    }
    private void areaDebrisClusters() {
        // MineShaft.tryToAddAreaUniques: earth 11..29, frost (10%), non-dark lava.
        boolean earth=floor>10 && floor<30, frost=floor>=40 && floor<80, lava=floor>=80 && floor<=120;
        if(!earth && !lava && (!frost || random.nextDouble()>=.1)) return;
        if(sourceDark || data.isMonsterArea()) return;
        Block block=earth?ModBlocks.MINE_EARTH_WEEDS.get():lava?ModBlocks.MINE_LAVA_WEEDS.get():ModBlocks.MINE_ICE_DEBRIS.get();
        int tries=7+random.nextInt(17);
        for(int i=0;i<tries;i++) {
            int x=random.nextInt(layout.width),z=random.nextInt(layout.depth);
            areaDebrisCluster(x,z,1.0,(10+random.nextInt(30))/100.0,block);
        }
    }
    private void areaDebrisCluster(int x,int z,double growth,double decay,Block block) {
        // Utility.recursiveObjectPlacement excludes missing Type and Dirt, not rock floors.
        if(!clear(x,z,false) || layout.cell(x,z).type().isEmpty() || layout.cell(x,z).type().equals("Dirt")) return;
        // The source doubles failChance (.29): 42% of valid visits place a debris object.
        if(random.nextDouble()>=.58) {
            var variant=block instanceof MineIceDebrisBlock?MineIceDebrisBlock.VARIANT:MineGroundWeedsBlock.VARIANT;
            if(level.setBlock(pos(x,z),block.defaultBlockState().setValue(variant,random.nextInt(3)),2)) {
                occupied.add(pos(x,z));objects.add(pos(x,z));
            }
        }
        growth-=decay;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
            if(random.nextDouble()<growth) areaDebrisCluster(x+d[0],z+d[1],growth,decay,block);
    }
    private int bugFacing;
    private String chooseMonster(int x,int z) {
        selectedDustConstructor=-1;
        double distance=layout.distanceFromEntry(x,z);
        if(floor>120) {
            if(loadedDark)return random.nextDouble()<.18 && distance>8?"carbon_ghost":"mummy";
            if(floor%20==0 && distance>10)return "iridium_bat";
            if(floor%16==0){bugFacing=random.nextInt(4);return "bug";}
            if(random.nextDouble()<.33 && distance>10)return "serpent";
            if(random.nextDouble()<.33 && distance>10 && floor>=171)return "iridium_bat";
            if(floor>=126 && distance>10 && random.nextDouble()<.04)return "pepper_rex";
            if(random.nextDouble()<.33){bugFacing=random.nextInt(4);return "bug";}
            if(random.nextDouble()<.25)return "sludge";
            if(floor>=146 && random.nextDouble()<.25)return "iridium_crab";
            return "big_slime";
        }
        if(floor<40) {
            if(random.nextDouble()<.25) {bugFacing=random.nextInt(4);return "bug";}
            if(floor<15) {
                if(layout.cell(x,z).diggable()) return "duggy";
                return random.nextDouble()<.15?"rock_crab":"green_slime";
            }
            if(floor<=30) {
                if(layout.cell(x,z).diggable()) return "duggy";
                if(random.nextDouble()<.15) return "rock_crab";
                if(random.nextDouble()<.05 && distance>10) return "fly";
                return random.nextDouble()<.45?"green_slime":"grub";
            }
            return random.nextDouble()<.1 && distance>10?"bat":"rock_golem";
        }
        if(floor<80) {
            if(floor>=70 && random.nextDouble()<.75) {random.nextDouble();return "skeleton";}
            if(random.nextDouble()<.3) {selectedDustConstructor=random.nextDouble()<.8?1:0;return "dust_sprite";}
            if(random.nextDouble()<.3 && distance>10) return "frost_bat";
            if(!ghostAdded && floor>50 && random.nextDouble()<.3 && distance>10) {ghostAdded=true;return "ghost";}
            return "frost_jelly";
        }
        if(sourceDark && random.nextDouble()<.25) return "lava_bat";
        if(random.nextDouble()<.15) return "sludge";
        if(random.nextDouble()<.15) return "metal_head";
        if(random.nextDouble()<.25) return "shadow_brute";
        if(random.nextDouble()<.25) return "shadow_shaman";
        if(random.nextDouble()<.25) return "lava_crab";
        if(random.nextDouble()<.2 && distance>8 && floor>=90) return "squid_kid";
        return "sludge";
    }
    private void spawnGroup(int x,int z) {
        String monster=chooseMonster(x,z);int dustConstructor=selectedDustConstructor;selectedDustConstructor=-1;
        double chance=monster.equals("grub")?.4:monster.equals("dust_sprite")?.6:0;
        if(chance>0) for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) if(random.nextDouble()<chance && clear(x+d[0],z+d[1],true)) spawn(x+d[0],z+d[1],monster);
        selectedDustConstructor=dustConstructor;spawn(x,z,monster,random.nextDouble()<.00175);
    }
    private void spawn(int x,int z,String monster) { spawn(x,z,monster,false); }
    private void spawn(int x,int z,String monster,boolean special) {
        var mob=MineMonsterSpawnHandler.spawnConfiguredMonster(level,monster,Vec3.atBottomCenterOf(pos(x,z)),monster.equals("bug")?bugFacing*90-180:0,com.stardew.craft.monster.MonsterSpawnContext.mine(level,floor,data),m->{if(m instanceof com.stardew.craft.entity.monster.MineDustSpiritEntity dust && selectedDustConstructor>=0)dust.sourceChargingConstructor(selectedDustConstructor==1);m.setPersistenceRequired();m.addTag(OrdinaryMineRuntime.MOB_TAG);if(special)m.addTag(OrdinaryMineSpecialLoot.TAG);});
        if(mob!=null) {occupied.add(pos(x,z));data.addGeneratedMonster(mob.getUUID());}
    }
    private void clearings() {
        int tries=data.getStonesLeft()/35;
        for(int i=0;i<tries && !stones.isEmpty();i++) {
            BlockPos center=new ArrayList<>(objects).get(random.nextInt(objects.size()));
            if(!stones.containsKey(center))continue;
            int radius=3+random.nextInt(5);boolean monsters=random.nextDouble()<.1;
            for(int x=center.getX()-radius/2;x<center.getX()+radius/2;x++) for(int z=center.getZ()-radius/2;z<center.getZ()+radius/2;z++) {
                BlockPos p=new BlockPos(x,center.getY(),z); if(stones.remove(p)==null) continue;
                level.removeBlock(p,false);occupied.remove(p);objects.remove(p);data.removeGeneratedStone(p);
                int tx=x-layout.origin(floor).getX()-layout.tileX,tz=z-layout.origin(floor).getZ()-layout.tileZ;
                if(layout.distanceFromEntry(tx,tz)>5 && monsters && random.nextDouble()<.12) spawn(tx,tz,chooseMonster(tx,tz));
            }
        }
    }
    private void oreClusters() {
        if(random.nextDouble()>=.55+luck) return;
        int x=random.nextInt(layout.width),z=random.nextInt(layout.depth);
        for(int tries=0;tries<1 || random.nextDouble()<.25+luck;tries++) {
            if(clear(x,z,false) && !layout.cell(x,z).diggable()) {
                Node ore=floor>120?(random.nextDouble()<.02?new Node("765",16):new Node("764",8)):floor<40?new Node("751",3):floor<80?(random.nextDouble()<.8?new Node("290",4):new Node("751",3)):(random.nextDouble()<.8?new Node("764",8):new Node("751",3));
                if(random.nextDouble()<.25 && !(floor>=40 && floor<80)) {
                    random.nextBoolean();ore=new Node("668",2);
                }
                cluster(x,z,ore,.95);
            }
            x=random.nextInt(layout.width);z=random.nextInt(layout.depth);
        }
    }
    private void cluster(int x,int z,Node ore,double growth) {
        if(!clear(x,z,false) || layout.cell(x,z).type().isEmpty() || layout.cell(x,z).diggable()) return;
        if(random.nextDouble()>=.1) {
            Node placed=ore.source().equals("668")?new Node(random.nextInt(2)==0?"668":"670",ore.health()):ore;
            if(!ore.source().equals("668"))random.nextInt(1);
            stone(x,z,placed,false);
        }
        growth-=.3;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if(random.nextDouble()<growth) cluster(x+d[0],z+d[1],ore,growth);
    }
}
