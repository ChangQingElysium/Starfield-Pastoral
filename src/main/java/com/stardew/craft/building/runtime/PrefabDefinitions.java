package com.stardew.craft.building.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PrefabDefinitions {
    public static final ResourceLocation COOP = new ResourceLocation("stardewcraft:coop");
    public static final ResourceLocation BARN = new ResourceLocation("stardewcraft:barn");
    public static boolean supported(ResourceLocation id) { return com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(id).isPresent(); }
    public static net.minecraft.world.level.block.Block managerBlock(ResourceLocation id) {
        return com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(id).orElseThrow(() -> new IllegalArgumentException("Unregistered building family "+id)).manager().get();
    }
    public static net.minecraft.world.item.Item managerItem(ResourceLocation id) { return managerBlock(id).asItem(); }
    public static net.minecraft.world.item.Item blueprintItem(ResourceLocation id) {
        return com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(id).orElseThrow(() -> new IllegalArgumentException("Unregistered building family "+id)).blueprint().get();
    }
    private static Map<ResourceLocation, Family> families = Map.of();
    private static long generation;
    public static long generation() { return generation; }
    private static final Map<ResourceLocation, Template> templates = new HashMap<>();

    private PrefabDefinitions() {}
    public record Upgrade(int money, int wood, int stone, int days) {
        public Upgrade { if (money < 0 || wood < 0 || stone < 0 || days < 1) throw new IllegalArgumentException("Invalid upgrade cost/days"); }
    }
    public record Facilities(int troughs, int automaticTroughs, int hoppers, int incubators) {
        public Facilities{if(troughs<0||automaticTroughs<0||hoppers<0||incubators<0)throw new IllegalArgumentException("Negative facility requirements");}
    }
    public record Tier(int level, ResourceLocation structure, BlockPos size, BlockPos anchor,
                       BlockPos manager, BlockPos animalSpawn, BuildingBounds bounds, Facilities facilities, Upgrade upgrade) {}
    public record Family(ResourceLocation id, BuildingBounds reservation, List<Tier> tiers,
                         int selfRadius, int selfHeight, int managerPrice) {
        public Tier tier(int tier) { return tiers.get(tier - 1); }
        public BuildingBounds selfBounds(BlockPos manager) {
            return new BuildingBounds(manager.offset(-selfRadius, 0, -selfRadius),
                    manager.offset(selfRadius + 1, selfHeight, selfRadius + 1));
        }
    }
    public record Cell(BlockPos pos, BlockState state, CompoundTag blockEntity) {}
    /** Cells are the projection; retained ground is deliberately absent from all placement/protection paths. */
    public record Template(BlockPos size, List<Cell> cells, java.util.Set<BlockPos> retainedGround) {
        public Template(BlockPos size, List<Cell> authored) {
            this(size, authored.stream().filter(cell -> !retainsGround(cell)).toList(),
                    authored.stream().filter(PrefabDefinitions::retainsGround).map(Cell::pos)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }

    static boolean retainsGround(Cell cell) {
        if (cell.state().is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) return true;
        if (cell.pos().getY() != 0) return false;
        var block = cell.state().getBlock();
        return block == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                || block == net.minecraft.world.level.block.Blocks.DIRT
                || block instanceof com.stardew.craft.block.nature.YellowDirtBlock
                || block instanceof com.stardew.craft.block.terrain.TerrainGrassBlock
                || block instanceof com.stardew.craft.block.terrain.TerrainDirtBlock;
    }

    public static java.util.Set<BlockPos> retainedGround(ServerLevel level, BuildingRecord record) {
        if (record.mode() != BuildingRecord.Mode.PREFAB) return java.util.Set.of();
        var tier = get(record.family()).tier(record.tier());
        var rotation = rotation(record.facing());
        return template(level, tier).retainedGround().stream()
                .map(pos -> world(pos, tier.anchor(), record.anchor(), rotation))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "farm_building_prefabs") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Family> loaded = new LinkedHashMap<>();
                json.forEach((id, value) -> loaded.put(id, parse(id, value.getAsJsonObject())));
                if (!loaded.containsKey(COOP) || !loaded.containsKey(BARN)) throw new IllegalArgumentException("Missing coop prefab definition");
                families = Map.copyOf(loaded);
                generation++;
                templates.clear();
                BuildingProtection.clearMasks();
            }
        });
    }

    public static Family get(ResourceLocation family) {
        Family value = families.get(family);
        if (value == null) throw new IllegalArgumentException("Unknown prefab family: " + family);
        return value;
    }

    public static Facilities facilities(ResourceLocation family,int tier){
        var rules=com.stardew.craft.animal.model.AnimalBuildingTierDefinitions.find(family.getNamespace().equals("stardewcraft")?family.getPath():family.toString(),tier);
        if(rules!=null){var v=rules.validation();return new Facilities(v.feedTroughs(),v.autoFeedTroughs(),v.hayHoppers(),v.incubators());}
        return get(family).tier(tier).facilities();
    }
    public static boolean available(BuildingRecord record){return available(record.family())&&record.tier()<=maxTier(record.family())&&(record.phase()!=BuildingRecord.Phase.UPGRADING||record.tier()<maxTier(record.family()));}
    public static boolean available(ResourceLocation family){return families.containsKey(family);}
    public static int maxTier(ResourceLocation family) {
        var value=families.get(family);return value!=null?value.tiers().size():family.equals(UtilityBuildings.SILO)?1:Integer.MAX_VALUE;
    }
    public static net.minecraft.world.level.block.state.BlockState managerState(ResourceLocation family,net.minecraft.core.Direction facing){
        var state=managerBlock(family).defaultBlockState();var property=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        return state.hasProperty(property)?state.setValue(property,facing):state;
    }
    private static Family parse(ResourceLocation id, JsonObject json) {
        if (json.get("format_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported prefab format");
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiers")) {
            JsonObject tier = element.getAsJsonObject();
            int number = tier.get("tier").getAsInt();
            JsonObject facilities = tier.has("facilities") ? tier.getAsJsonObject("facilities") : new JsonObject();
            JsonObject upgrade = tier.has("upgrade") ? tier.getAsJsonObject("upgrade") : new JsonObject();
            tiers.add(new Tier(number, new ResourceLocation(tier.get("structure").getAsString()),
                    pos(tier, "size"), pos(tier, "anchor"), pos(tier.getAsJsonObject("manager"), "position"),
                    pos(tier, "animal_spawn"), bounds(tier.getAsJsonObject("bounds_from_anchor")),
                    new Facilities(integer(facilities, "troughs", 0), integer(facilities, "automatic_troughs", 0),
                            integer(facilities, "hoppers", 0), integer(facilities, "incubators", 0)),
                    new Upgrade(integer(upgrade, "money", 0), integer(upgrade, "wood", 0), integer(upgrade, "stone", 0), integer(upgrade, "days", 2))));
            if (number != tiers.size()) throw new IllegalArgumentException("Nonsequential prefab tiers: " + id);
        }
        if (tiers.isEmpty()) throw new IllegalArgumentException("Expected at least one prefab tier: " + id);
        if(!supported(id))throw new IllegalArgumentException("Prefab has no registered family binding: "+id);
        JsonObject self = json.has("self_build") ? json.getAsJsonObject("self_build") : new JsonObject();
        BuildingBounds reservation = bounds(json.getAsJsonObject("reservation_bounds_from_anchor"));
        for (Tier tier : tiers) {
            if (!reservation.contains(tier.bounds.min()) || !reservation.contains(tier.bounds.maxInclusive())) {
                throw new IllegalArgumentException("Tier escapes family reservation: " + id);
            }
        }
        int radius = integer(self, "radius", 0), height = integer(self, "height", 1), price = integer(self, "manager_price", 0);
        if (radius < 0 || radius > 16 || height < 1 || height > 32 || price < 0) {
            throw new IllegalArgumentException("Invalid self-building rules: " + id);
        }
        return new Family(id, reservation, List.copyOf(tiers), radius, height, price);
    }

    public static Template template(ServerLevel level, Tier tier) {
        return templates.computeIfAbsent(tier.structure(), id -> {
            ResourceLocation path = id.withPath("structures/" + id.getPath() + ".nbt");
            try (var input = level.getServer().getResourceManager().getResourceOrThrow(path).open()) {
                CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(16_000_000L));
                ListTag sizeTag = tag.getList("size", 3);
                BlockPos size = new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
                if (!size.equals(tier.size())) throw new IllegalArgumentException("Prefab size mismatch: " + id);
                List<BlockState> palette = new ArrayList<>();
                for (var entry : tag.getList("palette", 10)) {
                    CompoundTag value = (CompoundTag) entry;
                    ResourceLocation block = new ResourceLocation(value.getString("Name"));
                    if (!BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalArgumentException("Unknown prefab block: " + block);
                    var definition = BuiltInRegistries.BLOCK.get(block).getStateDefinition();
                    CompoundTag props = value.getCompound("Properties");
                    for (String key : props.getAllKeys()) {
                        var property = definition.getProperty(key);
                        if (property == null || property.getValue(props.getString(key)).isEmpty()) {
                            throw new IllegalArgumentException("Unknown prefab state: " + block + " " + key + "=" + props.getString(key));
                        }
                    }
                    palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), value));
                }
                List<Cell> cells = new ArrayList<>();
                java.util.Set<BlockPos> occupied = new java.util.HashSet<>();
                for (var entry : tag.getList("blocks", 10)) {
                    CompoundTag block = (CompoundTag) entry;
                    ListTag position = block.getList("pos", 3);
                    BlockPos pos = new BlockPos(position.getInt(0), position.getInt(1), position.getInt(2));
                    if (!new BuildingBounds(BlockPos.ZERO, size).contains(pos) || !occupied.add(pos)) {
                        throw new IllegalArgumentException("Invalid prefab block position: " + id);
                    }
                    cells.add(new Cell(pos, palette.get(block.getInt("state")),
                            block.contains("nbt", 10) ? block.getCompound("nbt").copy() : null));
                }
                if (cells.size() != size.getX() * size.getY() * size.getZ() || !tag.getList("entities", 10).isEmpty()) {
                    throw new IllegalArgumentException("Prefab must include all air cells and no entities: " + id);
                }
                return new Template(size, List.copyOf(cells));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load prefab " + id, exception);
            }
        });
    }

    public static void validateAssets(ServerLevel level,ResourceLocation family){
        var binding=com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(family).orElseThrow();
        if(!(binding.blueprint().get() instanceof BuildingBlueprintItem item)||!item.family().equals(family))throw new IllegalArgumentException("Wrong blueprint binding for "+family);
        if (com.stardew.craft.pet.PetBowlBuildings.isBowl(family)) {
            if (!(binding.manager().get() instanceof com.stardew.craft.pet.PetBowlBlock bowl) || !com.stardew.craft.pet.PetBowlBuildings.family(bowl.style).equals(family)) throw new IllegalArgumentException("Wrong bowl binding for " + family);
        } else if(!(binding.manager().get().asItem() instanceof BuildingManagerItem managerItem)||!managerItem.family().equals(family))throw new IllegalArgumentException("Wrong manager binding for "+family);
        for(var tier:get(family).tiers()){
            var template=template(level,tier);
            if(template.cells().stream().noneMatch(c->c.pos().equals(tier.manager())&&c.state().is(binding.manager().get())))throw new IllegalArgumentException("Wrong template manager for "+family+" tier "+tier.level());
            if(tier.level()>1){var permit=binding.upgrades().get(tier.level());if(permit==null||!(permit.get() instanceof BuildingUpgradePermitItem upgrade)||!upgrade.family().equals(family)||upgrade.targetTier()!=tier.level())throw new IllegalArgumentException("Missing upgrade permit for "+family+" tier "+tier.level());}
        }
    }
    public static CompoundTag previewTag(ServerLevel level, ResourceLocation family, int number) {
        Tier tier = get(family).tier(number);
        CompoundTag result = new CompoundTag();
        result.putString("Family", family.toString());
        result.putInt("Tier", number);
        result.putLong("ReservationMin", get(family).reservation().min().asLong());
        result.putLong("ReservationMax", get(family).reservation().maxExclusive().asLong());
        result.putLong("BoundsMin", tier.bounds().min().asLong());
        result.putLong("BoundsMax", tier.bounds().maxExclusive().asLong());
        result.putLong("ManagerRelative", tier.manager().subtract(tier.anchor()).asLong());
        result.putIntArray("Size", new int[]{tier.size().getX(), tier.size().getY(), tier.size().getZ()});
        result.putIntArray("Anchor", new int[]{tier.anchor().getX(), tier.anchor().getY(), tier.anchor().getZ()});
        ListTag blocks = new ListTag();
        for (Cell cell : template(level, tier).cells()) {
            if (cell.state().isAir()) continue;
            CompoundTag value = new CompoundTag();
            value.putIntArray("Pos", new int[]{cell.pos().getX(), cell.pos().getY(), cell.pos().getZ()});
            value.put("State", NbtUtils.writeBlockState(cell.state()));
            if (cell.blockEntity() != null) value.put("Appearance", cell.blockEntity().copy());
            blocks.add(value);
        }
        result.put("Blocks", blocks);
        return result;
    }

    public static Rotation rotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Vertical prefab facing");
        };
    }
    public static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }
    /** Rotate block centers about the corner vertex, not about the corner block's center. */
    public static BlockPos rotateCell(BlockPos pos, Rotation rotation) {
        BlockPos rotated = pos.rotate(rotation);
        return switch (rotation) {
            case CLOCKWISE_90 -> rotated.offset(-1, 0, 0);
            case CLOCKWISE_180 -> rotated.offset(-1, 0, -1);
            case COUNTERCLOCKWISE_90 -> rotated.offset(0, 0, -1);
            default -> rotated;
        };
    }
    public static BlockPos world(BlockPos local, BlockPos templateAnchor, BlockPos worldAnchor, Rotation rotation) {
        return rotateCell(local.subtract(templateAnchor), rotation).offset(worldAnchor);
    }
    public static BuildingBounds transform(BuildingBounds bounds, BlockPos anchor, Rotation rotation) {
        BlockPos min = bounds.min(), max = bounds.maxInclusive();
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int x : new int[]{min.getX(), max.getX()}) for (int z : new int[]{min.getZ(), max.getZ()}) {
            BlockPos point = rotateCell(new BlockPos(x, 0, z), rotation);
            x0 = Math.min(x0, point.getX()); z0 = Math.min(z0, point.getZ());
            x1 = Math.max(x1, point.getX()); z1 = Math.max(z1, point.getZ());
        }
        return new BuildingBounds(anchor.offset(x0, min.getY(), z0), anchor.offset(x1 + 1, max.getY() + 1, z1 + 1));
    }
    private static BlockPos pos(JsonObject json, String key) {
        var values = json.getAsJsonArray(key);
        if (values.size() != 3) throw new IllegalArgumentException("Invalid position: " + key);
        return new BlockPos(values.get(0).getAsInt(), values.get(1).getAsInt(), values.get(2).getAsInt());
    }
    private static BuildingBounds bounds(JsonObject json) { return new BuildingBounds(pos(json, "min"), pos(json, "max_exclusive")); }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
}
