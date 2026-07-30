package com.stardew.craft.world.interaction;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.interaction.StardewInteractionHint;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintContext;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintDecision;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import com.stardew.craft.api.v1.interaction.StardewInteractionHints;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeExtensionBlock;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.blockentity.BushBlockEntity;
import com.stardew.craft.blockentity.DailyStatueBlockEntity;
import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import com.stardew.craft.blockentity.UtilityAutomationAccess;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.entity.decor.CarpetEntity;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import com.stardew.craft.entity.npc.BooksellerEntity;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.entity.seat.CushionEntity;
import com.stardew.craft.event.LewisBasementEntranceEvents;
import com.stardew.craft.event.WildTreeShakeEvents;
import com.stardew.craft.festival.EggFestivalService;
import com.stardew.craft.festival.FlowerDanceService;
import com.stardew.craft.festival.LuauFestivalService;
import com.stardew.craft.festival.SpiritEveFestivalService;
import com.stardew.craft.festival.desert.DesertFestivalVendorService;
import com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService;
import com.stardew.craft.festival.nightmarket.NightMarketWarperService;
import com.stardew.craft.mastery.MasterySite;
import com.stardew.craft.mastery.MasterySiteInstaller;
import com.stardew.craft.museum.LostBookRegistry;
import com.stardew.craft.museum.LostBookService;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.shop.ShopInteractionBindings;
import com.stardew.craft.secretnote.SecretNote21Service;
import com.stardew.craft.secretnote.SecretNote31BushInteraction;
import com.stardew.craft.secretnote.SecretNoteFurnitureService;
import com.stardew.craft.secretnote.SecretNote20Service;
import com.stardew.craft.specialorder.SpecialOrderDropBoxAnchor;
import com.stardew.craft.specialorder.SpecialOrderDropBoxService;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.world.MutantBugLairService;
import com.stardew.craft.world.WitchAreaService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Server-authoritative, mutation-free resolver for the icon shown beside the
 * crosshair. A hint is emitted only when a state probe, authored binding, tag,
 * add-on provider, or current menu provider establishes an actual action.
 */
public final class InteractionHintService {
    private static final ResourceLocation ENTITY_INTERACTION_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "entity_interaction");
    private static final ResourceLocation LEWIS_BASEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "lewis_basement");
    private static final ResourceLocation WILD_TREE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "wild_tree");

    private InteractionHintService() {
    }

    public static Optional<StardewInteractionHint> resolveBlock(
            ServerPlayer player,
            BlockPos pos
    ) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        StardewInteractionHintContext context =
                new StardewInteractionHintContext(
                        player, null, pos, state);
        StardewInteractionHintDecision addon =
                StardewInteractionHints.dispatch(context);
        if (addon.handled()) {
            return Optional.ofNullable(addon.hint());
        }
        if (state.is(ModTags.Blocks.INTERACTION_HINT_NONE)) {
            return Optional.empty();
        }

        Optional<MapInteractionService.ReadableTarget> readable =
                MapInteractionService.findReadable(player, pos);
        if (readable.isPresent()) {
            MapInteractionService.ReadableTarget target = readable.get();
            return Optional.of(new StardewInteractionHint(
                    StardewInteractionHintType.LOOK,
                    target.read(),
                    target.definitionId()));
        }

        ResourceLocation lostBookId = LostBookRegistry.at(
                level.dimension().location(), pos);
        if (lostBookId != null) {
            boolean done = PlayerDataManager.getPlayerData(player)
                    .hasMailFlag(LostBookService.readFlag(lostBookId));
            return Optional.of(new StardewInteractionHint(
                    StardewInteractionHintType.LOOK,
                    done,
                    lostBookId));
        }

        Optional<StardewInteractionHint> authored =
                resolveAuthoredBlockInteraction(player, pos, state);
        if (authored.isPresent()) {
            return authored;
        }

        Probe probe = resolveStatefulBlock(player, pos, state);
        if (probe.handled()) {
            return Optional.ofNullable(probe.hint());
        }

        Optional<StardewInteractionHint> tagged =
                resolveTaggedBlock(state);
        if (tagged.isPresent()) {
            return tagged;
        }

        if (state.getMenuProvider(level, pos) != null) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        return Optional.empty();
    }

    public static Optional<StardewInteractionHint> resolveEntity(
            ServerPlayer player,
            Entity entity
    ) {
        BlockPos pos = entity.blockPosition();
        StardewInteractionHintContext context =
                new StardewInteractionHintContext(
                        player, entity, pos,
                        player.serverLevel().getBlockState(pos));
        StardewInteractionHintDecision addon =
                StardewInteractionHints.dispatch(context);
        if (addon.handled()) {
            return Optional.ofNullable(addon.hint());
        }
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_NONE)) {
            return Optional.empty();
        }

        if (entity instanceof StardewNpcEntity npc) {
            NpcInteractionService.InteractionHintProbe probe =
                    NpcInteractionService.probeInteractionHint(player, npc);
            if (!probe.visible()) {
                return Optional.empty();
            }
            StardewInteractionHintType type =
                    probe.kind()
                            == NpcInteractionService
                                    .InteractionHintKind.GIFT
                    ? StardewInteractionHintType.GIFT
                    : StardewInteractionHintType.TALK;
            return Optional.of(new StardewInteractionHint(
                    type,
                    probe.done(),
                    entityIdentity(entity)));
        }
        if (entity instanceof ServerPlayer targetPlayer
                && FlowerDanceService.canOpenPlayerDanceAsk(
                        player, targetPlayer)) {
            return Optional.of(hint(
                    StardewInteractionHintType.TALK,
                    entityIdentity(entity)));
        }
        if (SecretNote20Service.isTruckInteraction(entity)) {
            return SecretNote20Service.canUseTruck(
                    PlayerDataManager.getPlayerData(player))
                    ? Optional.of(hint(
                            StardewInteractionHintType.GRAB,
                            entityIdentity(entity)))
                    : Optional.empty();
        }
        if (EggFestivalService.isEggInteraction(entity)) {
            return EggFestivalService.canCollectEgg(player, entity)
                    ? Optional.of(hint(
                            StardewInteractionHintType.GRAB,
                            entityIdentity(entity)))
                    : Optional.empty();
        }
        if (entity instanceof BaseCoopAnimalEntity animal) {
            if (animal.getManagedAnimalId() <= 0L
                    || AnimalWorldData.get(player.serverLevel())
                            .getAnimal(animal.getManagedAnimalId())
                            .isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    entityIdentity(entity)));
        }
        if (entity instanceof MinecartStationEntity
                || entity instanceof BooksellerEntity
                || entity instanceof TravelingCartEntity
                || entity instanceof CamelMerchantEntity
                || entity instanceof CushionEntity) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    entityIdentity(entity)));
        }
        if (entity instanceof CarpetEntity) {
            return player.getMainHandItem().getItem()
                    instanceof BlockItem
                    ? Optional.of(hint(
                            StardewInteractionHintType.GRAB,
                            entityIdentity(entity)))
                    : Optional.empty();
        }
        Optional<StardewInteractionHint> tagged =
                resolveTaggedEntity(entity);
        if (tagged.isPresent()) {
            return tagged;
        }
        if (hasAuthoredInteractionAction(player, entity)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    entityIdentity(entity)));
        }
        return Optional.empty();
    }

    private static boolean hasAuthoredInteractionAction(
            ServerPlayer player,
            Entity entity
    ) {
        if (!(entity instanceof Interaction)) {
            return false;
        }
        if (entity.getTags().stream().anyMatch(tag ->
                tag.startsWith("sdv_portal_target:"))) {
            return true;
        }
        if (entity.getTags().contains(
                DesertFestivalSpecialInteractionService
                        .WARPER_MARKER_TAG)
                || entity.getTags().contains(
                        NightMarketWarperService
                                .INTERACTION_MARKER_TAG)) {
            return true;
        }
        if (!MasterySite.isMasteryDimension(player.level())) {
            return false;
        }
        for (SkillType skill : SkillType.values()) {
            if (entity.getTags().contains(
                    MasterySiteInstaller.interactionTagFor(skill))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<StardewInteractionHint>
            resolveAuthoredBlockInteraction(
                    ServerPlayer player,
                    BlockPos pos,
                    BlockState state
            ) {
        if (ShopInteractionBindings.canOpenBlock(player, pos)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        if (LuauFestivalService.canInteractWithSoup(player, pos)
                || LuauFestivalService.canOpenPierreFestivalShop(player)
                || SpiritEveFestivalService
                        .canOpenGoldenPumpkinChest(player, pos)
                || DesertFestivalVendorService.canOpenAtPlayer(player)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        if (SecretNote21Service.canTrigger(player, pos)
                || SecretNote31BushInteraction.canTrigger(player, pos)
                || SecretNoteFurnitureService.canClaimAt(player, pos)
                || WitchAreaService.isMagicInkInteraction(player, pos)
                || MutantBugLairService.isRewardChestInteraction(
                        player, pos)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        Optional<SpecialOrderDropBoxAnchor> dropBox =
                SpecialOrderDropBoxAnchor.at(pos);
        if (dropBox.isPresent()
                && SpecialOrderDropBoxService.activeDropBoxIds(player)
                        .contains(dropBox.get().dropBoxId())) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        if (MasterySite.isMasteryDimension(player.level())
                && (MasterySite.isCentralPedestal(pos)
                        || MasterySite.isDoorPos(pos))) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        if (state.is(ModBlocks.JUKEBOX.get())) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        if (LewisBasementEntranceEvents.canEnterBasement(
                player, player.getMainHandItem())) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    LEWIS_BASEMENT_ID));
        }
        if (WildTreeShakeEvents.canShake(player, pos)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    WILD_TREE_ID));
        }
        return Optional.empty();
    }

    private static Probe resolveStatefulBlock(
            ServerPlayer player,
            BlockPos pos,
            BlockState state
    ) {
        ServerLevel level = player.serverLevel();
        Block block = state.getBlock();
        if (block instanceof StardewCropBlock crop) {
            return Probe.handled(crop.isReadyForRightClickHarvest(
                    level, pos, state)
                    ? hint(StardewInteractionHintType.HARVEST,
                            blockIdentity(state))
                    : null);
        }
        if (block instanceof ForageBlock) {
            return Probe.handled(hint(
                    StardewInteractionHintType.HARVEST,
                    blockIdentity(state)));
        }
        if (block instanceof BerryBushBlock bush) {
            BlockPos mainPos = bush.findMainPos(level, pos, state);
            StardewTimeManager time = StardewTimeManager.get();
            boolean ready = false;
            if (mainPos != null && time != null) {
                BerryBushBlock.BerryKind berry =
                        BerryBushBlock.getBloomBerry(
                                time.getCurrentSeason(),
                                time.getCurrentDay());
                BlockEntity blockEntity =
                        level.getBlockEntity(mainPos);
                ready = berry != BerryBushBlock.BerryKind.NONE
                        && blockEntity instanceof BushBlockEntity data
                        && data.getLastHarvestAbsoluteDay()
                                != time.getAbsoluteDay()
                        && BerryBushBlock.hasBerriesToday(
                                mainPos, berry,
                                time.getAbsoluteDay());
            }
            return Probe.handled(ready
                    ? hint(StardewInteractionHintType.HARVEST,
                            blockIdentity(state))
                    : null);
        }
        if (block instanceof FruitTreeBlock
                || block instanceof FruitTreeExtensionBlock) {
            BlockPos root = FruitTreeBlock.findRoot(level, pos);
            boolean ready = root != null
                    && level.getBlockEntity(root)
                            instanceof FruitTreeBlockEntity tree
                    && tree.getFruitCount() > 0;
            return Probe.handled(ready
                    ? hint(StardewInteractionHintType.HARVEST,
                            blockIdentity(state))
                    : null);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnimalProduceSpotBlockEntity produce) {
            boolean ready = !produce.getProduceStack().isEmpty()
                    || produce.getProduceLedgerEntryId() > 0L;
            return Probe.handled(ready
                    ? hint(StardewInteractionHintType.HARVEST,
                            blockIdentity(state))
                    : null);
        }
        if (blockEntity instanceof DailyStatueBlockEntity statue) {
            return Probe.handled(statue.isReady()
                    ? hint(StardewInteractionHintType.HARVEST,
                            blockIdentity(state))
                    : null);
        }
        UtilityAutomationAccess access =
                findUtilityAccess(level, pos, state, blockEntity);
        if (access != null) {
            if (access instanceof MenuProvider
                    || state.getMenuProvider(level, pos) != null) {
                return Probe.handled(hint(
                        StardewInteractionHintType.GRAB,
                        blockIdentity(state)));
            }
            if (access.isAutomationReady()) {
                return Probe.handled(hint(
                        StardewInteractionHintType.HARVEST,
                        blockIdentity(state)));
            }
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                ItemStack remainder =
                        access.insertAutomation(held.copy(), true);
                if (remainder.getCount() < held.getCount()) {
                    return Probe.handled(hint(
                            StardewInteractionHintType.GRAB,
                            blockIdentity(state)));
                }
            }
            return Probe.handled(null);
        }
        return Probe.pass();
    }

    private static UtilityAutomationAccess findUtilityAccess(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity
    ) {
        if (blockEntity instanceof UtilityAutomationAccess access) {
            return access;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (!level.getBlockState(adjacent).is(state.getBlock())) {
                continue;
            }
            if (level.getBlockEntity(adjacent)
                    instanceof UtilityAutomationAccess access) {
                return access;
            }
        }
        return null;
    }

    private static Optional<StardewInteractionHint> resolveTaggedBlock(
            BlockState state
    ) {
        if (state.is(ModTags.Blocks.INTERACTION_HINT_HARVEST)) {
            return Optional.of(hint(
                    StardewInteractionHintType.HARVEST,
                    blockIdentity(state)));
        }
        if (state.is(ModTags.Blocks.INTERACTION_HINT_GIFT)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GIFT,
                    blockIdentity(state)));
        }
        if (state.is(ModTags.Blocks.INTERACTION_HINT_TALK)) {
            return Optional.of(hint(
                    StardewInteractionHintType.TALK,
                    blockIdentity(state)));
        }
        if (state.is(ModTags.Blocks.INTERACTION_HINT_LOOK)) {
            return Optional.of(hint(
                    StardewInteractionHintType.LOOK,
                    blockIdentity(state)));
        }
        if (state.is(ModTags.Blocks.INTERACTION_HINT_GRAB)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    blockIdentity(state)));
        }
        return Optional.empty();
    }

    private static Optional<StardewInteractionHint> resolveTaggedEntity(
            Entity entity
    ) {
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_HARVEST)) {
            return Optional.of(hint(
                    StardewInteractionHintType.HARVEST,
                    entityIdentity(entity)));
        }
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_GIFT)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GIFT,
                    entityIdentity(entity)));
        }
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_TALK)) {
            return Optional.of(hint(
                    StardewInteractionHintType.TALK,
                    entityIdentity(entity)));
        }
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_LOOK)) {
            return Optional.of(hint(
                    StardewInteractionHintType.LOOK,
                    entityIdentity(entity)));
        }
        if (entity.getType().is(
                ModTags.EntityTypes.INTERACTION_HINT_GRAB)) {
            return Optional.of(hint(
                    StardewInteractionHintType.GRAB,
                    entityIdentity(entity)));
        }
        return Optional.empty();
    }

    private static StardewInteractionHint hint(
            StardewInteractionHintType type,
            ResourceLocation identity
    ) {
        return new StardewInteractionHint(type, false, identity);
    }

    private static ResourceLocation blockIdentity(BlockState state) {
        ResourceLocation id =
                BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null
                ? ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "unknown_block")
                : id;
    }

    private static ResourceLocation entityIdentity(Entity entity) {
        ResourceLocation typeId =
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String namespace = typeId == null
                ? StardewCraft.MODID
                : typeId.getNamespace();
        String path = (typeId == null ? "entity" : typeId.getPath())
                + "/" + entity.getUUID();
        ResourceLocation id =
                ResourceLocation.tryBuild(namespace, path);
        return id == null ? ENTITY_INTERACTION_ID : id;
    }

    private record Probe(
            boolean handled,
            StardewInteractionHint hint
    ) {
        private static Probe pass() {
            return new Probe(false, null);
        }

        private static Probe handled(StardewInteractionHint hint) {
            return new Probe(true, hint);
        }
    }
}
