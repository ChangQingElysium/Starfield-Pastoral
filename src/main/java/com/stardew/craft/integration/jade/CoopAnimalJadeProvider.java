package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Optional;

@SuppressWarnings("null")
public enum CoopAnimalJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "coop_animal");

    private static final String NBT_NAME_KEY = "animalNameKey";
    private static final String NBT_ANIMAL_TYPE = "animalType";
    private static final String NBT_IS_BABY = "isBaby";
    private static final String NBT_AGE_DAYS = "ageDays";
    private static final String NBT_DAYS_TO_MATURE = "daysToMature";
    private static final String NBT_PRODUCE_READY = "produceReady";
    private static final String NBT_PRODUCE_NAME_KEY =
            "produceNameKey";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(accessor.getEntity() instanceof BaseCoopAnimalEntity animal)) {
            return;
        }

        String animalType = animal.getManagedAnimalType();
        boolean isBaby = animal.isBaby();
        int ageDays = 0;
        int daysToMature = 0;
        boolean produceReady = false;
        String produceNameKey = "";

        long managedId = animal.getManagedAnimalId();
        if (managedId > 0L) {
            Optional<FarmAnimalRecord> record = AnimalWorldData.get(serverLevel).getAnimal(managedId);
            if (record.isPresent()) {
                FarmAnimalRecord farmRecord = record.get();
                animalType = farmRecord.animalTypeId();
                isBaby = farmRecord.isBaby();
                ageDays = farmRecord.ageDays();
                daysToMature = farmRecord.daysToMature();
                produceReady = !farmRecord.currentProduceId().isBlank();
                ResourceLocation produceId =
                        ResourceLocation.tryParse(
                                farmRecord.currentProduceId());
                if (produceId != null
                        && BuiltInRegistries.ITEM
                                .containsKey(produceId)
                        && BuiltInRegistries.ITEM.get(produceId)
                                != Items.AIR) {
                    produceNameKey = BuiltInRegistries.ITEM
                            .get(produceId)
                            .getDescriptionId();
                } else {
                    produceReady = false;
                }
            }
        }

        if (animalType == null || animalType.isBlank()) {
            return;
        }

        String nameKey =
                FarmAnimalDefinitions.displayNameKeyFor(
                        animalType);
        tag.putString(NBT_NAME_KEY, nameKey);
        tag.putString(NBT_ANIMAL_TYPE, animalType);
        tag.putBoolean(NBT_IS_BABY, isBaby);
        tag.putInt(NBT_AGE_DAYS, Math.max(0, ageDays));
        tag.putInt(NBT_DAYS_TO_MATURE, Math.max(0, daysToMature));
        tag.putBoolean(NBT_PRODUCE_READY, produceReady && !isBaby);
        tag.putString(
                NBT_PRODUCE_NAME_KEY, produceNameKey);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(NBT_NAME_KEY)) {
            return;
        }

        String nameKey = data.getString(NBT_NAME_KEY);
        String animalType = data.getString(NBT_ANIMAL_TYPE);
        Component speciesName =
                Component.translatable(nameKey);
        Component displayName = data.getBoolean(NBT_IS_BABY)
            ? Component.translatable("stardewcraft.jade.animal.baby", speciesName)
            : speciesName;
        tooltip.add(Component.translatable("stardewcraft.tooltip.animal.name", displayName)
            .withStyle(ChatFormatting.WHITE));

        if (data.getBoolean(NBT_IS_BABY)) {
            int ageDays = data.getInt(NBT_AGE_DAYS);
            int daysToMature = Math.max(1, data.getInt(NBT_DAYS_TO_MATURE));
            String growth = ageDays + "/" + daysToMature;
            tooltip.add(Component.translatable("stardewcraft.tooltip.animal.growth", growth)
                .withStyle(ChatFormatting.AQUA));
            return;
        }

        if (!data.getBoolean(NBT_PRODUCE_READY)) {
            return;
        }

        String produceNameKey =
                data.getString(NBT_PRODUCE_NAME_KEY);
        if (!produceNameKey.isBlank()) {
            tooltip.add(Component.translatable(
                            "stardewcraft.tooltip.animal.produce_ready",
                            Component.translatable(
                                    produceNameKey))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
