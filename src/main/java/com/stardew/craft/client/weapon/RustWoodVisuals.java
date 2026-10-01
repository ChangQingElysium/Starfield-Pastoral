package com.stardew.craft.client.weapon;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class RustWoodVisuals {
    private record Cast(int actor,String skill,long tick){}
    private static final Map<Cast,Long> CASTS=new LinkedHashMap<>();
    private static ClientLevel level;
    private RustWoodVisuals(){}
    public static void ensureLevel(){var next=Minecraft.getInstance().level;if(next!=level){level=next;CASTS.clear();}}
    public static void start(WeaponSkillAnimPayload p){
        ensureLevel();if(level==null)return;
        if(CASTS.putIfAbsent(new Cast(p.casterEntityId(),p.skillId(),p.startGameTick()),level.getGameTime())!=null)return;
        while(CASTS.size()>64)CASTS.remove(CASTS.keySet().iterator().next());
        level.playLocalSound(p.originX(),p.originY()+.8,p.originZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.33f,RUST_STRIKE.equals(p.skillId())?.85f:1.05f,false);
    }
    public static boolean recentBlessing(int actor){ensureLevel();return level!=null&&CASTS.entrySet().stream().anyMatch(e->e.getKey().actor==actor&&WOOD_BLESS.equals(e.getKey().skill)&&level.getGameTime()-e.getValue()<40);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){ensureLevel();if(level!=null)CASTS.values().removeIf(t->level.getGameTime()-t>=40);}
}
