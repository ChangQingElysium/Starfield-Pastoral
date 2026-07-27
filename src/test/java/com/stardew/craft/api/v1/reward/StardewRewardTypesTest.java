package com.stardew.craft.api.v1.reward;

import com.stardew.craft.api.v1.progress.StardewProgress;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardDescriptor;
import com.stardew.craft.api.v1.festival.StardewFestivalRewards;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewRewardTypesTest {
    @Test
    void previewsAndComponentsDefensivelyCopyDisplayData() {
        ItemStack icon = new ItemStack(Items.DIAMOND, 3);
        StardewRewardComponent component =
                new StardewRewardComponent(
                        StardewRewardComponent.Kind.ITEM,
                        ResourceLocation.parse("minecraft:diamond"),
                        3,
                        icon,
                        Component.literal("Diamonds"),
                        false);
        ArrayList<StardewRewardComponent> source =
                new ArrayList<>(List.of(component));
        StardewRewardPreview preview = new StardewRewardPreview(
                StardewProgress.museumReward("museum50"),
                source,
                true);

        icon.setCount(1);
        source.clear();
        assertEquals(3, component.icon().getCount());
        assertEquals(1, preview.components().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> preview.components().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new StardewRewardComponent(
                        StardewRewardComponent.Kind.OTHER,
                        ResourceLocation.parse("example:invalid"),
                        -1,
                        ItemStack.EMPTY,
                        Component.empty(),
                        false));
    }

    @Test
    void festivalDescriptorsKeepCompoundIdentityAndCopyPreview() {
        ResourceLocation festival =
                ResourceLocation.parse("example:apple_day");
        ResourceLocation reward =
                ResourceLocation.parse("other:golden_apple");
        ArrayList<StardewRewardComponent> preview =
                new ArrayList<>(List.of(new StardewRewardComponent(
                        StardewRewardComponent.Kind.ITEM,
                        ResourceLocation.parse("minecraft:apple"),
                        1,
                        new ItemStack(Items.APPLE),
                        Items.APPLE.getDescription(),
                        false)));
        StardewFestivalRewardDescriptor descriptor =
                new StardewFestivalRewardDescriptor(
                        festival, reward, preview, true);

        preview.clear();
        assertEquals(1, descriptor.preview().size());
        assertEquals(
                ResourceLocation.parse(
                        "example:festival_reward/apple_day"),
                StardewFestivalRewards.progressKey(
                        festival, reward).domain());
        assertEquals(
                reward,
                StardewFestivalRewards.progressKey(
                        festival, reward).id());
    }
}
