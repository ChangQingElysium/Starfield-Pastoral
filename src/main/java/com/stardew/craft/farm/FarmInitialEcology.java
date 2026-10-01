package com.stardew.craft.farm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Paints an enlarged farm from source-derived regional ecology rates.
 *
 * <p>The original Paths layer is statistical input, never a placement mask.
 * Every source-map neighborhood supplies a smoothed mix, while the actual
 * Minecraft surface area determines how much is painted. Connected brush
 * strokes create pasture and debris patches; trees and bushes use loose groves.
 * Empty source cells therefore no longer turn enlarged playable margins into
 * empty scenery.</p>
 */
final class FarmInitialEcology {
    private static final int RANDOM_PICK_ATTEMPTS = 160;
    private static final int NEAR_PICK_ATTEMPTS = 72;
    private static final int INITIAL_PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private FarmInitialEcology() {
    }

    static Result populate(ServerLevel level, FarmInstance farm, boolean retrofit) {
        Profile profile = load(level, farm);
        if (profile.zones().isEmpty()) return Result.EMPTY;

        SurfaceIndex surfaces = new SurfaceIndex(level, farm, profile.zones());
        long seed = level.getSeed()
                ^ farm.getOwnerUUID().getMostSignificantBits()
                ^ Long.rotateLeft(farm.getOwnerUUID().getLeastSignificantBits(), 23)
                ^ farm.getFarmLayoutId().hashCode()
                ^ 0x6A09E667F3BCC909L;
        RandomSource random = RandomSource.create(seed);
        MutableResult result = new MutableResult();

        for (Zone zone : profile.zones()) {
            List<FarmDebrisPlacementRules.Surface> available = surfaces.availableIn(zone, farm.getOrigin());
            if (available.isEmpty()) continue;
            EnumMap<Kind, Integer> remaining = scaledTargets(zone, farm, available, random);
            if (retrofit) subtractExisting(level, farm, zone, remaining);
            ZoneRuntime runtime = new ZoneRuntime(level, farm, zone, surfaces, available, random, result);
            runtime.placeTreeGroves(remaining);
            runtime.placeBushGroves(remaining);
            runtime.placePasturePatches(remaining);
            runtime.placeMixedDebrisPatches(remaining);
            runtime.placeSaplingGroves(remaining);
        }

        Result immutable = result.snapshot();
        StardewCraft.LOGGER.info(
                "[FARM_INIT] Painted ecology for {}{}: trees={}, bushes={}, weeds={}, stones={}, twigs={}, pasture={}, saplings={}",
                farm.getOwnerName(), retrofit ? " (retrofit)" : "",
                immutable.trees(), immutable.bushes(), immutable.weeds(), immutable.stones(),
                immutable.twigs(), immutable.pasture(), immutable.saplings());
        return immutable;
    }

    private static EnumMap<Kind, Integer> scaledTargets(
            Zone zone, FarmInstance farm, List<FarmDebrisPlacementRules.Surface> surfaces, RandomSource random
    ) {
        EnumMap<Kind, Double> exact = new EnumMap<>(Kind.class);
        for (FarmDebrisPlacementRules.Surface surface : surfaces) {
            for (Kind kind : Kind.values()) {
                exact.merge(kind, zone.effectiveRate(farm, kind, surface.groundKind()), Double::sum);
            }
        }
        EnumMap<Kind, Integer> result = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            double value = exact.getOrDefault(kind, 0.0D) * kind.footprintScale;
            int target = (int) Math.floor(value);
            if (random.nextDouble() < value - target) target++;
            result.put(kind, target);
        }
        return result;
    }

    private static void subtractExisting(
            ServerLevel level, FarmInstance farm, Zone zone, EnumMap<Kind, Integer> targets
    ) {
        EnumMap<Kind, Integer> existing = new EnumMap<>(Kind.class);
        BlockPos origin = farm.getOrigin();
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int minX = Math.max(min.getX(), origin.getX() + zone.minX());
        int maxX = Math.min(max.getX(), origin.getX() + zone.maxX());
        int minZ = Math.max(min.getZ(), origin.getZ() + zone.minZ());
        int maxZ = Math.min(max.getZ(), origin.getZ() + zone.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    Kind kind = existingKind(level.getBlockState(new BlockPos(x, y, z)));
                    if (kind != null) {
                        existing.merge(kind, 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        existing.forEach((kind, count) -> targets.compute(
                kind, (ignored, target) -> Math.max(0, (target == null ? 0 : target) - count)));
    }

    @Nullable
    private static Kind existingKind(BlockState state) {
        Block block = state.getBlock();
        if (block == WildTrees.OAK.modernRoot().get()) return Kind.OAK;
        if (block == WildTrees.MAPLE.modernRoot().get()) return Kind.MAPLE;
        if (block == WildTrees.PINE.modernRoot().get()) return Kind.PINE;
        if (block == WildTrees.OAK.sapling0().get() || block == WildTrees.OAK.sapling1().get()
                || block == WildTrees.MAPLE.sapling0().get() || block == WildTrees.MAPLE.sapling1().get()
                || block == WildTrees.PINE.sapling0().get() || block == WildTrees.PINE.sapling1().get()) {
            return Kind.SAPLING;
        }
        if (block instanceof WildWeedsBlock) return Kind.WEEDS;
        if (block == ModBlocks.MINE_STONE_343.get() || block == ModBlocks.MINE_STONE_450.get()) return Kind.STONES;
        if (block instanceof FarmTwigBlock) return Kind.TWIGS;
        if (block == ModBlocks.BLUE_PASTURE_GRASS.get()) return Kind.BLUE_PASTURE;
        if (block instanceof PastureGrassBlock) return Kind.PASTURE;
        if (block == ModBlocks.SMALL_BUSH.get()) return Kind.SMALL_BUSH;
        if (block == ModBlocks.BERRY_BUSH.get()
                && state.getValue(BerryBushBlock.PART) == BerryBushBlock.Part.MAIN) return Kind.LARGE_BUSH;
        return null;
    }

    private static Profile load(ServerLevel level, FarmInstance farm) {
        String path = "farm_ecology/" + farm.getFarmLayoutId().getPath() + ".json";
        ResourceLocation id = new ResourceLocation(StardewCraft.MODID, path);
        var resource = level.getServer().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            StardewCraft.LOGGER.error("Missing farm ecology profile {}", id);
            return Profile.EMPTY;
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("format").getAsInt() != 3) throw new IllegalArgumentException("unsupported format");
            List<Zone> zones = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("zones")) {
                JsonObject json = element.getAsJsonObject();
                JsonArray bounds = json.getAsJsonArray("bounds");
                EnumMap<SourceGround, EnumMap<Kind, Double>> rates = new EnumMap<>(SourceGround.class);
                JsonObject rateJson = json.getAsJsonObject("rates");
                for (SourceGround ground : SourceGround.values()) {
                    EnumMap<Kind, Double> groundRates = new EnumMap<>(Kind.class);
                    JsonObject values = rateJson.getAsJsonObject(ground.jsonName);
                    if (values != null) {
                        for (var entry : values.entrySet()) {
                            Kind kind = Kind.fromJson(entry.getKey());
                            if (kind != null) groundRates.put(kind, entry.getValue().getAsDouble());
                        }
                    }
                    rates.put(ground, groundRates);
                }
                zones.add(new Zone(bounds.get(0).getAsInt(), bounds.get(1).getAsInt(),
                        bounds.get(2).getAsInt(), bounds.get(3).getAsInt(), rates));
            }
            return new Profile(List.copyOf(zones));
        } catch (Exception exception) {
            StardewCraft.LOGGER.error("Failed to read farm ecology profile {}", id, exception);
            return Profile.EMPTY;
        }
    }

    private enum SourceGround {
        DIRT("dirt"), GRASS("grass");
        private final String jsonName;
        SourceGround(String jsonName) { this.jsonName = jsonName; }
    }

    private enum Kind {
        OAK("oak", 0.78D), MAPLE("maple", 0.78D), PINE("pine", 0.78D),
        WEEDS("weeds", 1.0D), STONES("stones", 1.0D), TWIGS("twigs", 1.0D),
        PASTURE("pasture", 1.0D), BLUE_PASTURE("blue_pasture", 1.0D),
        SAPLING("saplings", 1.0D), LARGE_BUSH("large_bush", 0.55D), SMALL_BUSH("small_bush", 1.0D);

        private final String jsonName;
        private final double footprintScale;
        Kind(String jsonName, double footprintScale) {
            this.jsonName = jsonName;
            this.footprintScale = footprintScale;
        }
        private boolean isMatureTree() { return this == OAK || this == MAPLE || this == PINE; }
        private boolean needsWideProtection() { return isMatureTree() || this == LARGE_BUSH; }
        @Nullable
        private static Kind fromJson(String value) {
            for (Kind kind : values()) if (kind.jsonName.equals(value)) return kind;
            return null;
        }
    }

    private record Profile(List<Zone> zones) {
        private static final Profile EMPTY = new Profile(List.of());
    }

    private record Zone(
            int minX, int minZ, int maxX, int maxZ,
            EnumMap<SourceGround, EnumMap<Kind, Double>> rates
    ) {
        private boolean contains(FarmInstance farm, int worldX, int worldZ) {
            int localX = worldX - farm.getOrigin().getX();
            int localZ = worldZ - farm.getOrigin().getZ();
            return localX >= minX && localX <= maxX && localZ >= minZ && localZ <= maxZ;
        }
        private double sourceRate(SourceGround ground, Kind kind) {
            EnumMap<Kind, Double> values = rates.get(ground);
            return values == null ? 0.0D : values.getOrDefault(kind, 0.0D);
        }
        private double effectiveRate(
                FarmInstance farm, Kind kind, FarmDebrisPlacementRules.GroundKind ground
        ) {
            double dirt = sourceRate(SourceGround.DIRT, kind);
            double grass = sourceRate(SourceGround.GRASS, kind);
            double rate = switch (ground) {
                case DIRT -> dirt;
                case GRASS -> dirt * 0.45D + grass * 0.55D;
                case DARK_GRASS -> dirt * 0.25D + grass * 0.75D;
                case SAND -> dirt * 0.85D + grass * 0.15D;
                default -> 0.0D;
            };
            if (rate <= 0.0D) return 0.0D;
            boolean forest = farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(FarmType.FOREST));
            boolean meadowlands = farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(FarmType.MEADOWLANDS));
            boolean beach = farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(FarmType.BEACH));
            if (kind.isMatureTree() || kind == Kind.SAPLING) {
                if (ground == FarmDebrisPlacementRules.GroundKind.GRASS) rate *= forest ? 1.30D : 1.12D;
                if (ground == FarmDebrisPlacementRules.GroundKind.DARK_GRASS) rate *= forest ? 1.70D : 1.35D;
                if (ground == FarmDebrisPlacementRules.GroundKind.SAND) rate *= beach ? 0.72D : 0.0D;
            } else if (kind == Kind.LARGE_BUSH || kind == Kind.SMALL_BUSH) {
                if (ground == FarmDebrisPlacementRules.GroundKind.GRASS) rate *= forest ? 1.25D : 1.05D;
                if (ground == FarmDebrisPlacementRules.GroundKind.DARK_GRASS) rate *= forest ? 1.55D : 1.25D;
                if (ground == FarmDebrisPlacementRules.GroundKind.SAND) rate *= beach ? 0.80D : 0.0D;
            } else if (kind == Kind.PASTURE && ground == FarmDebrisPlacementRules.GroundKind.SAND) {
                rate *= beach ? 1.35D : 0.0D;
            } else if (kind == Kind.BLUE_PASTURE) {
                if (!meadowlands) return 0.0D;
                if (ground == FarmDebrisPlacementRules.GroundKind.GRASS) rate *= 1.25D;
                if (ground == FarmDebrisPlacementRules.GroundKind.DARK_GRASS) rate *= 1.40D;
                if (ground == FarmDebrisPlacementRules.GroundKind.SAND) return 0.0D;
            } else if (ground == FarmDebrisPlacementRules.GroundKind.SAND) {
                if (!beach) return 0.0D;
                if (kind == Kind.WEEDS) rate *= 0.75D;
                if (kind == Kind.TWIGS) rate *= 0.90D;
            }
            return rate;
        }
    }

    record Result(int trees, int bushes, int weeds, int stones, int twigs, int pasture, int saplings) {
        private static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0, 0);
    }

    private static final class MutableResult {
        private int trees;
        private int bushes;
        private int weeds;
        private int stones;
        private int twigs;
        private int pasture;
        private int saplings;
        private void placed(Kind kind) {
            switch (kind) {
                case OAK, MAPLE, PINE -> trees++;
                case LARGE_BUSH, SMALL_BUSH -> bushes++;
                case WEEDS -> weeds++;
                case STONES -> stones++;
                case TWIGS -> twigs++;
                case PASTURE, BLUE_PASTURE -> pasture++;
                case SAPLING -> saplings++;
            }
        }
        private Result snapshot() { return new Result(trees, bushes, weeds, stones, twigs, pasture, saplings); }
    }

    private static final class ZoneRuntime {
        private final ServerLevel level;
        private final FarmInstance farm;
        private final Zone zone;
        private final SurfaceIndex surfaces;
        private final List<FarmDebrisPlacementRules.Surface> available;
        private final RandomSource random;
        private final MutableResult result;
        private final EnumMap<Kind, Double> maxRates = new EnumMap<>(Kind.class);

        private ZoneRuntime(
                ServerLevel level, FarmInstance farm, Zone zone, SurfaceIndex surfaces,
                List<FarmDebrisPlacementRules.Surface> available, RandomSource random, MutableResult result
        ) {
            this.level = level;
            this.farm = farm;
            this.zone = zone;
            this.surfaces = surfaces;
            this.available = available;
            this.random = random;
            this.result = result;
            for (Kind kind : Kind.values()) {
                double max = 0.0D;
                for (FarmDebrisPlacementRules.Surface surface : available) {
                    max = Math.max(max, zone.effectiveRate(farm, kind, surface.groundKind()));
                }
                maxRates.put(kind, max);
            }
        }

        private void placeTreeGroves(EnumMap<Kind, Integer> remaining) {
            List<Kind> trees = expandedKinds(remaining, Kind.OAK, Kind.MAPLE, Kind.PINE);
            List<BlockPos> grove = new ArrayList<>();
            int failures = 0;
            while (!trees.isEmpty() && failures < trees.size() * 64 + 160) {
                int index = random.nextInt(trees.size());
                Kind kind = trees.get(index);
                FarmDebrisPlacementRules.Surface target = grove.isEmpty() || random.nextDouble() < 0.20D
                        ? randomSurface(kind) : nearbyTreeSurface(kind, grove);
                if (target != null && place(target, kind)) {
                    grove.add(target.place());
                    trees.remove(index);
                    remaining.computeIfPresent(kind, (ignored, count) -> Math.max(0, count - 1));
                    failures = 0;
                } else failures++;
            }
        }

        private void placeBushGroves(EnumMap<Kind, Integer> remaining) {
            int large = remaining.getOrDefault(Kind.LARGE_BUSH, 0);
            List<BlockPos> grove = new ArrayList<>();
            int failures = 0;
            while (large > 0 && failures < large * 64 + 160) {
                FarmDebrisPlacementRules.Surface target = grove.isEmpty() || random.nextDouble() < 0.34D
                        ? randomSurface(Kind.LARGE_BUSH) : nearbyBushSurface(grove);
                if (target != null && place(target, Kind.LARGE_BUSH)) {
                    grove.add(target.place());
                    remaining.put(Kind.LARGE_BUSH, --large);
                    failures = 0;
                } else failures++;
            }
            placeClustered(remaining, false, 2, 6, Kind.SMALL_BUSH);
        }

        private void placePasturePatches(EnumMap<Kind, Integer> remaining) {
            placeClustered(remaining, true, 8, 20, Kind.PASTURE, Kind.BLUE_PASTURE);
        }

        private void placeMixedDebrisPatches(EnumMap<Kind, Integer> remaining) {
            placeClustered(remaining, false, 4, 11, Kind.WEEDS, Kind.STONES, Kind.TWIGS);
        }

        private void placeSaplingGroves(EnumMap<Kind, Integer> remaining) {
            int count = remaining.getOrDefault(Kind.SAPLING, 0);
            List<BlockPos> grove = new ArrayList<>();
            int failures = 0;
            while (count > 0 && failures < count * 48 + 96) {
                FarmDebrisPlacementRules.Surface target = grove.isEmpty() || random.nextDouble() < 0.28D
                        ? randomSurface(Kind.SAPLING) : nearbyTreeSurface(Kind.SAPLING, grove);
                if (target != null && place(target, Kind.SAPLING)) {
                    grove.add(target.place());
                    remaining.put(Kind.SAPLING, --count);
                    failures = 0;
                } else failures++;
            }
        }

        private void placeClustered(
                EnumMap<Kind, Integer> remaining, boolean connected,
                int minCluster, int maxCluster, Kind... kinds
        ) {
            int failures = 0;
            while (remainingTotal(remaining, kinds) > 0
                    && failures < remainingTotal(remaining, kinds) * 28 + 128) {
                int clusterSize = minCluster + random.nextInt(maxCluster - minCluster + 1);
                List<BlockPos> patch = new ArrayList<>();
                boolean placedAny = false;
                for (int i = 0; i < clusterSize; i++) {
                    Kind kind = chooseRemaining(remaining, kinds);
                    if (kind == null) break;
                    FarmDebrisPlacementRules.Surface target = patch.isEmpty()
                            ? randomSurface(kind) : nearbySurface(kind, patch, connected);
                    if (target == null && !patch.isEmpty()) target = randomSurface(kind);
                    if (target != null && place(target, kind)) {
                        patch.add(target.place());
                        remaining.computeIfPresent(kind, (ignored, count) -> Math.max(0, count - 1));
                        placedAny = true;
                    } else failures++;
                }
                if (placedAny) failures = 0;
            }
        }

        private List<Kind> expandedKinds(EnumMap<Kind, Integer> remaining, Kind... kinds) {
            List<Kind> result = new ArrayList<>();
            for (Kind kind : kinds) {
                for (int i = 0; i < remaining.getOrDefault(kind, 0); i++) result.add(kind);
            }
            return result;
        }

        @Nullable
        private Kind chooseRemaining(EnumMap<Kind, Integer> remaining, Kind... kinds) {
            int total = remainingTotal(remaining, kinds);
            if (total <= 0) return null;
            int roll = random.nextInt(total);
            for (Kind kind : kinds) {
                roll -= remaining.getOrDefault(kind, 0);
                if (roll < 0) return kind;
            }
            return null;
        }

        private int remainingTotal(EnumMap<Kind, Integer> remaining, Kind... kinds) {
            int total = 0;
            for (Kind kind : kinds) total += remaining.getOrDefault(kind, 0);
            return total;
        }

        @Nullable
        private FarmDebrisPlacementRules.Surface randomSurface(Kind kind) {
            double maxRate = maxRate(kind);
            if (maxRate <= 0.0D) return null;
            for (int attempt = 0; attempt < RANDOM_PICK_ATTEMPTS; attempt++) {
                FarmDebrisPlacementRules.Surface candidate = available.get(random.nextInt(available.size()));
                if (accept(candidate, kind, maxRate)) return candidate;
            }
            return null;
        }

        private double maxRate(Kind kind) {
            return maxRates.getOrDefault(kind, 0.0D);
        }

        @Nullable
        private FarmDebrisPlacementRules.Surface nearbySurface(Kind kind, List<BlockPos> patch, boolean connected) {
            double maxRate = maxRate(kind);
            for (int attempt = 0; attempt < NEAR_PICK_ATTEMPTS; attempt++) {
                BlockPos anchor = patch.get(random.nextInt(patch.size()));
                int dx;
                int dz;
                if (connected || random.nextDouble() < 0.76D) {
                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    dx = direction.getStepX();
                    dz = direction.getStepZ();
                } else {
                    dx = random.nextInt(7) - 3;
                    dz = random.nextInt(7) - 3;
                    if (dx == 0 && dz == 0) continue;
                }
                FarmDebrisPlacementRules.Surface candidate = surfaces.get(anchor.getX() + dx, anchor.getZ() + dz);
                if (candidate != null && accept(candidate, kind, maxRate)) return candidate;
            }
            return null;
        }

        @Nullable
        private FarmDebrisPlacementRules.Surface nearbyTreeSurface(Kind kind, List<BlockPos> grove) {
            double maxRate = maxRate(kind);
            for (int attempt = 0; attempt < NEAR_PICK_ATTEMPTS * 2; attempt++) {
                BlockPos anchor = grove.get(random.nextInt(grove.size()));
                int dx = random.nextInt(21) - 10;
                int dz = random.nextInt(21) - 10;
                int distance = dx * dx + dz * dz;
                if (distance < 12 || distance > 100) continue;
                FarmDebrisPlacementRules.Surface candidate = surfaces.get(anchor.getX() + dx, anchor.getZ() + dz);
                if (candidate == null || !accept(candidate, kind, maxRate)) continue;
                boolean tooClose = false;
                for (BlockPos root : grove) {
                    int rootDx = root.getX() - candidate.place().getX();
                    int rootDz = root.getZ() - candidate.place().getZ();
                    if (rootDx * rootDx + rootDz * rootDz < 12) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) return candidate;
            }
            return randomSurface(kind);
        }

        @Nullable
        private FarmDebrisPlacementRules.Surface nearbyBushSurface(List<BlockPos> grove) {
            double maxRate = maxRate(Kind.LARGE_BUSH);
            for (int attempt = 0; attempt < NEAR_PICK_ATTEMPTS; attempt++) {
                BlockPos anchor = grove.get(random.nextInt(grove.size()));
                int dx = random.nextInt(17) - 8;
                int dz = random.nextInt(17) - 8;
                int distance = dx * dx + dz * dz;
                if (distance < 25 || distance > 64) continue;
                FarmDebrisPlacementRules.Surface candidate = surfaces.get(anchor.getX() + dx, anchor.getZ() + dz);
                if (candidate != null && accept(candidate, Kind.LARGE_BUSH, maxRate)) return candidate;
            }
            return randomSurface(Kind.LARGE_BUSH);
        }

        private boolean accept(FarmDebrisPlacementRules.Surface surface, Kind kind, double maxRate) {
            BlockPos place = surface.place();
            int margin = kind.needsWideProtection() ? 2 : 0;
            if (!zone.contains(farm, place.getX(), place.getZ())
                    || FarmInstanceInitializer.isNearProtected(farm, place.getX(), place.getZ(), margin)
                    || !isInitialOpen(level, place)) return false;
            double rate = zone.effectiveRate(farm, kind, surface.groundKind());
            if (rate <= 0.0D || maxRate <= 0.0D || random.nextDouble() >= rate / maxRate) return false;
            return kind != Kind.LARGE_BUSH || canPlaceLargeBush(place);
        }

        private boolean canPlaceLargeBush(BlockPos main) {
            for (int[] offset : BerryBushBlock.CELL_OFFSETS) {
                BlockPos cell = main.offset(offset[0], offset[1], offset[2]);
                if (!farm.contains(cell)
                        || FarmInstanceInitializer.isNearProtected(farm, cell.getX(), cell.getZ(), 0)
                        || !isInitialOpen(level, cell)) return false;
                if (offset[1] == 0) {
                    FarmDebrisPlacementRules.Surface surface = surfaces.get(cell.getX(), cell.getZ());
                    if (surface == null || surface.place().getY() != main.getY()) return false;
                }
            }
            return true;
        }

        private boolean place(FarmDebrisPlacementRules.Surface surface, Kind kind) {
            BlockPos place = surface.place();
            if (kind == Kind.LARGE_BUSH) {
                for (int[] offset : BerryBushBlock.CELL_OFFSETS) {
                    clearInitialCover(level, place.offset(offset[0], offset[1], offset[2]));
                }
            } else clearInitialCover(level, place);
            int season = Math.clamp(StardewTimeManager.get().getCurrentSeason(), 0, 3);
            boolean placed = switch (kind) {
                case OAK -> PrefabTreeManager.tryPlaceRandomVariant(level, place, WildTrees.OAK);
                case MAPLE -> PrefabTreeManager.tryPlaceRandomVariant(level, place, WildTrees.MAPLE);
                case PINE -> PrefabTreeManager.tryPlaceRandomVariant(level, place, WildTrees.PINE);
                case WEEDS -> {
                    level.setBlock(place, ModBlocks.WILD_WEEDS.get().defaultBlockState()
                                    .setValue(WildWeedsBlock.SEASON, season)
                                    .setValue(WildWeedsBlock.VARIANT, random.nextInt(3)), INITIAL_PLACE_FLAGS);
                    yield true;
                }
                case STONES -> {
                    Block stone = random.nextBoolean() ? ModBlocks.MINE_STONE_343.get() : ModBlocks.MINE_STONE_450.get();
                    level.setBlock(place, stone.defaultBlockState(), INITIAL_PLACE_FLAGS);
                    yield true;
                }
                case TWIGS -> {
                    Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    level.setBlock(place, ModBlocks.FARM_TWIG.get().defaultBlockState()
                            .setValue(FarmTwigBlock.VARIANT, random.nextInt(2))
                            .setValue(FarmTwigBlock.FACING, facing), INITIAL_PLACE_FLAGS);
                    yield true;
                }
                case PASTURE, BLUE_PASTURE -> {
                    Block grass = kind == Kind.BLUE_PASTURE
                            ? ModBlocks.BLUE_PASTURE_GRASS.get() : ModBlocks.PASTURE_GRASS.get();
                    BlockState state = grass.defaultBlockState()
                            .setValue(PastureGrassBlock.VARIANT,
                                    random.nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT))
                            .setValue(PastureGrassBlock.CLUMPS, 3);
                    if (!state.canSurvive(level, place)) yield false;
                    level.setBlock(place, state, INITIAL_PLACE_FLAGS);
                    yield true;
                }
                case SAPLING -> {
                    WildTrees.Def[] definitions = {WildTrees.OAK, WildTrees.MAPLE, WildTrees.PINE};
                    WildTrees.Def definition = definitions[random.nextInt(definitions.length)];
                    BlockState state = (random.nextBoolean()
                            ? definition.sapling0().get() : definition.sapling1().get()).defaultBlockState();
                    if (!state.canSurvive(level, place)) yield false;
                    level.setBlock(place, state, INITIAL_PLACE_FLAGS);
                    yield true;
                }
                case LARGE_BUSH -> {
                    BerryBushBlock block = (BerryBushBlock) ModBlocks.BERRY_BUSH.get();
                    BlockState state = block.defaultBlockState();
                    level.setBlock(place, state, INITIAL_PLACE_FLAGS);
                    block.setPlacedBy(level, place, state, null, ItemStack.EMPTY);
                    yield true;
                }
                case SMALL_BUSH -> {
                    level.setBlock(place, ModBlocks.SMALL_BUSH.get().defaultBlockState(), INITIAL_PLACE_FLAGS);
                    yield true;
                }
            };
            if (placed) result.placed(kind);
            return placed;
        }
    }

    private static boolean isInitialOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || isInitialCover(state);
    }

    private static boolean isInitialCover(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH);
    }

    private static void clearInitialCover(ServerLevel level, BlockPos pos) {
        if (isInitialCover(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), INITIAL_PLACE_FLAGS);
        }
    }

    /** One terrain scan per farm; brush strokes never repeat the Y search. */
    private static final class SurfaceIndex {
        private final FarmInstance farm;
        private final FarmDebrisPlacementRules.Surface[][] surfaces;

        private SurfaceIndex(ServerLevel level, FarmInstance farm, List<Zone> zones) {
            this.farm = farm;
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            surfaces = new FarmDebrisPlacementRules.Surface[
                    max.getX() - min.getX() + 1][max.getZ() - min.getZ() + 1];
            int scanMinX = max.getX();
            int scanMaxX = min.getX();
            int scanMinZ = max.getZ();
            int scanMaxZ = min.getZ();
            BlockPos origin = farm.getOrigin();
            for (Zone zone : zones) {
                scanMinX = Math.min(scanMinX, origin.getX() + zone.minX());
                scanMaxX = Math.max(scanMaxX, origin.getX() + zone.maxX());
                scanMinZ = Math.min(scanMinZ, origin.getZ() + zone.minZ());
                scanMaxZ = Math.max(scanMaxZ, origin.getZ() + zone.maxZ());
            }
            scanMinX = Math.max(scanMinX, min.getX());
            scanMaxX = Math.min(scanMaxX, max.getX());
            scanMinZ = Math.max(scanMinZ, min.getZ());
            scanMaxZ = Math.min(scanMaxZ, max.getZ());
            for (int x = scanMinX; x <= scanMaxX; x++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    surfaces[x - min.getX()][z - min.getZ()] = findInitialSurface(level, farm, x, z);
                }
            }
        }

        @Nullable
        private static FarmDebrisPlacementRules.Surface findInitialSurface(
                ServerLevel level, FarmInstance farm, int x, int z
        ) {
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            for (int y = max.getY(); y >= min.getY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) return null;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || isInitialCover(state) || isExistingEcology(state)) continue;
                if (!FarmDebrisPlacementRules.isBareDebrisGround(state)
                        || FarmDebrisPlacementRules.isPlayerFloor(state)) return null;
                FarmDebrisPlacementRules.GroundKind kind = FarmDebrisPlacementRules.groundKind(state);
                if (kind == FarmDebrisPlacementRules.GroundKind.OTHER) return null;
                BlockPos place = pos.above();
                return farm.contains(place)
                        ? new FarmDebrisPlacementRules.Surface(pos.immutable(), place.immutable(), kind) : null;
            }
            return null;
        }

        private static boolean isExistingEcology(BlockState state) {
            Block block = state.getBlock();
            return state.is(BlockTags.LEAVES)
                    || WildTrees.findByAnyPart(state) != null
                    || existingKind(state) != null
                    || (block == ModBlocks.BERRY_BUSH.get()
                    && state.getValue(BerryBushBlock.PART) == BerryBushBlock.Part.EXTENSION);
        }

        private List<FarmDebrisPlacementRules.Surface> availableIn(Zone zone, BlockPos origin) {
            List<FarmDebrisPlacementRules.Surface> result = new ArrayList<>();
            int minX = Math.max(farm.getFarmBoundsMin().getX(), origin.getX() + zone.minX());
            int maxX = Math.min(farm.getFarmBoundsMax().getX(), origin.getX() + zone.maxX());
            int minZ = Math.max(farm.getFarmBoundsMin().getZ(), origin.getZ() + zone.minZ());
            int maxZ = Math.min(farm.getFarmBoundsMax().getZ(), origin.getZ() + zone.maxZ());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    FarmDebrisPlacementRules.Surface surface = get(x, z);
                    if (surface != null) result.add(surface);
                }
            }
            return result;
        }

        @Nullable
        private FarmDebrisPlacementRules.Surface get(int x, int z) {
            BlockPos min = farm.getFarmBoundsMin();
            int localX = x - min.getX();
            int localZ = z - min.getZ();
            if (localX < 0 || localX >= surfaces.length
                    || localZ < 0 || localZ >= surfaces[localX].length) return null;
            return surfaces[localX][localZ];
        }
    }
}
