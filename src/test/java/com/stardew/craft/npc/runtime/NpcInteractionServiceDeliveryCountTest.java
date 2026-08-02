package com.stardew.craft.npc.runtime;

import com.stardew.craft.quest.StardewQuest;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcInteractionServiceDeliveryCountTest {

    @Test
    void multiItemDeliveryRequiresTheWholeRemainingStackAtOnce() {
        StardewQuest quest = new ObjectiveCountQuest(3, 10);

        assertEquals(7, NpcInteractionService.requiredDeliveryCount(quest));
    }

    @Test
    void singleItemAndLegacyDeliveriesRequireOneItem() {
        assertEquals(1, NpcInteractionService.requiredDeliveryCount(new ObjectiveCountQuest(0, 1)));
        assertEquals(1, NpcInteractionService.requiredDeliveryCount(new ObjectiveCountQuest(-1, -1)));
    }

    @Test
    void legacyDeliveryHookConsumesTheRequestedBatchInOneCall() {
        CountingDeliveryQuest quest = new CountingDeliveryQuest();

        assertTrue(quest.onItemOfferedToNpc(null, "robin", "stardewcraft:wood_hard", 10));
        assertEquals(10, quest.delivered);
    }

    private static final class ObjectiveCountQuest extends StardewQuest {
        private final int current;
        private final int total;

        private ObjectiveCountQuest(int current, int total) {
            this.current = current;
            this.total = total;
        }

        @Override
        public int getCurrentObjectiveCount() {
            return current;
        }

        @Override
        public int getTotalObjectiveCount() {
            return total;
        }
    }

    private static final class CountingDeliveryQuest extends StardewQuest {
        private int delivered;

        @Override
        public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String itemId) {
            delivered++;
            return true;
        }
    }
}
