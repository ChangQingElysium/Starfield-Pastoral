package com.stardew.craft.item.quality;

import com.google.gson.JsonParser;
import com.stardew.craft.inventory.InventoryOrganizeService;
import com.stardew.craft.item.StardewQualityItem;
import com.stardew.craft.item.fish.crabpot.MusselItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ItemQualityStackTest {
    private static Item mushroom, mussel, forage, fruit, grape, cockle, oyster, seaweed;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var registry=(MappedRegistry<Item>)BuiltInRegistries.ITEM;registry.unfreeze();
        mushroom=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","mushroom"),
                new StardewQualityItem("stardewcraft.type.misc",40,15,true,new Item.Properties()));
        mussel=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","mussel"),
                new MusselItem(new Item.Properties()));
        forage=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","forage"),
                new StardewQualityItem("stardewcraft.type.forage",50,5,true,new Item.Properties()));
        fruit=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","fruit"),
                new StardewQualityItem("stardewcraft.type.fruit",100,15,true,new Item.Properties()));
        grape=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","grape"),
                new com.stardew.craft.item.crop.fall.GrapeItem(new Item.Properties()));
        cockle=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","cockle"),
                new com.stardew.craft.item.fish.crabpot.CockleItem(new Item.Properties()));
        oyster=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","oyster"),
                new com.stardew.craft.item.fish.crabpot.OysterItem(new Item.Properties()));
        seaweed=Registry.register(registry,ResourceLocation.fromNamespaceAndPath("quality_test","seaweed"),
                new com.stardew.craft.item.fish.misc.SeaweedItem(new Item.Properties()));
        registry.freeze();
    }
    private static ItemStack quality(int quality) {
        var stack=new ItemStack(mushroom);QualityHelper.setQuality(stack,quality);return stack;
    }
    private static ItemStack legacy(int quality,Integer model) {
        var stack=new ItemStack(mushroom);var tag=new CompoundTag();tag.putInt("Quality",quality);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        if(model!=null)stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(model));
        return stack;
    }

    @Test void normalHarvestMatchesPlainMachineProductAndNoEmptyComponentsRemain() {
        var plain=new ItemStack(mushroom);var harvested=quality(0);
        assertTrue(ItemStack.isSameItemSameComponents(plain,harvested));
        assertFalse(harvested.has(DataComponents.CUSTOM_DATA));assertFalse(harvested.has(DataComponents.CUSTOM_MODEL_DATA));
        for(int q=1;q<=3;q++) {
            QualityHelper.setQuality(harvested,q);assertEquals(q,harvested.get(DataComponents.CUSTOM_MODEL_DATA).value());
            QualityHelper.setQuality(harvested,0);assertTrue(ItemStack.isSameItemSameComponents(plain,harvested));
        }
    }

    @Test void changingQualityRefreshesPreviouslyAssignedModelAndPreservesOtherData() {
        var stack=quality(1);var tag=stack.get(DataComponents.CUSTOM_DATA).copyTag();tag.putString("origin","keep");
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        QualityHelper.setQuality(stack,3);assertEquals(3,stack.get(DataComponents.CUSTOM_MODEL_DATA).value());
        QualityHelper.setQuality(stack,2);assertEquals(2,stack.get(DataComponents.CUSTOM_MODEL_DATA).value());
        QualityHelper.setQuality(stack,0);
        assertEquals("keep",stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("origin"));
        assertFalse(stack.get(DataComponents.CUSTOM_DATA).copyTag().contains("Quality"));
    }

    @Test void realConstructorCopyAndSaveReloadRepairLegacyRepresentations() {
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for(int q=0;q<=3;q++)for(Integer cmd:new Integer[]{null,0,1,2,3}) {
            var old=legacy(q,cmd);var expected=quality(q);
            assertTrue(ItemStack.isSameItemSameComponents(expected,old.copy()),"copy quality="+q+" cmd="+cmd);
            var loaded=ItemStack.parse(registries,old.save(registries)).orElseThrow();
            assertTrue(ItemStack.isSameItemSameComponents(expected,loaded),"reload quality="+q+" cmd="+cmd);
        }
    }

    @Test void organizingOldStacksMergesOnlyEquivalentQualityWithoutLosingCount() {
        var inventory=new SimpleContainer(5);
        var old=legacy(0,0);old.setCount(7);inventory.setItem(0,old);
        var plain=new ItemStack(mushroom,9);inventory.setItem(1,plain);
        var oldGold=legacy(2,1);oldGold.setCount(3);inventory.setItem(2,oldGold);
        var gold=quality(2);gold.setCount(4);inventory.setItem(3,gold);
        InventoryOrganizeService.organizeContainer(inventory,5);
        int normal=0,golden=0,occupied=0;
        for(int i=0;i<5;i++) {var stack=inventory.getItem(i);if(stack.isEmpty())continue;occupied++;
            if(QualityHelper.getQuality(stack)==0)normal+=stack.getCount();else golden+=stack.getCount();}
        assertEquals(2,occupied);assertEquals(16,normal);assertEquals(7,golden);
    }

    @Test void genuineComponentDifferencesAndUnmanagedItemsRemainDistinct() {
        assertFalse(ItemStack.isSameItemSameComponents(quality(1),quality(2)));
        var named=quality(1);named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep my name"));
        assertFalse(ItemStack.isSameItemSameComponents(named.copy(),quality(1)));
        var marked=legacy(1,null);var tag=marked.get(DataComponents.CUSTOM_DATA).copyTag();tag.putInt("FishSize",42);
        marked.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        assertEquals(42,marked.copy().get(DataComponents.CUSTOM_DATA).copyTag().getInt("FishSize"));
        assertFalse(ItemStack.isSameItemSameComponents(marked.copy(),quality(1)));
        var custom=quality(1);custom.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(901));
        assertEquals(901,custom.copy().get(DataComponents.CUSTOM_MODEL_DATA).value());
        assertFalse(ItemStack.isSameItemSameComponents(custom.copy(),quality(1)));
        var vanilla=new ItemStack(Items.STICK);vanilla.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        vanilla.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(3));
        assertEquals(vanilla.getComponents(),vanilla.copy().getComponents());
        var red=legacy(2,null);tag=red.get(DataComponents.CUSTOM_DATA).copyTag();tag.putInt("FlowerColor",1);
        red.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        var blue=legacy(2,null);tag=blue.get(DataComponents.CUSTOM_DATA).copyTag();tag.putInt("FlowerColor",2);
        blue.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        assertFalse(ItemStack.isSameItemSameComponents(red.copy(),blue.copy()));
        assertEquals(121,red.copy().get(DataComponents.CUSTOM_MODEL_DATA).value());
        assertEquals(122,blue.copy().get(DataComponents.CUSTOM_MODEL_DATA).value());
    }

    @Test void queryingMushroomAndShellfishNamesIsReadOnly() {
        for(Item item:List.of(mushroom,mussel,forage,fruit,grape,cockle,oyster,seaweed)) {
            var stack=new ItemStack(item);var tag=new CompoundTag();tag.putInt("Quality",2);
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            var before=stack.getComponentsPatch();item.getName(stack);
            assertEquals(before,stack.getComponentsPatch());
        }
    }

    @Test void otherForageShellfishAndFruitRepairEveryLegacyQualityRepresentation() {
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for(Item item:List.of(forage,fruit,grape,mussel,cockle,oyster)) {
            for(int q=0;q<=3;q++)for(Integer model:new Integer[]{null,0,1,2,3}) {
                var expected=new ItemStack(item);QualityHelper.setQuality(expected,q);
                var old=new ItemStack(item);var tag=new CompoundTag();tag.putInt("Quality",q);
                old.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                if(model!=null)old.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(model));
                assertTrue(ItemStack.isSameItemSameComponents(expected,old.copy()),item+" q="+q+" model="+model);
                var reloaded=ItemStack.parse(registries,old.save(registries)).orElseThrow();
                assertTrue(ItemStack.isSameItemSameComponents(expected,reloaded));
                if(q==0)assertTrue(ItemStack.isSameItemSameComponents(expected,new ItemStack(item)));
            }
        }
    }

    @Test void seaweedNeverAcquiresHiddenQualityAndLegacyPickupsMergeWithPlainDrops() {
        for(int q=0;q<=3;q++) {
            var picked=new ItemStack(seaweed);QualityHelper.setQuality(picked,q);
            assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(seaweed),picked));
            var old=new ItemStack(seaweed);var tag=new CompoundTag();tag.putInt("Quality",q);
            old.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            old.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(q));
            assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(seaweed),old.copy()));
            tag.putString("origin","keep");old.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            old.set(DataComponents.CUSTOM_NAME,Component.literal("My seaweed"));
            var repaired=old.copy();assertEquals("keep",repaired.get(DataComponents.CUSTOM_DATA).copyTag().getString("origin"));
            assertEquals(old.get(DataComponents.CUSTOM_NAME),repaired.get(DataComponents.CUSTOM_NAME));
        }
    }

    @Test void allAffectedModelsSelectExistingNativeStarLayers() throws Exception {
        var assets=Path.of(System.getProperty("stardewcraft.projectDir"),"src/main/resources/assets/stardewcraft");
        String[] stars={"silver","gold","iridium"};
        var blockSource=Files.readString(assets.getParent().getParent().getParent().resolve("java/com/stardew/craft/block/ModBlocks.java"));
        var pattern=java.util.regex.Pattern.compile("= forage\\(\"([a-z_]+)\"");
        var matcher=pattern.matcher(blockSource);var forageIds=new java.util.LinkedHashSet<String>();
        while(matcher.find())forageIds.add(matcher.group(1));
        assertTrue(forageIds.size()>=40,"Audit every registered ground forage, including fruit caves");
        for(String item:forageIds) {
            if(item.equals("seaweed"))continue; // Intentionally has no quality variants.
            var base=JsonParser.parseString(Files.readString(assets.resolve("models/item/"+item+".json"))).getAsJsonObject();
            var overrides=base.getAsJsonArray("overrides");assertEquals(3,overrides.size());
            for(int i=0;i<3;i++) {
                var override=overrides.get(i).getAsJsonObject();assertEquals(i+1,override.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
                String name=override.get("model").getAsString();
                var model=JsonParser.parseString(Files.readString(assets.resolve("models/"+name.substring("stardewcraft:".length())+".json"))).getAsJsonObject();
                assertEquals(base.getAsJsonObject("textures").get("layer0"),model.getAsJsonObject("textures").get("layer0"));
                assertEquals("stardewcraft:item/quality/"+stars[i]+"_star",model.getAsJsonObject("textures").get("layer1").getAsString());
                for(var texture:model.getAsJsonObject("textures").entrySet()) {
                    assertTrue(Files.exists(assets.resolve("textures/"+texture.getValue().getAsString().substring("stardewcraft:".length())+".png")));
                }
            }
        }
    }
}
