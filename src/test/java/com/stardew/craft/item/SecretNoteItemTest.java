package com.stardew.craft.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretNoteItemTest {
    @Test
    void allDisplayNotesHaveDistinctCreativeComponents() {
        List<ItemStack> notes = new ArrayList<>();
        Set<String> variantKeys = new HashSet<>();

        for (int number = SecretNoteItem.FIRST_DISPLAY_NOTE;
             number <= SecretNoteItem.LAST_DISPLAY_NOTE;
             number++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            SecretNoteItem.bindDisplayNumber(stack, number);
            assertEquals(number, SecretNoteItem.getBoundDisplayNumber(stack));
            variantKeys.add(SecretNoteItem.getVariantKey(stack));
            notes.add(stack);
        }

        assertEquals(26, notes.size());
        assertEquals(26, variantKeys.size());
        assertEquals("note=unbound", SecretNoteItem.getVariantKey(new ItemStack(Items.PAPER)));
        for (int left = 0; left < notes.size(); left++) {
            for (int right = left + 1; right < notes.size(); right++) {
                assertFalse(ItemStack.isSameItemSameComponents(notes.get(left), notes.get(right)));
            }
        }
    }
}
