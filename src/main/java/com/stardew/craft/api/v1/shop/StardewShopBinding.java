package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Binds a shop to an NPC interaction or a right-clickable world region. */
public record StardewShopBinding(
        String shop,
        Optional<String> npc,
        Optional<ResourceLocation> dimension,
        Optional<BlockPoint> min,
        Optional<BlockPoint> max,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewShopBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("shop").forGetter(StardewShopBinding::shop),
            Codec.STRING.optionalFieldOf("npc").forGetter(StardewShopBinding::npc),
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(StardewShopBinding::dimension),
            BlockPoint.CODEC.optionalFieldOf("min").forGetter(StardewShopBinding::min),
            BlockPoint.CODEC.optionalFieldOf("max").forGetter(StardewShopBinding::max),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewShopBinding::availableWhen)
    ).apply(instance, StardewShopBinding::new));

    public StardewShopBinding {
        availableWhen = List.copyOf(availableWhen);
    }

    public record BlockPoint(int x, int y, int z) {
        public static final Codec<BlockPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(BlockPoint::x),
                Codec.INT.fieldOf("y").forGetter(BlockPoint::y),
                Codec.INT.fieldOf("z").forGetter(BlockPoint::z)
        ).apply(instance, BlockPoint::new));
    }
}
