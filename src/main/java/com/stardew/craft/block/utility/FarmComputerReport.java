package com.stardew.craft.block.utility;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.blockentity.MushroomBoxBlockEntity;
import com.stardew.craft.blockentity.UtilityMachineInfo;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.farm.FarmCaveChoice;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.UUID;

public record FarmComputerReport(
        String farmName,
        int hay,
        int hayCapacity,
        int totalCrops,
        int openHoeDirt,
        int cropsReady,
        int greenhouseCropsReady,
        boolean hasGreenhouse,
        int unwateredCrops,
        int forageItems,
        int machinesReady,
        boolean farmCaveReady
) {
    private static final int GREENHOUSE_SCAN_SIZE = 64;

    public static FarmComputerReport create(ServerLevel level, BlockPos computerPos, ServerPlayer player) {
        FarmInstance farm = FarmAreaResolver.getFarmAt(computerPos);
        if (farm == null) {
            farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        }
        UUID owner = FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
        if (owner == null) {
            owner = player.getUUID();
        }

        Counts counts = new Counts();
        String farmName = "";
        boolean hasGreenhouse = false;
        if (farm != null) {
            farmName = farm.getFarmName();
            scan(level, farm.getFarmBoundsMin(), farm.getFarmBoundsMax(), counts, farm, false);
            hasGreenhouse = scanGreenhouse(level, owner, counts);
        } else {
            scan(level, computerPos.offset(-32, -8, -32), computerPos.offset(32, 8, 32), counts, null, false);
        }

        AnimalWorldData animalData = AnimalWorldData.get(level);
        return new FarmComputerReport(
                farmName,
                animalData.getHayAmount(owner),
                animalData.getHayCapacity(owner),
                counts.totalCrops,
                counts.openHoeDirt,
                counts.cropsReady,
                counts.greenhouseCropsReady,
                hasGreenhouse,
                counts.unwateredCrops,
                counts.forageItems,
                counts.machinesReady,
                counts.farmCaveReady
        );
    }

    public void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenObjectDialoguePayload(toDialogue()));
    }

    public Component toDialogue() {
        Component report = Component.empty();
        if (farmName == null || farmName.isBlank()) {
            report = report.copy().append(Component.translatable("stardewcraft.farm_computer.intro.generic"));
        } else {
            report = report.copy().append(Component.translatable("stardewcraft.farm_computer.intro.farm", farmName));
        }
        report = report.copy()
                .append(Component.literal("^--------------^"))
                .append(Component.translatable("stardewcraft.farm_computer.hay", hay, hayCapacity))
                .append(Component.literal(" ^"))
                .append(Component.translatable("stardewcraft.farm_computer.total_crops", totalCrops))
                .append(Component.literal("  ^"))
                .append(Component.translatable("stardewcraft.farm_computer.crops_ready", cropsReady))
                .append(Component.literal("  ^"))
                .append(Component.translatable("stardewcraft.farm_computer.crops_unwatered", unwateredCrops))
                .append(Component.literal("  ^"));
        if (hasGreenhouse) {
            report = report.copy()
                    .append(Component.translatable("stardewcraft.farm_computer.greenhouse_crops_ready", greenhouseCropsReady))
                    .append(Component.literal("  ^"));
        }
        return report.copy()
                .append(Component.translatable("stardewcraft.farm_computer.open_hoe_dirt", openHoeDirt))
                .append(Component.literal("  ^"))
                .append(Component.translatable("stardewcraft.farm_computer.forage", forageItems))
                .append(Component.literal("  ^"))
                .append(Component.translatable("stardewcraft.farm_computer.machines_ready", machinesReady))
                .append(Component.literal("  ^"))
                .append(Component.translatable("stardewcraft.farm_computer.farm_cave",
                        Component.translatable(farmCaveReady ? "stardewcraft.ui.yes" : "stardewcraft.ui.no")));
    }

    private static boolean scanGreenhouse(ServerLevel level, UUID owner, Counts counts) {
        PlayerInteriorAllocator allocator = PlayerInteriorAllocator.get(level);
        if (!allocator.getPlayersWithGreenhouse().contains(owner)) {
            return false;
        }
        BlockPos origin = allocator.getGreenhouseOrigin(owner);
        scan(level, origin, origin.offset(GREENHOUSE_SCAN_SIZE - 1, GREENHOUSE_SCAN_SIZE - 1, GREENHOUSE_SCAN_SIZE - 1),
                counts, null, true);
        return true;
    }

    private static void scan(ServerLevel level, BlockPos min, BlockPos max, Counts counts,
                             FarmInstance farm, boolean greenhouseOnly) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || isUpperHalf(state)) {
                        continue;
                    }

                    StardewCropState crop =
                            StardewCropRuntime.inspect(level, cursor);
                    if (crop != null && crop.root().equals(cursor)) {
                        if (greenhouseOnly) {
                            if (crop.mature()) {
                                counts.greenhouseCropsReady++;
                            }
                        } else {
                            counts.totalCrops++;
                            if (crop.mature()) {
                                counts.cropsReady++;
                            }
                            if (isUnwateredCrop(level, crop)) {
                                counts.unwateredCrops++;
                            }
                        }
                    } else if (!greenhouseOnly && state.getBlock() instanceof FarmBlock && level.getBlockState(cursor.above()).isAir()) {
                        counts.openHoeDirt++;
                    } else if (!greenhouseOnly && state.getBlock() instanceof ForageBlock) {
                        counts.forageItems++;
                    }

                    BlockEntity blockEntity = level.getBlockEntity(cursor);
                    if (blockEntity != null) {
                        if (isMachineReady(blockEntity)) {
                            counts.machinesReady++;
                        }
                        if (!greenhouseOnly && isFarmCaveReadyBlock(blockEntity, farm)) {
                            counts.farmCaveReady = true;
                        }
                    }
                }
            }
        }
    }

    private static boolean isUpperHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean isUnwateredCrop(
            ServerLevel level,
            StardewCropState crop
    ) {
        return !crop.soilPositions().isEmpty()
                && crop.soilPositions().stream()
                .map(level::getBlockState)
                .filter(soil -> soil.getBlock() instanceof FarmBlock)
                .anyMatch(soil -> soil.getValue(FarmBlock.MOISTURE) <= 0);
    }

    private static boolean isMachineReady(BlockEntity blockEntity) {
        if (blockEntity instanceof UtilityMachineInfo info) {
            return info.isReadyForDisplay();
        }
        try {
            Method method = blockEntity.getClass().getMethod("isReady");
            if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                return Boolean.TRUE.equals(method.invoke(blockEntity));
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isFarmCaveReadyBlock(BlockEntity blockEntity, FarmInstance farm) {
        return farm != null
                && farm.getCaveChoice() == FarmCaveChoice.MUSHROOMS
                && blockEntity instanceof MushroomBoxBlockEntity box
                && box.isReady()
                && isInsideFarmCave(blockEntity.getBlockPos(), farm);
    }

    private static boolean isInsideFarmCave(BlockPos pos, FarmInstance farm) {
        var layout = farm.getFarmLayout();
        if (layout == null || layout.caveClearBox() == null) {
            return false;
        }
        BlockPos min = farm.getOrigin().offset(layout.caveClearBox().min());
        BlockPos max = farm.getOrigin().offset(layout.caveClearBox().max());
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static final class Counts {
        int totalCrops;
        int openHoeDirt;
        int cropsReady;
        int greenhouseCropsReady;
        int unwateredCrops;
        int forageItems;
        int machinesReady;
        boolean farmCaveReady;
    }
}
