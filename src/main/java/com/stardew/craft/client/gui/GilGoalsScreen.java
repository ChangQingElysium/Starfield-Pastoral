package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.OpenGilGoalsPayload;
import com.stardew.craft.network.payload.OpenMailPayload;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Original read-only monster board, rendered by the shared letter viewer. Rewards belong to Gil. */
public final class GilGoalsScreen extends LetterViewerScreen {
    public GilGoalsScreen(List<OpenGilGoalsPayload.GoalEntry> goals) {
        super(new OpenMailPayload("map_interaction:guild_board", text(goals), "", 0, "", List.of(), 0, "", "", false, 0));
    }
    private static String text(List<OpenGilGoalsPayload.GoalEntry> goals) {
        StringBuilder text = new StringBuilder(Component.translatable("stardewcraft.gil.board.header").getString()).append("^");
        for (var goal : goals) {
            String format = goal.currentKills() == 0 ? "none" : goal.currentKills() >= goal.requiredKills() ? "complete" : "progress";
            text.append(Component.translatable("stardewcraft.gil.board." + format,
                    goal.currentKills(), goal.requiredKills(), Component.translatable(goal.translationKey())).getString()).append("^");
        }
        return text.append(Component.translatable("stardewcraft.gil.board.footer").getString()).toString();
    }
}
