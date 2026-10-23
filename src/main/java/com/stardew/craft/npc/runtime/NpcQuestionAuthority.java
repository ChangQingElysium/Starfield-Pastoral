package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Only the server's offered response tuples may change friendship or dispatch dialogue commands. */
public final class NpcQuestionAuthority {
    public record Answer(String id,int points,String next) {}
    private record Pending(String npc,long expires,List<Answer> answers) {}
    private static final Pattern RESPONSE=Pattern.compile("\\$r\\s+([A-Za-z0-9_-]{1,64})\\s+(-?\\d+)\\s+([^\\s#]+)\\s*#");
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private NpcQuestionAuthority() {}

    public static List<Answer> parse(String text) {
        List<Answer> answers=new ArrayList<>();
        var matcher=RESPONSE.matcher(text);
        while (matcher.find()) answers.add(new Answer(matcher.group(1),Integer.parseInt(matcher.group(2)),matcher.group(3)));
        return List.copyOf(answers);
    }
    public static void validate(Map<String,JsonObject> events) {
        for (var event:events.entrySet()) {
            if (!event.getKey().equals("npc_questions") && !event.getKey().endsWith(":npc_questions")) continue;
            var questions=event.getValue().getAsJsonObject("questions");
            if (questions==null) throw new IllegalArgumentException("Missing NPC questions");
            for (var question:questions.entrySet()) for (var element:question.getValue().getAsJsonArray()) {
                var option=element.getAsJsonObject();
                if (!option.get("id").getAsString().matches("[A-Za-z0-9_-]{1,64}")
                        || option.get("next").getAsString().isBlank()
                        || option.get("next").getAsString().length()>256)
                    throw new IllegalArgumentException("Invalid NPC answer: "+question.getKey());
                option.get("points").getAsBigDecimal().intValueExact();
            }
        }
    }

    public static void open(ServerPlayer player,String npc,String source) {
        String text=source;
        while (text.length()>1 && text.charAt(0)=='$' && Character.isLetter(text.charAt(1))) text=text.substring(2);
        if (text.startsWith("__direct_dialogue::")) {
            try { text=new String(Base64.getUrlDecoder().decode(text.substring("__direct_dialogue::".length())),StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { text=""; }
        }
        var choices=new ArrayList<>(parse(text));
        for (var event:NpcDataRegistry.events().entrySet()) {
            if (!event.getKey().equals("npc_questions") && !event.getKey().endsWith(":npc_questions")) continue;
            JsonObject questions=event.getValue().getAsJsonObject("questions");
            if (questions==null || !questions.has(text)) continue;
            for (var option:questions.getAsJsonArray(text)) {
                var value=option.getAsJsonObject();
                choices.add(new Answer(value.get("id").getAsString(),value.get("points").getAsInt(),value.get("next").getAsString()));
            }
        }
        // Sending a new page invalidates the previous page, even if the NPC is unchanged.
        PENDING.remove(player.getUUID());
        if (!choices.isEmpty()) PENDING.put(player.getUUID(),new Pending(npc,player.getServer().getTickCount()+12000,List.copyOf(choices)));
    }
    static boolean hasPending(ServerPlayer player,String npc) {
        Pending pending=PENDING.get(player.getUUID());
        return pending!=null && pending.npc().equals(npc) && player.getServer().getTickCount()<pending.expires();
    }
    public static Answer consume(ServerPlayer player,String npc,String id,int points,String next) {
        Pending pending=PENDING.get(player.getUUID());
        if (pending==null || !pending.npc().equals(npc) || player.getServer().getTickCount()>=pending.expires()) return null;
        Answer requested=new Answer(id,points,next);
        int index=pending.answers().indexOf(requested);
        if (index<0) return null;
        PENDING.remove(player.getUUID());
        return pending.answers().get(index);
    }
    public static void close(UUID player) { PENDING.remove(player); }
    public static void clear() { PENDING.clear(); }
}
