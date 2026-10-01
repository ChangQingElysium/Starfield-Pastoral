package com.stardew.craft.client.building;

import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.building.runtime.UtilityBuildings;
import com.stardew.craft.client.gui.*;
import com.stardew.craft.network.payload.*;
import com.stardew.craft.shop.CarpenterBlueprint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.UUID;

public final class BuildingRoutesScreen extends FarmFolioScreen implements com.stardew.craft.client.gui.common.StardewGuiContentSize {
    @Override public int minimumCanvasWidth() { return preferredWidth()+16; }
    @Override public int minimumCanvasHeight() { return preferredHeight()+20; }
    private final OpenBuildingRoutesPayload offer;
    private final List<CarpenterBlueprint.MaterialEntry> materials;
    private boolean pending, detail;
    private int money, materialPage;
    private UUID pendingRequest;
    private Button selfButton, prefabButton;

    public BuildingRoutesScreen(OpenBuildingRoutesPayload offer, CarpenterMenuScreen catalog, CarpenterBlueprint blueprint) {
        super(Component.translatable("building.stardewcraft.routes"), catalog);
        this.offer = offer;
        this.materials = FarmMaterialCosts.combined(blueprint.materials());
        money = offer.money();
    }

    @Override protected void layout() {
        int px=x+194, pw=w-210;
        selfButton=button(ui(pending?"requesting":"buy_manager"),px+12,y+111,pw-24,26,()->purchase(true));
        prefabButton=button(ui(pending?"requesting":"buy_blueprint"),px+12,y+266,pw-24,26,()->purchase(false));
        if(materials.size()>2) {
            arrow(false,px+12,y+238,materialPage>0,()->{materialPage--;init();});
            arrow(true,px+pw-36,y+238,(materialPage+1)*2<materials.size(),()->{materialPage++;init();});
        }
        button(Component.translatable("gui.back"),x+16,y+h-34,92,24,this::onClose);
        button(ui("requirements"),x+w-176,y+h-34,160,24,()->{detail=!detail;init();});
        updateButtons();
    }
    private void updateButtons() {
        selfButton.active=!pending && money>=offer.managerPrice();
        prefabButton.active=!pending && !offer.robinBusy() && PrefabDefinitions.supported(offer.family())
                && money>=offer.prefabPrice() && FarmMaterialCosts.available(materials);
        disabledReason(selfButton,pending?ui("requesting"):Component.translatable("livestock.stardewcraft.money"));
        disabledReason(prefabButton,pending?ui("requesting"):offer.robinBusy()
                ? Component.translatable("building.stardewcraft.robin_busy")
                : Component.translatable(!PrefabDefinitions.supported(offer.family())
                ?"gui.stardewcraft.farm_ui.prefab_unavailable":money<offer.prefabPrice()?"livestock.stardewcraft.money":"stardewcraft.workbench.need_materials"));
    }
    @Override public void tick() { updateButtons(); }
    private void purchase(boolean self) {
        if(pending)return;
        pending=true;pendingRequest=UUID.randomUUID();
        ((CarpenterMenuScreen)parent).awaitingPurchase(pendingRequest);init();
        PacketDistributor.sendToServer(new BuildingPurchasePayload(offer.revision(),self,pendingRequest,offer.family()));
    }
    public void result(CarpenterPurchaseResultPayload result) {
        if(!pending || !result.requestId().equals(pendingRequest))return;
        pending=false;money=result.newMoney();
        ((CarpenterMenuScreen)parent).updateMoney(money);
        if(result.success())onClose();else init();
    }
    @Override public void onClose() {
        // Back is always available; an in-flight purchase still locks the catalog until its reply.
        if(!pending)((CarpenterMenuScreen)parent).resumeChoices();
        super.onClose();
    }
    @Override protected void paint(GuiGraphics g) {
        money(g,money,x+w-126,y+8,LIGHT);
        int px=x+194,pw=w-210;
        paper(g,x+16,y+36,166,h-82);
        if(detail) {
            label(g,ui("self_build"),x+28,y+50,142,INK);
            paragraph(g,Component.translatable(offer.family().equals(UtilityBuildings.SILO)
                    ?"building.stardewcraft.self_route.silo":"building.stardewcraft.self_route.generic",
                    offer.width(),offer.width(),offer.height()),x+28,y+74,142,y+214,MUTED);
            rule(g,x+28,y+221,142);
            paragraph(g,ui("prefab_summary"),x+28,y+235,142,y+284,MUTED);
        } else RobinCatalogArt.draw(g,offer.family().toString(),x+24,y+50,150,h-118);
        paper(g,px,y+36,pw,108);
        paper(g,px,y+151,pw,147);
        item(g,new ItemStack(PrefabDefinitions.managerItem(offer.family())),px+12,y+47,1);
        label(g,ui("self_build"),px+38,y+51,pw-166,INK);
        money(g,offer.managerPrice(),px+pw-124,y+44,money>=offer.managerPrice()?INK:RED);
        paragraph(g,(offer.family().equals(UtilityBuildings.SILO)?Component.translatable("building.stardewcraft.silo_summary"):ui("self_summary",offer.width(),offer.width(),offer.height())),px+12,y+76,pw-24,y+103,MUTED);
        if(PrefabDefinitions.supported(offer.family()))item(g,new ItemStack(PrefabDefinitions.blueprintItem(offer.family())),px+12,y+161,1);
        label(g,ui("robin_build"),px+38,y+165,pw-166,INK);
        money(g,offer.prefabPrice(),px+pw-124,y+158,money>=offer.prefabPrice()?INK:RED);
        for(int i=materialPage*2;i<Math.min(materials.size(),(materialPage+1)*2);i++)
            materialRow(g,materials.get(i),px+12,y+188+(i-materialPage*2)*24,pw-24);
        if(materials.size()>2)label(g,Component.literal((materialPage+1)+" / "+((materials.size()+1)/2)),px+pw/2-25,y+245,50,MUTED);
    }
}
