package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * spawn_entity: spawn a real entity (not an EventActorEntity) at a position.
 * Currently supports: "junimo"
 * JSON: {"cmd":"spawn_entity", "entity_type":"junimo", "tag":"cage_junimo",
 *        "x":0.5, "y":64, "z":0.5, "color":"0x32CD32"}
 */
public class SpawnEntityCommand implements EventCommand {
    private final String entityType;
    private final String tag;
    private final double x, y, z;
    private final int color;
    private final float facing;
    private final String anchor;

    public SpawnEntityCommand(String entityType, String tag, double x, double y, double z, int color, float facing) {
        this(entityType, tag, x, y, z, color, facing, null);
    }

    public SpawnEntityCommand(String entityType, String tag, double x, double y, double z, int color, float facing, String anchor) {
        this.entityType = entityType;
        this.tag = tag;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.facing = facing;
        this.anchor = anchor;
    }

    @Override
    public void start(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Entity entity = switch (entityType) {
            case "junimo", "stardewcraft:junimo" -> {
                JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), level);
                junimo.setJunimoColor(color > 0 ? color : 0x32CD32);
                junimo.setNoAi(true);
                junimo.setNoTimeout(true);
                yield junimo;
            }
            default -> null;
        };

        if (entity == null) return;

        double ax = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetX(anchor);
        double ay = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetY(anchor);
        double az = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetZ(anchor);
        entity.setPos(x + ax, y + ay, z + az);
        entity.setYRot(facing);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.setYHeadRot(facing);
            living.setYBodyRot(facing);
        }
        int fakeId = -(tag.hashCode() & 0x7FFFFFFF) - 1;
        entity.setId(fakeId);
        level.putNonPlayerEntity(entity.getId(), entity); // PORT(1.20.1): 1.21 ClientLevel#addEntity(entity)
        // Track as a generic entity using the actor system (Mob cast if possible)
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            player.registerActor(tag, mob);
        }
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
}
