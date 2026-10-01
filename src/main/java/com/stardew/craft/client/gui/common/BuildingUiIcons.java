package com.stardew.craft.client.gui.common;

import com.stardew.craft.item.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reuses the shipped Stardew action sprites and native item rendering at their authored pixel scale. */
public final class BuildingUiIcons {
    public enum Icon { RENAME, MOVE, RANGE, DEMOLISH, ANIMALS, BUILD, GRAZE, SELL, DONE, CLOSE, BACK, NEXT, HEART }
    private BuildingUiIcons() {}
    public static Icon action(Component label) {
        if(!(label.getContents() instanceof TranslatableContents text))return null;
        String key=text.getKey();
        return switch(key) {
            case "building.stardewcraft.rename" -> Icon.RENAME;
            case "building.stardewcraft.move_building", "stardewcraft.animal.query.move_confirm" -> Icon.MOVE;
            case "building.stardewcraft.preview_upgrade", "building.stardewcraft.toggle_range" -> Icon.RANGE;
            case "building.stardewcraft.demolish" -> Icon.DEMOLISH;
            case "livestock.stardewcraft.manage" -> Icon.ANIMALS;
            case "building.stardewcraft.accept_upgrade", "building.stardewcraft.accept_build", "building.stardewcraft.order_upgrade" -> Icon.BUILD;
            case "building.stardewcraft.grazing_button.open", "building.stardewcraft.grazing_button.closed", "livestock.stardewcraft.grazing.open", "livestock.stardewcraft.grazing.closed" -> Icon.GRAZE;
            case "stardewcraft.animal.query.hover.sell", "livestock.stardewcraft.buy" -> Icon.SELL;
            case "gui.done", "menu.returnToGame" -> Icon.DONE;
            case "gui.back" -> Icon.BACK;
            case "gui.cancel", "building.stardewcraft.cancel_move" -> Icon.CLOSE;
            default -> null;
        };
    }
    public static void draw(GuiGraphics g,Icon icon,int x,int y) {
        if(icon==null)return;
        switch(icon) {
            case RANGE -> g.renderItem(new ItemStack(ModItems.COOP_BLUEPRINT.get()),x,y);
            case DEMOLISH -> g.renderItem(new ItemStack(ModItems.PICKAXE.get()),x,y);
            case BUILD -> g.renderItem(new ItemStack(ModItems.COOP_MANAGER.get()),x,y);
            case GRAZE -> g.renderItem(new ItemStack(ModItems.HAY.get()),x,y);
            case ANIMALS -> animal(g,"white_chicken",x,y,16);
            default -> sprite(g,switch(icon) {
                case RENAME -> "rename";case MOVE -> "move_icon";case SELL -> "sell_icon";
                case DONE -> "ok_yes_tile46";case CLOSE -> "cancel_no_tile47";case BACK -> "page_left";
                case NEXT -> "page_right";case HEART -> "friendship_icon";default -> throw new IllegalStateException();
            },x,y,16,16,16,16);
        }
    }
    public static void animal(GuiGraphics g,String species,int x,int y,int size) {
        String name=switch(species) {case "white_cow","brown_cow" -> "cow";case "brown_chicken","blue_chicken" -> "white_chicken";default -> species;};
        sprite(g,"icon_"+name,x,y,size,size,32,32);
    }
    public static void sprite(GuiGraphics g,String name,int x,int y,int w,int h,int tw,int th) {
        g.blit(new ResourceLocation("stardewcraft:textures/gui/animal_query/"+name+".png"),x,y,w,h,0,0,tw,th,tw,th);
    }
    public static void hearts(GuiGraphics g,int x,int y,int friendship) {
        for(int i=0;i<10;i++)sprite(g,friendship>=(i+1)*100?"heart_filled":"heart_empty",x+i*8,y,7,6,7,6);
    }
}
