package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class LivestockHomes {
    private LivestockHomes() {}
    public static com.stardew.craft.animal.model.AnimalBuildingTierDefinition rules(BuildingRecord home) {
        String family=home.family().getNamespace().equals("stardewcraft")?home.family().getPath():home.family().toString();
        return com.stardew.craft.animal.model.AnimalBuildingTierDefinitions.find(family,home.tier());
    }
    public static int capacity(BuildingRecord home) { var rules=rules(home);return rules==null?0:rules.capacity(); }
    public static boolean allowsPregnancy(BuildingRecord home) {var rules=rules(home);return rules!=null&&rules.allowsPregnancy();}
    public static boolean automaticFeed(BuildingRecord home) {var rules=rules(home);return rules!=null&&rules.automaticFeed();}
    public static int capacity(ServerLevel level,BuildingRecord home) {
        var rule=com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi.building(level,home.manager(),level.getBlockState(home.manager()));
        return rule==null?capacity(home):Math.max(0,rule.capacity());
    }
    public static boolean accepts(ServerLevel level,BuildingRecord home,LivestockSpecies species) {
        if(!accepts(home,species))return false;
        var rule=com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi.building(level,home.manager(),level.getBlockState(home.manager()));
        if(rule==null||rule.acceptedAnimals().isEmpty())return true;
        var entity=species.entityType();return entity!=null&&rule.acceptedAnimals().contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity));
    }
    public static void describe(net.minecraft.nbt.CompoundTag row,ServerLevel level,BuildingRecord home) {
        var allowed=new net.minecraft.nbt.ListTag();
        for(var species:LivestockSpecies.values())if(accepts(level,home,species))allowed.add(net.minecraft.nbt.StringTag.valueOf(species.id()));
        row.put("AllowedSpecies",allowed);
        var title=home.title();if(title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated)row.putString("TitleKey",translated.getKey());else row.putString("Title",title.getString());
    }
    public static boolean offered(net.minecraft.nbt.CompoundTag home,net.minecraft.nbt.CompoundTag animal) {
        return home.getList("AllowedSpecies",8).stream().anyMatch(id->id.getAsString().equals(animal.getString("Species")));
    }
    public static int capacity(int tier) { return tier * 4; }
    public static boolean accepts(BuildingRecord home) {
        return home != null && !UtilityBuildings.supported(home.family()) && PrefabDefinitions.available(home)
                && (home.phase() == BuildingRecord.Phase.READY || home.phase() == BuildingRecord.Phase.UPGRADING)
                && home.residence() == BuildingRecord.Residence.VALID;
    }
    public static boolean accepts(BuildingRecord home, LivestockSpecies species) {
        return species.known() && accepts(home) && home.family().equals(species.family());
    }
    public static BuildingBounds bounds(BuildingRecord home) {
        if (home.mode() == BuildingRecord.Mode.SELF_BUILT) return home.claim();
        var tier = PrefabDefinitions.get(home.family()).tier(home.tier());
        return PrefabDefinitions.transform(tier.bounds(), home.anchor(), PrefabDefinitions.rotation(home.facing()));
    }
    public static BlockPos preferred(BuildingRecord home) {
        if (home.mode() == BuildingRecord.Mode.SELF_BUILT) return home.manager().relative(home.facing());
        var tier = PrefabDefinitions.get(home.family()).tier(home.tier());
        return PrefabDefinitions.world(tier.animalSpawn(), tier.anchor(), home.anchor(), PrefabDefinitions.rotation(home.facing()));
    }
    public static boolean safe(ServerLevel level, BuildingBounds bounds, BlockPos pos) {
        return safe(level, bounds, pos, LivestockSpecies.WHITE_CHICKEN.dimensions(false));
    }
    public static boolean safe(ServerLevel level, BuildingBounds bounds, BlockPos pos, net.minecraft.world.entity.EntityDimensions body) {
        return bounds.contains(pos) && level.hasChunkAt(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.noCollision(body.makeBoundingBox(pos.getX() + .5, pos.getY() + .01, pos.getZ() + .5));
    }
    public static BlockPos spawn(ServerLevel level, BuildingRecord home) {
        return spawn(level, home, LivestockSpecies.WHITE_CHICKEN, false);
    }
    public static BlockPos spawn(ServerLevel level, BuildingRecord home, LivestockSpecies species, boolean baby) {
        var bounds = bounds(home); var preferred = preferred(home); var body = species.dimensions(baby);
        if (safe(level, bounds, preferred, body)) return preferred;
        BlockPos best = null; double distance = Double.MAX_VALUE;
        for (var pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) {
            double candidate = pos.distSqr(preferred);
            if (candidate < distance && safe(level, bounds, pos, body)) { best = pos.immutable(); distance = candidate; }
        }
        return best;
    }
    public static void load(ServerLevel level, BuildingBounds bounds) {
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++)
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) level.getChunk(x, z);
    }
}
