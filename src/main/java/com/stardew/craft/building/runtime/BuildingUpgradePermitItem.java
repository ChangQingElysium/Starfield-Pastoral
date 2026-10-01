package com.stardew.craft.building.runtime;

import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Paid permission to upgrade one eligible prefab at home, not an instant shop upgrade. */
public final class BuildingUpgradePermitItem extends com.stardew.craft.item.SimpleStardewItem {
    private final ResourceLocation family;
    private final int targetTier;
    public BuildingUpgradePermitItem(ResourceLocation family, int targetTier, Properties properties) {
        super("stardewcraft.type.building", -1, properties.stacksTo(1)); this.family = family; this.targetTier = targetTier;
    }
    public ResourceLocation family() { return family; }
    public int targetTier() { return targetTier; }
    public boolean availableFor(ServerPlayer player) {
        if(!PrefabDefinitions.available(family)||targetTier<2||targetTier>PrefabDefinitions.maxTier(family))return false;
        var binding=com.stardew.craft.api.v1.building.StardewBuildingFamilies.find(family).orElse(null);
        if(binding==null||!binding.upgrades().containsKey(targetTier)||binding.upgrades().get(targetTier).get()!=this)return false;
        var farm = FarmInstanceRegistry.get(player.serverLevel().getServer()).getFarmForPlayer(player.getUUID());
        if (farm == null) return false;
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        return data.all().stream().anyMatch(record -> record.farmId().equals(farm.getInstanceId())
                && record.family().equals(family) && record.mode() == BuildingRecord.Mode.PREFAB
                && record.tier() == targetTier - 1 && record.phase() == BuildingRecord.Phase.READY
                && data.transfer(record.id()) == null && BuildingService.canManage(player, record));
    }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,
            @javax.annotation.Nullable net.minecraft.world.level.Level level,
            java.util.List<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("building.stardewcraft.upgrade_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        return BuildingUpgradeService.use(player, context.getClickedPos(), context.getItemInHand(), this)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}
