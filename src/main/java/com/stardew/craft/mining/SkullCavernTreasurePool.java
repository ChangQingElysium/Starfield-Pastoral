package com.stardew.craft.mining;

import com.stardew.craft.api.v1.world.StardewWorldLootPools;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.world.data.WorldLootPoolData;

import java.util.List;

/** MineShaft.getTreasureRoomItem: 26 equally weighted source slots, with progression gates.
 * Unimplemented rewards remain empty instead of being replaced with unrelated geodes. */
public final class SkullCavernTreasurePool {

    private SkullCavernTreasurePool() {}

    /** Missing source items are intentionally empty; their probability slots remain. */
    public static ItemStack roll(RandomSource random) {
        return roll(random, null);
    }

    public static ItemStack roll(RandomSource random, ServerPlayer player) {
        if (player != null
                && PlayerDataManager.getPlayerData(player).hasMastery(SkillType.FARMING)
                && random.nextDouble() < 0.02) {
            return stack(ModItems.GOLDEN_ANIMAL_CRACKER, 1);
        }
        if (StardewTrinketItem.canSpawnFor(player) && random.nextDouble() < 0.045) {
            ItemStack trinket = StardewTrinketItem.createRandomNaturalTrinket(random, player);
            if (!trinket.isEmpty()) {
                return trinket;
            }
        }
        if (player == null) {
            return rollSlot(random.nextInt(26),random,null);
        }
        List<ItemStack> rewards = WorldLootPoolData.resolve(
                StardewWorldLootPools.SKULL_CAVERN_TREASURE,
                "default",
                player.serverLevel(),
                player,
                random);
        return rewards.isEmpty() ? ItemStack.EMPTY : rewards.getFirst();
    }

    public static void registerQuery() {
        com.stardew.craft.api.v1.query.StardewItemQueries.register(
            new net.minecraft.resources.ResourceLocation("stardewcraft","skull_treasure_slot"),
            com.mojang.serialization.Codec.intRange(0,25).fieldOf("slot").codec(),
            (context,slot)->{
                var result=rollSlot(slot,net.minecraft.util.RandomSource.create(context.random().nextLong()),context.player());
                return result.isEmpty()?List.of():List.of(result);
            });
    }
    public static ItemStack rollSlot(int slot,RandomSource r,ServerPlayer player) {
        return switch(slot) {
            case 0->object("288",5);
            case 1->stack(ModItems.BOMB_ITEM,10);
            case 2->{
                var host=player==null?null:player.getServer().getPlayerList().getPlayers().stream()
                        .filter(p->player.getServer().isSingleplayerOwner(p.getGameProfile())).findFirst().orElse(player);
                boolean unlocked=host!=null && com.stardew.craft.mail.MailService.hasOrWillReceiveMail(host,"volcanoShortcutUnlocked");
                yield unlocked && r.nextDouble()<.66?object("848",5+(1+r.nextInt(3))*5):object("275",5);
            }
            case 3->object("773",2+r.nextInt(3));
            case 4->object("749",5+(r.nextDouble()<.25?5:0));
            case 5->object("688",5);
            case 6->object("681",1+r.nextInt(3));
            case 7->object(Integer.toString(628+r.nextInt(6)),1);
            case 8->object("645",1+r.nextInt(2));
            case 9->object("621",4);
            case 10->r.nextDouble()<.33?object("802",15):object(Integer.toString(472+r.nextInt(27)),(1+r.nextInt(4))*5);
            case 11->object("286",15);
            case 12->object(r.nextDouble()<.5?"265":"437",1);
            case 13->object("439",1);
            case 14->r.nextDouble()<.33?object(r.nextDouble()<.5?"226":"732",5):object("349",2+r.nextInt(3));
            case 15->object("337",2+r.nextInt(2));
            case 16->r.nextDouble()<.33?object(r.nextDouble()<.5?"226":"732",5):object(Integer.toString(235+r.nextInt(10)),5);
            case 17->object("74",1);
            case 18->named("crystalarium");
            case 19->named("seed_maker");
            case 20->named("auto_grabber");
            case 21->named(r.nextBoolean()?"red_cowboy_hat":"blue_cowboy_hat");
            case 22->{
                if(player!=null && PlayerDataManager.getPlayerData(player).hasMailFlag("sawQiPlane"))
                    yield object(PlayerDataManager.getPlayerData(player).hasMastery(SkillType.FORAGING)?"GoldenMysteryBox":"MysteryBox",5);
                yield object("749",5+(r.nextDouble()<.25?5:0));
            }
            case 23->named("white_turban");
            case 24->named("auto_petter");
            case 25->named("dark_cowboy_hat");
            default->ItemStack.EMPTY;
        };
    }
    private static ItemStack object(String id,int count) {
        return com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack("(O)"+id,count);
    }
    private static ItemStack named(String name) {
        return com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack("stardewcraft:"+name,1);
    }

    private static ItemStack stack(java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike> supplier,
                                   int count) {
        return new ItemStack(supplier.get(), count);
    }

}
