package com.stardew.craft.client.building;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** SDV menu source rectangles, deliberately separate from Minecraft world previews. */
public final class RobinCatalogArt {
    private RobinCatalogArt() {}
    public static boolean draw(GuiGraphics graphics, com.stardew.craft.shop.CarpenterBlueprint blueprint,int x,int y,int width,int height){
        var presentation=blueprint.presentation();String family=presentation.getString("ArtFamily");int tier=presentation.getInt("Tier");
        return draw(graphics,family.isEmpty()?blueprint.id():family+(tier>1?"_upgrade_"+tier:""),x,y,width,height);
    }
    public static boolean draw(GuiGraphics graphics, String id, int x, int y, int width, int height) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return false;
        String path=key.getPath();int tier=1;int split=path.lastIndexOf("_upgrade_");
        if(split>=0)try{tier=Integer.parseInt(path.substring(split+9));path=path.substring(0,split);}catch(NumberFormatException ignored){}
        var binding=com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(new ResourceLocation(key.getNamespace(),path));
        var display=binding.isEmpty()?null:binding.get().displays().get(tier);
        if(display!=null){com.stardew.craft.client.gui.FarmFolioScreen.image(graphics,display.texture(),display.width(),display.height(),x,y,width,height,false);return true;}
        if(!key.getNamespace().equals("stardewcraft"))return false;
        String name = key.getPath();
        int w, h, tw, th;
        if (java.util.Set.of("coop", "coop_upgrade_2", "coop_upgrade_3").contains(name)) { w = 96; h = 112; tw = 96; th = 128; }
        else if (java.util.Set.of("barn", "barn_upgrade_2", "barn_upgrade_3").contains(name)) { w = 112; h = 112; tw = 112; th = 128; }
        else if (name.equals("silo")) { w = 48; h = 128; tw = 48; th = 128; }
        else if (name.equals("fish_pond")) { w = 80; h = 112; tw = 160; th = 176; }
        else return false;
        float scale = Math.min((float) width / w, (float) height / h);
        if (scale >= 1) scale = (float) Math.floor(scale);
        ResourceLocation texture = new ResourceLocation("stardewcraft", "textures/gui/robin_buildings/" + name + ".png");
        graphics.pose().pushPose();
        graphics.pose().translate(x + (width - w * scale) / 2, y + (height - h * scale) / 2, 0);
        graphics.pose().scale(scale, scale, 1);
        if (name.equals("fish_pond")) {
            // Source FishPond.drawInMenu: water, rim, ripple and the raised net are separate layers.
            graphics.pose().pushPose(); graphics.pose().translate(0, 32, 0);
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(60 / 255f, 126 / 255f, 150 / 255f, 1);
            graphics.blit(texture, 0, 0, 0, 80, 80, 80, tw, th); graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.blit(texture, 0, 0, 0, 0, 80, 80, tw, th);
            graphics.blit(texture, 16, 12, 16, 160, 48, 7, tw, th);
            graphics.blit(texture, 0, -32, 80, 0, 80, 48, tw, th);
            graphics.pose().popPose(); graphics.pose().popPose(); return true;
        }
        graphics.blit(texture, 0, 0, 0, 0, w, h, tw, th);
        if (name.startsWith("coop")) graphics.blit(texture, 32, 96, 0, 112, 16, 16, tw, th);
        if (name.startsWith("barn")) {
            int doorX = name.equals("barn") ? 48 : 64;
            graphics.blit(texture, doorX, 84, 0, 112, 32, 12, tw, th);
            graphics.blit(texture, doorX, 96, 0, 112, 32, 16, tw, th);
        }
        graphics.pose().popPose();
        return true;
    }
}
