package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * /stardew shop <shopId>
 *
 * Debug command to open the SDV shop screen for the executing player.
 * Valid shopIds: SeedShop, FishShop, AnimalShop  (defined in ShopRegistry)
 */
@SuppressWarnings("null")
public class ShopDebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("shop")
                    .then(Commands.argument("shopId", StringArgumentType.word())
                        .suggests((ctx, builder) ->
                            SharedSuggestionProvider.suggest(ShopRegistry.allShopIds(), builder))
                        .executes(ShopDebugCommand::openShop)
                    )
                )
        );
    }

    private static int openShop(CommandContext<CommandSourceStack> ctx) {
        String shopId = StringArgumentType.getString(ctx, "shopId");

        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }

        ShopRegistry.ShopDefinition shop = ShopRegistry.get(shopId);
        if (shop == null) {
            source.sendFailure(Component.literal(
                "Unknown shopId: " + shopId + ". Available: " + ShopRegistry.allShopIds()));
            return 0;
        }

        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(shopId, shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            shopId,
            money,
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new java.util.ArrayList<>(shop.acceptedSellTypes())
        );

        PacketDistributor.sendToPlayer(player, payload);
        return 1;
    }
}
