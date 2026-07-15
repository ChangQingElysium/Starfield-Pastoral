package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

import java.util.ArrayList;
import java.util.List;

/** Plays the Luau potluck ceremony and the source-derived governor reaction branch. */
public class LuauMainEventCommand implements EventCommand {
    private static final List<ActorPlacement> MAIN_EVENT_ACTORS = List.of(
        new ActorPlacement("demetrius", "demetrius", 58.5, 60.0, 100.5, 0.0F),
        new ActorPlacement("robin", "robin", 59.5, 60.0, 100.5, 0.0F),
        new ActorPlacement("pam", "pam", 55.5, 60.0, 102.5, 0.0F),
        new ActorPlacement("haley", "haley", 52.5, 60.0, 107.5, -90.0F),
        new ActorPlacement("emily", "emily", 55.5, 60.0, 110.5, -90.0F),
        new ActorPlacement("governor", "governor", 60.5, 60.0, 105.5, 0.0F),
        new ActorPlacement("lewis", "lewis", 61.5, 60.0, 104.5, 0.0F),
        new ActorPlacement("clint", "clint", 67.5, 60.0, 95.5, 0.0F),
        new ActorPlacement("willy", "willy", 71.5, 60.0, 98.5, 90.0F),
        new ActorPlacement("marlon", "marlon", 88.5, 60.0, 103.5, 0.0F),
        new ActorPlacement("sam", "sam", 51.5, 60.0, 117.5, 0.0F),
        new ActorPlacement("sebastian", "sebastian", 50.5, 60.0, 118.5, -90.0F),
        new ActorPlacement("abigail", "abigail", 52.5, 60.0, 118.5, 90.0F),
        new ActorPlacement("vincent", "vincent", 46.5, 60.0, 131.5, 180.0F),
        new ActorPlacement("jas", "jas", 47.5, 60.0, 131.5, 180.0F),
        new ActorPlacement("leah", "leah", 60.5, 60.0, 120.5, 180.0F),
        new ActorPlacement("elliott", "elliott", 61.5, 60.0, 120.5, 180.0F),
        new ActorPlacement("marnie", "marnie", 64.5, 60.0, 109.5, 90.0F),
        new ActorPlacement("jodi", "jodi", 62.5, 60.0, 111.5, 180.0F),
        new ActorPlacement("gus", "gus", 58.5, 60.0, 111.5, 180.0F),
        new ActorPlacement("pierre", "pierre", 67.5, 60.0, 110.5, 90.0F),
        new ActorPlacement("caroline", "caroline", 67.5, 60.0, 109.5, 90.0F),
        new ActorPlacement("shane", "shane", 70.5, 60.0, 107.5, 90.0F),
        new ActorPlacement("george", "george", 72.5, 60.0, 118.5, 180.0F),
        new ActorPlacement("evelyn", "evelyn", 73.5, 60.0, 118.5, 180.0F),
        new ActorPlacement("harvey", "harvey", 80.5, 60.0, 117.5, 0.0F),
        new ActorPlacement("alex", "alex", 80.5, 60.0, 125.5, 180.0F),
        new ActorPlacement("linus", "linus", 84.5, 60.0, 121.5, 90.0F),
        new ActorPlacement("maru", "maru", 76.5, 60.0, 121.5, -90.0F),
        new ActorPlacement("penny", "penny", 85.5, 60.0, 128.5, 0.0F)
    );

    private final List<EventCommand> commands;
    private int commandIndex;
    private boolean done;

    public LuauMainEventCommand(int reaction) {
        this.commands = buildCommands(Math.max(0, Math.min(6, reaction)));
    }

    @Override
    public void start(EventPlayer player) {
        commandIndex = 0;
        done = commands.isEmpty();
        if (!done) {
            commands.get(commandIndex).start(player);
        }
    }

    @Override
    public void tick(EventPlayer player) {
        if (done || commandIndex >= commands.size()) {
            done = true;
            return;
        }
        EventCommand current = commands.get(commandIndex);
        if (!current.isComplete()) {
            current.tick(player);
            return;
        }
        commandIndex++;
        if (commandIndex >= commands.size()) {
            done = true;
            return;
        }
        commands.get(commandIndex).start(player);
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    private static List<EventCommand> buildCommands(int reaction) {
        List<EventCommand> commands = new ArrayList<>();
        addOpening(commands);
        addReaction(commands, reaction);
        commands.add(new FadeCommand(true, 20));
        commands.add(new StopMusicCommand());
        commands.add(new EndCommand());
        return commands;
    }

    private static void addOpening(List<EventCommand> commands) {
        commands.add(new LockPlayerCommand());
        commands.add(new FadeCommand(true, 20));
        for (ActorPlacement actor : MAIN_EVENT_ACTORS) {
            commands.add(new HideNpcCommand(actor.npcId()));
        }
        for (ActorPlacement actor : MAIN_EVENT_ACTORS) {
            commands.add(new SpawnActorCommand(actor.actor(), actor.npcId(), actor.x(), actor.y(), actor.z(), false, actor.facing(), null));
        }
        commands.add(new CameraCommand(61.627, 69.374, 130.542, 179.7F, 30.0F, false, 0));
        commands.add(new MusicCommand("OCEAN_AMBIENCE"));
        commands.add(new FadeCommand(false, 20));
        commands.add(new PauseCommand(40));
        commands.add(new FaceActorCommand("lewis", 90.0F, null));
        commands.add(new PauseCommand(5));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new PauseCommand(5));
        commands.add(new FaceActorCommand("lewis", -90.0F, null));
        commands.add(new PauseCommand(5));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.main.lewis_intro"));
        commands.add(new PauseCommand(20));
        commands.add(new FaceActorCommand("lewis", 90.0F, null));
        commands.add(new PauseCommand(10));
        commands.add(new SpeakCommand("lewis", "event.luau.main.lewis_invite"));
        commands.add(new PauseCommand(10));
        commands.add(new SpeakCommand("governor", "event.luau.main.governor_accept"));
        commands.add(new StopMusicCommand());
        commands.add(new PauseCommand(10));
        commands.add(new SimultaneousCommand(List.of(
            new CameraCommand(61.725, 63.372, 111.767, 178.8F, 44.7F, false, 40),
            new MoveActorCommand("governor", 60.5, 60.0, 107.5, 40, false, null)
        )));
        commands.add(new PauseCommand(20));
        commands.add(new PlaySoundCommand("stardewcraft:dwop", 1.0F, 1.0F));
        commands.add(new PauseCommand(20));
        commands.add(new AnimateCommand("governor", "eat", false));
        commands.add(new PauseCommand(22));
        commands.add(new PlaySoundCommand("stardewcraft:siptea", 1.0F, 1.0F));
        commands.add(new PauseCommand(20));
        commands.add(new PlaySoundCommand("stardewcraft:gulp", 1.0F, 1.0F));
        commands.add(new PauseCommand(18));
        commands.add(new AnimateCommand("governor", "idle", true));
        commands.add(new PauseCommand(18));
        commands.add(new SpeakCommand("governor", "event.luau.main.governor_after_drink"));
        commands.add(new PauseCommand(6));
    }

    private static void addReaction(List<EventCommand> commands, int reaction) {
        switch (reaction) {
            case 6 -> addShortsReaction(commands);
            case 5 -> addMissingIngredientReaction(commands);
            case 4 -> addBestReaction(commands);
            case 3 -> addGoodReaction(commands);
            case 2 -> addNeutralReaction(commands);
            case 1 -> addBadReaction(commands);
            default -> addWorstReaction(commands);
        }
    }

    private static void addShortsReaction(List<EventCommand> commands) {
        commands.add(new PauseCommand(20));
        commands.add(new MusicCommand("OCEAN_AMBIENCE"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction6.governor_tangy"));
        commands.add(new PauseCommand(12));
        commands.add(new EmoteCommand("governor", "question"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction6.governor_bowl"));
        commands.add(new PauseCommand(10));
        commands.add(new PlaySoundCommand("stardewcraft:slimehit", 1.0F, 1.0F));
        commands.add(new JumpCommand("governor", 0.5));
        commands.add(new PauseCommand(10));
        commands.add(new JumpCommand("lewis", 0.5));
		commands.add(new TextAboveHeadCommand("lewis", "event.luau.reaction6.lewis_head", 20, 0.0));
        commands.add(new JumpCommand("marnie", 0.5));
		commands.add(new TextAboveHeadCommand("marnie", "event.luau.reaction6.marnie_head", 20, 0.0));
        commands.add(new ShakeActorCommand("governor", 40, 0.08));
        commands.add(new EmoteCommand("governor", "angry"));
        commands.add(new PauseCommand(10));
        commands.add(new SpeakCommand("governor", "event.luau.reaction6.governor_outrage"));
        commands.add(new PauseCommand(12));
        commands.add(new PlaySoundCommand("stardewcraft:throw_down_item", 1.0F, 1.0F));
        commands.add(new JumpCommand("lewis", 0.5));
        commands.add(new PauseCommand(20));
        commands.add(new SpeakCommand("governor", "event.luau.reaction6.governor_sick"));
        commands.add(new FadeCommand(true, 8));
        commands.add(new WarpCommand("governor", -100.0, 60.0, -100.0, false, null));
        commands.add(new PlaySoundCommand("stardewcraft:slimedead", 1.0F, 1.0F));
        commands.add(new CameraCommand(61.627, 69.374, 130.542, 179.7F, 30.0F, false, 0));
        commands.add(new FadeCommand(false, 8));
        commands.add(new PauseCommand(20));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction6.lewis_private"));
        commands.add(new PauseCommand(10));
        commands.add(new EmoteCommand("lewis", "angry"));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction6.lewis_home"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.reaction6.message_prank"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addMissingIngredientReaction(List<EventCommand> commands) {
        commands.add(new EmoteCommand("governor", "pause"));
        commands.add(new PauseCommand(8));
        commands.add(new SpeakCommand("governor", "event.luau.reaction5.governor"));
        commands.add(new EmoteCommand("lewis", "sad"));
        addLewisDrinks(commands);
        commands.add(new SpeakCommand("lewis", "event.luau.reaction5.lewis_bland"));
        commands.add(new PauseCommand(20));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction5.lewis_thanks"));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction5.lewis_wants"));
        commands.add(new PauseCommand(20));
        commands.add(new MessageCommand("event.luau.reaction5.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addBestReaction(List<EventCommand> commands) {
        commands.add(new EmoteCommand("governor", "exclamation"));
        commands.add(new PauseCommand(12));
        commands.add(new ShakeActorCommand("governor", 20, 0.08));
        commands.add(new JumpCommand("governor", 0.5));
        commands.add(new PauseCommand(16));
        commands.add(new MusicCommand("SETTLINGIN"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction4.governor"));
        commands.add(new ShakeActorCommand("governor", 100, 0.06));
        commands.add(new AnimateCommand("governor", "eat", true));
        commands.add(new PauseCommand(16));
        addLewisDrinks(commands);
        commands.add(new SpeakCommand("lewis", "event.luau.reaction4.lewis_delicious"));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction4.lewis_wonderful"));
        commands.add(new PauseCommand(20));
        commands.add(new MessageCommand("event.luau.reaction4.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addGoodReaction(List<EventCommand> commands) {
        commands.add(new EmoteCommand("governor", "happy"));
        commands.add(new PauseCommand(8));
        commands.add(new MusicCommand("JAUNTY"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction3.governor"));
        addLewisDrinks(commands);
        commands.add(new SpeakCommand("lewis", "event.luau.reaction3.lewis_tasty"));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction3.lewis_wants"));
        commands.add(new PauseCommand(20));
        commands.add(new MessageCommand("event.luau.reaction3.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addNeutralReaction(List<EventCommand> commands) {
        commands.add(new PauseCommand(8));
        commands.add(new MusicCommand("OCEAN_AMBIENCE"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction2.governor"));
        commands.add(new EmoteCommand("lewis", "sad"));
        addLewisDrinks(commands);
        commands.add(new SpeakCommand("lewis", "event.luau.reaction2.lewis"));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction2.lewis_wants"));
        commands.add(new PauseCommand(20));
        commands.add(new MessageCommand("event.luau.reaction2.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addBadReaction(List<EventCommand> commands) {
        commands.add(new EmoteCommand("governor", "sad"));
        commands.add(new PauseCommand(8));
        commands.add(new MusicCommand("OCEAN_AMBIENCE"));
        commands.add(new SpeakCommand("governor", "event.luau.reaction1.governor"));
        commands.add(new EmoteCommand("lewis", "sad"));
        addLewisDrinks(commands);
        commands.add(new SpeakCommand("lewis", "event.luau.reaction1.lewis_yuck"));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction1.lewis_wants"));
        commands.add(new PauseCommand(20));
        commands.add(new MessageCommand("event.luau.reaction1.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addWorstReaction(List<EventCommand> commands) {
        commands.add(new PauseCommand(10));
        commands.add(new ShakeActorCommand("governor", 40, 0.08));
        commands.add(new EmoteCommand("governor", "angry"));
        commands.add(new PauseCommand(10));
        commands.add(new SpeakCommand("governor", "event.luau.reaction0.governor_vile"));
        commands.add(new JumpCommand("lewis", 0.5));
        commands.add(new PauseCommand(16));
        commands.add(new SpeakCommand("governor", "event.luau.reaction0.governor_lie_down"));
        commands.add(new FadeCommand(true, 12));
        commands.add(new PauseCommand(20));
        commands.add(new WarpCommand("governor", -100.0, 60.0, -100.0, false, null));
        commands.add(new PlaySoundCommand("stardewcraft:slimedead", 1.0F, 1.0F));
        commands.add(new CameraCommand(61.627, 69.374, 130.542, 179.7F, 30.0F, false, 0));
        commands.add(new FadeCommand(false, 8));
        commands.add(new PauseCommand(20));
        commands.add(new FaceActorCommand("lewis", 0.0F, null));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction0.lewis_ashamed"));
        commands.add(new PauseCommand(10));
        commands.add(new EmoteCommand("lewis", "angry"));
        commands.add(new SpeakCommand("lewis", "event.luau.reaction0.lewis_home"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.reaction0.message"));
        commands.add(new PauseCommand(10));
        commands.add(new MessageCommand("event.luau.message.home"));
    }

    private static void addLewisDrinks(List<EventCommand> commands) {
        commands.add(new PauseCommand(8));
        commands.add(new SimultaneousCommand(List.of(
            new MoveActorCommand("lewis", 64.5, 60.0, 109.5, 50, false, null),
            new CameraCommand(61.725, 63.372, 111.767, 178.8F, 44.7F, false, 30)
        )));
        commands.add(new PauseCommand(10));
        commands.add(new FaceActorCommand("lewis", 90.0F, null));
        commands.add(new AnimateCommand("lewis", "eat", false));
        commands.add(new PauseCommand(16));
        commands.add(new PlaySoundCommand("stardewcraft:gulp", 1.0F, 1.0F));
        commands.add(new PauseCommand(12));
        commands.add(new PlaySoundCommand("stardewcraft:gulp", 1.0F, 1.0F));
        commands.add(new PauseCommand(20));
        commands.add(new AnimateCommand("lewis", "idle", true));
    }

    private record ActorPlacement(String actor, String npcId, double x, double y, double z, float facing) {}
}
