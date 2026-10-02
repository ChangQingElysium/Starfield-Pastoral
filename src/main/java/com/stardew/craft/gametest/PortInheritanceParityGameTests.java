package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.port.PortAttributes;
import com.stardew.craft.port.PortCriteria;
import com.stardew.craft.port.PortEntities;
import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.port.PortInheritance;
import com.stardew.craft.port.PortPassengerAttachments;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Port verification only (never shipped from main): StardewCraft classes must observe the 1.21.1 bodies of the
 * vanilla/NeoForge methods they inherit (see {@code PortInheritance} and bulk-port-gaps.md "继承的原版默认实现").
 * Each expected value is the 1.21.1 result read from the 1.21.1 sources; the 1.20.1 inherited body gives a different
 * value in every case below.
 */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PortInheritanceParityGameTests {
    private PortInheritanceParityGameTests() {
    }

    private static FakePlayer survivalPlayer(ServerLevel level, BlockPos at) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "port-inherit"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(at.above(8)));
        return player;
    }

    private static int itemsAround(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0)).size();
    }

    /**
     * 1.21.1 {@code IBlockExtension#onDestroyedByPlayer} does not call {@code playerWillDestroy} (1.20.1's default
     * does); {@code ServerPlayerGameMode#destroyBlock} calls it exactly once. A forage block harvests in
     * {@code playerWillDestroy}, so the drop count tells which body ran.
     */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void modBlockBreakCallsPlayerWillDestroyOnceFromGameMode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos direct = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos viaGameMode = helper.absolutePos(new BlockPos(5, 2, 1));
        BlockState forage = ModBlocks.FORAGE_LEEK.get().defaultBlockState();
        for (BlockPos pos : new BlockPos[]{direct, viaGameMode}) {
            level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(pos, forage, 3);
        }
        FakePlayer player = survivalPlayer(level, direct);

        BlockState state = level.getBlockState(direct);
        boolean removed = state.onDestroyedByPlayer(level, direct, player, true, level.getFluidState(direct));
        helper.assertTrue(removed && level.getBlockState(direct).isAir(), "onDestroyedByPlayer did not remove the block");
        helper.assertTrue(itemsAround(level, direct) == 0,
                "1.21.1 onDestroyedByPlayer must not run playerWillDestroy (forage was harvested)");

        boolean broken = player.gameMode.destroyBlock(viaGameMode);
        helper.assertTrue(broken && level.getBlockState(viaGameMode).isAir(), "Game-mode break did not remove the forage");
        helper.assertTrue(itemsAround(level, viaGameMode) == 1,
                "Game-mode break must harvest exactly once, not duplicate or omit playerWillDestroy");
        helper.succeed();
    }

    /** 1.21.1 {@code DoorBlock#updateShape}: a StardewCraft half adopts any door half of the other kind next to it. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void modDoorHalfFollowsAnyDoorNeighbour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState modLower = ModBlocks.GREEN_PANEL_DOOR.get().defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState oakUpper = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState updated = modLower.updateShape(Direction.UP, oakUpper, level, pos, pos.above());
        helper.assertTrue(updated.equals(oakUpper.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)),
                "StardewCraft door did not take the 1.21.1 neighbour state: " + updated);
        // Vanilla doors keep 1.20.1 behaviour (scope check).
        BlockState oakLower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState modUpper = ModBlocks.GREEN_PANEL_DOOR.get().defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        helper.assertTrue(oakLower.updateShape(Direction.UP, modUpper, level, pos, pos.above()).isAir(),
                "Vanilla door behaviour changed");
        helper.succeed();
    }

    /** 1.21.1 {@code LivingEntity#getScale()} is the SCALE attribute only; babies are scaled through their dimensions. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void babyModAnimalScaleAndDimensions(GameTestHelper helper) {
        var duck = ModEntities.DUCK.get().create(helper.getLevel());
        helper.assertTrue(duck != null, "duck");
        duck.setBaby(true);
        duck.refreshDimensions();
        helper.assertTrue(duck.getScale() == 1.0F, "Baby getScale() should be 1.0 (1.21.1), was " + duck.getScale());
        float expectedWidth = ModEntities.DUCK.get().getDimensions().width * 0.5F;
        helper.assertTrue(Math.abs(duck.getBbWidth() - expectedWidth) < 1.0E-5F,
                "Baby width " + duck.getBbWidth() + " != " + expectedWidth);
        helper.succeed();
    }

    /** New HEAD dimensions and the pre-existing attribute RETURN hook must never multiply the scale twice. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void modDimensionsApplyAttributeScaleExactlyOnce(GameTestHelper helper) {
        var dust = ModEntities.DUST_SPIRIT.get().create(helper.getLevel());
        helper.assertTrue(dust != null, "dust spirit");
        dust.getAttribute(PortAttributes.SCALE.get()).setBaseValue(0.8);
        dust.refreshDimensions();
        var expected = ModEntities.DUST_SPIRIT.get().getDimensions().scale(0.8F);
        helper.assertTrue(Math.abs(dust.getBbWidth() - expected.width) < 1.0E-5F
                        && Math.abs(dust.getBbHeight() - expected.height) < 1.0E-5F,
                "Scale was missing or multiplied twice: " + dust.getDimensions(dust.getPose()) + " vs " + expected);
        helper.assertTrue(Math.abs(dust.getScale() - 0.8F) < 1.0E-5F, "getScale did not return the attribute");
        helper.succeed();
    }

    /**
     * 1.21.1 {@code Mob#isWithinMeleeAttackRange}: attack box (inflated by sqrt(2.04)-0.6) intersecting the target's
     * hit box. The target sits where 1.20.1's squared-distance rule says "out of range" and 1.21.1 says "in range".
     */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void modMobMeleeRangeUsesAttackBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob mob = ModEntities.LUCKY_PURPLE_SHORTS_MONSTER.get().create(level);
        IronGolem target = EntityType.IRON_GOLEM.create(level);
        helper.assertTrue(mob != null && target != null, "entities");
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        double w = mob.getBbWidth(), tw = target.getBbWidth();
        double reach121 = w / 2 + tw / 2 + PortInheritance.DEFAULT_ATTACK_REACH;
        double reach1201 = Math.sqrt(w * 2 * w * 2 + tw);
        helper.assertTrue(reach121 - reach1201 > 0.05, "Geometry does not separate the rules: " + reach121 + " vs " + reach1201);
        double distance = (reach121 + reach1201) / 2;
        mob.setPos(origin);
        target.setPos(origin.add(distance, 0, 0));
        helper.assertTrue(mob.isWithinMeleeAttackRange(target),
                "Target at " + distance + " should be in 1.21.1 melee range (attack box)");
        target.setPos(origin.add(reach121 + 0.05, 0, 0));
        helper.assertTrue(!mob.isWithinMeleeAttackRange(target), "Target beyond the attack box counted as in range");
        helper.succeed();
    }

    /** A mounted player's feet are below its vehicle attachment, which is the 1.21 melee clipping point. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void ridingTargetHitboxUsesPassengerAttachment(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = PortGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
        var seat = ModEntities.SOFA_SEAT.get().create(level);
        helper.assertTrue(seat != null, "seat");
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        seat.setPos(at);
        player.startRiding(seat, true);
        seat.positionRider(player);
        helper.assertTrue(Math.abs(player.getY() - seat.getY() + 0.6) < 1.0E-5,
                "Seat fixture did not position the player by its hip attachment");
        helper.assertTrue(Math.abs(PortInheritance.hitbox(player).minY - seat.getY()) < 1.0E-5,
                "Melee hitbox was clipped at feet/old riding offset instead of the mod seat's riding point");
        player.stopRiding();

        Boat boat = EntityType.BOAT.create(level);
        helper.assertTrue(boat != null, "boat");
        boat.setPos(at);
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(boat, player) - boat.getY() - 0.1875F) < 1.0E-5,
                "Boat target clipping retained the old -0.1 seat height");
        boat.setVariant(Boat.Type.BAMBOO);
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(boat, player) - boat.getY() - 0.5F) < 1.0E-5,
                "Raft target clipping did not use 1.21 boat-height * 8/9");
        Minecart minecart = EntityType.MINECART.create(level);
        helper.assertTrue(minecart != null, "minecart");
        minecart.setPos(at);
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(minecart, player) - minecart.getY() - 0.1875F) < 1.0E-5,
                "Minecart target clipping did not use its 1.21 type passenger attachment");
        var horse = EntityType.HORSE.create(level);
        var pig = EntityType.PIG.create(level);
        var camel = EntityType.CAMEL.create(level);
        helper.assertTrue(horse != null && pig != null && camel != null, "mounts");
        horse.setPos(at); pig.setPos(at); camel.setPos(at);
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(horse, player) - horse.getY() - 1.44375F) < 1.0E-5,
                "Horse attachment does not match 1.21");
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(pig, player) - pig.getY() - 0.86875F) < 1.0E-5,
                "Pig attachment does not match 1.21");
        // A fresh GameTest world can still be in the initial 52-tick stand-up transition. Pin its start rather than
        // assuming it is fully standing: 1.21 body height 2.375 - .375 + (.2 - 1.43) = .77 at transition time <= 0.
        camel.resetLastPoseChangeTick(level.getGameTime() + 1);
        helper.assertTrue(Math.abs(PortPassengerAttachments.ridingY(camel, player) - camel.getY() - 0.77F) < 1.0E-5,
                "Camel's start-of-rise attachment does not match 1.21");
        helper.succeed();
    }

    /**
     * 1.21.1 {@code Projectile#shootFromRotation} adds the shooter's known movement (a server player's last client
     * movement) and {@code shoot} sets {@code hasImpulse}; 1.20.1 adds {@code getDeltaMovement()}.
     */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void modProjectileInheritsShooterKnownMovement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = survivalPlayer(level, helper.absolutePos(new BlockPos(1, 2, 1)));
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        PortEntities.setKnownMovement(player, new Vec3(0.5, 0.0, 0.0));
        Projectile projectile = ModEntities.MEOWMERE_PROJECTILE.get().create(level);
        helper.assertTrue(projectile != null, "projectile");
        projectile.shootFromRotation(player, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        Vec3 motion = projectile.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.x - 0.5) < 1.0E-6 && Math.abs(motion.z - 1.0) < 1.0E-6,
                "Mod projectile did not inherit the known movement: " + motion);
        helper.assertTrue(projectile.hasImpulse, "shoot() did not set hasImpulse");

        FishingHook hook = new FishingHook(player, level, 0, 0);
        hook.shootFromRotation(player, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        helper.assertTrue(Math.abs(hook.getDeltaMovement().x - 0.5) < 1.0E-6,
                "Fishing cast did not inherit the known movement: " + hook.getDeltaMovement());
        hook.discard();
        helper.succeed();
    }

    /** 1.21.1 boat seat: the passenger's feet sit at boat height / 3 (Moonlight Jellies lantern boat). */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void lanternBoatSeatsDisplayAt121Height(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Boat boat = EntityType.BOAT.create(level);
        Display.ItemDisplay lantern = EntityType.ITEM_DISPLAY.create(level);
        helper.assertTrue(boat != null && lantern != null, "entities");
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        boat.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        lantern.getSlot(0).set(new ItemStack(ModItems.WATER_LANTERN.get()));
        lantern.moveTo(at.x, at.y + 2, at.z, 0.0F, 0.0F);
        lantern.startRiding(boat, true);
        boat.positionRider(lantern);
        double expected = EntityType.BOAT.getDimensions().height / 3.0F;
        helper.assertTrue(Math.abs(lantern.getY() - boat.getY() - expected) < 1.0E-5,
                "Seat height " + (lantern.getY() - boat.getY()) + " != 1.21.1 " + expected);
        lantern.stopRiding();
        // No registry or scoreboard-tag blanket rewrite: an ordinary display remains a 1.20.1 passenger.
        lantern.getSlot(0).set(new ItemStack(Items.STONE));
        lantern.startRiding(boat, true);
        boat.positionRider(lantern);
        helper.assertTrue(Math.abs(lantern.getY() - boat.getY()
                        - boat.getPassengersRidingOffset() - lantern.getMyRidingOffset()) < 1.0E-5,
                "Non-lantern boat seating changed");
        lantern.stopRiding();
        helper.succeed();
    }

    /** NeoForge fake players do not construct menus or become passengers through the mod's interactions. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void fakePlayerRefusesModMenuAndRiding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = survivalPlayer(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        int[] created = {0};
        MenuProvider provider = new SimpleMenuProvider((id, inventory, owner) -> {
            created[0]++;
            return ChestMenu.oneRow(id, inventory);
        }, Component.literal("port test"));
        helper.assertTrue(player.openMenu(provider).isEmpty() && created[0] == 0,
                "Fake player created or opened a mod-requested menu");
        var seat = ModEntities.SOFA_SEAT.get().create(level);
        helper.assertTrue(seat != null, "seat");
        helper.assertTrue(!player.startRiding(seat, true) && !player.isPassenger() && !seat.isVehicle(),
                "Fake player mounted a mod seat");
        helper.succeed();
    }

    /** Test the real game-mode routing and criterion listeners, not only the bridge's thread-local flag. */
    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void defaultDoorUseFiresOnlyDefaultCriterion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var lower = ModBlocks.GREEN_PANEL_DOOR.get().defaultBlockState();
        level.setBlock(pos, lower, 3);
        level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Port default use"));
        player.setPos(Vec3.atCenterOf(pos.offset(1, 0, 0)));
        var defaultInstance = PortCriteria.DEFAULT_BLOCK_USE.createInstance(new JsonObject(),
                new DeserializationContext(new ResourceLocation("stardewcraft", "port_default_use"),
                        level.getServer().getLootData()));
        var itemInstance = new ItemUsedOnLocationTrigger.TriggerInstance(CriteriaTriggers.ITEM_USED_ON_BLOCK.getId(),
                ContextAwarePredicate.ANY, ContextAwarePredicate.ANY);
        Advancement defaultAdvancement = Advancement.Builder.advancement().addCriterion("use", defaultInstance)
                .build(new ResourceLocation("stardewcraft", "port_default_use"));
        Advancement itemAdvancement = Advancement.Builder.advancement().addCriterion("use", itemInstance)
                .build(new ResourceLocation("stardewcraft", "port_item_use"));
        var defaultListener = new CriterionTrigger.Listener<>(defaultInstance, defaultAdvancement, "use");
        var itemListener = new CriterionTrigger.Listener<>(itemInstance, itemAdvancement, "use");
        var advancements = player.getAdvancements();
        PortCriteria.DEFAULT_BLOCK_USE.addPlayerListener(advancements, defaultListener);
        CriteriaTriggers.ITEM_USED_ON_BLOCK.addPlayerListener(advancements, itemListener);
        try {
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false);
            var offhand = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.OFF_HAND, hit);
            helper.assertTrue(!offhand.consumesAction() && !level.getBlockState(pos).getValue(DoorBlock.OPEN),
                    "Inherited default door interaction ran for the offhand");
            var result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result.consumesAction() && level.getBlockState(pos).getValue(DoorBlock.OPEN),
                    "Main-hand default door interaction failed");
            helper.assertTrue(advancements.getOrStartProgress(defaultAdvancement).isDone(),
                    "Default block use criterion did not fire");
            helper.assertTrue(!advancements.getOrStartProgress(itemAdvancement).isDone(),
                    "Default block use also fired the item-used criterion");
        } finally {
            PortCriteria.DEFAULT_BLOCK_USE.removePlayerListener(advancements, defaultListener);
            CriteriaTriggers.ITEM_USED_ON_BLOCK.removePlayerListener(advancements, itemListener);
            player.discard();
        }
        helper.succeed();
    }
}
