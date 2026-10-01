package com.stardew.craft.client.model;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

/** Native board models and their rotated centers, exported from the approved crate assets. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SupplyCrateModels {
    private static final String[] MODELS = {"supply_crate_blue", "supply_crate_square", "supply_crate_angled"};
    private static final String[] PARTS = {"floor", "front", "back", "left", "right", "lid_left", "lid_right"};
    private static final Vec3[][] CENTERS = {
            {new Vec3(0.500000000, 0.031250000, 0.500000000), new Vec3(0.631547430, 0.375000000, 0.182416411), new Vec3(0.368452570, 0.375000000, 0.817583589), new Vec3(0.182416411, 0.375000000, 0.368452570), new Vec3(0.817583589, 0.375000000, 0.631547430), new Vec3(0.326772588, 0.718750000, 0.428246856), new Vec3(0.673227412, 0.718750000, 0.571753144)},
            {new Vec3(0.500000000, 0.031250000, 0.500000000), new Vec3(0.500000000, 0.375000000, 0.218750000), new Vec3(0.500000000, 0.375000000, 0.781250000), new Vec3(0.156250000, 0.375000000, 0.500000000), new Vec3(0.843750000, 0.375000000, 0.500000000), new Vec3(0.312500000, 0.718750000, 0.500000000), new Vec3(0.687500000, 0.718750000, 0.500000000)},
            {new Vec3(0.500000000, 0.031250000, 0.500000000), new Vec3(0.631547430, 0.343750000, 0.182416411), new Vec3(0.368452570, 0.343750000, 0.817583589), new Vec3(0.182416411, 0.343750000, 0.368452570), new Vec3(0.817583589, 0.343750000, 0.631547430), new Vec3(0.326772588, 0.656250000, 0.428246856), new Vec3(0.673227412, 0.656250000, 0.571753144)}
    };

    public static Vec3 center(int variant, int part) { return CENTERS[variant][part]; }

    public static ModelResourceLocation id(int variant, int part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID, "block/farm_debris/beach_supply_crates/" + MODELS[variant] + "/" + PARTS[part]), "standalone");
    }

    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (int variant = 0; variant < 3; variant++) for (int part = 0; part < 7; part++) event.register(id(variant, part));
    }
}
