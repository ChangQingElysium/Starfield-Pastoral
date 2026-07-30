package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

@SuppressWarnings("null")
public record OvernightSettlementPayload(
        List<ShippedItem> shippedItems,
        List<LevelUpData> levelUps,
        int passOutType,               // -1 = 未晕倒；>=0 = PassOutService.PassOutType.getId()
        int passOutMoneyLost,
        List<ItemStack> passOutLostItems,
        OvernightContext context
) implements CustomPacketPayload {

    /** 无晕倒的便捷构造（兼容旧调用点） */
    public OvernightSettlementPayload(List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(shippedItems, levelUps, -1, 0, List.of(), OvernightContext.unknown());
    }

    /** 兼容尚未提供日期/天气快照的旧调用点。 */
    public OvernightSettlementPayload(
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems
    ) {
        this(shippedItems, levelUps, passOutType, passOutMoneyLost, passOutLostItems, OvernightContext.unknown());
    }

    /** 是否包含晕倒数据 */
    public boolean hasPassOut() {
        return passOutType >= 0;
    }

    public static final Type<OvernightSettlementPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_settlement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightSettlementPayload> STREAM_CODEC = StreamCodec.composite(
            ShippedItem.STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::shippedItems,
            LevelUpData.STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::levelUps,
            ByteBufCodecs.VAR_INT, OvernightSettlementPayload::passOutType,
            ByteBufCodecs.VAR_INT, OvernightSettlementPayload::passOutMoneyLost,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::passOutLostItems,
            OvernightContext.STREAM_CODEC, OvernightSettlementPayload::context,
            OvernightSettlementPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightSettlementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OvernightSettlementPayload payload) {
        OvernightCollapseClientState.acceptSettlement(payload);
    }

    public record LevelUpData(int skillIndex, int newLevel) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUpData::skillIndex,
                ByteBufCodecs.VAR_INT, LevelUpData::newLevel,
                LevelUpData::new
        );
    }

    public record ShippedItem(ItemStack stack, int category, int pricePerItem) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ShippedItem> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, ShippedItem::stack,
                ByteBufCodecs.VAR_INT, ShippedItem::category,
                ByteBufCodecs.VAR_INT, ShippedItem::pricePerItem,
                ShippedItem::new
        );
    }

    /**
     * Immutable night snapshot. The client must not infer these values from the
     * live time/weather caches: those caches switch to the new day while the
     * end-of-night menus are still being shown.
     */
    public record OvernightContext(
            int previousDay,
            int previousSeason,
            int previousYear,
            int newDay,
            int newSeason,
            int newYear,
            String previousWeather
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OvernightContext> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousSeason());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousYear());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newSeason());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newYear());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.previousWeather());
                },
                buffer -> new OvernightContext(
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)
                )
        );

        public OvernightContext {
            previousDay = Math.max(1, previousDay);
            previousSeason = Math.max(0, Math.min(3, previousSeason));
            previousYear = Math.max(1, previousYear);
            newDay = Math.max(1, newDay);
            newSeason = Math.max(0, Math.min(3, newSeason));
            newYear = Math.max(1, newYear);
            previousWeather = previousWeather == null || previousWeather.isBlank() ? "Sun" : previousWeather;
        }

        public static OvernightContext unknown() {
            return new OvernightContext(1, 0, 1, 2, 0, 1, "Sun");
        }
    }
}
