package com.stardew.craft.cutscene.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates {@link EventCommand} instances from JSON command objects.
 */
public final class EventCommandFactory {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EventCommandFactory() {}

    /**
     * Parse a single JSON command object into an EventCommand.
     * Returns null for unknown/comment commands.
     */
    public static EventCommand create(JsonObject obj) {
        String cmd = obj.get("cmd").getAsString();

        if (cmd.indexOf(':') > 0) {
            ResourceLocation id = ResourceLocation.tryParse(cmd);
            if (id == null) {
                LOGGER.warn("Invalid namespaced event command: {}", cmd);
                return null;
            }
            JsonObject data = obj.deepCopy();
            data.remove("cmd");
            EventCommand custom = com.stardew.craft.api.v1.cutscene.StardewCutsceneCommands.create(id, data);
            if (custom == null) LOGGER.warn("Unknown namespaced event command: {}", cmd);
            return custom;
        }

        return switch (cmd) {
            case "lock_player"   -> new LockPlayerCommand();
            case "unlock_player" -> new UnlockPlayerCommand();
            case "pause"         -> new PauseCommand(obj.get("ticks").getAsInt());
            case "skippable"     -> new SkippableCommand();
            case "end"           -> new EndCommand();
            case "comment"       -> null; // skip comments

            case "spawn_actor"   -> new SpawnActorCommand(
                    obj.get("actor").getAsString(),
                    obj.get("npc_id").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    getBool(obj, "relative", false),
                    getFloat(obj, "facing", 0),
                    getString(obj, "anchor", null)
            );
            case "remove_actor"  -> new RemoveActorCommand(obj.get("actor").getAsString());
            case "move_player"   -> new MovePlayerCommand(
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    obj.get("ticks").getAsInt(),
                    getBool(obj, "relative", false),
                    getString(obj, "anchor", null)
            );

            case "move_actor"    -> new MoveActorCommand(
                    obj.get("actor").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    obj.get("ticks").getAsInt(),
                    getBool(obj, "relative", true),
                    getString(obj, "anchor", null)
            );
            case "navigate_actor" -> new NavigateActorCommand(
                    obj.get("actor").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    getBool(obj, "relative", false),
                    getDouble(obj, "speed", 0.11D),
                    getString(obj, "anchor", null)
            );
            case "face_actor"    -> new FaceActorCommand(
                    obj.get("actor").getAsString(),
                    obj.has("yaw") ? obj.get("yaw").getAsFloat() : null,
                    obj.has("face_actor") ? obj.get("face_actor").getAsString() : null
            );
            case "animate"       -> new AnimateCommand(
                    obj.get("actor").getAsString(),
                    obj.get("anim").getAsString(),
                    getBool(obj, "loop", false)
            );
            case "hide_npc"      -> new HideNpcCommand(obj.get("npc_id").getAsString());

            case "camera"        -> {
                if (obj.has("action") && "reset".equals(obj.get("action").getAsString())) {
                    yield new CameraResetCommand();
                }
                yield new CameraCommand(
                        getDouble(obj, "x", 0),
                        getDouble(obj, "y", 0),
                        getDouble(obj, "z", 0),
                        getFloat(obj, "yaw", 0),
                        getFloat(obj, "pitch", 30),
                        getBool(obj, "relative", false),
                        getInt(obj, "ticks", 0),
                        getString(obj, "anchor", null)
                );
            }

            case "speak"         -> new SpeakCommand(
                    obj.get("npc_id").getAsString(),
                    obj.get("text").getAsString()
            );
            case "object_dialogue" -> new ObjectDialogueCommand(obj.get("text").getAsString());

            case "egg_festival_players" -> new EggFestivalPlayersCommand(
                    getString(obj, "action", "spawn_lineup"),
                    getInt(obj, "ticks", 60)
            );

            case "egg_festival_speak" -> new EggFestivalSpeakCommand(
                    getString(obj, "npc_id", "lewis"),
                    getString(obj, "mode", "winner"),
                    getString(obj, "text", ""),
                    getString(obj, "player_text", ""),
                    getString(obj, "npc_text", "")
            );

            case "ice_fishing_speak" -> new IceFishingSpeakCommand(
                    getString(obj, "npc_id", "lewis"),
                    getString(obj, "mode", "winner"),
                    getString(obj, "text", ""),
                    getString(obj, "player_text", ""),
                    getString(obj, "npc_text", "")
            );
			case "ice_fishing_message" -> new IceFishingMessageCommand(
					getString(obj, "winner_text", "event.festival_of_ice.message.won"),
					getString(obj, "other_text", "event.festival_of_ice.message.not_won")
			);

            case "egg_festival_stage" -> new EggFestivalStageCommand(
                    getString(obj, "stage", "main"),
                    getInt(obj, "ticks", 10)
            );

            case "flower_dance_stage" -> new FlowerDanceStageCommand(
                    getString(obj, "stage", "main"),
                    getInt(obj, "ticks", 10)
            );

            case "moonlight_jellies_stage" -> new MoonlightJelliesStageCommand(
                    getString(obj, "stage", "release"),
                    getInt(obj, "ticks", 10)
            );

            case "flower_dance_dancers" -> new FlowerDanceDancersCommand(
                    getString(obj, "action", "spawn"),
                    getInt(obj, "ticks", 220)
            );

            case "egg_festival_finish" -> new EggFestivalFinishCommand();

            case "luau_main_event" -> new LuauMainEventCommand(
                    getInt(obj, "reaction", 2)
            );

            case "winter_star_stage" -> new WinterStarStageCommand(
                    getString(obj, "action", "")
            );

            case "fade"          -> new FadeCommand(
                    "out".equals(getString(obj, "mode", "out")),
                    getInt(obj, "ticks", 20)
            );

            case "emote"         -> new EmoteCommand(
                    obj.get("actor").getAsString(),
                    obj.get("emote").getAsString()
            );
            case "jump"          -> new JumpCommand(
                    obj.get("actor").getAsString(),
                    getDouble(obj, "strength", 0.5)
            );
            case "play_sound"    -> new PlaySoundCommand(
                    obj.get("sound").getAsString(),
                    getFloat(obj, "volume", 1.0f),
                    getFloat(obj, "pitch", 1.0f)
            );
            case "door"          -> new DoorCommand(
                    getInt(obj, "x", 0),
                    getInt(obj, "y", 0),
                    getInt(obj, "z", 0),
                    getBool(obj, "open", true)
            );
            case "message"       -> new MessageCommand(obj.get("text").getAsString());
            case "add_quest"     -> new AddQuestCommand(obj.get("quest_id").getAsString());
            case "remove_quest"  -> new RemoveQuestCommand(obj.get("quest_id").getAsString());
            case "set_flag"      -> new SetFlagCommand(obj.get("flag").getAsString());
            case "grant_rusty_key" -> new GrantRustyKeyCommand();
            case "grant_magnifying_glass" -> new GrantMagnifyingGlassCommand();
            case "grant_bear_knowledge" -> new GrantBearKnowledgeCommand();
            case "mark_opened_sewer" -> new MarkOpenedSewerCommand();
            case "show_npc"      -> new ShowNpcCommand(obj.get("npc_id").getAsString());
            case "spawn_entity"  -> new SpawnEntityCommand(
                    obj.get("entity_type").getAsString(),
                    obj.get("tag").getAsString(),
                    getDouble(obj, "x", 0), getDouble(obj, "y", 0), getDouble(obj, "z", 0),
                    getInt(obj, "color", 0),
                    getFloat(obj, "facing", 0),
                    getString(obj, "anchor", null)
            );
            case "screen_flash"  -> new ScreenFlashCommand(getInt(obj, "ticks", 4));

            // ── Layer 2: new commands ──

            case "music"         -> new MusicCommand(obj.get("track").getAsString());
            case "stop_music"    -> new StopMusicCommand();

            case "add_friendship" -> new AddFriendshipCommand(
                    obj.get("npc").getAsString(),
                    getInt(obj, "points", 250)
            );
            case "add_item"      -> new AddItemCommand(
                    obj.get("item").getAsString(),
                    getInt(obj, "count", 1)
            );
            case "remove_item"   -> new RemoveItemCommand(
                    obj.get("item").getAsString(),
                    getInt(obj, "count", 1)
            );
            case "add_mail"      -> new AddMailCommand(obj.get("id").getAsString());
            case "add_mail_now"  -> new AddMailNowCommand(obj.get("id").getAsString());
            case "add_mail_for_tomorrow" -> new AddMailForTomorrowCommand(obj.get("id").getAsString());
            case "add_recipe"    -> new AddRecipeCommand(obj.get("recipe").getAsString());
            case "apply_unlock_source" -> new ApplyUnlockSourceCommand(obj.get("source").getAsString());
            case "set_cave_choice" -> new SetCaveChoiceCommand(obj.get("choice").getAsString());

            case "question"      -> QuestionCommand.fromJson(obj);

            case "simultaneous"  -> {
                JsonArray cmdsArr = obj.getAsJsonArray("commands");
                List<EventCommand> subCmds = new ArrayList<>();
                for (var el : cmdsArr) {
                    EventCommand sub = create(el.getAsJsonObject());
                    if (sub != null) subCmds.add(sub);
                }
                yield new SimultaneousCommand(subCmds);
            }

            case "camera_shake"  -> new CameraShakeCommand(
                    getFloat(obj, "intensity", 0.5f),
                    getInt(obj, "ticks", 20)
            );
            case "shake_actor"   -> new ShakeActorCommand(
                    obj.get("actor").getAsString(),
                    getInt(obj, "ticks", 20),
                    getDouble(obj, "amplitude", 0.08)
            );
            case "camera_follow" -> new CameraFollowCommand(
                    obj.get("actor").getAsString(),
                    getDouble(obj, "dx", 0),
                    getDouble(obj, "dy", 3),
                    getDouble(obj, "dz", 5),
                    getFloat(obj, "yaw", 0),
                    getFloat(obj, "pitch", 30),
                    getInt(obj, "ticks", 60)
            );
            case "warp"          -> new WarpCommand(
                    obj.get("actor").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    getBool(obj, "relative", false),
                    getString(obj, "anchor", null)
            );
            case "particle"      -> new ParticleCommand(
                    obj.get("type").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    getInt(obj, "count", 3),
                    getInt(obj, "ticks", 20),
                    getString(obj, "anchor", null)
            );
            case "temporary_block" -> new TemporaryBlockCommand(
                    getString(obj, "id", obj.get("block").getAsString() + "@" + getInt(obj, "x", 0) + "," + getInt(obj, "y", 0) + "," + getInt(obj, "z", 0)),
                    obj.get("block").getAsString(),
                    getInt(obj, "x", 0),
                    getInt(obj, "y", 0),
                    getInt(obj, "z", 0),
                    net.minecraft.core.Direction.byName(getString(obj, "facing", "north"))
            );
            case "restore_temporary_block" -> new RestoreTemporaryBlockCommand(
                    obj.get("id").getAsString()
            );

            case "hold_item"     -> new HoldItemCommand(
                    obj.get("actor").getAsString(),
                    obj.get("item").getAsString(),
                    getInt(obj, "ticks", 60),
                    getFloat(obj, "offset_y", 0)
            );
            case "ground_item"    -> new GroundItemCommand(
                    getString(obj, "id", obj.get("item").getAsString() + "@" + getDouble(obj, "x", 0) + "," + getDouble(obj, "z", 0)),
                    obj.get("item").getAsString(),
                    getDouble(obj, "x", 0),
                    getDouble(obj, "y", 0),
                    getDouble(obj, "z", 0),
                    getFloat(obj, "scale", 0.98F),
                    getFloat(obj, "yaw", 0.0F)
            );
            case "text_above_head", "textAboveHead" -> new TextAboveHeadCommand(
                    obj.get("actor").getAsString(),
                    obj.get("text").getAsString(),
                    getInt(obj, "ticks", 40),
                    getDouble(obj, "offset_y", 0.0)
            );

            case "teleport_cc"   -> new TeleportCCCommand();

            default -> {
                LOGGER.warn("Unknown event command: {}", cmd);
                yield null;
            }
        };
    }

    // ─── JSON helpers ───

    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }
}
