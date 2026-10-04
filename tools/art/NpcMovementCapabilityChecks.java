import com.google.gson.JsonParser;
import com.stardew.craft.npc.data.NpcAnimationInspector;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Verify shipped server capabilities against the animation resources actually on the classpath. */
public final class NpcMovementCapabilityChecks {
    public static void main(String[] args) throws Exception {
        var migrated = Set.of("sam", "sebastian", "abigail", "robin", "haley", "elliott", "wizard", "mister_qi", "alex", "shane", "harvey", "leah", "maru", "penny", "emily", "pierre", "caroline","lewis","marnie","demetrius","evelyn","george","jas","vincent","jodi","willy","gus","pam","linus","clint","dwarf","krobus","sandy","gunther");
        var loader = NpcMovementCapabilityChecks.class.getClassLoader();
        int checked = 0;
        try (var stream = loader.getResourceAsStream("data/stardewcraft/npc/capabilities/base_profiles.json")) {
            if (stream == null) throw new AssertionError("Missing shipped capability profiles");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.getAsJsonArray("npcs")) {
                var npc = entry.getAsJsonObject();
                String id = npc.get("id").getAsString();
                if (!migrated.contains(id)) continue;
                boolean hasWalk = NpcAnimationInspector.hasWalkAnimation(id);
                // This is the same downgrade condition used when server capability data loads.
                String animation = hasWalk ? npc.get("animation_profile").getAsString() : NpcCapabilityProfile.ANIM_IDLE_ONLY;
                var profile = new NpcCapabilityProfile(id, npc.get("implemented").getAsBoolean(),
                        npc.get("pathing_enabled").getAsBoolean(), animation, 0, 0, 0, 0, 0, false);
                if (!hasWalk || !profile.canRunPathing() || !profile.hasWalkAnimation())
                    throw new AssertionError(id + " cannot use its shipped walking animation/pathing");
                checked++;
            }
        }
        if (checked != migrated.size()) throw new AssertionError("Missing migrated capability profile");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            if (!NpcAnimationInspector.hasWalkAnimation(" MISTER_QI ")) throw new AssertionError("Locale-dependent NPC ID");
        } finally { Locale.setDefault(original); }
        if (!NpcAnimationInspector.hasWalkAnimation("morris")) throw new AssertionError("Unmigrated NPC walk lost");
        if (NpcAnimationInspector.hasWalkAnimation("nonexistent_npc") || NpcAnimationInspector.hasWalkAnimation(null))
            throw new AssertionError("Missing animation reported as available");
        System.out.println("PASS: " + checked + " migrated NPCs permit pathing and recognize shipped native walk clips; legacy fallback and absent assets checked");
    }
}
