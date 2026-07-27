package com.stardew.craft.blockentity;

import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.WizardBuildingBlock;
import com.stardew.craft.block.utility.WizardBuildingKind;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Inventory and original-style harvesting runtime shared by the Wizard buildings. */
@SuppressWarnings("null")
public final class WizardBuildingBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements Container, MenuProvider, GeoBlockEntity {
    private static final int SLOT_COUNT = 36;
    private static final int HARVEST_RADIUS = 8;
    private static final int MAX_HARVESTERS = 3;
    private static final int CHECK_INTERVAL = 20;
    private static final int RAISIN_BUFF_DAYS = 7;
    private static final int[] COMMON_JUNIMO_COLORS = {
            0x32CD32, 0xFFA500, 0x90EE90, 0xD2B48C,
            0xADFF2F, 0x7CFC00, 0x98FB98, 0x40E0D0
    };
    private static final int[] RARE_JUNIMO_COLORS = {
            0xFF0000, 0xDAA520, 0xFFFF00, 0x00FF00,
            0x00FFB4, 0x0064FF, 0x9370DB, 0xFA8072
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    private int raisinDaysLeft;
    private int lastProcessedDay = Integer.MIN_VALUE;
    private boolean goldClockEnabled = true;

    public WizardBuildingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIZARD_BUILDING.get(), pos, state);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean isGoldClockEnabled() {
        return goldClockEnabled;
    }

    public void toggleGoldClock(net.minecraft.server.level.ServerPlayer player) {
        if (!kind().isGoldClock() || level == null) {
            return;
        }
        goldClockEnabled = !goldClockEnabled;
        syncGoldClockFarmState();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.playSound(null, worldPosition, ModSounds.YOBA.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(goldClockEnabled
                ? "message.stardewcraft.gold_clock.on"
                : "message.stardewcraft.gold_clock.off"), true);
    }

    public void syncGoldClockFarmState() {
        if (!kind().isGoldClock() || owner == null) {
            return;
        }
        com.stardew.craft.farm.FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get()
                .getFarmForPlayer(owner);
        if (farm != null) {
            farm.setGoldClockState(true, goldClockEnabled);
            com.stardew.craft.farm.FarmInstanceRegistry.get().setDirty();
        }
    }

    public WizardBuildingKind kind() {
        return getBlockState().getBlock() instanceof WizardBuildingBlock building
                ? building.kind() : WizardBuildingKind.JUNIMO_HUT;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WizardBuildingBlockEntity hut) {
        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        hut.processNewDay();
        List<JunimoEntity> workers = hut.harvesters();
        if (!hut.canHarvestNow()) {
            BlockPos entrance = hut.findEntrance();
            for (JunimoEntity worker : workers) {
                if (!worker.isReturningToHarvestHut()) {
                    worker.returnToHarvestHut(pos, entrance);
                }
            }
            return;
        }

        Set<BlockPos> reserved = new HashSet<>();
        for (JunimoEntity worker : workers) {
            BlockPos target = worker.getHarvestTargetPos();
            if (target != null && !worker.isReturningToHarvestHut()) {
                reserved.add(target);
            }
        }
        for (JunimoEntity worker : workers) {
            if (worker.getHarvestTargetPos() == null) {
                HarvestTarget target = hut.findHarvestTarget(reserved, worker);
                if (target != null) {
                    reserved.add(target.cropPos());
                    worker.assignHarvestTarget(pos, target.cropPos(), target.approachPos());
                }
            }
        }
        if (workers.size() < MAX_HARVESTERS) {
            hut.spawnHarvester(reserved, hut.firstUnusedWorkerNumber(workers));
        }
    }

    public static void onHarvesterArrived(JunimoEntity junimo) {
        if (!(junimo.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos hutPos = junimo.getHarvestHutPos();
        BlockPos targetPos = junimo.getHarvestTargetPos();
        if (hutPos == null || !(level.getBlockEntity(hutPos) instanceof WizardBuildingBlockEntity hut)) {
            junimo.clearHarvestAssignment();
            junimo.startFadeOut();
            return;
        }
        if (junimo.isReturningToHarvestHut() || !hut.canHarvestNow()) {
            for (ItemStack harvested : junimo.takeCarriedHarvest()) {
                hut.insertHarvest(harvested);
            }
            level.playSound(null, junimo.blockPosition(), ModSounds.TINY_WHIP.get(),
                    SoundSource.NEUTRAL, 0.8F, 1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    junimo.getX(), junimo.getY() + 0.6D, junimo.getZ(),
                    4, 0.15D, 0.25D, 0.15D, 0.0D);
            junimo.clearHarvestAssignment();
            junimo.startFadeOut();
            return;
        }

        List<ItemStack> harvested = new ArrayList<>();
        ItemStack primary = ItemStack.EMPTY;
        if (targetPos != null) {
            var result = StardewCropRuntime.harvestForAutomation(
                    level, targetPos, hut.ownerFarmingLevel(), harvested::add);
            if (result.harvested() && !harvested.isEmpty()) {
                primary = harvested.getFirst().copy();
            }
            if (!primary.isEmpty() && hut.raisinDaysLeft > 0
                    && level.random.nextDouble() < 0.2) {
                harvested.add(primary.copyWithCount(1));
            }
        }

        if (!primary.isEmpty()) {
            level.playSound(null, junimo.blockPosition(), ModSounds.HARVEST.get(),
                    SoundSource.NEUTRAL, 0.75F, 1.05F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, primary.copyWithCount(1)),
                    junimo.getX(), junimo.getY() + 0.9D, junimo.getZ(),
                    harvested.size() > 1 ? 10 : 6, 0.18D, 0.25D, 0.18D, 0.04D);
            junimo.beginCarryingHarvest(hutPos, hut.findEntrance(), harvested);
        } else {
            junimo.returnToHarvestHut(hutPos, hut.findEntrance());
        }
    }

    private void processNewDay() {
        int day = StardewTimeManager.get().getAbsoluteDay();
        if (lastProcessedDay == Integer.MIN_VALUE) {
            lastProcessedDay = day;
            return;
        }
        if (day == lastProcessedDay) {
            return;
        }
        lastProcessedDay = day;
        if (raisinDaysLeft > 0) {
            raisinDaysLeft--;
        }
        if (raisinDaysLeft == 0 && StardewTimeManager.get().getCurrentSeason() != 3
                && consumeOne(ModItems.RAISINS.get())) {
            raisinDaysLeft = RAISIN_BUFF_DAYS;
        }
        setChanged();
    }

    private boolean canHarvestNow() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        int time = StardewTimeManager.get().getCurrentTime();
        return StardewTimeManager.get().getCurrentSeason() != 3
                && !WeatherManager.isRaining(serverLevel)
                && time >= 360 && time <= 1140;
    }

    @Nullable
    private HarvestTarget findHarvestTarget(Set<BlockPos> reserved, JunimoEntity worker) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(
                worldPosition.offset(-HARVEST_RADIUS, -3, -HARVEST_RADIUS),
                worldPosition.offset(HARVEST_RADIUS, 3, HARVEST_RADIUS))) {
            BlockPos pos = cursor.immutable();
            if (reserved.contains(pos)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(pos);
            StardewCropState crop = StardewCropRuntime.inspect(serverLevel, pos);
            if (crop == null
                    || crop.part() != StardewCropState.Part.ROOT
                    || !crop.mature()) {
                continue;
            }
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                continue;
            }
            UUID cropOwner = com.stardew.craft.core.FarmAreaResolver.getOwnerAt(pos);
            if (owner != null && cropOwner != null
                    && !com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .areFarmmates(owner, cropOwner)) {
                continue;
            }
            if (!(state.getBlock() instanceof StardewCropBlock)
                    || !StardewCropBlock.isPlayerPlacedDecorative(
                            serverLevel, pos, state)) {
                candidates.add(pos);
            }
        }
        while (!candidates.isEmpty()) {
            BlockPos cropPos = candidates.remove(serverLevel.random.nextInt(candidates.size()));
            BlockPos approachPos = findReachableApproach(worker, cropPos);
            if (approachPos != null) {
                return new HarvestTarget(cropPos, approachPos);
            }
        }
        return null;
    }

    @Nullable
    private JunimoEntity spawnHarvester(Set<BlockPos> reserved, int workerNumber) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), serverLevel);
        BlockPos entrance = findEntrance();
        junimo.setPos(entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5);
        // Navigation needs a live entity/world context. Testing canReach before
        // addFreshEntity made every candidate fail and prevented all workers from spawning.
        if (!serverLevel.addFreshEntity(junimo)) {
            return null;
        }
        junimo.setOnGround(true);
        HarvestTarget target = findHarvestTarget(reserved, junimo);
        if (target == null) {
            junimo.discard();
            return null;
        }
        JunimoAppearance appearance = chooseAppearance(serverLevel.random, workerNumber);
        junimo.setJunimoColor(appearance.color());
        junimo.setPrismatic(appearance.prismatic());
        junimo.setHarvestWorkerNumber(workerNumber);
        junimo.assignHarvestTarget(worldPosition, target.cropPos(), target.approachPos());
        serverLevel.playSound(null, entrance, ModSounds.TINY_WHIP.get(),
                SoundSource.NEUTRAL, 0.8F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                entrance.getX() + 0.5D, entrance.getY() + 0.6D, entrance.getZ() + 0.5D,
                4, 0.15D, 0.2D, 0.15D, 0.0D);
        return junimo;
    }

    @Nullable
    private BlockPos findReachableApproach(JunimoEntity worker, BlockPos cropPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) > 1) {
                        continue;
                    }
                    BlockPos candidate = cropPos.offset(dx, dy, dz);
                    if (canJunimoStand(serverLevel, worker, candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos -> worker.distanceToSqr(
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)));
        for (BlockPos candidate : candidates) {
            Path path = worker.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static boolean canJunimoStand(ServerLevel level, JunimoEntity worker, BlockPos feetPos) {
        if (!level.isLoaded(feetPos) || level.getBlockState(feetPos.below())
                .getCollisionShape(level, feetPos.below()).isEmpty()) {
            return false;
        }
        double halfWidth = worker.getBbWidth() * 0.5D;
        AABB body = new AABB(
                feetPos.getX() + 0.5D - halfWidth, feetPos.getY(), feetPos.getZ() + 0.5D - halfWidth,
                feetPos.getX() + 0.5D + halfWidth, feetPos.getY() + worker.getBbHeight(), feetPos.getZ() + 0.5D + halfWidth
        ).deflate(1.0E-7D);
        return level.noCollision(worker, body);
    }

    private BlockPos findEntrance() {
        Direction facing = getBlockState().hasProperty(MapDecorStaticBlock.FACING)
                ? getBlockState().getValue(MapDecorStaticBlock.FACING)
                : Direction.NORTH;
        // Model-relative north-east exit. For the north-facing model this is
        // two blocks east and two north, just outside the model's north-east corner.
        BlockPos preferred = worldPosition.relative(facing.getClockWise(), 2).relative(facing, 2);
        if (isOpen(preferred)) {
            return preferred;
        }
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = preferred.offset(dx, 0, dz);
                    if (isOpen(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return worldPosition.above();
    }

    private boolean isOpen(BlockPos pos) {
        return level != null
                && level.isEmptyBlock(pos)
                && level.isEmptyBlock(pos.above())
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private int firstUnusedWorkerNumber(List<JunimoEntity> workers) {
        for (int candidate = 0; candidate < MAX_HARVESTERS; candidate++) {
            int number = candidate;
            if (workers.stream().noneMatch(worker -> worker.getHarvestWorkerNumber() == number)) {
                return candidate;
            }
        }
        return MAX_HARVESTERS - 1;
    }

    private JunimoAppearance chooseAppearance(RandomSource random, int workerNumber) {
        boolean prismatic = items.stream().anyMatch(stack -> stack.is(ModItems.PRISMATIC_SHARD.get()));
        List<Integer> gemColors = new ArrayList<>();
        for (ItemStack stack : items) {
            VanillaObjectCatalog.Entry source = VanillaObjectCatalog.resolve(stack);
            if (source == null || (source.category() != -12 && source.category() != -2)) {
                continue;
            }
            Integer color = dyeColor(stack);
            if (color != null) {
                gemColors.add(color);
            }
        }
        if (!gemColors.isEmpty()) {
            return new JunimoAppearance(gemColors.get(random.nextInt(gemColors.size())), prismatic);
        }

        long seed = worldPosition.getX() * 341873128712L
                + worldPosition.getZ() * 132897987541L + workerNumber * 777L;
        RandomSource seeded = RandomSource.create(seed);
        int[] palette = seeded.nextFloat() < 0.25F ? RARE_JUNIMO_COLORS : COMMON_JUNIMO_COLORS;
        int color = seeded.nextFloat() < 0.0025F
                ? 0xFFFFFF : palette[seeded.nextInt(palette.length)];
        return new JunimoAppearance(color, prismatic);
    }

    @Nullable
    private static Integer dyeColor(ItemStack stack) {
        if (stack.is(ModItems.EMERALD.get())) return 0x00A65A;
        if (stack.is(ModItems.AQUAMARINE.get())) return 0x7FFFD4;
        if (stack.is(ModItems.RUBY.get())) return 0xE0115F;
        if (stack.is(ModItems.AMETHYST.get())) return 0x9966CC;
        if (stack.is(ModItems.TOPAZ.get())) return 0xFFD700;
        if (stack.is(ModItems.JADE.get())) return 0x00A86B;
        if (stack.is(ModItems.DIAMOND.get())) return 0xB9F2FF;
        if (stack.is(ModItems.PRISMATIC_SHARD.get())) return 0xFFFFFF;

        Set<String> tags = FishPondQualifiedItemService.getContextTags(stack);
        if (tags.contains("color_red")) return 0xDC143C;
        if (tags.contains("color_orange")) return 0xFFA500;
        if (tags.contains("color_yellow") || tags.contains("color_gold")) return 0xFFD700;
        if (tags.contains("color_green") || tags.contains("color_jade")) return 0x00A86B;
        if (tags.contains("color_aquamarine")) return 0x7FFFD4;
        if (tags.contains("color_blue")) return 0x6495ED;
        if (tags.contains("color_purple")) return 0x9370DB;
        if (tags.contains("color_white")) return 0xFFFFFF;
        return null;
    }

    private record JunimoAppearance(int color, boolean prismatic) {
    }

    private record HarvestTarget(BlockPos cropPos, BlockPos approachPos) {
    }

    private int ownerFarmingLevel() {
        return owner == null ? 0 : PlayerDataManager.getPlayerData(owner).getSkillLevel(SkillType.FARMING);
    }

    private List<JunimoEntity> harvesters() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        AABB search = new AABB(worldPosition).inflate(HARVEST_RADIUS + 8, 6, HARVEST_RADIUS + 8);
        return serverLevel.getEntitiesOfClass(JunimoEntity.class, search,
                entity -> worldPosition.equals(entity.getHarvestHutPos()));
    }

    public void dismissHarvesters() {
        for (JunimoEntity harvester : harvesters()) {
            harvester.clearHarvestAssignment();
            harvester.startFadeOut();
        }
    }

    private boolean consumeOne(net.minecraft.world.item.Item item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(item)) {
                items.get(i).shrink(1);
                if (items.get(i).isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private void insertHarvest(ItemStack incoming) {
        if (incoming.isEmpty()) {
            return;
        }
        ItemStack remaining = incoming.copy();
        for (ItemStack stored : items) {
            if (ItemStack.isSameItemSameComponents(stored, remaining) && stored.getCount() < stored.getMaxStackSize()) {
                int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
                stored.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) {
                    setChanged();
                    return;
                }
            }
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack inserted = remaining.copy();
                inserted.setCount(moved);
                items.set(i, inserted);
                remaining.shrink(moved);
                if (remaining.isEmpty()) {
                    setChanged();
                    return;
                }
            }
        }
        if (level != null && !level.isClientSide && !remaining.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, remaining);
        }
        setChanged();
    }

    public void dropAllContents(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            Containers.dropContents(level, pos, new SimpleContainer(items.toArray(new ItemStack[0])));
            clearContent();
        }
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())));
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override
    public void clearContent() { items.clear(); setChanged(); }

    @Override
    public Component getDisplayName() { return Component.translatable("container.stardewcraft.junimo_hut"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChestMenu(MenuType.GENERIC_9x4, id, inventory, this, 4);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("RaisinDays", raisinDaysLeft);
        tag.putInt("LastProcessedDay", lastProcessedDay);
        tag.putBoolean("GoldClockEnabled", goldClockEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        raisinDaysLeft = tag.getInt("RaisinDays");
        lastProcessedDay = tag.contains("LastProcessedDay")
                ? tag.getInt("LastProcessedDay") : Integer.MIN_VALUE;
        goldClockEnabled = !tag.contains("GoldClockEnabled") || tag.getBoolean("GoldClockEnabled");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("GoldClockEnabled", goldClockEnabled);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(6.0); }
}
