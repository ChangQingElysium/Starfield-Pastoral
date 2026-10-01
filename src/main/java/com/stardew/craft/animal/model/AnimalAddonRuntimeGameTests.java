package com.stardew.craft.animal.model;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.port.PortItemData;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.api.v1.agriculture.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.farm.*;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.*;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runtime contracts exercised with addon identities, public callbacks and real ledger services. */
@GameTestHolder("stardewcraft_animal_addons")
@PrefixGameTestTemplate(false)
public final class AnimalAddonRuntimeGameTests {
    private static ResourceLocation unique(String path){return new ResourceLocation("addon_contract",path+"_"+UUID.randomUUID().toString().replace("-",""));}
    private static JsonObject animal(String id){
        try(var in=AnimalAddonRuntimeGameTests.class.getResourceAsStream("/data/stardewcraft/stardewcraft/farm_animals/vanilla_1_6_15.json")){
            var json=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("animals").get(0).getAsJsonObject().deepCopy();
            json.addProperty("animal_type_id",id);json.addProperty("entity_type","stardewcraft:duck");
            json.addProperty("purchase_price",123);json.addProperty("days_to_mature",7);
            json.addProperty("required_building_tier",1);json.addProperty("shop_order",1);
            json.add("alternate_purchase_types",new JsonArray());json.add("deluxe_produce",new JsonArray());
            json.add("egg_items",JsonParser.parseString("[\"minecraft:amethyst_shard\"]"));
            json.addProperty("incubation_time",1600);
            json.add("produce_stats",JsonParser.parseString("[{\"id\":\"Gift\",\"stat_name\":\"addon_contract:gifts\",\"required_items\":[\"minecraft:diamond\"]}]"));
            json.add("produce",JsonParser.parseString("[{\"id\":\"Default\",\"item\":\"minecraft:diamond\",\"minimum_friendship\":0}]"));
            return json;
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void install(JsonObject animal){
        var root=new JsonObject();var array=new JsonArray();array.add(animal);root.add("animals",array);
        var decoded=FarmAnimalDefinitions.decodeForTests(Map.of(unique("animals"),root));
        AnimalDefinitionSnapshot.validateCrossReferences(decoded,AnimalBuildingTierDefinitions.currentSnapshot());
        AnimalDefinitionSnapshot.publish(decoded,AnimalBuildingTierDefinitions.currentSnapshot());
    }
    private static BuildingRecord home(GameTestHelper h,FarmInstance farm){
        var level=h.getLevel();var manager=h.absolutePos(new BlockPos(8,1,8));var bounds=PrefabDefinitions.get(PrefabDefinitions.COOP).selfBounds(manager);
        LivestockHomes.load(level,bounds);
        for(int x=bounds.min().getX();x<bounds.maxExclusive().getX();x++)for(int z=bounds.min().getZ();z<bounds.maxExclusive().getZ();z++){
            level.setBlock(new BlockPos(x,manager.getY()-1,z),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(new BlockPos(x,manager.getY()+7,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
        }
        level.setBlock(manager,ModBlocks.COOP_MANAGER.get().defaultBlockState(),3);
        for(int i=1;i<=4;i++)level.setBlock(manager.offset(i,0,0),ModBlocks.FEED_TROUGH.get().defaultBlockState(),3);
        level.setBlock(manager.offset(0,0,2),ModBlocks.HAY_HOPPER.get().defaultBlockState(),3);
        var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),PrefabDefinitions.COOP,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),manager,manager,Direction.SOUTH,bounds);
        var buildings=BuildingWorldData.get(level.getServer());h.assertTrue(buildings.register(record)==BuildingWorldData.Result.SUCCESS,"Fixture overlaps");
        h.assertTrue(buildings.acceptSelf(record.id(),record.revision(),BuildingResidence.scan(level,bounds,record.family()).eligibleTier())==BuildingWorldData.Result.SUCCESS,"Fixture home not ready");
        return buildings.find(record.id());
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void addonPurchaseUsesDefinitionProjectionAndServerEligibility(GameTestHelper h){
        var previous=FarmAnimalDefinitions.currentSnapshot();var tiers=AnimalBuildingTierDefinitions.currentSnapshot();
        var level=h.getLevel();var farms=FarmInstanceRegistry.get(level.getServer());var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"AddonBuyer","Addon",FarmType.STANDARD);var id=unique("duck").toString();
        try{
            install(animal(id));var species=LivestockSpecies.parse(id);var home=home(h,farm);
            var player=FakePlayerFactory.get(level,new GameProfile(owner,"AddonBuyer"));PlayerStardewDataAPI.setMoney(player,1000);
            var limit=new AtomicInteger(1);
            StardewAgricultureDataApi.registerBuildingProvider(unique("beds"),1000,(world,pos,state)->pos.equals(home.manager())?new StardewBuildingData(home.family(),limit.get(),List.of(new ResourceLocation("stardewcraft:duck")),List.of()):null);
            h.assertTrue(species.matureDays()==7&&species.price()==246,"JSON values did not reach runtime identity");
            var row=new CompoundTag();LivestockHomes.describe(row,level,home);var type=new CompoundTag();LivestockUiData.describe(type,species);
            h.assertTrue(LivestockHomes.offered(row,type),"Client eligibility omits addon type");
            var nonce=LivestockShop.openForPlayer(player);
            h.assertTrue(LivestockShop.purchase(player,nonce,home.id(),home.revision(),"Juniper",id).isEmpty(),"Addon shop purchase rejected");
            h.assertTrue(PlayerStardewDataAPI.getMoney(player)==754,"Wrong data-driven price");
            var projection=level.getEntity(nonce);
            h.assertTrue(projection instanceof BaseCoopAnimalEntity&&projection.getType()==ModEntities.DUCK.get(),"Addon type was replaced by chicken projection");
            var again=LivestockShop.openForPlayer(player);
            h.assertTrue(LivestockShop.purchase(player,again,home.id(),home.revision(),"Full",id).equals("full"),"Building provider capacity ignored");
            h.assertTrue(IncubatorBlockEntity.resolveAnimalTypeId(new ItemStack(Items.AMETHYST_SHARD)).equals(id),"JSON egg mapping ignored");
            var saved=LivestockWorldData.get(level.getServer()).find(nonce).save();
            AnimalDefinitionSnapshot.publish(previous,tiers);
            var missing=LivestockRecord.load(saved);h.assertTrue(missing.species().id().equals(id)&&!missing.species().known(),"Missing addon identity was lost");
            install(animal(id));h.assertTrue(missing.species().known(),"Restored definition did not reactivate identity");
            limit.set(0);h.assertTrue(LivestockHomes.capacity(level,home)==0,"Live provider update ignored");
        }finally{AnimalDefinitionSnapshot.publish(previous,tiers);farms.deleteFarm(owner);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void handlerStateAndComponentProductsCommitTogether(GameTestHelper h){
        var previous=FarmAnimalDefinitions.currentSnapshot();var tiers=AnimalBuildingTierDefinitions.currentSnapshot();var level=h.getLevel();
        var farms=FarmInstanceRegistry.get(level.getServer());var owner=UUID.randomUUID();var farm=farms.createFarm(owner,"Handler","Handler",FarmType.STANDARD);
        try{
            var id=unique("handler").toString();install(animal(id));var home=home(h,farm);var data=LivestockWorldData.get(level.getServer());
            var record=new LivestockRecord(UUID.randomUUID(),owner,farm.getInstanceId(),home.id(),"Pearl",data.allocateRandomId(),1,LivestockCare.purchased()).species(LivestockSpecies.parse(id));data.put(record);
            var key=StardewAnimalPersistentData.register(unique("state"),1);var calls=new AtomicInteger();
            var named=new ItemStack(Items.DIAMOND,2);PortItemData.set(named, DataComponents.CUSTOM_NAME,Component.literal("Pearl's gift"));
            StardewAnimalDailyHandlers.register(unique("daily"),id,100,context->{
                calls.incrementAndGet();var payload=new CompoundTag();payload.putInt("visits",1);
                h.assertTrue(context.entityId().equals(record.id()),"Wrong entity UUID in public context");
                h.assertTrue(StardewAnimalPersistentData.write(level,context.animalId(),key,payload),"Numeric handle did not reach staged record");
                h.assertTrue(context.persistentData(key).isPresent(),"Facade write and context read diverged");
                context.setHeldProduce(named);context.placeOvernightProduce(named,false);return StardewAnimalDailyHandlers.Result.SKIP_DEFAULT_PRODUCTION;
            });
            var products=new ArrayList<LivestockWorldData.Product>();
            var settled=LivestockDayState.settle(level,home,record,2,true,false,false,false,StardewDeterministicRandom.create(1,2,3),products,0);
            h.assertTrue(StardewAnimalPersistentData.read(level,record.id(),key).isEmpty(),"Callback changed ledger before transaction commit");
            h.assertTrue(ItemStack.isSameItemSameTags(LivestockProducts.held(level,settled),named),"Held produce lost components");
            var stagedAnimals=new ArrayList<>(List.of(settled));var statPlan=LivestockStats.plan(stagedAnimals);
            data.prepare(new LivestockWorldData.Batch(home.id(),stagedAnimals,products,List.of(),List.of(),farm.getInstanceId(),0,2,Map.of(),statPlan));
            LivestockService.recover(level.getServer());LivestockService.recover(level.getServer());
            h.assertTrue(com.stardew.craft.player.PlayerDataManager.getPlayerData(owner).getStat("addon_contract:gifts")==2,"Recovery duplicated or lost filtered production stats");
            h.assertTrue(calls.get()==1&&StardewAnimalPersistentData.read(level,record.id(),key).isPresent(),"Recovery reran handler or lost its state");
            var product=data.eggs().stream().filter(p->p.animal().equals(record.id())).findFirst().orElseThrow();
            var restored=LivestockWorldData.Product.load(product.save());var stack=LivestockProductEntity.stack(restored,level);
            h.assertTrue(stack.getCount()==2&&ItemStack.isSameItemSameTags(stack,named),"Product persistence lost stack metadata/count");
            var moved=LivestockRecord.load(data.find(record.id()).rename("Moved").rehome(UUID.randomUUID()).save());
            h.assertTrue(StardewAnimalPersistentData.read(moved,key).isPresent(),"Rename/rehome lost addon state");
        }finally{AnimalDefinitionSnapshot.publish(previous,tiers);farms.deleteFarm(owner);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void facilityProvidersRespectInventoryRulesAndReplay(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,1,6));level.setBlock(pos,Blocks.BARREL.defaultBlockState(),3);
        var state=new CompoundTag();state.putInt("hay",3);var held=new ArrayList<ItemStack>(List.of(ItemStack.EMPTY,ItemStack.EMPTY));
        StardewAnimalFacilities.register(unique("facility"),1000,(world,at)->world==level&&at.equals(pos)?new StardewAnimalFacilities.Access(){
            public int units(StardewAnimalFacilities.Role role){return role==StardewAnimalFacilities.Role.AUTOMATIC_TROUGH?4:role==StardewAnimalFacilities.Role.COLLECTOR?1:0;}
            public int hay(){return state.getInt("hay");}
            public CompoundTag snapshot(){return state.copy();}
            public CompoundTag withHay(CompoundTag input,int amount){input=input.copy();input.putInt("hay",amount);return input;}
            public List<ItemStack> inventory(){return held.stream().map(ItemStack::copy).toList();}
            public int slotLimit(int slot,ItemStack stack){return 1;}
            public boolean canInsert(int slot,ItemStack stack){return slot==1;}
            public CompoundTag withInventory(CompoundTag input,List<ItemStack> items){input=input.copy();if(!items.get(1).isEmpty())input.put("item",PortItemStacks.save(items.get(1), level.registryAccess()));return input;}
            public void apply(CompoundTag plan){state.merge(plan);held.set(1,PortItemStacks.parseOptional(level.registryAccess(),state.getCompound("item")));}
        }:null);
        var facility=StardewAnimalFacilities.resolve(level,pos);var plan=facility.plan(facility.access().withHay(facility.access().snapshot(),1));
        h.assertTrue(state.getInt("hay")==3,"Facility planning mutated world");StardewAnimalFacilities.apply(level,pos,plan);StardewAnimalFacilities.apply(level,pos,plan);
        h.assertTrue(state.getInt("hay")==1,"Facility replay deducted twice");
        var home=new BuildingRecord(UUID.randomUUID(),UUID.randomUUID(),0,PrefabDefinitions.COOP,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),pos,pos,Direction.SOUTH,new BuildingBounds(pos,pos.offset(1,2,1)),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,0,"");
        var animal=new LivestockRecord(UUID.randomUUID(),UUID.randomUUID(),home.farmId(),home.id(),"Gift",2,1,LivestockCare.purchased());
        var eggs=new ArrayList<LivestockWorldData.Product>();eggs.add(LivestockWorldData.Product.fromStack(level,animal,new ItemStack(Items.DIAMOND,2)));
        h.assertTrue(LivestockCollectors.collect(level,home,new ArrayList<>(),eggs).isEmpty()&&eggs.size()==1,"Collector bypassed insertion/slot limit");
        eggs.clear();eggs.add(LivestockWorldData.Product.fromStack(level,animal,new ItemStack(Items.DIAMOND)));
        var plans=LivestockCollectors.collect(level,home,new ArrayList<>(),eggs);h.assertTrue(eggs.isEmpty()&&held.get(1).isEmpty(),"Collection was not staged");
        StardewAnimalFacilities.apply(level,pos,plans.get(pos));h.assertTrue(held.get(1).is(Items.DIAMOND),"Collector plan not applied");
        StardewAnimalFacilities.registerUpgrade(unique("upgrade"),new ResourceLocation("minecraft:barrel"),new ResourceLocation("minecraft:chest"),tag->{tag.putBoolean("converted",true);return tag;});
        var converted=StardewAnimalFacilities.upgrade(Blocks.BARREL,Blocks.CHEST,state);
        h.assertTrue(converted.getBoolean("converted")&&!state.contains("converted"),"Upgrade converter was not isolated");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void definitionReloadAndLoadedAnimalProviderChangeActualProduce(GameTestHelper h){
        var previous=FarmAnimalDefinitions.currentSnapshot();var tiers=AnimalBuildingTierDefinitions.currentSnapshot();var level=h.getLevel();
        var farms=FarmInstanceRegistry.get(level.getServer());var owner=UUID.randomUUID();var farm=farms.createFarm(owner,"Data","Data",FarmType.STANDARD);
        try{
            var definition=animal("white_chicken");install(definition);var home=home(h,farm);
            var animal=new LivestockRecord(UUID.randomUUID(),owner,farm.getInstanceId(),home.id(),"Data",2,1,new LivestockCare(20,20,1000,255,255,99,0,true)).species(LivestockSpecies.WHITE_CHICKEN);
            var products=new ArrayList<LivestockWorldData.Product>();
            LivestockDayState.settle(level,home,animal,2,true,false,false,false,StardewDeterministicRandom.create(1,1,1),products,0);
            h.assertTrue(products.size()==1&&LivestockProductEntity.stack(com.stardew.craft.port.PortJava.getFirst(products),level).is(Items.DIAMOND),"Definition produce override ignored");
            var entity=LivestockProjection.create(level,animal);entity.setUUID(animal.id());entity.moveTo(home.manager().getX(),home.manager().getY(),home.manager().getZ());level.addFreshEntity(entity);
            StardewAgricultureDataApi.registerAnimalProvider(unique("produce"),1000,e->e.getUUID().equals(animal.id())?new StardewAnimalData(home.family(),0,0,new ResourceLocation("minecraft:emerald"),1):null);
            products.clear();LivestockDayState.settle(level,home,animal,3,true,false,false,false,StardewDeterministicRandom.create(1,1,1),products,0);
            h.assertTrue(products.size()==1&&LivestockProductEntity.stack(com.stardew.craft.port.PortJava.getFirst(products),level).is(Items.EMERALD),"Loaded entity data provider ignored");
            definition.addProperty("purchase_price",222);definition.addProperty("days_to_mature",9);install(definition);
            h.assertTrue(animal.species().price()==444&&animal.species().matureDays()==9,"Record cached obsolete definition values");entity.discard();
        }finally{AnimalDefinitionSnapshot.publish(previous,tiers);farms.deleteFarm(owner);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void javaTypesIncubationQueryAndReproductionContractsRemainCallable(GameTestHelper h){
        String id=unique("legacy").toString();
        StardewAnimalTypes.register(unique("type"),id,"coop",5,ModEntities.DUCK::get);
        StardewAnimalShopEntries.register(new StardewAnimalShopEntry(unique("shop"),id,"coop",1,77,"Goose","entity.example.goose","example.description","example.lock",20));
        StardewAnimalIncubation.register(unique("egg"),100,stack->stack.is(Items.ENDER_PEARL)?id:null);
        StardewAnimalQueryDefinitions.register(new StardewAnimalQueryDefinition(unique("query"),id,100,125,true));
        var species=LivestockSpecies.parse(id);
        h.assertTrue(species.known()&&!species.hasDefaultBehavior()&&species.price()==77&&species.sellPrice(1000)==125&&species.pregnancy(),"Java type/shop/query contract ignored");
        h.assertTrue(IncubatorBlockEntity.resolveAnimalTypeId(new ItemStack(Items.ENDER_PEARL)).equals(id),"Public incubation resolver disconnected");
        var row=new CompoundTag();LivestockUiData.describe(row,species);h.assertTrue(row.getString("NameKey").equals("entity.example.goose"),"Java listing lost display metadata");
        var home=new BuildingRecord(UUID.randomUUID(),UUID.randomUUID(),0,PrefabDefinitions.COOP,BuildingRecord.Mode.SELF_BUILT,h.getLevel().dimension().location(),BlockPos.ZERO,BlockPos.ZERO,Direction.SOUTH,new BuildingBounds(BlockPos.ZERO,new BlockPos(3,4,3)),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,0,"");
        var animal=new LivestockRecord(UUID.randomUUID(),UUID.randomUUID(),home.farmId(),home.id(),"Legacy",2,1,new LivestockCare(20,20,1000,255,255,99,0,true)).species(species);
        var calls=new AtomicInteger();StardewAnimalReproductionRules.register(unique("deny"),id,100,context->{calls.incrementAndGet();h.assertTrue(context.buildingFamily().equals("coop"),"Builtin family contract changed");return StardewAnimalReproductionRules.Decision.DENY;});
        h.assertTrue(!StardewAnimalReproductionRules.allows(new StardewAnimalReproductionContext(h.getLevel(),home,animal,2))&&calls.get()==1,"New reproduction context bypasses veto");
        var unknown=animal.species(LivestockSpecies.parse(unique("absent").toString()));var extra=new CompoundTag();extra.putString("opaque","retained");unknown=unknown.extra(extra);
        var saved=LivestockRecord.load(unknown.save());h.assertTrue(saved.species()==unknown.species()&&saved.extra().equals(extra),"Uninstalled addon state lost");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void registeredFamilySupportsOneAndFourLevelsWithoutChickenFallback(GameTestHelper h) throws Exception {
        var family=unique("aviary");var builtin=PrefabDefinitions.get(PrefabDefinitions.COOP);var level=h.getLevel();
        var bindings=new com.stardew.craft.api.v1.building.StardewBuildingFamilies.Binding(ModBlocks.COOP_MANAGER,com.stardew.craft.item.ModItems.COOP_BLUEPRINT,Map.of(),Map.of(1,new com.stardew.craft.api.v1.building.StardewBuildingFamilies.Display("example.aviary",new ResourceLocation("example:aviary.png"),64,48)));
        com.stardew.craft.api.v1.building.StardewBuildingFamilies.register(family,bindings);
        h.assertTrue(PrefabDefinitions.supported(family)&&com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(family).orElseThrow().displays().get(1).width()==64,"Family/display registration disconnected");
        boolean rejected=false;try{PrefabDefinitions.blueprintItem(unique("unknown"));}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Unknown family silently became a chicken blueprint");
        var field=PrefabDefinitions.class.getDeclaredField("families");field.setAccessible(true);
        @SuppressWarnings("unchecked") var before=(Map<ResourceLocation,PrefabDefinitions.Family>)field.get(null);
        var definitions=new LinkedHashMap<>(before);var tiers=new ArrayList<PrefabDefinitions.Tier>();
        for(int n=1;n<=4;n++){var t=builtin.tier(1);tiers.add(new PrefabDefinitions.Tier(n,t.structure(),t.size(),t.anchor(),t.manager(),t.animalSpawn(),t.bounds(),new PrefabDefinitions.Facilities(0,0,0,0),t.upgrade()));}
        var configured=new PrefabDefinitions.Family(family,builtin.reservation(),List.copyOf(tiers),1,4,500);definitions.put(family,configured);
        var data=BuildingWorldData.get(level.getServer());UUID id=null;
        try{
            field.set(null,Map.copyOf(definitions));h.assertTrue(PrefabDefinitions.maxTier(family)==4,"Family capped at three levels");
            var pos=h.absolutePos(new BlockPos(5,1,5));var bounds=configured.selfBounds(pos);
            for(int x=bounds.min().getX();x<bounds.maxExclusive().getX();x++)for(int z=bounds.min().getZ();z<bounds.maxExclusive().getZ();z++)level.setBlock(new BlockPos(x,pos.getY()+3,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            var home=BuildingRecord.waiting(UUID.randomUUID(),0,family,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),pos,pos,Direction.SOUTH,bounds);id=home.id();
            h.assertTrue(data.register(home)==BuildingWorldData.Result.SUCCESS,"Custom family registration failed");
            int eligible=BuildingResidence.scan(level,bounds,family).eligibleTier();h.assertTrue(eligible==4,"Custom tier requirements ignored");
            for(int n=1;n<=4;n++){var current=data.find(id);h.assertTrue(data.acceptSelf(id,current.revision(),eligible)==BuildingWorldData.Result.SUCCESS,"Self construction/upgrade failed at "+n);}
            h.assertTrue(data.find(id).tier()==4&&data.find(id).claim().equals(bounds),"Upgrade changed fixed residence bounds");
            definitions.put(family,new PrefabDefinitions.Family(family,builtin.reservation(),List.of(com.stardew.craft.port.PortJava.getFirst(tiers)),1,4,500));field.set(null,Map.copyOf(definitions));
            h.assertTrue(PrefabDefinitions.maxTier(family)==1&&!PrefabDefinitions.available(data.find(id)),"Removed tier still operates");
            var single=BuildingRecord.waiting(UUID.randomUUID(),0,family,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),pos.east(6),pos.east(6),Direction.SOUTH,configured.selfBounds(pos.east(6)));
            h.assertTrue(data.register(single)==BuildingWorldData.Result.SUCCESS&&data.acceptSelf(single.id(),single.revision(),1)==BuildingWorldData.Result.SUCCESS,"Single-tier home cannot complete");
        }finally{field.set(null,before);}
        h.succeed();
    }

}
