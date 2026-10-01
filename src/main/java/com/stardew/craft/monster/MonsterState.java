package com.stardew.craft.monster;

import com.stardew.craft.combat.MonsterStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Server-owned instance state. Current HP belongs exclusively to LivingEntity. */
public final class MonsterState {
    public enum Life { ALIVE, DOWNED, TRANSFORMING, DEAD, CLEANUP }
    public enum Settlement { DROPS_AND_PROGRESS, WEAPON_REWARDS, RINGS, POPULATION }
    private final ResourceLocation identity;
    private final MonsterSpawnContext context;
    private final int sourceMaxHealth;
    private final List<MonsterDefinition.Drop> dropTable;
    private final List<String> bornDrops;
    private final EnumSet<Settlement> settled = EnumSet.noneOf(Settlement.class);
    private MonsterStats stats;
    private Life life = Life.ALIVE;

    public MonsterState(MonsterDefinition definition, MonsterSpawnContext context, RandomSource random) {
        this(definition.id(), context, definition.health(), definition.drops(), definition.rollDrops(random),
                MonsterStats.builder().damage(definition.damage()).resilience(definition.resilience())
                        .missChance(definition.missChance()).experience(definition.experience()).build());
    }
    private MonsterState(ResourceLocation identity, MonsterSpawnContext context, int sourceMaxHealth,
                         List<MonsterDefinition.Drop> dropTable, List<String> bornDrops, MonsterStats stats) {
        this.identity = identity; this.context = context; this.sourceMaxHealth = sourceMaxHealth;
        this.dropTable = List.copyOf(dropTable); this.bornDrops = new ArrayList<>(bornDrops); this.stats = stats;
    }
    public ResourceLocation identity() { return identity; }
    public MonsterSpawnContext context() { return context; }
    public int sourceMaxHealth() { return sourceMaxHealth; }
    public MonsterStats stats() { return stats; }
    public void stats(MonsterStats value) { stats = value; }
    public Life life() { return life; }
    public void life(Life value) {
        if ((life == Life.DEAD || life == Life.CLEANUP) && value != life) throw new IllegalStateException("Terminal monster lifecycle");
        life = value;
    }
    public boolean claim(Settlement settlement) { return life == Life.DEAD && settled.add(settlement); }
    public List<String> bornDrops() { return List.copyOf(bornDrops); }
    public void addBornDrop(ResourceLocation item) { bornDrops.add(item.toString()); }
    public List<String> rerollDrops(RandomSource random) {
        var result = new ArrayList<String>();
        for (var drop : dropTable) if (random.nextDouble() < drop.chance()) result.add(drop.item());
        return List.copyOf(result);
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putString("Identity", identity.toString()); tag.put("Context", context.save());
        tag.putInt("SourceMaxHealth", sourceMaxHealth); tag.put("Stats", stats.toNBT()); tag.putString("Life", life.name());
        var table = new ListTag();
        for (var drop : dropTable) {
            var entry = new CompoundTag(); entry.putString("Item", drop.item()); entry.putDouble("Chance", drop.chance()); table.add(entry);
        }
        tag.put("DropTable", table);
        var drops = new ListTag(); bornDrops.forEach(id -> drops.add(StringTag.valueOf(id.toString()))); tag.put("BornDrops", drops);
        var claims = new ListTag(); settled.forEach(s -> claims.add(StringTag.valueOf(s.name()))); tag.put("Settled", claims);
        return tag;
    }
    public static MonsterState load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported monster instance version");
        var table = new ArrayList<MonsterDefinition.Drop>();
        for (var element : tag.getList("DropTable", Tag.TAG_COMPOUND)) {
            var drop = (CompoundTag) element;
            table.add(new MonsterDefinition.Drop(drop.getString("Item"), drop.getDouble("Chance")));
        }
        var drops = new ArrayList<String>();
        for (var element : tag.getList("BornDrops", Tag.TAG_STRING)) drops.add(element.getAsString());
        var state = new MonsterState(new ResourceLocation(tag.getString("Identity")),
                MonsterSpawnContext.load(tag.getCompound("Context")), tag.getInt("SourceMaxHealth"), table, drops,
                MonsterStats.fromNBT(tag.getCompound("Stats")));
        state.life = Life.valueOf(tag.getString("Life"));
        for (var element : tag.getList("Settled", Tag.TAG_STRING)) state.settled.add(Settlement.valueOf(element.getAsString()));
        return state;
    }
}
