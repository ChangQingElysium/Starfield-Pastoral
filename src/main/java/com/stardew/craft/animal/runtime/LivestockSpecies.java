package com.stardew.craft.animal.runtime;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.api.v1.agriculture.*;
import com.stardew.craft.entity.animal.CoopAnimalVariant;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Stable identities; all gameplay values are resolved from the current definition snapshot. */
public final class LivestockSpecies {
    private static final Map<String, LivestockSpecies> IDENTITIES = new java.util.concurrent.ConcurrentHashMap<>();
    public static final LivestockSpecies WHITE_CHICKEN = parse("white_chicken");
    public static final LivestockSpecies BROWN_CHICKEN = parse("brown_chicken");
    public static final LivestockSpecies BLUE_CHICKEN = parse("blue_chicken");
    public static final LivestockSpecies VOID_CHICKEN = parse("void_chicken");
    public static final LivestockSpecies GOLDEN_CHICKEN = parse("golden_chicken");
    public static final LivestockSpecies DUCK = parse("duck");
    public static final LivestockSpecies RABBIT = parse("rabbit");
    public static final LivestockSpecies DINOSAUR = parse("dinosaur");
    public static final LivestockSpecies WHITE_COW = parse("white_cow");
    public static final LivestockSpecies BROWN_COW = parse("brown_cow");
    public static final LivestockSpecies GOAT = parse("goat");
    public static final LivestockSpecies SHEEP = parse("sheep");
    public static final LivestockSpecies PIG = parse("pig");
    public static final LivestockSpecies OSTRICH = parse("ostrich");
    public enum Harvest { DROP, MILK, SHEAR, DIG }
    private final String id;
    private LivestockSpecies(String id) { this.id = id; }
    public String id() { return id; }
    @Override public String toString(){return id;}
    public static LivestockSpecies parse(String id) {
        String normalized = Objects.requireNonNull(id).strip().toLowerCase(Locale.ROOT);
        if(normalized.equals("cow"))normalized="white_cow";
        new ResourceLocation(normalized.contains(":") ? normalized : "stardewcraft:" + normalized);
        return IDENTITIES.computeIfAbsent(normalized, LivestockSpecies::new);
    }
    public static LivestockSpecies[] values() {
        var ids = new LinkedHashSet<String>();
        FarmAnimalDefinitions.all().forEach(d -> ids.add(d.id().equals("cow") ? "white_cow" : d.id()));
        StardewAnimalShopEntries.entries().forEach(entry->ids.add(entry.animalTypeId()));
        StardewAnimalTypes.registeredTypeIds().stream().sorted().forEach(ids::add);
        return ids.stream().map(LivestockSpecies::parse).toArray(LivestockSpecies[]::new);
    }
    public String definitionId() { return id.equals("white_cow") ? "cow" : id; }
    public FarmAnimalDefinition definition() { return FarmAnimalDefinitions.find(definitionId()); }
    public boolean known() { return definition() != null || StardewAnimalTypes.definition(id) != null; }
    public boolean hasDefaultBehavior() { return definition() != null; }
    public ResourceLocation family() {
        var d = definition(); var legacy = StardewAnimalTypes.definition(id);
        String family = d != null ? d.family() : legacy == null ? "stardewcraft:missing" : legacy.family();
        return new ResourceLocation(family.contains(":") ? family : "stardewcraft:" + family);
    }
    public int minimumTier() { var d=definition(); var s=StardewAnimalShopEntries.entry(id); return d!=null?d.requiredBuildingTier():s==null?1:s.requiredTier(); }
    public int price() { var d=definition(); var s=StardewAnimalShopEntries.entry(id); return d!=null?(d.purchasePrice()<0?-1:Math.multiplyExact(d.purchasePrice(),2)):s==null?-1:s.price(); }
    public int matureDays() { var d=definition(); var legacy=StardewAnimalTypes.definition(id); return d!=null?d.daysToMature():legacy==null?Integer.MAX_VALUE:legacy.daysToMature(); }
    public int interval() { return definition()==null?Integer.MAX_VALUE:definition().daysToProduce(); }
    public int drain() { return definition()==null?0:definition().happinessDrain(); }
    public String normal() { return definition()==null?"":definition().produceItemId().toString(); }
    public String deluxe() { var d=definition(); return d==null||d.deluxeProduceItemId()==null?"":d.deluxeProduceItemId().toString(); }
    public int deluxeFriendship() { return definition()==null?Integer.MAX_VALUE:definition().deluxeProduceMinimumFriendship(); }
    public double deluxeDivisor() { return definition()==null?1:definition().deluxeProduceCareDivisor(); }
    public double luckMultiplier() { return definition()==null?0:definition().deluxeProduceLuckMultiplier(); }
    public Harvest harvest() {
        var d=definition(); if(d==null)return Harvest.DROP;
        return switch(d.harvestType()) {
            case DROP_OVERNIGHT -> Harvest.DROP;
            case DIG_UP -> Harvest.DIG;
            case HARVEST_WITH_TOOL -> new ResourceLocation("stardewcraft:shears").equals(d.harvestTool())?Harvest.SHEAR:Harvest.MILK;
        };
    }
    public ProfessionType profession() { return profession(definition()==null?-1:definition().professionForHappinessBoost()); }
    public static ProfessionType profession(int source) { return source==2?ProfessionType.COOPMASTER:source==3?ProfessionType.SHEPHERD:null; }
    public int grassAmount() { return definition()==null?0:definition().grassEatAmount(); }
    public boolean pregnancy() {
        var query=StardewAnimalQueryDefinitions.definition(definitionId());
        return query!=null?query.reproductionToggleAvailable():definition()!=null&&definition().canGetPregnant();
    }
    public int sellPrice(int friendship) {
        var query=StardewAnimalQueryDefinitions.definition(definitionId());
        if(query!=null)return query.sellPrice(friendship);
        return definition()==null?0:(int)(definition().sellPrice()*(Math.clamp(friendship,0,1000)/1000.0+.3));
    }
    public net.minecraft.world.entity.EntityType<?> entityType() {
        var d=definition();
        return d!=null?net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(d.entityTypeId()).orElse(null):StardewAnimalTypes.entityType(id);
    }
    public net.minecraft.world.entity.EntityDimensions dimensions(boolean baby) {
        var entity=entityType();
        return (entity==null?net.minecraft.world.entity.EntityDimensions.scalable(.65f,.8f):entity.getDimensions()).scale(baby?.65f:1f);
    }
    public boolean builtinAsset() { return ASSETS.containsKey(id); }
    public CoopAnimalVariant asset() { return Objects.requireNonNull(ASSETS.get(id), "No builtin animal asset for " + id); }
    private static final Map<String,CoopAnimalVariant> ASSETS = Map.ofEntries(
        Map.entry("white_chicken", CoopAnimalVariant.WHITE_CHICKEN),
        Map.entry("brown_chicken", CoopAnimalVariant.WHITE_CHICKEN),
        Map.entry("blue_chicken", CoopAnimalVariant.WHITE_CHICKEN),
        Map.entry("void_chicken", CoopAnimalVariant.VOID_CHICKEN),
        Map.entry("golden_chicken", CoopAnimalVariant.GOLDEN_CHICKEN),
        Map.entry("duck", CoopAnimalVariant.DUCK),
        Map.entry("rabbit", CoopAnimalVariant.RABBIT),
        Map.entry("dinosaur", CoopAnimalVariant.DINOSAUR),
        Map.entry("white_cow", CoopAnimalVariant.COW),
        Map.entry("brown_cow", CoopAnimalVariant.COW),
        Map.entry("goat", CoopAnimalVariant.GOAT),
        Map.entry("sheep", CoopAnimalVariant.SHEEP),
        Map.entry("pig", CoopAnimalVariant.PIG),
        Map.entry("ostrich", CoopAnimalVariant.OSTRICH)
    );
}
