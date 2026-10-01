package com.stardew.craft.templates;

import com.stardew.craft.port.PortItemData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

public final class TemplateBlockEntity extends BlockEntity {
    public static final ModelProperty<BlockState> MATERIAL_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<BlockState> FILL_MATERIAL_PROPERTY = new ModelProperty<>();
    private static final String FILL_MATERIAL_TAG = "fill_material";
    private static final String MATERIAL_TAG = "material";

    @Nullable
    private BlockState material;
    @Nullable private BlockState fillMaterial;

    public TemplateBlockEntity(BlockPos pos, BlockState state) {
        super(TemplateContent.TEMPLATE_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public BlockState material() {
        return material;
    }

    public void setMaterial(@Nullable BlockState material) {
        if (java.util.Objects.equals(this.material, material)) {
            return;
        }
        this.material = material;
        setChanged();
        requestModelDataUpdate();
        synchronizeMaterialProperties();
    }

    @Nullable
    public BlockState fillMaterial() {
        return fillMaterial;
    }

    public void setFillMaterial(@Nullable BlockState material) {
        if (!(getBlockState().getBlock() instanceof CompositeTemplateBlock)
                || (material != null && !TemplateMaterials.isValidFill(material))
                || java.util.Objects.equals(fillMaterial, material)) return;
        fillMaterial = material;
        setChanged();
        requestModelDataUpdate();
        synchronizeMaterialProperties();
    }

    @Nullable
    public BlockState effectiveFillMaterial() {
        return fillMaterial != null ? fillMaterial : getBlockState().getBlock() instanceof CompositeTemplateBlock composite
                ? composite.defaultFillMaterial() : null;
    }

    private void synchronizeMaterialProperties() {
        if (level != null) {
            BlockState current = getBlockState();
            BlockState fillMaterial = effectiveFillMaterial();
            BlockState updated = current;
            BlockState effective = material == null ? TemplateMaterials.defaultMaterial() : material;
            if (!level.isClientSide() && current.hasProperty(MaterialTemplateBlock.SOLID)) {
                updated = current.setValue(MaterialTemplateBlock.SOLID, effective.canOcclude() && (fillMaterial == null || fillMaterial.canOcclude()))
                        .setValue(MaterialTemplateBlock.PROPAGATES_SKYLIGHT, effective.propagatesSkylightDown(level, worldPosition)
                                && (fillMaterial == null || fillMaterial.propagatesSkylightDown(level, worldPosition)));
                if (current.hasProperty(RoofTemplateBlock.FILLED)) {
                    updated = updated.setValue(RoofTemplateBlock.FILLED, fillMaterial != null);
                }
                if (!updated.equals(current)) {
                    level.setBlock(worldPosition, updated, net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
            level.sendBlockUpdated(worldPosition, current, updated, net.minecraft.world.level.block.Block.UPDATE_ALL);
            updateMaterialLight();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            synchronizeMaterialProperties();
        }
    }

    private void updateMaterialLight() {
        if (level == null) return;
        var lights = com.stardew.craft.port.PortAuxLight.getAuxLightManager(level, worldPosition);
        if (lights != null) {
            BlockState effective = material == null ? TemplateMaterials.defaultMaterial() : material;
            BlockState fillMaterial = effectiveFillMaterial();
            lights.setLightAt(worldPosition, Math.max(effective.getLightEmission(level, worldPosition),
                    fillMaterial == null ? 0 : fillMaterial.getLightEmission(level, worldPosition)));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (material != null) {
            tag.put(MATERIAL_TAG, NbtUtils.writeBlockState(material));
        }
        if (fillMaterial != null) tag.put(FILL_MATERIAL_TAG, NbtUtils.writeBlockState(fillMaterial));
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        readMaterial(tag, registries);
        if (level != null && !level.isClientSide()) synchronizeMaterialProperties();
    }

    private void readMaterial(CompoundTag tag, HolderLookup.Provider registries) {
        BlockState loaded = tag.contains(MATERIAL_TAG, Tag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(MATERIAL_TAG))
                : null;
        material = TemplateMaterials.isValid(loaded) ? loaded : null;
        BlockState loadedFill = tag.contains(FILL_MATERIAL_TAG, Tag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(FILL_MATERIAL_TAG)) : null;
        fillMaterial = getBlockState().getBlock() instanceof CompositeTemplateBlock && TemplateMaterials.isValidFill(loadedFill)
                ? loadedFill : null;
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        ModelData.Builder builder = ModelData.builder();
        if (material != null) {
            builder.with(MATERIAL_PROPERTY, material);
        }
        if (effectiveFillMaterial() != null) builder.with(FILL_MATERIAL_PROPERTY, effectiveFillMaterial());
        return builder.build();
    }

    public static ModelData itemMaterials(net.minecraft.world.item.ItemStack stack) {
        var data = PortItemData.get(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return ModelData.EMPTY;
        CompoundTag tag = data.copyTag();
        ModelData.Builder builder = ModelData.builder();
        BlockState roof = tag.contains(MATERIAL_TAG, Tag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(MATERIAL_TAG)) : null;
        BlockState fill = tag.contains(FILL_MATERIAL_TAG, Tag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(FILL_MATERIAL_TAG)) : null;
        if (TemplateMaterials.isValid(roof)) builder.with(MATERIAL_PROPERTY, roof);
        if (TemplateMaterials.isValidFill(fill)) builder.with(FILL_MATERIAL_PROPERTY, fill);
        return builder.build();
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        if (material != null) {
            tag.put(MATERIAL_TAG, NbtUtils.writeBlockState(material));
        }
        if (fillMaterial != null) tag.put(FILL_MATERIAL_TAG, NbtUtils.writeBlockState(fillMaterial));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        readMaterial(tag, registries);
        markClientModelDirty();
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        readMaterial(packet.getTag(), registries);
        markClientModelDirty();
    }

    private void markClientModelDirty() {
        if (level != null && level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state,
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            updateMaterialLight();
            if(state.getBlock() instanceof RoofTemplateBlock) RoofTemplateBlock.refreshTouchingRoofs(level,worldPosition);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
