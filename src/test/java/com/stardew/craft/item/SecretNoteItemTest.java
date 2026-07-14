package com.stardew.craft.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretNoteItemTest {
    @Test
    void allDisplayNotesHaveDistinctCreativeComponents() {
        List<ItemStack> notes = new ArrayList<>();

        for (int number = SecretNoteItem.FIRST_DISPLAY_NOTE;
             number <= SecretNoteItem.LAST_DISPLAY_NOTE;
             number++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            SecretNoteItem.bindDisplayNumber(stack, number);
            assertEquals(number, SecretNoteItem.getBoundDisplayNumber(stack));
            notes.add(stack);
        }

        assertEquals(26, notes.size());
        for (int left = 0; left < notes.size(); left++) {
            for (int right = left + 1; right < notes.size(); right++) {
                assertFalse(ItemStack.isSameItemSameComponents(notes.get(left), notes.get(right)));
            }
        }
    }
}
