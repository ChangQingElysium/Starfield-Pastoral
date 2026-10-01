package com.stardew.craft.client.slingshot;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Tracks only the released item's recoil; no player skeleton, skin, armor or world render ownership. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class SlingshotPresentation {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static Object level;
    private static final class State {net.minecraft.world.InteractionHand hand;int slot;int used;long released=-100;float charge;ItemStack stack=ItemStack.EMPTY;}
    /** The first-person renderer may retain an older stack object across an inventory sync. */
    public static boolean isDrawing(LivingEntity entity, ItemStack rendered) {
        return entity.isUsingItem() && rendered.getItem() instanceof SlingshotItem
                && ItemStack.isSameItem(rendered, entity.getUseItem());
    }
    public static float draw(LivingEntity entity,ItemStack stack,float partial){
        boolean using=isDrawing(entity,stack);
        var state=STATES.get(entity.getUUID());float release=-1,charge=0;
        if(!using&&state!=null&&state.hand!=null&&ItemStack.isSameItem(entity.getItemInHand(state.hand),stack)&&stack.getItem()==state.stack.getItem()
                &&(!(entity instanceof net.minecraft.world.entity.player.Player p)||p.getInventory().selected==state.slot)){release=(entity.level().getGameTime()-state.released+partial)/20f;charge=state.charge;}
        return SlingshotPose.draw(using?(entity.getTicksUsingItem()+partial)/20f:0,release,charge);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(level!=mc.level){STATES.clear();level=mc.level;}if(mc.level==null)return;
        STATES.keySet().removeIf(id->mc.level.getPlayerByUUID(id)==null);
        for(var p:mc.level.players()){
            var state=STATES.get(p.getUUID());
            if(p.isUsingItem()&&p.getUseItem().getItem() instanceof SlingshotItem){
                if(state==null){state=new State();STATES.put(p.getUUID(),state);}state.used=p.getTicksUsingItem();state.stack=p.getUseItem();state.hand=p.getUsedItemHand();state.slot=p.getInventory().selected;
            }else if(state!=null&&state.used>0){state.released=p.level().getGameTime();state.charge=state.used/20f;state.used=0;}
            else if(state!=null&&p.level().getGameTime()-state.released>6)STATES.remove(p.getUUID());
        }
    }
}
