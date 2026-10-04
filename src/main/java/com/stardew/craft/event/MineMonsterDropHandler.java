package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.monster.*;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.item.trinket.TrinketDropService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.shop.MonsterSlayerGoalRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;
import java.util.Set;

/** Source spawn tables and death-time extras follow GameLocation.monsterDrop ordering. */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class MineMonsterDropHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        if (!(entity instanceof StardewMonsterEntity nativeMonster) || !nativeMonster.initialized()) {
            addExternalBurglarReroll(event, serverLevel);
            return;
        }
        Set<String> tags = entity.getTags();
        Collection<ItemEntity> drops = event.getDrops();
        drops.clear();
        nativeMonster.acceptFinalDeath();
        if (!nativeMonster.claimSettlement(MonsterState.Settlement.DROPS_AND_PROGRESS)) return;

        RandomSource random = serverLevel.getRandom();

        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        // Source GameLocation records the species before MetalHead.getExtraDropItems.
        if (killer != null && tags.contains("sd_mob_metal_head"))
            PlayerDataManager.getPlayerData(killer).addMonsterKills("source:Metal Head", 1);
        boolean burglar = killer != null && com.stardew.craft.combat.equipment.RingEffectHandler.hasBurglar(killer);
        boolean specialMineDrop = tags.contains(com.stardew.craft.mining.OrdinaryMineSpecialLoot.TAG);
        if (specialMineDrop) {
            // MineShaft's special item replaces GameLocation.monsterDrop entirely.
            addDrop(drops, entity, com.stardew.craft.mining.OrdinaryMineSpecialLoot.roll(serverLevel,
                    entity instanceof com.stardew.craft.entity.monster.GreenSlimeEntity slime ? slime.sourceFloor()
                            : com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(entity.blockPosition()), entity.blockPosition()));
        } else {
            var born = new java.util.ArrayList<String>(nativeMonster.monsterState().bornDrops().stream().map(Object::toString).toList());
            var extra = MonsterExtraLoot.roll(entity, killer, random);
            if (burglar) {
                born.addAll(nativeMonster.monsterState().rerollDrops(random).stream().map(Object::toString).toList());
            }
            for (var stack : MonsterSourceLoot.materialize(born, random)) addDrop(drops, entity, stack);
            for (var stack : extra) addDrop(drops, entity, stack);
            // Trinkets are separate world debris in SDV, outside Book_Void's duplicate list.
            var trinkets = new java.util.ArrayList<ItemEntity>();
            if (killer != null) TrinketDropService.tryAddMonsterDrop(trinkets, entity, killer, random);
            if (burglar) for (var stack : MonsterExtraLoot.roll(entity, killer, random)) addDrop(drops, entity, stack);
            if (killer != null) {
                BookPowerEffects.applyVoidMonsterDropDuplicate(PlayerDataManager.getPlayerData(killer), drops, entity, random);
                addDrop(drops, entity, com.stardew.craft.secretnote.SecretNoteService.tryCreateFromSource(killer, random, 0.033F));
                for (var stack : MonsterExtraLoot.common(killer, entity, random)) addDrop(drops, entity, stack);
            }
            drops.addAll(trinkets);
        }
        if (killer != null) {
            com.stardew.craft.book.BookAcquisitionService.recordMonsterKilledAndMaybeAddVoidBook(killer, drops, entity, random, !specialMineDrop);
            if (!specialMineDrop && tags.contains("sd_secret_woods_slime") && random.nextDouble() < .1)
                addDrop(drops, entity, MonsterSourceLoot.item("292", 1));
        }

        // ---- Monster Slayer kill tracking (SDV Gil goals) ----
        if (event.getSource() != null && event.getSource().getEntity() instanceof ServerPlayer player) {
            java.util.Set<String> progressedGoals = new java.util.LinkedHashSet<>();
            // Prismatic Slime has its own name and bred baby slimes are skipped by Stats.monsterKilled.
            boolean countsForSlayerGoals = !tags.contains("sd_mob_prismatic_slime")
                    && !(entity instanceof com.stardew.craft.entity.monster.GreenSlimeEntity slime && !slime.firstGeneration());
            if (countsForSlayerGoals) {
                for (String tag : tags) {
                    progressedGoals.addAll(MonsterSlayerGoalRegistry.getGoalKeysForTag(tag));
                }
            }
            PlayerStardewData slayerData = PlayerDataManager.getPlayerData(player);
            progressedGoals.forEach(goalKey -> slayerData.addMonsterKills(goalKey, 1));
            // One kill event per monster: SDV matches monster.Name.Contains(target) once per quest/objective.
            java.util.Set<String> mobTags = new java.util.LinkedHashSet<>();
            for (String tag : tags) {
                if (tag.startsWith("sd_mob_")) mobTags.add(tag);
            }
            if (!mobTags.isEmpty()) {
                com.stardew.craft.quest.StardewQuestEvents.fireMonsterSlain(player, mobTags);
            }
            java.util.Set<String> orderNames = new java.util.LinkedHashSet<>(mobTags);
            if (tags.contains("sd_mob_prismatic_slime")) orderNames.add("Prismatic Slime");
            if (!orderNames.isEmpty()) {
                // SlayObjective.IgnoreFarmMonsters defaults to true.
                boolean onFarm = com.stardew.craft.core.FarmAreaResolver.isInFarmArea(serverLevel, entity.blockPosition());
                com.stardew.craft.specialorder.SpecialOrderManager.recordMonsterSlain(player, orderNames, onFarm);
            }
            com.stardew.craft.festival.desert.DesertFestivalMarlonChallengeService.recordMonsterSlain(player, tags);
            if (MonsterFactory.ownedFloor(nativeMonster) != null
                    && nativeMonster.monsterState().context().floor() == com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(entity.blockPosition())) {
                com.stardew.craft.mining.OrdinaryMineRuntime.monsterKilled(serverLevel, player, entity.blockPosition());
            }
        }
    }

    private static void addExternalBurglarReroll(
            LivingDropsEvent event,
            ServerLevel level
    ) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !com.stardew.craft.combat.equipment.RingEffectHandler
                        .hasBurglar(player)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(
                        LootContextParams.KILLER_ENTITY,
                        event.getSource().getEntity()
                )
                .withOptionalParameter(
                        LootContextParams.DIRECT_KILLER_ENTITY,
                        event.getSource().getDirectEntity()
                )
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        LootTable lootTable = level.getServer()
                .getLootData()
                .getLootTable(entity.getLootTable());
        lootTable.getRandomItems(params)
                .forEach(stack -> addDrop(event.getDrops(), entity, stack));
    }

    private static void addDrop(Collection<ItemEntity> drops, LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity itemEntity = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack);
        itemEntity.setDefaultPickUpDelay();
        drops.add(itemEntity);
    }
}
