package com.stardew.craft.tree.fruit;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public enum FruitTreeType {
    CHERRY("cherry", 0, 0, 628, 638, 850, 80, 2, 2, 3, 6, 3),
    APRICOT("apricot", 0, 1, 629, 634, 500, 50, 2, 2, 3, 6, 3),
    ORANGE("orange", 1, 2, 630, 635, 1000, 100, 2, 2, 3, 6, 3),
    PEACH("peach", 1, 3, 631, 636, 1500, 140, 3, 2, 3, 6, 3),
    POMEGRANATE("pomegranate", 2, 4, 632, 637, 1500, 140, 3, 2, 3, 6, 3),
    APPLE("apple", 2, 5, 633, 613, 1000, 100, 3, 2, 3, 6, 3),
    BANANA("banana", 1, 7, 69, 91, 850, 150, 4, 1, 3, 7, 4),
    MANGO("mango", 1, 8, 835, 834, 850, 130, 2, 2, 3, 6, 3);

    public static final int DAYS_TO_MATURE = 28;
    public static final int MAX_FRUIT = 3;
    public static final int QUALITY_STEP_DAYS = 112;

    private final String id;
    private final int season;
    private final int sourceSpriteRow;
    private final int saplingSourceId;
    private final int fruitSourceId;
    private final int saplingSellPrice;
    private final int fruitSellPrice;
    private final int extensionRadiusX;
    private final int extensionRadiusZ;
    private final int canopyStartY;
    private final int maxExtensionY;
    private final int trunkTopY;

    FruitTreeType(String id, int season, int sourceSpriteRow, int saplingSourceId, int fruitSourceId,
                  int saplingSellPrice, int fruitSellPrice, int extensionRadiusX, int extensionRadiusZ,
                  int canopyStartY, int maxExtensionY, int trunkTopY) {
        this.id = id;
        this.season = season;
        this.sourceSpriteRow = sourceSpriteRow;
        this.saplingSourceId = saplingSourceId;
        this.fruitSourceId = fruitSourceId;
        this.saplingSellPrice = saplingSellPrice;
        this.fruitSellPrice = fruitSellPrice;
        this.extensionRadiusX = extensionRadiusX;
        this.extensionRadiusZ = extensionRadiusZ;
        this.canopyStartY = canopyStartY;
        this.maxExtensionY = maxExtensionY;
        this.trunkTopY = trunkTopY;
    }

    public String id() {
        return id;
    }

    public int season() {
        return season;
    }

    public int sourceSpriteRow() {
        return sourceSpriteRow;
    }

    public int saplingSourceId() {
        return saplingSourceId;
    }

    public int fruitSourceId() {
        return fruitSourceId;
    }

    public int saplingSellPrice() {
        return saplingSellPrice;
    }

    public int fruitSellPrice() {
        return fruitSellPrice;
    }

    public String saplingBlockId() {
        return id + "_sapling";
    }

    public String saplingItemId() {
        return id + "_sapling";
    }

    public String matureBlockId() {
        return id + "_tree";
    }

    public String extensionBlockId() {
        return id + "_tree_extension";
    }

    public String fruitItemId() {
        return id;
    }

    public ResourceLocation matureModel() {
        return modLocation("geo/block/tree/fruit/" + id + "_tree.geo.json");
    }

    public ResourceLocation matureTexture() {
        return modLocation("textures/block/tree/fruit/" + id + "_tree.png");
    }

    public Item saplingItem() {
        return BuiltInRegistries.ITEM.get(modLocation(saplingItemId()));
    }

    public Item fruitItem() {
        return BuiltInRegistries.ITEM.get(modLocation(fruitItemId()));
    }

    public Block matureBlock() {
        return BuiltInRegistries.BLOCK.get(modLocation(matureBlockId()));
    }

    public Block extensionBlock() {
        return BuiltInRegistries.BLOCK.get(modLocation(extensionBlockId()));
    }

    public int extensionRadiusX() {
        return extensionRadiusX;
    }

    public int extensionRadiusZ() {
        return extensionRadiusZ;
    }

    public int canopyStartY() {
        return canopyStartY;
    }

    public int maxExtensionY() {
        return maxExtensionY;
    }

    public int trunkTopY() {
        return trunkTopY;
    }

    public int visualStageFromDaysRemaining(int daysRemaining) {
        int grownDays = Math.max(0, DAYS_TO_MATURE - Math.max(0, daysRemaining));
        return Math.min(3, grownDays / 7);
    }

    public static FruitTreeType byId(String id) {
        for (FruitTreeType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return CHERRY;
    }

    private static ResourceLocation modLocation(String path) {
        return new ResourceLocation(StardewCraft.MODID, path);
    }
}
