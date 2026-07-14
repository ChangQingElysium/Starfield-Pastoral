package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewTreeData;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.tree.fruit.FruitTreeRules;
import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

public class FruitTreeBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String TAG_TYPE = "Type";
    private static final String TAG_FRUIT_COUNT = "FruitCount";
    private static final String TAG_DAYS_SINCE_MATURE = "DaysSinceMature";
    private static final String TAG_LIGHTNING_DAYS = "LightningDays";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private FruitTreeType type = FruitTreeType.CHERRY;
    private int fruitCount;
    private int daysSinceMature;
    private int lightningDays;

    public FruitTreeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FRUIT_TREE.get(), pos, state);
        if (state.getBlock() instanceof FruitTreeBlock fruitTreeBlock) {
            this.type = fruitTreeBlock.getType();
        }
    }

    public FruitTreeType getFruitTreeType() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof FruitTreeBlock fruitTreeBlock) {
            return fruitTreeBlock.getType();
        }
        return type;
    }

    public int getFruitCount() {
        return fruitCount;
    }

    public int getMaxStoredFruit() {
        StardewTreeData publicData = resolvePublicData();
        return publicData == null ? FruitTreeType.MAX_FRUIT : publicData.maxStoredProduct();
    }

    public int getDaysSinceMature() {
        return daysSinceMature;
    }

    public int getCurrentFruitQuality() {
        return getFruitQuality();
    }

    public int getLightningDays() {
        return lightningDays;
    }

    public void setNewlyMature(FruitTreeType type) {
        this.type = type;
        this.fruitCount = 0;
        this.daysSinceMature = 0;
        this.lightningDays = 0;
        syncChanged();
    }

    public void strikeByLightning(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        if (fruitCount > 0) {
            Block.popResource(level, pos, new ItemStack(Items.COAL, fruitCount));
            fruitCount = 0;
        }
        lightningDays = 4;
        syncChanged();
    }

    public void dailyUpdate(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }

        if (lightningDays > 0) {
            lightningDays--;
            if (lightningDays <= 0) {
                fruitCount = 0;
            }
        }

        daysSinceMature++;
        FruitTreeType currentType = getFruitTreeType();
        StardewTreeData publicData = resolvePublicData();
        if (FruitTreeRules.canFruitToday(level, pos, currentType, publicData)
                || (lightningDays > 0 && !FruitTreeRules.isWinterTreeHere(level, pos))) {
            int dailyProduct = publicData == null ? 1 : publicData.productCount();
            int maxStored = publicData == null ? FruitTreeType.MAX_FRUIT : publicData.maxStoredProduct();
            if (fruitCount < maxStored) {
                fruitCount = Math.min(maxStored, fruitCount + dailyProduct);
            }
        }
        syncChanged();
    }

    public boolean harvestFruit(Player player) {
        if (level == null || level.isClientSide() || fruitCount <= 0) {
            return false;
        }
        ItemStack stack = createHarvestStack(fruitCount);
        Block.popResource(level, worldPosition, stack);
        level.playSound(null, worldPosition, ModSounds.LEAFRUSTLE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        fruitCount = 0;
        syncChanged();
        return true;
    }

    public void dropStoredFruit(Level level, BlockPos pos) {
        if (fruitCount <= 0) {
            return;
        }
        ItemStack stack = createHarvestStack(fruitCount);
        Block.popResource(level, pos, stack);
        fruitCount = 0;
        setChanged();
    }

    private int getFruitQuality() {
        if (lightningDays > 0 || daysSinceMature < FruitTreeType.QUALITY_STEP_DAYS) {
            return QualityHelper.NORMAL;
        }
        if (daysSinceMature >= FruitTreeType.QUALITY_STEP_DAYS * 3) {
            return QualityHelper.IRIDIUM;
        }
        if (daysSinceMature >= FruitTreeType.QUALITY_STEP_DAYS * 2) {
            return QualityHelper.GOLD;
        }
        return QualityHelper.SILVER;
    }

    private ItemStack createHarvestStack(int count) {
        if (lightningDays > 0) {
            return new ItemStack(Items.COAL, count);
        }
        StardewTreeData publicData = resolvePublicData();
        net.minecraft.world.item.Item fruit = publicData == null
                ? getFruitTreeType().fruitItem()
                : BuiltInRegistries.ITEM.get(publicData.product());
        if (fruit == Items.AIR) {
            fruit = getFruitTreeType().fruitItem();
        }
        ItemStack stack = new ItemStack(fruit, count);
        QualityHelper.setQuality(stack, getFruitQuality());
        return stack;
    }

    @Nullable
    private StardewTreeData resolvePublicData() {
        return level == null ? null : StardewAgricultureDataApi.tree(level, worldPosition, getBlockState());
    }

    private void syncChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.0, 7.0, 4.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_TYPE, getFruitTreeType().id());
        tag.putInt(TAG_FRUIT_COUNT, fruitCount);
        tag.putInt(TAG_DAYS_SINCE_MATURE, daysSinceMature);
        tag.putInt(TAG_LIGHTNING_DAYS, lightningDays);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = FruitTreeType.byId(tag.getString(TAG_TYPE));
        fruitCount = Math.max(0, tag.getInt(TAG_FRUIT_COUNT));
        daysSinceMature = Math.max(0, tag.getInt(TAG_DAYS_SINCE_MATURE));
        lightningDays = Math.max(0, tag.getInt(TAG_LIGHTNING_DAYS));
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
