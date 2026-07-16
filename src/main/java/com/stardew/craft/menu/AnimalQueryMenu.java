package com.stardew.craft.menu;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class AnimalQueryMenu extends AbstractContainerMenu {
    private final Player player;
    private long animalId;
    private int ageDays;
    private int daysToMature;
    private int wasPetToday;
    private int friendship;
    private int allowReproduction;
    private int hasEatenAnimalCracker;
    private int variantIndex;
    private int moodMessage;
    private int wasFedToday;
    private int fullness;

    public AnimalQueryMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, -1L, 0, 5, false, 0, false, false, 0, 0, false, 0);
    }

    public AnimalQueryMenu(int containerId,
                           Inventory playerInventory,
                           long animalId,
                           int ageDays,
                           int daysToMature,
                           boolean wasPetToday,
                           int friendship,
                           boolean allowReproduction,
                           boolean hasEatenAnimalCracker,
                           int variantIndex,
                           int moodMessage,
                           boolean wasFedToday,
                           int fullness) {
        super(ModMenuTypes.ANIMAL_QUERY.get(), containerId);
        this.player = playerInventory.player;
        this.animalId = animalId;
        this.ageDays = Math.max(0, ageDays);
        this.daysToMature = Math.max(1, daysToMature);
        this.wasPetToday = wasPetToday ? 1 : 0;
        this.friendship = Math.max(0, friendship);
        this.allowReproduction = allowReproduction ? 1 : 0;
        this.hasEatenAnimalCracker = hasEatenAnimalCracker ? 1 : 0;
        this.variantIndex = Math.max(0, variantIndex);
        this.moodMessage = Math.max(0, moodMessage);
        this.wasFedToday = wasFedToday ? 1 : 0;
        this.fullness = Math.max(0, Math.min(255, fullness));

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (int) AnimalQueryMenu.this.animalId;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.animalId = value;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.ageDays;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.ageDays = Math.max(0, value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.daysToMature;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.daysToMature = Math.max(1, value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.wasPetToday;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.wasPetToday = value > 0 ? 1 : 0;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.friendship;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.friendship = Math.max(0, value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.allowReproduction;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.allowReproduction = value > 0 ? 1 : 0;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.hasEatenAnimalCracker;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.hasEatenAnimalCracker = value > 0 ? 1 : 0;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.variantIndex;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.variantIndex = Math.max(0, value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.moodMessage;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.moodMessage = Math.max(0, value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.wasFedToday;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.wasFedToday = value > 0 ? 1 : 0;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return AnimalQueryMenu.this.fullness;
            }

            @Override
            public void set(int value) {
                AnimalQueryMenu.this.fullness = Math.max(0, Math.min(255, value));
            }
        });
    }

    public int getFullness() {
        return fullness;
    }

    public boolean wasFedToday() {
        return wasFedToday > 0;
    }

    public long getAnimalId() {
        return animalId;
    }

    public int getAgeDays() {
        return ageDays;
    }

    public int getDaysToMature() {
        return daysToMature;
    }

    public boolean isBaby() {
        return ageDays < daysToMature;
    }

    public boolean wasPetToday() {
        return wasPetToday > 0;
    }

    public int getFriendship() {
        return friendship;
    }

    public boolean allowReproduction() {
        return allowReproduction > 0;
    }

    public boolean canToggleReproduction() {
        if (isBaby()) {
            return false;
        }
        return variantIndex >= 7 && variantIndex <= 11;
    }

    public boolean hasEatenAnimalCracker() {
        return hasEatenAnimalCracker > 0;
    }

    public int getVariantIndex() {
        return variantIndex;
    }

    public int getMoodMessage() {
        return moodMessage;
    }

    public String getMoodTranslationKey() {
        return switch (moodMessage) {
            case 1 -> "stardewcraft.animal.query.mood.1";
            case 2 -> "stardewcraft.animal.query.mood.2";
            case 3 -> "stardewcraft.animal.query.mood.3";
            case 4 -> "stardewcraft.animal.query.mood.4";
            case 5 -> "stardewcraft.animal.query.mood.5";
            case 6 -> "stardewcraft.animal.query.mood.6";
            default -> "stardewcraft.animal.query.mood.2";
        };
    }

    public int getEstimatedSellPrice() {
        int basePrice = getBaseAnimalSellPrice();
        if (player instanceof ServerPlayer serverPlayer) {
            return ProfessionSellPriceService.quoteAnimal(serverPlayer, basePrice, SellSource.ANIMAL_SALE).totalPrice();
        }
        return basePrice;
    }

    private int getBaseAnimalSellPrice() {
        int base = switch (variantIndex) {
            case 2 -> 1200; // duck
            case 4 -> 800;  // rabbit
            case 5 -> 10000; // ostrich
            case 6 -> 350;  // dinosaur
            case 7 -> 1500; // cow
            case 8 -> 4000; // goat
            case 9, 10 -> 8000; // sheep / sheared sheep
            case 11 -> 16000; // pig
            default -> 800; // chicken types
        };
        double friendshipRatio = Math.max(0.0, Math.min(1.0, friendship / 1000.0));
        return (int) Math.floor(base * (friendshipRatio + 0.3));
    }

    public void setAllowReproductionValue(boolean allow) {
        this.allowReproduction = allow ? 1 : 0;
    }

    public void handleToggleReproduction(boolean allow) {
        if (!(player instanceof ServerPlayer serverPlayer) || animalId <= 0L) {
            return;
        }
        AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());
        FarmAnimalRecord animal = data.getAnimal(animalId).orElse(null);
        if (animal == null) return;
        AnimalBuildingRecord building = data.getBuilding(animal.buildingId()).orElse(null);
        if (building == null) return;
        // 权限检查：只有农场成员可以操作
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), building.ownerPlayerUuid())) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.animal.query.no_permission"));
            return;
        }
        if (data.setAllowReproduction(animalId, allow)) {
            this.allowReproduction = allow ? 1 : 0;
        }
    }

    public void handleSellAnimal() {
        if (!(player instanceof ServerPlayer serverPlayer) || animalId <= 0L) {
            return;
        }

        AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());
        FarmAnimalRecord animal = data.getAnimal(animalId).orElse(null);
        if (animal == null) return;
        AnimalBuildingRecord building = data.getBuilding(animal.buildingId()).orElse(null);
        if (building == null) return;
        // 权限检查：只有农场成员可以卖动物
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), building.ownerPlayerUuid())) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.animal.query.no_permission"));
            return;
        }

        SellQuote quote = ProfessionSellPriceService.quoteAnimal(
            serverPlayer,
            getBaseAnimalSellPrice(),
            SellSource.ANIMAL_SALE
        );
        if (!data.removeAnimal(animalId)) {
            return;
        }

        ProfessionSellPriceService.payout(serverPlayer, quote);

        BaseCoopAnimalEntity entity = AnimalEntitySyncService.findLoaded(serverPlayer.serverLevel(), animalId);
        if (entity != null) {
            serverPlayer.serverLevel().sendParticles(
                ParticleTypes.CLOUD,
                entity.getX(),
                entity.getY() + 0.6D,
                entity.getZ(),
                14,
                0.6D,
                0.4D,
                0.6D,
                0.02D
            );
            serverPlayer.serverLevel().playSound(null, entity.blockPosition(), ModSounds.NEW_RECIPE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            serverPlayer.serverLevel().playSound(null, entity.blockPosition(), ModSounds.MONEY.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        }
        AnimalEntitySyncService.removeLoaded(serverPlayer.serverLevel(), animalId);
        serverPlayer.closeContainer();
    }

    public void handleOpenMoveHomeScreen() {
        if (!(player instanceof ServerPlayer serverPlayer) || animalId <= 0L) {
            return;
        }

        AnimalWorldData data = AnimalWorldData.get(serverPlayer.serverLevel());
        FarmAnimalRecord animal = data.getAnimal(animalId).orElse(null);
        if (animal == null) {
            return;
        }
        AnimalBuildingRecord current = data.getBuilding(animal.buildingId()).orElse(null);
        if (current == null) {
            return;
        }

        // 权限检查：只有农场成员可以迁移动物
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), current.ownerPlayerUuid())) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.animal.query.no_permission"));
            return;
        }

        String family = AnimalTypeCatalog.resolve(animal.animalTypeId()).family();
        List<OpenAnimalMoveHomeScreenPayload.BuildingOption> options = new ArrayList<>();
        for (AnimalBuildingRecord building : data.getBuildings()) {
            // 只显示同一农场的建筑（自己的或同农场成员的）
            if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                    .canOperateBuilding(
                        java.util.UUID.fromString(current.ownerPlayerUuid()),
                        building.ownerPlayerUuid())) {
                continue;
            }
            if (!family.equalsIgnoreCase(building.buildingType().family())) {
                continue;
            }

            int animals = building.memberAnimalIds().size();
            int capacity = Math.max(0, building.capacity());
            boolean selectable = !building.buildingId().equals(current.buildingId()) && animals < capacity;
            options.add(new OpenAnimalMoveHomeScreenPayload.BuildingOption(
                building.buildingId(),
                resolveBuildingDisplayName(building),
                animals,
                capacity,
                selectable
            ));
        }

        String animalName = resolveAnimalDisplayName(animal);
        PacketDistributor.sendToPlayer(
            serverPlayer,
            new OpenAnimalMoveHomeScreenPayload(animal.animalId(), animalName, animal.animalTypeId(), current.buildingId(), options)
        );
    }

    private static String resolveBuildingDisplayName(AnimalBuildingRecord building) {
        if (building.customName() != null && !building.customName().isBlank()) {
            return building.customName();
        }
        return building.buildingId();
    }

    private static String resolveAnimalDisplayName(FarmAnimalRecord animal) {
        if (animal.customName() != null && !animal.customName().isBlank()) {
            return animal.customName();
        }
        return animal.animalTypeId();
    }

    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return true;
    }
}
