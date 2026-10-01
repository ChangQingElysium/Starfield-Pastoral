package com.stardew.craft.client.gui;

import com.stardew.craft.client.building.BuildingRoutesScreen;
import com.stardew.craft.client.building.RobinCatalogArt;
import com.stardew.craft.network.payload.*;
import com.stardew.craft.shop.CarpenterBlueprint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;

public class CarpenterMenuScreen extends FarmFolioScreen {
    private final String builder;
    private final List<CarpenterBlueprint> blueprints;
    private final long catalogRevision;
    private int playerMoney, selected, page, materialsPage;
    private boolean upgrades, pending, awaitingRoutes;
    private String requestedBlueprint = "";
    private Button purchase;
    private java.util.UUID pendingRequest;
    private final boolean robinBusy;
    public void awaitingPurchase(java.util.UUID request) { pending=true; pendingRequest=request; }

    public CarpenterMenuScreen(OpenCarpenterMenuPayload payload) {
        super(ui("catalog"), null);
        builder=payload.builder();blueprints=payload.blueprints();playerMoney=payload.playerMoney();
        catalogRevision=payload.catalogRevision();
        robinBusy=payload.robinBusy();
    }
    private List<CarpenterBlueprint> entries() { return blueprints.stream().filter(b->b.isUpgrade()==upgrades).toList(); }
    public void resumeChoices() { pending=false;awaitingRoutes=false; }
    public void updateMoney(int money) { playerMoney=money; }
    public void onPurchaseResult(CarpenterPurchaseResultPayload result) {
        if(!pending || !result.requestId().equals(pendingRequest))return;
        resumeChoices();playerMoney=result.newMoney();init();
    }
    /** A delayed choice response must not reopen an abandoned catalog or target another selection. */
    public void openChoices(OpenBuildingRoutesPayload offer) {
        if(!awaitingRoutes || !offer.requestId().equals(pendingRequest) || offer.revision()!=catalogRevision || !offer.family().toString().equals(requestedBlueprint))return;
        var blueprint=blueprints.stream().filter(b->b.id().equals(requestedBlueprint)).findFirst().orElse(null);
        if(blueprint==null)return;
        awaitingRoutes=false;
        minecraft.setScreen(new BuildingRoutesScreen(offer,this,blueprint));
    }
    @Override public void onClose() { awaitingRoutes=false;super.onClose(); }
    @Override protected int maximumTitleWidth() { return 212; }
    @Override protected void layout() {
        var rows=entries();int count=3;
        page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/count));
        selected=Math.clamp(selected,page*count,Math.max(page*count,Math.min(rows.size()-1,(page+1)*count-1)));
        button(ui("new_building"),x+236,y+8,76,26,upgrades?"tab":"packet_selected",null,
                ()->{upgrades=false;selected=page=materialsPage=0;init();}).active=!pending;
        if(blueprints.stream().anyMatch(CarpenterBlueprint::isUpgrade))
            button(ui("upgrade"),x+318,y+8,76,26,upgrades?"packet_selected":"tab",null,
                    ()->{upgrades=true;selected=page=materialsPage=0;init();}).active=!pending;
        for(int i=page*count;i<Math.min(rows.size(),(page+1)*count);i++) {
            int index=i;
            button(rows.get(i).displayName(),x+10,y+82+(i-page*count)*46,92,36,
                    selected==i?"packet_selected":"tab","home",()->{selected=index;materialsPage=0;init();}).active=!pending;
        }
        if(rows.size()>count) {
            arrow(false,x+16,y+236,!pending&&page>0,()->{page--;selected=page*count;materialsPage=0;init();});
            arrow(true,x+60,y+236,!pending&&(page+1)*count<rows.size(),()->{page++;selected=page*count;materialsPage=0;init();});
        }
        button(Component.translatable("gui.back"),x+16,y+h-34,68,24,this::onClose);
        if(rows.isEmpty())return;
        var bp=rows.get(selected);var materials=FarmMaterialCosts.combined(bp.materials());
        materialsPage=Math.clamp(materialsPage,0,Math.max(0,(materials.size()-1)/2));
        if(materials.size()>2) {
            arrow(false,x+326,y+276,materialsPage>0,()->{materialsPage--;init();});
            arrow(true,x+w-40,y+276,(materialsPage+1)*2<materials.size(),()->{materialsPage++;init();});
        }
        purchase=button(ui(pending?"requesting":bp.presentation().getBoolean("ChooseRoute")?"choose_route":"purchase"),
                x+326,y+h-34,w-342,26,()->{
                    if(pending)return;
                    pending=true;awaitingRoutes=bp.presentation().getBoolean("ChooseRoute");requestedBlueprint=bp.id();pendingRequest=java.util.UUID.randomUUID();init();
                    PacketDistributor.sendToServer(new CarpenterPurchasePayload(builder,blueprints.indexOf(bp),bp.id(),catalogRevision,pendingRequest));
                });
        updatePurchase();
    }
    private void updatePurchase() {
        if(purchase==null || entries().isEmpty())return;
        var bp=entries().get(selected);
        // A route-capable entry may still be self-built; every direct Robin route
        // (including upgrades and pond prefabs) must wait for the current order.
        boolean blocked = robinBusy && (upgrades || !bp.presentation().getBoolean("ChooseRoute"));
        purchase.active=!pending && !blocked && (bp.presentation().getBoolean("ChooseRoute")
                || playerMoney>=bp.cost() && FarmMaterialCosts.available(bp.materials()));
        disabledReason(purchase,pending?ui("requesting"):blocked
                ? Component.translatable("building.stardewcraft.robin_busy")
                : Component.translatable(playerMoney<bp.cost()
                        ?"livestock.stardewcraft.money":"stardewcraft.workbench.need_materials"));
    }
    @Override public void tick() { updatePurchase(); }
    @Override protected void paint(GuiGraphics g) {
        money(g,playerMoney,x+w-120,y+11,LIGHT);
        var rows=entries();
        if(rows.isEmpty()) {
            paper(g,x+112,y+58,w-132,h-110);
            paragraph(g,Component.translatable("building.stardewcraft.no_prefabs"),x+128,y+80,w-164,y+h-70,MUTED);return;
        }
        var bp=rows.get(selected);var details=bp.presentation();
        paper(g,x+92,y+52,218,h-88);
        paper(g,x+326,y+68,w-342,h-114);
        if(!RobinCatalogArt.draw(g,bp,x+106,y+66,190,h-160)) {
            var id=ResourceLocation.tryParse(bp.resultItemId());
            if(id!=null)item(g,new ItemStack(BuiltInRegistries.ITEM.get(id)),x+180,y+130,3);
        }
        label(g,bp.displayName(),x+108,y+260,186,INK);
        if(details.getBoolean("ChooseRoute")) {
            label(g,ui("self_build"),x+108,y+286,86,INK);
            money(g,details.getInt("ManagerPrice"),x+206,y+280,INK);
        }
        label(g,bp.displayName(),x+342,y+84,w-374,INK);
        paragraph(g,bp.description(),x+342,y+108,w-374,y+146,MUTED);
        if(details.getInt("Capacity")>0)label(g,Component.translatable("gui.stardew_craft.animal_manager.target_capacity",details.getInt("Capacity")),x+342,y+150,w-374,INK);
        rule(g,x+342,y+171,w-374);
        label(g,ui(details.getBoolean("ChooseRoute")?"robin_build":"materials"),x+342,y+181,w-374,INK);
        money(g,bp.cost(),x+342,y+198,playerMoney>=bp.cost()?INK:RED);
        var materials=FarmMaterialCosts.combined(bp.materials());
        for(int i=materialsPage*2;i<Math.min(materials.size(),(materialsPage+1)*2);i++)
            materialRow(g,materials.get(i),x+342,y+225+(i-materialsPage*2)*26,w-374);
        if(materials.size()>2)label(g,Component.literal((materialsPage+1)+" / "+((materials.size()+1)/2)),x+388,y+284,80,MUTED);
        if(!details.getBoolean("ChooseRoute") && !pending && !purchase.active)
            label(g,Component.translatable("stardewcraft.carpenter.tooltip.insufficient_resources"),x+112,y+h-26,198,RED);
    }
}
