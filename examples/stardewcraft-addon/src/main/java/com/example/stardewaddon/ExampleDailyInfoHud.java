package com.example.stardewaddon;

import com.stardew.craft.api.v1.client.StardewClientConstructionProgress;
import com.stardew.craft.api.v1.client.StardewClientDailyInfo;
import com.stardew.craft.api.v1.client.StardewConstructionOrderSnapshot;
import com.stardew.craft.api.v1.client.StardewConstructionProgressSnapshot;
import com.stardew.craft.api.v1.client.StardewDailyInfoSnapshot;
import com.stardew.craft.api.v1.client.StardewHudSnapshot;
import com.stardew.craft.api.v1.client.StardewHudRenderEvent;
import com.stardew.craft.api.v1.client.StardewToolUpgradeSnapshot;
import com.stardew.craft.api.v1.client.StardewQueenOfSauceSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Minimal alignment example. Render an actual ItemStack, using only the public addon API. */
@Mod.EventBusSubscriber(modid = "example_stardew_addon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExampleDailyInfoHud {
    private ExampleDailyInfoHud() {}

    /** Use this to decide whether to show a learnable Queen of Sauce reminder in your overlay. */
    public static java.util.Optional<StardewQueenOfSauceSnapshot> queenOfSauceToLearn() {
        return StardewClientDailyInfo.current()
                .flatMap(StardewDailyInfoSnapshot::queenOfSauce)
                .filter(StardewQueenOfSauceSnapshot::canLearnRecipe);
    }

    /** Active Robin orders can be empty after sync and can contain multiple farm buildings. */
    public static java.util.List<StardewConstructionOrderSnapshot> robinOrders() {
        return StardewClientConstructionProgress.current()
                .map(StardewConstructionProgressSnapshot::orders)
                .orElseGet(java.util.List::of);
    }

    @SubscribeEvent
    public static void afterMainHud(StardewHudRenderEvent event) {
        StardewDailyInfoSnapshot info = StardewClientDailyInfo.current().orElse(null);
        if (info == null) return;
        StardewToolUpgradeSnapshot upgrade = info.toolUpgrade().orElse(null);
        if (upgrade == null) return;
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(upgrade.resultItemId()));
        if (icon.isEmpty()) return;
        StardewHudSnapshot hud = event.hud();
        var panel = hud.allocatedBounds();
        float scale = Math.min(hud.scale(), Math.min(hud.screenWidth(), hud.screenHeight()) / 16.0F);
        float size = 16 * scale;
        float gap = 2 * scale;
        float x = panel.x() - gap - size;
        float y = panel.y();
        if (x < 0) {
            x = panel.right() + gap;
            if (x + size > hud.screenWidth()) { x = panel.x(); y = panel.bottom() + gap; }
        }
        x = Math.max(0, Math.min(x, hud.screenWidth() - size));
        y = Math.max(0, Math.min(y, hud.screenHeight() - size));
        var graphics = event.graphics();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(icon, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }
}
