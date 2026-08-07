package com.stardew.craft.cutscene.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class QuestionCommand implements EventCommand {

    private final String questionText;
    private final List<ChoiceBranch> branches;
    private boolean waitingForChoice = true;
    private List<EventCommand> chosenCommands = null;
    private int subIndex = 0;

    public QuestionCommand(String questionText, List<ChoiceBranch> branches) {
        this.questionText = questionText;
        this.branches = branches;
    }

    public record ChoiceBranch(String text, List<EventCommand> commands) {}

    public static QuestionCommand fromJson(JsonObject obj) {
        String text = obj.has("text") ? obj.get("text").getAsString() : "";
        JsonArray choicesArr = obj.getAsJsonArray("choices");
        List<ChoiceBranch> branches = new ArrayList<>();
        for (var el : choicesArr) {
            JsonObject choiceObj = el.getAsJsonObject();
            String choiceText = choiceObj.get("text").getAsString();
            JsonArray cmdsArr = choiceObj.getAsJsonArray("commands");
            List<EventCommand> cmds = new ArrayList<>();
            for (var cmdEl : cmdsArr) {
                EventCommand cmd = EventCommandFactory.create(cmdEl.getAsJsonObject());
                if (cmd != null) cmds.add(cmd);
            }
            branches.add(new ChoiceBranch(choiceText, cmds));
        }
        return new QuestionCommand(text, branches);
    }

    @Override
    public void start(EventPlayer player) {
        waitingForChoice = true;
        chosenCommands = null;
        subIndex = 0;

        Minecraft minecraft = Minecraft.getInstance();
        String playerName = minecraft.player == null
                ? "player"
                : minecraft.player.getName().getString();
        List<Component> responses = new ArrayList<>(branches.size());
        for (ChoiceBranch b : branches) {
            responses.add(resolveText(b.text(), playerName));
        }

        StardewQuestionDialogSpec spec = StardewQuestionDialogSpec.of(
                resolveText(questionText, playerName),
                responses,
                index -> onChoiceSelected(index, player),
                -1
        );

        minecraft.setScreen(StardewConfirmDialogScreen.createQuestionDialog(spec));
    }

    private static Component resolveText(String text, String playerName) {
        String translated = text.startsWith("stardewcraft.")
                || text.startsWith("event.")
                || text.startsWith("message.")
                ? OpenNpcDialogueScreenPayload.rawTranslation(text)
                : text;
        return Component.literal(OpenNpcDialogueScreenPayload.resolvePlayerDialogueText(
                translated, playerName));
    }

    private void onChoiceSelected(int choiceIndex, EventPlayer player) {
        if (choiceIndex >= 0 && choiceIndex < branches.size()) {
            chosenCommands = branches.get(choiceIndex).commands();
        } else {
            chosenCommands = List.of();
        }
        waitingForChoice = false;
        subIndex = 0;
        if (chosenCommands != null && !chosenCommands.isEmpty()) {
            chosenCommands.get(0).start(player);
        }
    }

    @Override
    public void tick(EventPlayer player) {
        if (waitingForChoice) return;
        if (chosenCommands == null || chosenCommands.isEmpty()) return;
        if (subIndex >= chosenCommands.size()) return;
        EventCommand current = chosenCommands.get(subIndex);
        current.tick(player);
        if (current.isComplete()) {
            subIndex++;
            if (subIndex < chosenCommands.size()) {
                chosenCommands.get(subIndex).start(player);
            }
        }
    }

    @Override
    public boolean isComplete() {
        if (waitingForChoice) return false;
        if (chosenCommands == null || chosenCommands.isEmpty()) return true;
        return subIndex >= chosenCommands.size();
    }

    @Override
    public void onSkip(EventPlayer player) {
        waitingForChoice = false;
        if (!branches.isEmpty()) {
            for (EventCommand cmd : branches.get(0).commands()) {
                if (cmd.isStateCommand()) {
                    cmd.onSkip(player);
                }
            }
        }
        chosenCommands = List.of();
    }
}
