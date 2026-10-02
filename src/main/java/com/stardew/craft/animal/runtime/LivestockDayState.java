package com.stardew.craft.animal.runtime;

import com.stardew.craft.animal.rule.AnimalDayReducer;
import com.stardew.craft.api.v1.agriculture.*;
import com.stardew.craft.building.runtime.BuildingRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Staged handler mutations become part of the existing write-ahead daily batch. */
public final class LivestockDayState {
    private static final ThreadLocal<LivestockDayState> ACTIVE=new ThreadLocal<>();
    public static LivestockRecord read(ServerLevel level,UUID id) {
        var staged=ACTIVE.get();return staged!=null&&staged.level==level&&staged.record.id().equals(id)?staged.record:LivestockWorldData.get(level.getServer()).find(id);
    }
    public static void write(ServerLevel level,LivestockRecord record) {
        var staged=ACTIVE.get();if(staged!=null&&staged.level==level&&staged.record.id().equals(record.id()))staged.record=record;
        else LivestockWorldData.get(level.getServer()).put(record);
    }
    public final ServerLevel level;
    public LivestockRecord record;
    public AnimalDayReducer.State state;
    private final List<LivestockWorldData.Product> products;
    public LivestockDayState(ServerLevel level, LivestockRecord record, List<LivestockWorldData.Product> products) {
        this.level=level;this.record=record;this.products=products;
        var c=record.care();
        state=new AnimalDayReducer.State(c.age(),c.ownedDays(),c.friendship(),c.happiness(),c.fullness(),c.daysSinceLay(),c.petted(),c.autoPetted(),false,record.extra().getInt("MoodMessage"),record.produce(),c.quality());
    }
    public LivestockRecord finish(int day) {
        var settled=record.withCare(day,new LivestockCare(state.ageDays(),state.daysOwned(),state.friendship(),state.happiness(),state.fullness(),state.daysSinceLastProduce(),state.produceQuality(),state.wasPetToday(),state.wasAutoPetToday())).produce(state.currentProduceId());
        var extra=settled.extra();extra.putInt("MoodMessage",state.moodMessage());
        return settled.extra(extra);
    }
    public boolean product(ItemStack stack,boolean cracker) {
        if(stack==null||stack.isEmpty())return false;
        var submitted=stack.copy();if(cracker&&record.cracker())submitted.setCount(Math.multiplyExact(submitted.getCount(),2));
        products.add(LivestockWorldData.Product.fromStack(level,record,submitted));record=LivestockStats.stage(record,submitted);return true;
    }
    public static LivestockRecord settle(ServerLevel level, BuildingRecord home, LivestockRecord animal, int day,
            boolean hay,boolean festival,boolean outside,boolean sheltered,com.stardew.craft.util.StardewDeterministicRandom random,List<LivestockWorldData.Product> products,double luck) {
        var staged=new LivestockDayState(level,animal,products);var definition=animal.species().definition();
        boolean leftOut=outside;
        if(definition!=null) {
            var begin=AnimalDayReducer.begin(new AnimalDayReducer.BeginInput(definition,staged.state,outside?AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_CLOSED:sheltered?AnimalDayReducer.HomeSituation.INSIDE_DOOR_CLOSED:AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,2600));
            staged.state=begin.state();leftOut=begin.wasLeftOutLastNight();
        }
        StardewAnimalDailyHandlers.Result result;
        var previous=ACTIVE.get();ACTIVE.set(staged);
        try {result=StardewAnimalDailyHandlers.run(new StardewAnimalDailyContext(staged,day,day<com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay()));}
        finally {if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
        if(definition!=null) {
            var owner=com.stardew.craft.player.PlayerDataManager.getPlayerData(animal.owner());
            var fast=LivestockSpecies.profession(definition.professionForFasterProduce());
            var quality=LivestockSpecies.profession(definition.professionForQualityBoost());
            var conditions=new com.stardew.craft.api.v1.condition.StardewConditionContext(level,level.getServer().getPlayerList().getPlayer(animal.owner()));
            java.util.function.Function<java.util.List<com.stardew.craft.animal.model.FarmAnimalDefinition.ProduceEntry>,java.util.List<AnimalDayReducer.ProduceCandidate>> eligible=entries->entries.stream()
                    .filter(e->e.condition()==null||com.stardew.craft.api.v1.condition.StardewConditions.test(e.condition(),conditions).result().orElse(false))
                    .map(e->new AnimalDayReducer.ProduceCandidate(e.itemId(),e.minimumFriendship())).toList();
            var projection=level.getEntity(animal.id());
            var metadata=projection==null?null:StardewAgricultureDataApi.animal(projection);
            var normal=metadata==null?eligible.apply(definition.produce()):List.of(new AnimalDayReducer.ProduceCandidate(metadata.produce(),0));
            var finish=AnimalDayReducer.finish(new AnimalDayReducer.FinishInput(definition,staged.state,leftOut,!outside,hay,festival,result==StardewAnimalDailyHandlers.Result.SKIP_DEFAULT_PRODUCTION,
                    fast!=null&&owner.hasProfession(fast),quality!=null&&owner.hasProfession(quality),luck,metadata==null?null:metadata.produceIntervalDays(),normal,metadata==null?eligible.apply(definition.deluxeProduce()):List.of()),new AnimalDayReducer.RandomPort(){
                        public double nextDouble(){return random.nextDouble();}
                        public int nextInt(int bound){return random.nextInt(bound);}
                    });
            staged.state=finish.state();
            if(finish.production()!=null){var extra=staged.record.extra();extra.remove("HeldProduce");staged.record=staged.record.extra(extra);}
            if(finish.production()!=null&&finish.production().delivery()==AnimalDayReducer.Delivery.DROP_OVERNIGHT)
                staged.product(LivestockProducts.stack(finish.production().itemId().toString(),1,finish.production().quality()),true);
        }
        return staged.finish(day);
    }
}
