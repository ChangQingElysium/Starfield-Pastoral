package com.stardew.craft.client.building;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.building.runtime.BuildingBlueprintItem;
import com.stardew.craft.building.runtime.BuildingManagerItem;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import com.stardew.craft.network.payload.BuildingManagerInfoPayload;
import com.stardew.craft.network.payload.BuildingPreviewPayload;
import com.stardew.craft.network.payload.BuildingPreviewRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class BuildingPlacementPreview {
    private static BuildingPreviewPayload preview, upgradePreview;
    private static java.util.UUID hiddenDocument;
    public static void hideHeldPreview(){hiddenDocument=com.stardew.craft.building.runtime.BuildingDrafts.id(document);preview=null;target=null;pinned=false;held=false;}
    private static ResourceLocation upgradeDimension;
    private static java.util.UUID upgradeBuilding;
    public static boolean upgradeVisible(java.util.UUID id) { return upgradePreview!=null && id.equals(upgradeBuilding); }
    public static void closeRanges() { upgradePreview=null;upgradeBuilding=null;managerRange=null; }
    public static void showUpgrade(net.minecraft.nbt.CompoundTag tag) {
        var record = com.stardew.craft.building.runtime.BuildingRecord.load(tag); var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if(upgradeVisible(record.id())) { closeRanges();mc.setScreen(null);return; }
        upgradeBuilding=record.id();
        upgradePreview = new BuildingPreviewPayload(record.anchor(), record.facing(), false, 0, "valid", record.anchor(),
                record.claim().min(), record.claim().maxExclusive(), BlockPos.of(tag.getLong("TargetMin")), BlockPos.of(tag.getLong("TargetMax")), record.manager(), record.family(), record.tier() + 1);
        upgradeDimension = record.dimension(); mc.setScreen(null);
    }
    @SubscribeEvent public static void openPreviewControls(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if(!(event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen) || event.getCurrentScreen()!=null)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(upgradePreview!=null || managerRange!=null) {closeRanges();event.setCanceled(true);return;}
        var hand=mc.player.getMainHandItem().getItem() instanceof BuildingBlueprintItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if(mc.player.getItemInHand(hand).getItem() instanceof BuildingBlueprintItem && (hiddenDocument==null || !java.util.Objects.equals(hiddenDocument,com.stardew.craft.building.runtime.BuildingDrafts.id(mc.player.getItemInHand(hand)))))
            event.setNewScreen(new BuildingPreviewControlsScreen(hand));
    }
    private static BlockPos target;
    private static Direction facing;
    private static boolean self, pinned, held, aimed, ownsActionbar;
    private static ItemStack document = ItemStack.EMPTY;
    private static ResourceLocation world;
    private static ResourceLocation family;
    private static int sequence, ticks;
    private static List<BlockPos> flashingObstructions = List.of();
    private static ResourceLocation flashingDimension;
    private static int flashStarted, flashUntil;
    private static net.minecraft.nbt.CompoundTag pins=new net.minecraft.nbt.CompoundTag();
    public static void flashObstructions(ResourceLocation dimension, List<BlockPos> positions) {
        flashingDimension = dimension;
        flashingObstructions = List.copyOf(new java.util.LinkedHashSet<>(positions));
        flashStarted = ticks;
        flashUntil = ticks + 40;
    }
    public static void acceptPins(net.minecraft.nbt.CompoundTag tag){
        pins=tag.copy();var selected=com.stardew.craft.building.runtime.BuildingDrafts.id(document);
        if(!pinned || target==null || selected==null)return;
        for(var raw:tag.getList("Pins",10)){var pin=(net.minecraft.nbt.CompoundTag)raw;
            if(selected.equals(pin.getUUID("Id")) && target.asLong()==pin.getLong("Anchor") && facing.getName().equals(pin.getString("Facing"))) {
                preview=new BuildingPreviewPayload(target,facing,false,sequence,pin.getString("Issue"),target,BlockPos.of(pin.getLong("Min")),BlockPos.of(pin.getLong("Max")),BlockPos.of(pin.getLong("InnerMin")),BlockPos.of(pin.getLong("InnerMax")),BlockPos.of(pin.getLong("Manager")),new net.minecraft.resources.ResourceLocation(pin.getString("Family")),pin.getInt("Tier"));break;
            }
        }
    }
    private static AABB managerRange;
    private static long managerUntil;
    private static ResourceLocation managerDimension;
    private BuildingPlacementPreview() {}

    public static boolean rangeVisible(net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max) {
        return managerRange != null && managerRange.equals(box(min,max));
    }
    public static void toggleRange(net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max) {
        var mc = Minecraft.getInstance(); if(mc.level == null) return;
        managerRange = rangeVisible(min,max) ? null : box(min,max);
        managerUntil = Long.MAX_VALUE; managerDimension = mc.level.dimension().location();
    }
    public static void showManager(BuildingManagerInfoPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(StardewConfirmDialogScreen.createQuestionDialog(StardewQuestionDialogSpec.of(payload.text(), List.of(
                Component.translatable("building.stardewcraft.show_range"), Component.translatable("gui.done")), choice -> {
            if (choice == 0 && mc.level != null) {
                managerRange = box(payload.min(), payload.max()); managerUntil = mc.level.getGameTime() + 400;
                managerDimension = mc.level.dimension().location();
            }
            mc.setScreen(null);
        }, 1)));
    }
    public static void accept(BuildingPreviewPayload payload) {
        if (target != null && payload.sequence() == sequence && payload.anchor().equals(target)
                && payload.facing() == facing && payload.self() == self && payload.family().equals(family)) preview = payload;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); ticks++;
        if (mc.player == null || mc.level == null) {
            preview = null; target = null; managerRange = null; upgradePreview = null; pinned = false; held = false;
            document = ItemStack.EMPTY; pins=new net.minecraft.nbt.CompoundTag(); world = null;
            flashingObstructions=List.of();flashingDimension=null;BuildingTemplatePreview.clear(); return;
        }
        if (!mc.level.dimension().location().equals(world)) {
            preview = null; target = null; pinned = false; document = ItemStack.EMPTY; closeRanges();
            flashingObstructions=List.of();flashingDimension=null;world = mc.level.dimension().location();
        }
        if (mc.screen != null) {
            held = false;
            while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {}
            return;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof BuildingManagerItem) && !(stack.getItem() instanceof BuildingBlueprintItem)) {
            hand = InteractionHand.OFF_HAND; stack = mc.player.getOffhandItem();
        }
        if(hiddenDocument!=null){
            if(hiddenDocument.equals(com.stardew.craft.building.runtime.BuildingDrafts.id(stack))){held=false;return;}
            hiddenDocument=null;
        }
        boolean manager = stack.getItem() instanceof BuildingManagerItem;
        held = manager || stack.getItem() instanceof BuildingBlueprintItem;
        if (!held) {
            // Switching to a tool leaves a fixed plan visible; consuming/losing it removes the ghost.
            boolean stillOwned = !document.isEmpty() && mc.player.getInventory().contains(document);
            if (!pinned || !stillOwned) { preview = null; target = null; pinned = false; }
            while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {}
            return;
        }
        ResourceLocation nextFamily = manager ? ((BuildingManagerItem) stack.getItem()).family() : ((BuildingBlueprintItem) stack.getItem()).family();
        if (!manager && !com.stardew.craft.building.runtime.PrefabDefinitions.supported(nextFamily)) {
            preview = null; target = null;
            while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {}
            return;
        }
        BlockPos pin = manager ? null : BuildingBlueprintItem.pinned(stack, mc.level);
        var hitResult = manager ? mc.player.pick(mc.player.getBlockReach(), 1, false) : BuildingBlueprintItem.target(mc.player);
        BlockPos next;
        if (pin != null) next = pin;
        else if (hitResult instanceof BlockHitResult hit && hitResult.getType() == HitResult.Type.BLOCK && (manager || hit.getDirection() == Direction.UP))
            next = manager ? new BlockPlaceContext(mc.player, hand, stack, hit).getClickedPos() : BuildingBlueprintItem.previewTargetAnchor(stack,hit.getBlockPos(),BuildingBlueprintItem.facing(stack,mc.player));
        else {
            preview = null; target = null; pinned = false;
            while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {}
            return;
        }
        if (next == null) {
            preview = null; target = null; pinned = false;
            while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {}
            return;
        }
        Direction nextFacing = manager ? mc.player.getDirection().getOpposite() : BuildingBlueprintItem.facing(stack,mc.player);
        boolean changed = !next.equals(target) || nextFacing != facing || manager != self || !nextFamily.equals(family)
                || pinned != (pin != null) || !ItemStack.isSameItemSameTags(document, stack);
        if (changed) {
            target = next; facing = nextFacing; self = manager; family = nextFamily; pinned = pin != null; sequence++;
            document = stack.copy();
            int tier = BuildingBlueprintItem.draft(stack).getInt("MoveTier");
            preview = manager ? null : (BuildingBlueprintItem.isMove(stack) ? BuildingTemplatePreview.describeMove(BuildingBlueprintItem.draft(stack).getUUID("MoveBuilding"),family,tier,target,facing,sequence) : BuildingTemplatePreview.describe(family, tier == 0 ? 1 : tier, target, facing, sequence));
        }
        if (preview == null && !manager) {
            int tier = BuildingBlueprintItem.draft(stack).getInt("MoveTier");
            preview = (BuildingBlueprintItem.isMove(stack) ? BuildingTemplatePreview.describeMove(BuildingBlueprintItem.draft(stack).getUUID("MoveBuilding"),family,tier,target,facing,sequence) : BuildingTemplatePreview.describe(family, tier == 0 ? 1 : tier, target, facing, sequence));
        }
        aimed = !pinned || preview != null && BuildingBlueprintItem.aimsAt(mc.player, box(preview.min(), preview.max()));
        while (com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.consumeClick()) {
            if (!manager && aimed) PacketDistributor.sendToServer(new com.stardew.craft.network.payload.BuildingRotatePayload(hand, mc.player.isShiftKeyDown()));
        }
        if ((changed || ticks % 5 == 0) && aimed) PacketDistributor.sendToServer(new BuildingPreviewRequestPayload(target, facing, self, sequence));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean inspecting=upgradePreview!=null && mc.level.dimension().location().equals(upgradeDimension);
        BuildingPreviewPayload preview = inspecting?null:BuildingPlacementPreview.preview;
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        // Upgrade inspection is only the target bounds. It must never select or draw a template.
        if(inspecting) {
            drawEdges(pose,buffers,camera,com.stardew.craft.building.runtime.BuildingOutline.edges(
                    box(upgradePreview.structureMin(),upgradePreview.structureMax())),0xFFECC474);
        }
        if (preview != null && !preview.self()) {
            // Draw before borrowing the line buffer: model rendering flushes its own buffers.
            if (BuildingBlueprintItem.isMove(document)) BuildingTemplatePreview.selectMove(BuildingBlueprintItem.draft(document).getUUID("MoveBuilding"));
            else BuildingTemplatePreview.select(preview.family(), preview.tier());
            BuildingTemplatePreview.drawWorld(pose, preview.anchor(), com.stardew.craft.building.runtime.PrefabDefinitions.rotation(preview.facing()), preview.issue().equals("valid"));
        }
        if (preview != null) {
            boolean valid = preview.issue().equals("valid"), checking = preview.issue().equals("checking");
            int reservation = checking ? 0xFF9AA5AB : valid ? 0xFFECC474 : 0xFFE56F62;
            int current = checking ? 0xFF9AA5AB : valid ? 0xFF94C5A1 : 0xFFE56F62;
            AABB outer = box(preview.min(), preview.max()), inner = box(preview.structureMin(), preview.structureMax());
            drawEdges(pose, buffers, camera, com.stardew.craft.building.runtime.BuildingOutline.edges(outer), reservation);
            if (!preview.self()) drawEdges(pose, buffers, camera, com.stardew.craft.building.runtime.BuildingOutline.excluding(inner, outer), current);
            if (!valid && !checking) drawEdges(pose,buffers,camera,com.stardew.craft.building.runtime.BuildingOutline.edges(new AABB(preview.problem())),0xFFE56F62);
        }
        if(!inspecting && mc.level.dimension().location().toString().equals(pins.getString("Dimension"))) {
            var selected=com.stardew.craft.building.runtime.BuildingDrafts.id(document);
            for(var raw:pins.getList("Pins",10)) {
                var pin=(net.minecraft.nbt.CompoundTag)raw;var id=pin.getUUID("Id");
                if(preview!=null && id.equals(selected))continue;
                boolean owned=false;for(int slot=0;slot<mc.player.getInventory().getContainerSize();slot++)if(id.equals(com.stardew.craft.building.runtime.BuildingDrafts.id(mc.player.getInventory().getItem(slot)))){owned=true;break;}
                if(!owned)continue;
                if(pin.hasUUID("Moving"))BuildingTemplatePreview.selectMove(pin.getUUID("Moving"));else BuildingTemplatePreview.select(new net.minecraft.resources.ResourceLocation(pin.getString("Family")),pin.getInt("Tier"));
                boolean valid=pin.getString("Issue").equals("valid");
                BuildingTemplatePreview.drawWorld(pose,BlockPos.of(pin.getLong("Anchor")),com.stardew.craft.building.runtime.PrefabDefinitions.rotation(Direction.byName(pin.getString("Facing"))),valid);
                var outer=box(BlockPos.of(pin.getLong("Min")),BlockPos.of(pin.getLong("Max")));var inner=box(BlockPos.of(pin.getLong("InnerMin")),BlockPos.of(pin.getLong("InnerMax")));
                drawEdges(pose,buffers,camera,com.stardew.craft.building.runtime.BuildingOutline.edges(outer),valid?0xFFECC474:0xFFE56F62);
                drawEdges(pose,buffers,camera,com.stardew.craft.building.runtime.BuildingOutline.excluding(inner,outer),valid?0xFF94C5A1:0xFFE56F62);
            }
        }
        if (!flashingObstructions.isEmpty() && ticks < flashUntil
                && mc.level.dimension().location().equals(flashingDimension)
                && (ticks - flashStarted) / 4 % 2 == 0) {
            for (BlockPos pos : flashingObstructions) {
                drawEdges(pose, buffers, camera,
                        com.stardew.craft.building.runtime.BuildingOutline.edges(new AABB(pos).inflate(.012)),
                        0xFFFF3B30);
            }
        }
        if (ticks >= flashUntil) flashingObstructions = List.of();
        if (managerRange != null && mc.level.getGameTime() < managerUntil && mc.level.dimension().location().equals(managerDimension)) {
            drawEdges(pose,buffers,camera,com.stardew.craft.building.runtime.BuildingOutline.edges(managerRange),0xFF94C5A1);
        }
        pose.popPose();
    }
    private static void drawEdges(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
            net.minecraft.world.phys.Vec3 camera,List<com.stardew.craft.building.runtime.BuildingOutline.Edge> edges,int color) {
        com.stardew.craft.client.render.PortalHintRenderer.renderEdgesOnly(buffers,pose,camera,edges,color);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.screen==null && mc.player!=null && upgradePreview!=null) {
            var text=Component.translatable("building.stardewcraft.range_close_hint");
            var g=event.getGuiGraphics();var font=com.stardew.craft.client.font.StardewFonts.small();
            float reading = com.stardew.craft.client.font.StardewFonts.readingScale();
            int lineHeight = com.stardew.craft.client.font.StardewFonts.lineHeight(font);
            int w = Math.max(24, Math.min((int) ((mc.getWindow().getGuiScaledWidth() - 16) / reading), font.width(text) + 24));
            int h = font.split(text, w - 20).size() * (lineHeight + 2) + 16;
            reading = com.stardew.craft.client.gui.common.ReadingTextLayout.fitHudScale(reading, w, h,
                    mc.getWindow().getGuiScaledWidth() - 16, mc.getWindow().getGuiScaledHeight() - 55);
            int y = mc.getWindow().getGuiScaledHeight() - Math.round(h * reading) - 55;
            g.pose().pushPose(); g.pose().translate(8, y, 0); g.pose().scale(reading, reading, 1);
            com.stardew.craft.client.gui.common.CommonGuiTextures.drawTextureBox(g, 0, 0, w, h, 1, true);
            int yy = 8;
            for (var line : font.split(text, w - 20)) { g.drawString(font, line, 10, yy, 0xFF5C2B00, false); yy += lineHeight + 2; }
            g.pose().popPose();
        }
        if (mc.screen != null || mc.player == null || upgradePreview!=null || !held || target == null || self) {
            if (ownsActionbar) { mc.gui.setOverlayMessage(Component.empty(), false); ownsActionbar = false; }
            return;
        }
        Component use = mc.options.keyUse.getTranslatedKeyMessage();
        Component rotate = com.stardew.craft.client.ModKeyMappings.BUILDING_ROTATE.getTranslatedKeyMessage();
        Component shift = mc.options.keyShift.getTranslatedKeyMessage();
        Component placement = pinned ? (aimed ? Component.translatable("building.stardewcraft.controls_confirm", shift, use, use)
                : Component.translatable("building.stardewcraft.controls_unpin",shift,use))
                : Component.translatable("building.stardewcraft.controls_place", use);
        Component rotation = Component.translatable("building.stardewcraft.controls_rotate", rotate, shift, rotate);
        Component escape=Component.translatable("building.stardewcraft.controls_menu");
        Component hint = placement.copy().append("  ").append(aimed?rotation:Component.empty()).append("  ").append(escape);
        if (mc.font.width(hint) > mc.getWindow().getGuiScaledWidth() - 16) hint = (ticks / 50 % 2 == 0 || !aimed ? placement : rotation).copy().append("  ").append(escape);
        mc.gui.setOverlayMessage(highlightKeys(hint), false); ownsActionbar = true;
    }
    static Component highlightKeys(Component source) {
        var result=Component.empty();String text=source.getString();int offset=0;
        var matcher=java.util.regex.Pattern.compile("【[^】]+】|\\[[^\\]]+\\]").matcher(text);
        while(matcher.find()) {result.append(Component.literal(text.substring(offset,matcher.start())));
            result.append(Component.literal(matcher.group()).withStyle(net.minecraft.ChatFormatting.YELLOW,net.minecraft.ChatFormatting.BOLD));offset=matcher.end();}
        return result.append(Component.literal(text.substring(offset)));
    }
    private static AABB box(BlockPos min, BlockPos max) { return new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()); }
}
