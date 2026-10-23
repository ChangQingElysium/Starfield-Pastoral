// Upstream-SHA256: 73e0f2d01d31e2e1b4a085d048a3b63b93f214638b3ff328abb20101ba47d303
package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.platform.fabric.item.transfer.DeferredGardenItemStorage;
import com.stardew.craft.platform.fabric.item.transfer.TransactionalMachineAccess;
import com.stardew.craft.platform.fabric.item.transfer.TransactionalRandom;
import com.stardew.craft.platform.fabric.item.transfer.PreparedBlockLoot;
import com.stardew.craft.platform.fabric.item.transfer.NativeCropPlans;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.farming.FertilizerApplicationService;
import com.stardew.craft.item.FertilizerItem;
import com.stardew.craft.manager.FertilizerManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Persistent water and automation state; the planted crop remains the real block above the pot. */
public final class GardenPotBlockEntity extends BlockEntity
        implements UtilityAutomationAccess, AdvanceableUtility, UtilityMachineInfo, TransactionalMachineAccess {
    private final DeferredGardenItemStorage<GardenState> transactionalItems = new DeferredGardenItemStorage<>(
            new DeferredGardenItemStorage.Garden<>() {
                @Override public boolean available() {
                    return level instanceof ServerLevel && !isRemoved() && level.getBlockEntity(worldPosition) == GardenPotBlockEntity.this;
                }
                @Override public GardenState read() { return readTransactionalGarden(); }
                @Override public boolean matches(GardenState baseline) { return matchesTransactionalGarden(baseline); }
                @Override public ItemStack preview(GardenState state) {
                    if (!state.queue.isEmpty()) return state.queue.getFirst().copy();
                    var harvest = planTransactionalHarvest(state, null);
                    return harvest.isEmpty() || harvest.get().items.isEmpty() ? ItemStack.EMPTY : harvest.get().items.getFirst().copy();
                }
                @Override public java.util.Optional<DeferredGardenItemStorage.Step<GardenState>> insert(GardenState state,
                        ItemStack offered, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                    return planTransactionalGardenInsertion(state, offered, transaction);
                }
                @Override public java.util.Optional<DeferredGardenItemStorage.Step<GardenState>> extract(GardenState state,
                        net.fabricmc.fabric.api.transfer.v1.item.ItemVariant wanted, long amount,
                        net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
                    return planTransactionalGardenExtraction(state, wanted, amount, transaction);
                }
            });
    @Override public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
    stardewcraft$automationStorage() { return transactionalItems; }

    private record GardenState(Level level, BlockState soil, BlockState crop, BlockState upper,
            FertilizerType fertilizer, Object growth, Object tea, Object addon, long minute, int season,
            List<ItemStack> queue, boolean harvested) {
        private GardenState { queue = queue.stream().map(ItemStack::copy).toList(); }
        private GardenState next(BlockState crop, BlockState upper, FertilizerType fertilizer, List<ItemStack> queue, boolean harvested) {
            return new GardenState(level, soil, crop, upper, fertilizer, growth, tea, addon, minute, season, queue, harvested);
        }
    }
    private record GardenHarvest(GardenState state, List<ItemStack> items,
            java.util.function.BooleanSupplier valid, Runnable commit) {}
    private GardenState readTransactionalGarden() {
        ServerLevel server = (ServerLevel) level;
        BlockState crop = cropState();
        Object tea = crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock
                ? com.stardew.craft.manager.TeaBushManager.get(server).stardewcraft$automationSnapshot(server, cropPos()) : null;
        Object addon = !(crop.getBlock() instanceof StardewCropBlock) && StardewCropRuntime.isRegisteredBlock(crop)
                ? StardewCropRuntime.inspect(server, cropPos()) : null;
        return new GardenState(level, getBlockState(), crop, level.getBlockState(cropPos().above()),
                FertilizerManager.get(server).getFertilizer(server, worldPosition),
                com.stardew.craft.manager.CropGrowthManager.get(server).stardewcraft$automationSnapshot(server, cropPos()),
                tea, addon, TimedProductionBlockEntity.getCurrentAbsMinute(), StardewTimeManager.get().getCurrentSeason(), outputs, false);
    }
    private boolean matchesTransactionalGarden(GardenState previous) {
        GardenState current = readTransactionalGarden();
        if (previous.level != current.level || previous.soil != current.soil || previous.crop != current.crop || previous.upper != current.upper
                || previous.fertilizer != current.fertilizer || !java.util.Objects.equals(previous.growth, current.growth)
                || !java.util.Objects.equals(previous.tea, current.tea) || !java.util.Objects.equals(previous.addon, current.addon)
                || previous.minute != current.minute || previous.season != current.season || previous.queue.size() != current.queue.size()) return false;
        for (int i = 0; i < previous.queue.size(); i++) if (!ItemStack.matches(previous.queue.get(i), current.queue.get(i))) return false;
        return true;
    }
    private java.util.Optional<DeferredGardenItemStorage.Step<GardenState>> planTransactionalGardenInsertion(
            GardenState state, ItemStack offered, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        ServerLevel server = (ServerLevel) level;
        if (offered.getItem() instanceof BoneMealItem) {
            // The core runtime deliberately rejects bonemeal, matching canBoneMeal().
            if (state.crop.getBlock() instanceof StardewCropBlock || state.addon != null) return java.util.Optional.empty();
            if (!(state.crop.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock)) return java.util.Optional.empty();
            if (!NativeCropPlans.supported(state.crop)) {
                throw new UnsupportedOperationException("This BonemealableBlock needs a transaction planner: " + state.crop.getBlock().getClass().getName());
            }
            CropBlock crop = (CropBlock) state.crop.getBlock();
            if (crop.isMaxAge(state.crop)) return java.util.Optional.empty();
            BlockState next = NativeCropPlans.bonemeal(state.crop, TransactionalRandom.stream(server.random, transaction));
            return java.util.Optional.of(new DeferredGardenItemStorage.Step<>(state.next(next, state.upper, state.fertilizer, state.queue, false),
                    1, () -> true, () -> { server.setBlock(cropPos(), next, 2); syncToClient(); }));
        }
        if (offered.getItem() instanceof FertilizerItem item) {
            FertilizerType type = item.fertilizerType();
            boolean sprouted = state.crop.getBlock() instanceof StardewCropBlock
                    ? state.crop.getValue(StardewCropBlock.AGE) > 0
                    : state.crop.getBlock() instanceof CropBlock crop ? crop.getAge(state.crop) > 0
                    : state.addon instanceof StardewCropState addonCrop && addonCrop.visualStage() > 0;
            if (FertilizerApplicationService.checkRulesForAutomation(state.fertilizer, type, sprouted)
                    != FertilizerApplicationService.Status.APPLIED) return java.util.Optional.empty();
            return java.util.Optional.of(new DeferredGardenItemStorage.Step<>(state.next(state.crop, state.upper, type, state.queue, state.harvested),
                    1, () -> true, () -> {
                        if (!FertilizerManager.get(server).tryApplyFertilizer(server, worldPosition, type))
                            throw new IllegalStateException("Garden fertilizer violated its committed application plan");
                    }));
        }
        if (!state.crop.isAir()) return java.util.Optional.empty();
        BlockState plant;
        if (offered.is(com.stardew.craft.item.ModItems.MIXED_SEEDS.get())) {
            if (state.season == 3 && !com.stardew.craft.farming.SeasonLocationRules.seedsIgnoreSeasonsHere(server, cropPos()))
                return java.util.Optional.empty();
            plant = com.stardew.craft.item.MixedSeedsItem.pickCropStateForSeason(state.season, TransactionalRandom.stream(server.random, transaction));
        } else if (offered.is(com.stardew.craft.item.ModItems.TEA_SAPLING.get())) {
            plant = com.stardew.craft.block.nature.TeaBushBlock.isValidGround(state.soil)
                    && state.crop.canBeReplaced() && state.upper.canBeReplaced()
                    ? com.stardew.craft.block.ModBlocks.TEA_BUSH.get().defaultBlockState() : null;
        } else plant = resolvePlantState(offered);
        if (plant == null || (plant.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !state.upper.isAir())) return java.util.Optional.empty();
        BlockState placed = plant;
        BlockState upper = plant.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                ? plant.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER) : state.upper;
        return java.util.Optional.of(new DeferredGardenItemStorage.Step<>(state.next(plant, upper, state.fertilizer, state.queue, false), 1,
                () -> true, () -> {
                    server.setBlock(cropPos(), placed, Block.UPDATE_ALL);
                    server.playSound(null, cropPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    syncToClient();
                }));
    }
    private java.util.Optional<GardenHarvest> planTransactionalHarvest(GardenState state,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (state.harvested) return java.util.Optional.empty();
        ServerLevel server = (ServerLevel) level;
        List<ItemStack> produced;
        BlockState next = state.crop;
        BlockState upper = state.upper;
        java.util.function.BooleanSupplier valid = () -> true;
        Runnable commit;
        if (state.crop.getBlock() instanceof StardewCropBlock crop) {
            var random = transaction == null ? TransactionalRandom.preview(server.random) : TransactionalRandom.stream(server.random, transaction);
            var prepared = crop.stardewcraft$prepareAutomationHarvest(server, cropPos(), state.crop, 0,
                    state.fertilizer == null ? 0 : state.fertilizer.getQualityLevel(), random);
            if (prepared.isEmpty()) return java.util.Optional.empty();
            var plan = prepared.get();
            produced = plan.outputs(); next = plan.nextRoot(); valid = plan.stillValid();
            if (state.crop.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                if (next.isAir() && state.upper.getBlock() == state.crop.getBlock()) upper = next;
                else if (!next.isAir() && (state.upper.isAir() || state.upper.getBlock() == state.crop.getBlock()))
                    upper = next.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            }
            commit = () -> { plan.commit().accept(this::addOutput); syncToClient(); };
        } else if (state.crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock) {
            if (!(state.tea instanceof com.stardew.craft.manager.TeaBushManager.AutomationTeaSnapshot tea)) return java.util.Optional.empty();
            var manager = com.stardew.craft.manager.TeaBushManager.get(server);
            if (!manager.stardewcraft$isReadyForAutomation(tea)) return java.util.Optional.empty();
            produced = List.of(new ItemStack(com.stardew.craft.item.ModItems.TEA_LEAVES.get()));
            next = manager.stardewcraft$afterAutomationHarvest(state.crop, tea);
            upper = next.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            commit = () -> {
                manager.isReadyForHarvest(server, cropPos());
                if (!manager.harvest(server, cropPos())) throw new IllegalStateException("Tea bush violated its committed harvest plan");
                addOutput(new ItemStack(com.stardew.craft.item.ModItems.TEA_LEAVES.get())); syncToClient();
            };
        } else if (state.crop.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock forage) {
            ItemStack drop = forage.getAutomationDrop();
            if (drop.isEmpty()) return java.util.Optional.empty();
            ItemStack frozenDrop = drop.copy();
            produced = List.of(frozenDrop.copy());
            next = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (state.crop.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.upper.getBlock() == state.crop.getBlock()) upper = next;
            commit = () -> { addOutput(frozenDrop.copy()); clearCrop(); syncToClient(); };
        } else if (state.crop.getBlock() instanceof CropBlock crop) {
            if (!crop.isMaxAge(state.crop)) return java.util.Optional.empty();
            if (!NativeCropPlans.supported(state.crop)) throw new UnsupportedOperationException("Addon CropBlock needs an exact transaction loot planner");
            if (transaction == null) produced = PreparedBlockLoot.preview(server, cropPos(), state.crop);
            else { var plan = PreparedBlockLoot.prepare(server, cropPos(), state.crop, transaction); produced = plan.outputs(); valid = plan.stillValid(); }
            next = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            List<ItemStack> frozen = produced.stream().map(ItemStack::copy).toList();
            commit = () -> { frozen.forEach(this::addOutput); clearCrop(); syncToClient(); };
        } else {
            if (state.addon != null) throw new UnsupportedOperationException("Addon crop runtime must provide a pure transaction harvest planner");
            return java.util.Optional.empty();
        }
        List<ItemStack> queue = new ArrayList<>();
        produced.forEach(item -> mergeTransactionalOutput(queue, item));
        return java.util.Optional.of(new GardenHarvest(state.next(next, upper, state.fertilizer, queue, true), queue, valid, commit));
    }
    private java.util.Optional<DeferredGardenItemStorage.Step<GardenState>> planTransactionalGardenExtraction(GardenState state,
            net.fabricmc.fabric.api.transfer.v1.item.ItemVariant wanted, long amount,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        var harvest = state.queue.isEmpty() ? planTransactionalHarvest(state, transaction) : java.util.Optional.<GardenHarvest>empty();
        GardenState prepared = harvest.isPresent() ? harvest.get().state : state;
        if (prepared.queue.isEmpty() || !wanted.matches(prepared.queue.getFirst())) return java.util.Optional.empty();
        int count = (int) Math.min(amount, prepared.queue.getFirst().getCount());
        ItemStack expected = prepared.queue.getFirst().copyWithCount(count);
        List<ItemStack> queue = new ArrayList<>(prepared.queue.stream().map(ItemStack::copy).toList());
        queue.getFirst().shrink(count); if (queue.getFirst().isEmpty()) queue.removeFirst();
        return java.util.Optional.of(new DeferredGardenItemStorage.Step<>(
                prepared.next(prepared.crop, prepared.upper, prepared.fertilizer, queue, prepared.harvested), count,
                harvest.isPresent() ? harvest.get().valid : () -> true,
                () -> {
                    harvest.ifPresent(plan -> plan.commit.run());
                    if (!ItemStack.matches(expected, extractAutomation(count, false)))
                        throw new IllegalStateException("Garden pot violated its committed item output reservation");
                }));
    }

    public static final int WATER_PER_WATERING = 100;
    private static final int AUTOMATION_INTERVAL = 10;

    private final List<ItemStack> outputs = new ArrayList<>();
    private final IItemHandler itemHandler = new UtilityItemHandler(this);
    private final IFluidHandler fluidHandler = new WaterHandler();
    private int waterAmount;
    private int automationTicks;

    public GardenPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GARDEN_POT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GardenPotBlockEntity pot) {
        if (++pot.automationTicks < AUTOMATION_INTERVAL) {
            return;
        }
        pot.automationTicks = 0;
        if (pot.waterAmount >= WATER_PER_WATERING) {
            pot.waterAmount -= WATER_PER_WATERING;
            pot.water();
        }
        pot.normalizePottedCropRenderState();
    }

    public IFluidHandler getAutomationFluidHandler() {
        return fluidHandler;
    }

    private void normalizePottedCropRenderState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos lowerPos = cropPos();
        BlockState lower = level.getBlockState(lowerPos);
        if (!(lower.getBlock() instanceof StardewCropBlock)) {
            return;
        }
        if (!lower.getValue(StardewCropBlock.POTTED)) {
            lower = lower.setValue(StardewCropBlock.POTTED, true);
            level.setBlock(lowerPos, lower, Block.UPDATE_ALL);
        }
        if (lower.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = lowerPos.above();
            BlockState upper = level.getBlockState(upperPos);
            if (upper.getBlock() == lower.getBlock()
                    && upper.hasProperty(StardewCropBlock.POTTED)
                    && !upper.getValue(StardewCropBlock.POTTED)) {
                level.setBlock(upperPos, upper.setValue(StardewCropBlock.POTTED, true), Block.UPDATE_ALL);
            }
        }
    }

    public boolean hasOutput() {
        return !outputs.isEmpty();
    }

    public ItemStack harvestOne() {
        if (outputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = outputs.removeFirst();
        syncToClient();
        return result;
    }

    public boolean canBoneMeal() {
        BlockState crop = cropState();
        return StardewCropRuntime.inspect(level, cropPos()) == null
                && crop.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable
                && level instanceof ServerLevel serverLevel
                && bonemealable.isValidBonemealTarget(serverLevel, cropPos(), crop);
    }

    public boolean applyBoneMeal() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState crop = cropState();
        if (StardewCropRuntime.inspect(serverLevel, cropPos()) != null
                || !(crop.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(serverLevel, cropPos(), crop)
                || !bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, cropPos(), crop)) {
            return false;
        }
        bonemealable.performBonemeal(serverLevel, serverLevel.random, cropPos(), crop);
        syncToClient();
        return true;
    }

    public boolean harvestForPlayer(ServerPlayer player, InteractionHand hand) {
        BlockPos cropPos = cropPos();
        BlockState crop = level.getBlockState(cropPos);
        if (StardewCropRuntime.inspect(level, cropPos) != null) {
            return StardewCropRuntime.harvestForPlayer(player, cropPos, hand, false, false).harvested();
        }
        if (crop.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock) {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cropPos), Direction.UP, cropPos, false);
            return crop.useWithoutItem(level, player, hit).consumesAction();
        }
        if (crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock teaBush) {
            return teaBush.interact(player, player.serverLevel(), cropPos);
        }
        if (!(crop.getBlock() instanceof CropBlock vanillaCrop) || !vanillaCrop.isMaxAge(crop)) {
            return false;
        }
        List<ItemStack> drops = Block.getDrops(crop, player.serverLevel(), cropPos, null, player, player.getItemInHand(hand));
        clearCrop();
        drops.forEach(stack -> Block.popResource(player.serverLevel(), worldPosition.above(), stack));
        player.serverLevel().playSound(null, worldPosition, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public void advanceDays(int days) {
        if (days <= 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Stardew crops are already advanced by GrowCropsPayload; this covers MC crops for F8.
        if (StardewCropRuntime.inspect(level, cropPos()) == null) {
            for (int i = 0; i < days; i++) {
                BlockState crop = cropState();
                if (!(crop.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable)
                        || !bonemealable.isValidBonemealTarget(serverLevel, cropPos(), crop)) {
                    break;
                }
                bonemealable.performBonemeal(serverLevel, serverLevel.random, cropPos(), crop);
            }
            syncToClient();
        }
    }

    public void water() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof GardenPotBlock
                && state.getValue(FarmBlock.MOISTURE) < 7) {
            level.setBlock(worldPosition, state.setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_ALL);
        }
        syncToClient();
    }

    private boolean isCropMature() {
        BlockState crop = cropState();
        StardewCropState stardew = StardewCropRuntime.inspect(level, cropPos());
        if (stardew != null) {
            return stardew.mature();
        }
        if (crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock
                && level instanceof ServerLevel serverLevel) {
            return com.stardew.craft.manager.TeaBushManager.get(serverLevel)
                    .isReadyForHarvest(serverLevel, cropPos());
        }
        return crop.getBlock() instanceof CropBlock vanillaCrop && vanillaCrop.isMaxAge(crop)
                || crop.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock;
    }

    private void harvestForAutomation() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos cropPos = cropPos();
        BlockState crop = cropState();
        StardewCropState stardew = StardewCropRuntime.inspect(serverLevel, cropPos);
        if (stardew != null) {
            // The crop runtime owns removal/regrowth. Clearing it again here destroys regrowing crops.
            StardewCropRuntime.harvestForAutomation(serverLevel, cropPos, 0, this::addOutput);
        } else if (crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock) {
            if (com.stardew.craft.manager.TeaBushManager.get(serverLevel).harvest(serverLevel, cropPos)) {
                addOutput(new ItemStack(com.stardew.craft.item.ModItems.TEA_LEAVES.get()));
            }
        } else if (crop.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock forage) {
            ItemStack output = forage.getAutomationDrop();
            if (!output.isEmpty()) {
                addOutput(output);
                clearCrop();
            }
        } else if (crop.getBlock() instanceof CropBlock vanillaCrop && vanillaCrop.isMaxAge(crop)) {
            Block.getDrops(crop, serverLevel, cropPos, null).forEach(this::addOutput);
            clearCrop();
        }
        syncToClient();
    }

    private void clearCrop() {
        if (level == null) {
            return;
        }
        BlockPos root = cropPos();
        BlockState state = level.getBlockState(root);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = root.above();
            if (level.getBlockState(upper).getBlock() == state.getBlock()) {
                level.removeBlock(upper, false);
            }
        }
        level.removeBlock(root, false);
    }

    private void addOutput(ItemStack stack) { mergeTransactionalOutput(outputs, stack); }

    private static void mergeTransactionalOutput(List<ItemStack> outputs, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        for (ItemStack existing : outputs) {
            if (ItemStack.isSameItemSameComponents(existing, remainder)) {
                int transfer = Math.min(existing.getMaxStackSize() - existing.getCount(), remainder.getCount());
                existing.grow(transfer);
                remainder.shrink(transfer);
                if (remainder.isEmpty()) {
                    return;
                }
            }
        }
        outputs.add(remainder);
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return outputs.isEmpty() ? getAutomationHarvestPreview() : outputs.getFirst();
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || level == null) {
            return stack;
        }
        if (stack.getItem() instanceof BoneMealItem) {
            if (!canBoneMeal()) {
                return stack;
            }
            if (!simulate) {
                applyBoneMeal();
            }
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        if (stack.getItem() instanceof FertilizerItem fertilizer) {
            FertilizerType type = fertilizer.fertilizerType();
            if (!(level instanceof ServerLevel serverLevel)
                    || FertilizerApplicationService.checkRulesForAutomation(
                            FertilizerManager.get(serverLevel).getFertilizer(serverLevel, worldPosition),
                            type,
                            cropSprouted()) != FertilizerApplicationService.Status.APPLIED) {
                return stack;
            }
            if (!simulate && !FertilizerManager.get(serverLevel).tryApplyFertilizer(serverLevel, worldPosition, type)) {
                return stack;
            }
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        BlockState plant = resolvePlantState(stack);
        if (plant == null || !cropState().isAir() || !hasPlantSpace(plant)) {
            return stack;
        }
        if (!simulate) {
            level.setBlock(cropPos(), plant, Block.UPDATE_ALL);
            level.playSound(null, cropPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            syncToClient();
        }
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }

    @Nullable
    private BlockState resolvePlantState(ItemStack seed) {
        if (level == null) {
            return null;
        }
        // Vanilla Crops/499 excludes GardenPot, including automated planting.
        if (seed.is(com.stardew.craft.item.ModItems.ANCIENT_FRUIT_SEEDS.get())) {
            return null;
        }
        if (seed.is(com.stardew.craft.item.ModItems.MIXED_SEEDS.get())) {
            int season = StardewTimeManager.get().getCurrentSeason();
            // 冬季随机取春夏秋作物：花盆无视季节才种得成
            if (season == 3 && !com.stardew.craft.farming.SeasonLocationRules.seedsIgnoreSeasonsHere(level, cropPos())) {
                return null;
            }
            return com.stardew.craft.item.MixedSeedsItem.pickCropStateForSeason(season, level.getRandom());
        }
        if (seed.is(com.stardew.craft.item.ModItems.TEA_SAPLING.get())
                && com.stardew.craft.block.nature.TeaBushBlock.canPlantAt(level, cropPos())) {
            return com.stardew.craft.block.ModBlocks.TEA_BUSH.get().defaultBlockState();
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof StardewCropBlock crop && crop.isSeedItem(seed)
                    && crop.canPlantAt(level, cropPos())) {
                return block.defaultBlockState().setValue(StardewCropBlock.POTTED, true);
            }
        }
        if (seed.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.getBlock() instanceof CropBlock && state.canSurvive(level, cropPos())) {
                return state;
            }
        }
        return null;
    }

    private boolean hasPlantSpace(BlockState plant) {
        if (level == null || !level.getBlockState(cropPos()).isAir()) {
            return false;
        }
        return !plant.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || level.getBlockState(cropPos().above()).isAir();
    }

    private boolean cropSprouted() {
        BlockState crop = cropState();
        StardewCropState runtime = StardewCropRuntime.inspect(level, cropPos());
        if (runtime != null) {
            return runtime.visualStage() > 0;
        }
        return crop.getBlock() instanceof CropBlock vanilla && vanilla.getAge(crop) > 0;
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (outputs.isEmpty()) {
            ItemStack preview = getAutomationHarvestPreview();
            if (preview.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (simulate) {
                return AutomationStackHelper.extractUpTo(preview, amount);
            }
            harvestForAutomation();
        }
        if (outputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = AutomationStackHelper.extractUpTo(outputs.getFirst(), amount);
        if (!simulate) {
            outputs.getFirst().shrink(extracted.getCount());
            if (outputs.getFirst().isEmpty()) {
                outputs.removeFirst();
            }
            syncToClient();
        }
        return extracted;
    }

    @Override
    public IItemHandler getAutomationItemHandler() {
        return itemHandler;
    }

    @Override
    public String getUtilityTooltipKey() {
        return "garden_pot";
    }

    @Override
    public boolean isReadyForDisplay() {
        return false;
    }

    @Override
    public boolean isWorkingForDisplay() {
        return !cropState().isAir() && !isCropMature();
    }

    @Override
    public boolean shouldShowInputInDisplay() {
        return false;
    }

    @Override
    public ItemStack getDisplayOutput() {
        return ItemStack.EMPTY;
    }

    private ItemStack getAutomationHarvestPreview() {
        if (!(level instanceof ServerLevel serverLevel) || !isCropMature()) {
            return ItemStack.EMPTY;
        }
        BlockState crop = cropState();
        if (crop.getBlock() instanceof StardewCropBlock stardewCrop) {
            return stardewCrop.getAutomationHarvestPreview(crop);
        }
        if (crop.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock forage) {
            return forage.getAutomationDrop();
        }
        if (crop.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock) {
            return new ItemStack(com.stardew.craft.item.ModItems.TEA_LEAVES.get());
        }
        if (crop.getBlock() instanceof CropBlock) {
            List<ItemStack> drops = Block.getDrops(crop, serverLevel, cropPos(), null);
            return drops.isEmpty() ? ItemStack.EMPTY : drops.getFirst();
        }
        return ItemStack.EMPTY;
    }

    public void dropStoredOutputs() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ItemStack output : outputs) {
            if (!output.isEmpty()) {
                Containers.dropItemStack(
                        level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.copy());
            }
        }
        outputs.clear();
        setChanged();
    }

    public void clearStoredOutputs() {
        outputs.clear();
        setChanged();
    }

    private BlockPos cropPos() {
        return worldPosition.above();
    }

    private BlockState cropState() {
        return level == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : level.getBlockState(cropPos());
    }

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Water", waterAmount);
        ListTag list = new ListTag();
        for (ItemStack output : outputs) {
            if (!output.isEmpty()) {
                list.add(output.save(registries));
            }
        }
        tag.put("Outputs", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterAmount = Math.max(0, Math.min(tag.getInt("Water"), WATER_PER_WATERING - 1));
        outputs.clear();
        ListTag list = tag.getList("Outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(this::addOutput);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0D, 3.0D, 0.0D);
    }

    private record WaterGuardState(Level level, BlockState block, int amount) {}

    private final class WaterHandler implements IFluidHandler,
            com.stardew.craft.platform.fabric.fluid.transfer.TransactionalFluidHandler {
        private final net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>
                transferStorage = new com.stardew.craft.platform.fabric.fluid.transfer.GardenPotWaterStorage(this,
                        () -> new WaterGuardState(level, getBlockState(), waterAmount),
                        previous -> previous instanceof WaterGuardState old && old.level == level && old.block == getBlockState()
                                && old.amount == waterAmount && level instanceof ServerLevel && !isRemoved()
                                && level.getBlockEntity(worldPosition) == GardenPotBlockEntity.this);

        @Override
        public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>
                stardewcraft$fluidStorage() {
            return transferStorage;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 && waterAmount > 0
                    ? new FluidStack(net.minecraft.world.level.material.Fluids.WATER, waterAmount)
                    : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? WATER_PER_WATERING : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.is(FluidTags.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource) || resource.isEmpty()
                    || getBlockState().getValue(FarmBlock.MOISTURE) >= 7) {
                return 0;
            }
            int accepted = Math.min(resource.getAmount(), WATER_PER_WATERING - waterAmount);
            if (action.execute() && accepted > 0) {
                waterAmount += accepted;
                if (waterAmount >= WATER_PER_WATERING) {
                    waterAmount -= WATER_PER_WATERING;
                    water();
                } else {
                    syncToClient();
                }
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
