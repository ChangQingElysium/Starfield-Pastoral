package com.stardew.craft.client.weapon;

import com.stardew.craft.combat.network.ObsidianCrackPayload;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class ObsidianCrackEffectClient {
    private static final MineralFieldClient FIELDS = new MineralFieldClient(false);
    private ObsidianCrackEffectClient() {}
    public static void add(ObsidianCrackPayload p) {
        FIELDS.accept(p.casterId(), p.castTick(), p.phase(), new Vec3(p.x(), p.y(), p.z()),
                p.yaw(), p.length(), p.durationTicks());
    }
    public static void onClientTick(ClientTickEvent.Post event) { FIELDS.tick(); }
    public static void onRenderLevel(RenderLevelStageEvent event) { FIELDS.render(event); }
}
