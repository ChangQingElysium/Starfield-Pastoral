package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerGlowHandler;
import com.stardew.craft.player.PlayerGlowState;
import com.stardew.craft.player.PlayerMagnetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Live-world regressions: no light blocks, no forced pickup, and vanilla pickup eligibility. */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RingUtilityGameTests {
    private RingUtilityGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void ringLightOnlyChangesEntityMetadata(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.tickCount = 4;
        var equipment = PlayerDataManager.getPlayerData(player);
        equipment.setEquippedLeftRingStack(new ItemStack(ModItems.GLOW_RING.get()));
        BlockPos center = player.blockPosition();
        BlockPos authoredLight = center.offset(0, 1, 0);
        BlockState previous = helper.getLevel().getBlockState(authoredLight);
        helper.getLevel().setBlock(authoredLight,
                Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 3), 2);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-7, -2, -7), center.offset(7, 2, 7))) {
            before.put(pos.immutable(), helper.getLevel().getBlockState(pos));
        }
        try {
            PlayerGlowHandler.tick(player);
            helper.assertTrue(((PlayerGlowState) player).stardewcraft$getRingLight() == 10,
                    "equipped glow ring did not synchronize its light strength");
            player.setPos(player.getX() + 0.4, player.getY(), player.getZ());
            PlayerGlowHandler.tick(player);
            equipment.setEquippedLeftRingStack(new ItemStack(ModItems.SMALL_GLOW_RING.get()));
            PlayerGlowHandler.tick(player);
            helper.assertTrue(((PlayerGlowState) player).stardewcraft$getRingLight() == 5,
                    "changing ring strength left stale entity metadata");
            equipment.setEquippedLeftRingStack(ItemStack.EMPTY);
            PlayerGlowHandler.tick(player);
            helper.assertTrue(((PlayerGlowState) player).stardewcraft$getRingLight() == 0,
                    "unequipping the ring left its light active");
            for (var entry : before.entrySet()) {
                helper.assertTrue(helper.getLevel().getBlockState(entry.getKey()).equals(entry.getValue()),
                        "ring lighting mutated a world block at " + entry.getKey());
            }
        } finally {
            helper.getLevel().setBlock(authoredLight, previous, 2);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void magnetRespectsDelayTargetInventoryAndWalls(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerDataManager.getPlayerData(player).setEquippedLeftRingStack(new ItemStack(ModItems.MAGNET_RING.get()));
        ItemEntity item = item(helper, player, 2.0);
        try {
            item.setPickUpDelay(40);
            PlayerMagnetHandler.tick(player);
            unchanged(helper, item, "magnet pulled a newly thrown item or cleared its pickup delay");
            helper.assertTrue(item.hasPickUpDelay(), "pickup delay was cleared");
            item.setNeverPickUp();
            PlayerMagnetHandler.tick(player);
            unchanged(helper, item, "magnet pulled a display item with infinite pickup delay");
            item.setNoPickUpDelay();
            item.setTarget(UUID.randomUUID());
            PlayerMagnetHandler.tick(player);
            unchanged(helper, item, "magnet pulled an item reserved for someone else");
            item.setTarget(null);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 999));
            }
            PlayerMagnetHandler.tick(player);
            unchanged(helper, item, "magnet pulled an item into a full inventory");
            player.getInventory().clearContent();
            BlockPos wall = player.blockPosition().east();
            BlockState previous = helper.getLevel().getBlockState(wall);
            helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            try {
                PlayerMagnetHandler.tick(player);
                unchanged(helper, item, "magnet pulled through a solid wall");
            } finally {
                helper.getLevel().setBlock(wall, previous, 2);
            }
            // Thrower is provenance, not pickup ownership.
            item.setThrower(player(helper).getUUID());
            PlayerMagnetHandler.tick(player);
            helper.assertTrue(item.getDeltaMovement().x < 0, "an eligible item was not attracted");
            helper.assertTrue(item.getDeltaMovement().length() < 0.15, "attraction began with an abrupt impulse");
            helper.assertTrue(!item.hurtMarked, "magnet forced a redundant motion correction packet");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void magnetLeavesPickupToPlayerCollision(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerDataManager.getPlayerData(player).setEquippedLeftRingStack(new ItemStack(ModItems.MAGNET_RING.get()));
        ItemEntity item = item(helper, player, 1.1);
        try {
            PlayerMagnetHandler.tick(player);
            helper.assertTrue(item.isAlive() && player.getInventory().countItem(Items.DIAMOND) == 0,
                    "magnet picked up an item before it reached the player");
            helper.assertTrue(item.getDeltaMovement().x < 0, "nearby item should ease toward the player");
            item.setDeltaMovement(Vec3.ZERO);
            player.setHealth(0);
            PlayerMagnetHandler.tick(player);
            unchanged(helper, item, "dead player attracted items");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void magnetCatchesMovingWearerWithNormalItemPhysics(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setPos(player.getX() - 2, player.getY(), player.getZ());
        PlayerDataManager.getPlayerData(player).setEquippedLeftRingStack(new ItemStack(ModItems.MAGNET_RING.get()));
        ItemEntity item = item(helper, player, -4);
        item.setNoGravity(false);
        try {
            for (int tick = 0; tick < 25; tick++) {
                player.setKnownMovement(new Vec3(0.28, 0, 0));
                player.setPos(player.getX() + 0.28, player.getY(), player.getZ());
                PlayerMagnetHandler.tick(player);
                item.tick();
                helper.assertTrue(item.getX() <= player.getX() + 0.4, "item overshot the moving wearer");
                helper.assertTrue(!item.isNoGravity(), "magnet changed the item's physics flags");
                // Normal player collision would consume the item here.
                if (player.getX() - item.getX() < 0.8) break;
            }
            helper.assertTrue(player.getX() - item.getX() < 0.8,
                    "item could not reach a sprinting wearer's pickup box: gap=" + (player.getX() - item.getX()));
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Ring utility test"));
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static ItemEntity item(GameTestHelper helper, ServerPlayer player, double offset) {
        ItemEntity item = new ItemEntity(helper.getLevel(), player.getX() + offset,
                player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setNoPickUpDelay();
        item.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(helper.getLevel().addFreshEntity(item), "test item could not spawn");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6)).contains(item),
                "test item is not in the entity query");
        helper.assertTrue(player.isAlive(), "test player is not alive");
        helper.assertTrue(!player.isSpectator(), "test player is a spectator");
        var hit = helper.getLevel().clip(new ClipContext(item.getBoundingBox().getCenter(),
                player.position().add(0, 0.35, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, item));
        helper.assertTrue(hit.getType() == HitResult.Type.MISS,
                "test attraction path is blocked by " + helper.getLevel().getBlockState(hit.getBlockPos()) + " at " + hit.getBlockPos());
        return item;
    }

    private static void unchanged(GameTestHelper helper, ItemEntity item, String message) {
        helper.assertTrue(item.getDeltaMovement().lengthSqr() == 0.0, message);
    }
}
