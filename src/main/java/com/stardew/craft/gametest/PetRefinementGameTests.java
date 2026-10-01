package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.*;
import com.stardew.craft.floor.*;
import com.stardew.craft.pet.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_pet_refinement")
@PrefixGameTestTemplate(false)
public final class PetRefinementGameTests {
    /** GameTest's flat preset omits custom dimensions; scope the existing level's identity to this synchronous case. */
    private static final class FarmLevel implements AutoCloseable {
        final ServerLevel level;
        final java.lang.reflect.Field dimension;
        final Object previous;
        final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel> levels;
        final ServerLevel oldAlias;
        @SuppressWarnings("unchecked")
        FarmLevel(GameTestHelper h) throws ReflectiveOperationException {
            level = h.getLevel();
            dimension = net.minecraft.world.level.Level.class.getDeclaredField("dimension"); dimension.setAccessible(true);
            previous = dimension.get(level); dimension.set(level, ModDimensions.STARDEW_VALLEY);
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels"); field.setAccessible(true);
            levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel>) field.get(level.getServer());
            oldAlias = levels.put(ModDimensions.STARDEW_VALLEY, level);
        }
        @Override public void close() throws ReflectiveOperationException { dimension.set(level, previous); if (oldAlias == null) levels.remove(ModDimensions.STARDEW_VALLEY); else levels.put(ModDimensions.STARDEW_VALLEY, oldAlias); }
    }
    private static void clear(ServerLevel level, BlockPos origin) {
        for (int x = -6; x <= 10; x++) for (int z = -6; z <= 10; z++) {
            var p = origin.offset(x, -1, z);
            level.getChunkAt(p); SurfaceFloorData.get(level).remove(level, p, false);
            level.setBlock(p, ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(p.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @SuppressWarnings("unchecked")
    private static AutoCloseable onlineLookup(net.minecraft.server.level.ServerPlayer player) throws ReflectiveOperationException {
        var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        var players = (java.util.Map<UUID,net.minecraft.server.level.ServerPlayer>) field.get(player.server.getPlayerList());
        var old = players.put(player.getUUID(),player);
        return () -> { if (old == null) players.remove(player.getUUID()); else players.put(player.getUUID(),old); };
    }

    private static UUID nonce(net.minecraft.server.level.ServerPlayer player) throws ReflectiveOperationException {
        var field = PetManagement.class.getDeclaredField("sessions"); field.setAccessible(true);
        var session = ((java.util.Map<?, ?>) field.get(null)).get(player.getUUID());
        var method = session.getClass().getDeclaredMethod("nonce"); method.setAccessible(true); return (UUID) method.invoke(session);
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "empty")
    public static void feedbackFollowsSourceFramesAndStopsAtTransitions(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var sounds = new java.util.ArrayList<PetSoundPayload>();
        var observer = new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"PetListener"));
        observer.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),observer) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.unwrap(packet) instanceof com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof PetSoundPayload cue) {
                    var wire = new com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess(),com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
                    try { PetSoundPayload.CODEC.encode(wire,cue); sounds.add(PetSoundPayload.CODEC.decode(wire)); h.assertTrue(!wire.isReadable(),"Pet sound left unread wire data"); } finally { wire.release(); }
                }
            }
        };
        var pos = h.absolutePos(new BlockPos(1,2,1)); observer.moveTo(Vec3.atBottomCenterOf(pos)); level.players().add(observer);
        var entity = ModEntities.PET.get().create(level); entity.moveTo(Vec3.atBottomCenterOf(pos));
        var data = PetWorldData.get(level.getServer());
        var field = PetEntity.class.getDeclaredField("feedback"); field.setAccessible(true); var feedback = field.get(entity);
        var begin = feedback.getClass().getDeclaredMethod("begin",String.class); begin.setAccessible(true);
        var tick = feedback.getClass().getDeclaredMethod("tick"); tick.setAccessible(true);
        var stop = feedback.getClass().getDeclaredMethod("stop"); stop.setAccessible(true);
        var content = feedback.getClass().getDeclaredMethod("content"); content.setAccessible(true);
        try {
            var pet = new PetRecord(entity.getUUID(),UUID.randomUUID(),PetVariant.DOG0,"Sound",1); data.put(pet); entity.refresh(pet);
            com.stardew.craft.api.v1.pet.StardewPetFeedback definition = PetVariant.DOG0.species().feedback();
            h.assertTrue(definition.repeatContentTicks()==8 && definition.states().containsKey("SitDownPant"), "Source feedback graph missing");
            content.invoke(feedback);
            h.assertTrue(sounds.size()==2 && sounds.stream().allMatch(c -> c.sound().getPath().equals("dog_pant") && c.voice())
                    && sounds.get(0).delay()==0 && sounds.get(1).delay()==8, "Original dog content sound/repeat was lost");
            sounds.clear(); begin.invoke(feedback,"SitDownPant");
            for (int t=0;t<=24;t++) { entity.tickCount=t; tick.invoke(feedback); }
            h.assertTrue(sounds.size()==4 && sounds.stream().allMatch(c -> c.range()==5 && c.sound().getPath().equals("dog_pant")),"Pant loop did not sound at original 400ms/5-tile cadence");
            stop.invoke(feedback); for (int t=25;t<=40;t++) { entity.tickCount=t; tick.invoke(feedback); }
            h.assertTrue(sounds.size()==4,"Old behavior emitted sounds after transition");
            data.remove(pet.id); pet = new PetRecord(entity.getUUID(),UUID.randomUUID(),PetVariant.CAT0,"Sound",1); data.put(pet); entity.refresh(pet);
            sounds.clear(); entity.tickCount=0; begin.invoke(feedback,"SitDownLick");
            for (int t=0;t<10;t++) { entity.tickCount=t; tick.invoke(feedback); }
            h.assertTrue(sounds.isEmpty(),"Lick sound occurred before tongue frame"); entity.tickCount=10; tick.invoke(feedback);
            h.assertTrue(sounds.size()==1 && com.stardew.craft.port.PortJava.getFirst(sounds).sound().getPath().equals("cowboy_footstep") && !com.stardew.craft.port.PortJava.getFirst(sounds).voice(),"Cat lick effect was replaced by a voice");
            sounds.clear(); entity.tickCount=0; begin.invoke(feedback,"Flop");
            for (int t=0;t<=40;t++) { entity.tickCount=t; tick.invoke(feedback); }
            h.assertTrue(sounds.size()==1 && com.stardew.craft.port.PortJava.getFirst(sounds).sound().getPath().equals("thud_step"),"Held lying pose repeated its landing sound");
            for (var variant : PetVariant.values()) {
                var walk = variant.species().feedback().states().get("Walk");
                h.assertTrue(walk.loop() && walk.frames().size()==2 && walk.frames().stream().allMatch(f -> f.cue().rangeFromBorder()==1), "Ordinary walk lost original step phases/view margin");
                var id=variant.species().contentSound(); h.assertTrue(id!=null && net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id),"Pet content cue is missing");
            }
        } finally { data.remove(entity.getUUID()); entity.discard(); level.players().remove(observer); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "empty", timeoutTicks = 200)
    public static void bowlPlacementWateringAndMenuPriority(GameTestHelper h) throws Exception {
        try (var fixture = new FarmLevel(h)) {
            var level = fixture.level; var registry = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
            var farm = registry.createFarm(owner, "Water", "Water", FarmType.STANDARD);
            var pos = farm.getOrigin().offset(183, 25, 77); clear(level, pos);
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "BowlWater"));
            player.moveTo(Vec3.atBottomCenterOf(pos.north(2))); player.getAbilities().instabuild = false;
            try {
                var item = new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.PET_BOWL_WOOD.get());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
                var floorHit = new net.minecraft.world.phys.BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
                var placement = net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, floorHit));
                h.assertTrue(placement.consumesAction() && level.getBlockState(pos).getBlock() instanceof PetBowlBlock && item.isEmpty(), "Bowl placement transaction rolled back or duplicated item");
                var record = PetBowlBuildings.ensure(level, pos);
                h.assertTrue(record != null && BuildingProtection.protects(level, pos), "Placement did not immediately register/protect building");
                for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) h.assertTrue(SurfaceFloorData.get(level).at(pos.offset(x,-1,z)) != null, "Placed bowl lacked its complete pad");
                var can = new net.minecraft.world.item.ItemStack(com.stardew.craft.item.ModItems.WATERING_CAN.get());
                var tool = (com.stardew.craft.item.tool.WateringCanItem) can.getItem(); tool.setWater(can, 10);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, can);
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atBottomCenterOf(pos).add(0,.2,0), Direction.NORTH, pos, false);
                h.assertTrue(PortBlockInteraction.stateUseItemOn(level.getBlockState(pos), can, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)
                        == com.stardew.craft.port.net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "Bowl swallowed watering-can action");
                for (var aim : new Vec3[]{Vec3.atBottomCenterOf(pos).add(0,.2,0), new Vec3(pos.getX()+.04,pos.getY()+.001,pos.getZ()+.04)}) {
                    player.getCooldowns().removeCooldown(tool); player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, aim);
                    int before = tool.getWater(can);
                    var use = player.gameMode.useItemOn(player, level, can, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    h.assertTrue(use.consumesAction() && player.isUsingItem(), "Watering start result="+use+" using="+player.isUsingItem()+" mode="+player.gameMode.getGameModeForPlayer()+" item="+player.getMainHandItem());
                    tool.releaseUsing(can, level, player, tool.getUseDuration(can)-1); player.stopUsingItem();
                    h.assertTrue(tool.getWater(can) == before-1 && level.getBlockState(pos).getValue(PetBowlBlock.FULL), "Rim/support watering missed or charged wrong water");
                }
                var targets = tool.getAffectedBlocks(level, pos.below(), player, 3);
                h.assertTrue(targets.contains(pos), "Charged watering filtered the thin bowl above its support");
                var data = PetWorldData.get(level.getServer()); var saved = PetWorldData.load(data.save(new CompoundTag()),level.registryAccess());
                h.assertTrue(saved.bowl(pos).wateredDay() == com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay(), "Watering receipt did not persist");
                var update = PetService.class.getDeclaredMethod("updateBowls",ServerLevel.class); update.setAccessible(true); update.invoke(null,level);
                h.assertTrue(level.getBlockState(pos).getValue(PetBowlBlock.FULL), "Reconciliation emptied a watered bowl");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                PetManagement.openBowl(player,pos);
                PetManagement.submit(player,new PetActionPayload(nonce(player),new UUID(0,0),"bowl_initial","",BlockPos.ZERO));
                h.assertTrue(PetInitialAdoption.needed(player), "Opening the bowl's initial-choice button consumed the free pet");
                PetManagement.openBowl(player,pos); var request = new PetActionPayload(nonce(player),new UUID(0,0),"bowl_move","",pos.east(100));
                PetManagement.submit(player, request); PetManagement.submit(player, request);
                h.assertTrue(player.getInventory().items.stream().filter(BuildingBlueprintItem::isMove).count()==1, "GUI move nonce replay duplicated document");
            } finally { PetManagement.clear(owner); registry.deleteFarm(owner); }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "empty", timeoutTicks = 200)
    public static void bowlMoveDocumentUsesBuildingConfirmFlow(GameTestHelper h) throws Exception {
        try (var fixture = new FarmLevel(h)) {
            var level = fixture.level;
            var registry = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
            var farm = registry.createFarm(owner, "Bowl document move", "Bowl document move", FarmType.STANDARD);
            farm.markInitialized();
            var source = farm.getOrigin().offset(183, 25, 77); clear(level, source);
            var target = source.east(6);
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "BowlDocumentMove"));
            try (var online = onlineLookup(player)) {
            player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(source.north(2)));
            var block = ModBlocks.PET_BOWL_WOOD.get();
            level.setBlock(source, block.defaultBlockState(), 3);
            var before = PetBowlBuildings.ensure(level, source);
            h.assertTrue(before != null && PetBowlBuildings.beginMove(player, source), "Could not begin bowl move");

            int slot = -1;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                if (BuildingBlueprintItem.isMove(player.getInventory().items.get(i))) { slot = i; break; }
            }
            h.assertTrue(slot >= 0, "Move document was not delivered");
            player.getInventory().selected = slot;
            var document = player.getMainHandItem();
            var item = (BuildingBlueprintItem) document.getItem();
            player.moveTo(target.getX() + .5, target.getY() + 1, target.getZ() - .5);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    new Vec3(target.getX() + .5, target.getY(), target.getZ() + .5));

            item.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(target.equals(BuildingBlueprintItem.pinned(document, level)),
                    "First use did not pin the bowl at the selected site");
            h.assertTrue(level.getBlockState(source).is(block) && level.isEmptyBlock(target) && document.getCount() == 1,
                    "Preview click mutated the bowl before confirmation");

            var interruptedCopy = document.copy(); var staleOnUse = document.copy();
            var lastUse = BuildingBlueprintItem.class.getDeclaredField("LAST_USE"); lastUse.setAccessible(true);
            ((java.util.Map<?, ?>) lastUse.get(null)).clear();
            item.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            var after = BuildingWorldData.get(level.getServer()).find(before.id());
            h.assertTrue(level.isEmptyBlock(source) && level.getBlockState(target).is(block),
                    "Confirmed bowl move left the source or missed the destination");
            h.assertTrue(after != null && after.manager().equals(target) && after.revision() == before.revision() + 1,
                    "Confirmed bowl move did not commit its building record");
            PetHomes.prepare(level, farm);
            var petData = PetWorldData.get(level.getServer());
            h.assertTrue(level.isEmptyBlock(source) && level.getBlockState(target).is(block)
                            && petData.bowl(source) == null && petData.bowl(target) != null
                            && petData.bowls().stream().filter(bowl -> bowl.farm().equals(farm.getInstanceId())).count() == 1,
                    "Pet preparation resurrected the moved bowl at its old site");
            h.assertTrue(player.getInventory().items.stream().noneMatch(BuildingBlueprintItem::isMove)
                            && !BuildingBlueprintItem.isMove(player.getOffhandItem()),
                    "Confirmed bowl move left a reusable move document");
            player.getInventory().setItem(slot, interruptedCopy);
            item.inventoryTick(interruptedCopy, level, player, slot, true);
            h.assertTrue(interruptedCopy.isEmpty() && player.getInventory().items.stream().noneMatch(BuildingBlueprintItem::isMove),
                    "A committed move's stale document survived inventory recovery");
            player.getInventory().setItem(slot, staleOnUse);
            ((java.util.Map<?, ?>) lastUse.get(null)).clear();
            item.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(staleOnUse.isEmpty() && BuildingBlueprintItem.pinned(staleOnUse, level) == null,
                    "A committed move's stale document pinned another ghost preview");
            } finally { registry.deleteFarm(owner); }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "empty", timeoutTicks = 200)
    public static void bowlsMoveWithWaterBindingFloorsAndJournalReplay(GameTestHelper h) throws Exception {
        try (var fixture = new FarmLevel(h)) {
        var level = fixture.level;
        var registry = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Bowl move", "Bowl move", FarmType.STANDARD);
        var source = farm.getOrigin().offset(183, 25, 77); clear(level, source);
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "BowlMove"));
        var data = PetWorldData.get(level.getServer()); var buildings = BuildingWorldData.get(level.getServer());
        try (var online = onlineLookup(player)) {
            player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(source.north(2)));
            for (String style : new String[]{"wood", "stone", "hay"}) {
                player.moveTo(Vec3.atBottomCenterOf(source.north(2)));
                PrefabDefinitions.validateAssets(level, PetBowlBuildings.family(style));
                var block = PrefabDefinitions.managerBlock(PetBowlBuildings.family(style));
                level.setBlock(source, block.defaultBlockState(), 3); PetBowlBlock.water(level, source);
                int watered = data.bowl(source).wateredDay();
                var pet = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), PetVariant.CAT0, "Mochi", 1);
                pet.bowl = source; data.put(pet);
                for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                    SurfaceFloorData.get(level).restore(level, source.offset(x, -1, z), new SurfaceFloorData.Cover(SurfaceFloorType.WOOD, 0));
                PetBowlBuildings.beginMove(player, source); PetBowlBuildings.beginMove(player, source);
                var documents = player.getInventory().items.stream().filter(BuildingBlueprintItem::isMove).toList();
                h.assertTrue(documents.size() == 1, "Move documents count="+documents.size()+" style="+style);
                var record = BuildingBlueprintItem.moving(level, com.stardew.craft.port.PortJava.getFirst(documents));
                h.assertTrue(record != null && record.claim().maxExclusive().subtract(record.claim().min()).equals(new BlockPos(2, 1, 2)), "Bowl did not claim its 2x2 pad");
                var target = source.east(6);
                level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(!BuildingLifecycleService.move(player, record, target, Direction.WEST), "Occupied target was overwritten");
                h.assertTrue(data.bowl(source).wateredDay() == watered && source.equals(pet.bowl), "Rejected move changed care data");
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                var snapshot = BuildingTransfer.move(level, record, target, Direction.WEST);
                h.assertTrue(BuildingLifecycleService.move(player, record, target, Direction.WEST), "Bowl move failed");
                // Replaying the persisted projection after an interrupted save preserves assignment and contents.
                var restored = BuildingTransfer.load(snapshot.save(), level.registryAccess());
                restored.project(level);
                h.assertTrue(level.isEmptyBlock(source) && level.getBlockState(target).is(block)
                        && level.getBlockState(target).getValue(PetBowlBlock.FULL), "Bowl style/water blockstate lost");
                h.assertTrue(data.bowl(source) == null && target.equals(pet.bowl) && data.bowl(target).wateredDay() == watered, "Moved bowl lost pet or watering receipt");
                int covers = 0;
                for (var pos : BlockPos.betweenClosed(snapshot.after().claim().min().below(), snapshot.after().claim().maxInclusive().below()))
                    if (SurfaceFloorData.get(level).at(pos) != null) covers++;
                h.assertTrue(covers == 4 && level.getBlockState(source.below()).is(ModBlocks.GRASS_BLOCK.get())
                        && level.getBlockState(target.below()).is(ModBlocks.GRASS_BLOCK.get()), "Pad move replaced grass or lost floor tiles");
                h.assertTrue(!level.destroyBlock(target, true) && !level.removeBlock(target, false), "Ordinary mining removed a bowl building");
                for (var pos : BlockPos.betweenClosed(snapshot.after().claim().min().below(), snapshot.after().claim().maxInclusive().below()))
                    h.assertTrue(BuildingProtection.protects(level, pos) && !SurfaceFloorItem.mayEdit(player, pos), "A moved pad tile remained removable");
                player.moveTo(Vec3.atBottomCenterOf(target.north(2)));
                PetManagement.openBowl(player, target);
                PetManagement.submit(player, new PetActionPayload(nonce(player), pet.id, "bowl_demolish", "", source));
                h.assertTrue(data.bowl(target) == null && pet.bowl == null && buildings.find(record.id()) == null && data.find(pet.id) != null, "GUI demolition lost pet or left bowl identity");
                h.assertTrue(player.getInventory().countItem(PrefabDefinitions.managerItem(record.family())) == 1, "GUI demolition did not refund exactly one bowl");
                BuildingRemovalJournal.get(player.server).recover(player.server);
                h.assertTrue(player.getInventory().countItem(PrefabDefinitions.managerItem(record.family())) == 1, "Demolition journal replay duplicated bowl refund");
                for (var pos : BlockPos.betweenClosed(snapshot.after().claim().min().below(), snapshot.after().claim().maxInclusive().below()))
                    h.assertTrue(SurfaceFloorData.get(level).at(pos) == null && level.getBlockState(pos).is(ModBlocks.GRASS_BLOCK.get()), "Demolition left pad or destroyed terrain");
                data.remove(pet.id); player.getInventory().clearContent(); clear(level, source);
            }
        } finally { registry.deleteFarm(owner); }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "empty", timeoutTicks = 200)
    public static void initialPadsAndIntactLegacyPadsAreTwoByTwo(GameTestHelper h) throws Exception {
        try (var fixture = new FarmLevel(h)) {
        var level = fixture.level;
        var registry = FarmInstanceRegistry.get(level.getServer());
        for (int kind = 0; kind < 3; kind++) {
            boolean legacy = kind != 0;
            var owner = UUID.randomUUID(); var farm = registry.createFarm(owner, "Pad", "Pad", FarmType.STANDARD);
            var source = farm.getOrigin().offset(183, 25, 77); clear(level, source); farm.markInitialized();
            var data = PetWorldData.get(level.getServer()); var floors = SurfaceFloorData.get(level);
            try {
                if (legacy) {
                    data.markPrepared(farm.getInstanceId());
                    level.setBlock(source, ModBlocks.PET_BOWL_WOOD.get().defaultBlockState(), 3);
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                        floors.restore(level, source.offset(x, -1, z), new SurfaceFloorData.Cover(SurfaceFloorType.WOOD, 0));
                    if (kind == 2) floors.restore(level, source.offset(-1, -1, -1), new SurfaceFloorData.Cover(SurfaceFloorType.STONE, 0));
                }
                PetHomes.prepare(level, farm); PetHomes.prepare(level, farm);
                int count = 0;
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (floors.at(source.offset(x, -1, z)) != null) count++;
                h.assertTrue(count == (kind == 2 ? 9 : 4) && data.squareBowlFloor(farm.getInstanceId()), "Pad migration changed a custom floor or failed to make 2x2");
                var saved = PetWorldData.load(data.save(new CompoundTag()), level.registryAccess());
                h.assertTrue(saved.squareBowlFloor(farm.getInstanceId()), "Pad migration receipt not persisted");
                var player = FakePlayerFactory.get(level, new GameProfile(owner, "PadRemove")); player.moveTo(Vec3.atBottomCenterOf(source.north(2)));
                try (var online = onlineLookup(player)) { PetManagement.openBowl(player, source); PetManagement.submit(player, new PetActionPayload(nonce(player), new UUID(0,0), "bowl_demolish", "", BlockPos.ZERO)); }
                PetHomes.prepare(level, farm);
                h.assertTrue(level.isEmptyBlock(source), "Removed bowl respawned");
            } finally { registry.deleteFarm(owner); }
        }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_pet_refinement", template = "construction_site")
    public static void vegetationAndRestClipsDoNotMoveEntityBelowSupport(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(8, 3, 8));
        clear(level, origin);
        var pet = ModEntities.PET.get().create(level);
        pet.refresh(new PetRecord(UUID.randomUUID(), UUID.randomUUID(), PetVariant.CAT0, "Ground", 1));
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.GRASS, ModBlocks.WILD_WEEDS.get(), ModBlocks.PASTURE_GRASS.get(), ModBlocks.BLUE_PASTURE_GRASS.get()}) {
            level.setBlock(origin, block.defaultBlockState(), 3);
            for (String clip : new String[]{"walk", "sit_down", "lie_down", "lie_idle", "sleep_enter", "sleep", "sleep_exit"}) {
                pet.moveTo(Vec3.atBottomCenterOf(origin)); pet.setOnGround(true); pet.play(clip);
                for (int tick = 0; tick < 40; tick++) pet.travel(Vec3.ZERO);
                h.assertTrue(Math.abs(pet.getY() - origin.getY()) < .0001 && pet.getLightProbePosition(1).y > origin.getY(), "Pet physics/light probe sank during " + clip);
                h.assertTrue(level.getBlockState(origin).getLightBlock(level, origin) == 0, "Vegetation blocks pet light");
            }
        }
        pet.discard(); h.succeed();
    }
}
