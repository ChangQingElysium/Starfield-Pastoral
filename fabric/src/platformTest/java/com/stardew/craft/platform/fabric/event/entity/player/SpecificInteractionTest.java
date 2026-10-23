package com.stardew.craft.platform.fabric.event.entity.player;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class SpecificInteractionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void canceledPassAndEveryOtherResultSkipTheTargetWithoutCollapsingTheResult() throws Exception {
        var player = InteractionTestPlayer.create(ItemStack.EMPTY);
        var target = InteractionTestPlayer.create(ItemStack.EMPTY);
        var local = new Vec3(.25, 1.75, -.5);
        target.position = new BlockPos(12, 64, -31);
        for (var result : InteractionResult.values()) {
            Consumer<PlayerInteractEvent.EntityInteractSpecific> listener = event -> {
                assertSame(target, event.getTarget()); assertSame(player, event.getEntity());
                assertSame(local, event.getLocalPos()); assertEquals(target.position, event.getPos());
                assertSame(InteractionHand.OFF_HAND, event.getHand()); assertNull(event.getFace());
                event.setCanceled(true); event.setCancellationResult(result);
            };
            FabricEvents.GAME.addListener(PlayerInteractEvent.EntityInteractSpecific.class, listener);
            try {
                assertSame(result, EntityInteractionHooks.interactAt(target, player, local, InteractionHand.OFF_HAND,
                        () -> fail("Canceled interaction reached the native target: " + result)));
            } finally { FabricEvents.GAME.unregister(listener); }
        }
    }

    @Test void uncanceledSpecificInteractionKeepsItsNativeResultAndDoesNotEmitAGeneralInteraction() throws Exception {
        var player = InteractionTestPlayer.create(ItemStack.EMPTY);
        var calls = new ArrayList<String>();
        Consumer<PlayerInteractEvent.EntityInteract> general = event -> calls.add("general");
        FabricEvents.GAME.addListener(PlayerInteractEvent.EntityInteract.class, general);
        try {
            assertSame(InteractionResult.CONSUME, EntityInteractionHooks.interactAt(player, player, Vec3.ZERO,
                    InteractionHand.MAIN_HAND, () -> { calls.add("native"); return InteractionResult.CONSUME; }));
        } finally { FabricEvents.GAME.unregister(general); }
        assertEquals(List.of("native"), calls);
    }

    @Test void nativeClientAndAcceptedServerActionHaveExactlyOneMatchingTargetInvocation() throws Exception {
        for (var owner : List.of("net/minecraft/client/multiplayer/MultiPlayerGameMode",
                "net/minecraft/server/network/ServerGamePacketListenerImpl$1")) {
            var type = new ClassNode();
            try (var input = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
                assertNotNull(input); new ClassReader(input).accept(type, 0);
            }
            int calls = 0;
            for (var method : type.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/Entity")
                        && call.name.equals("interactAt")) {
                    calls++;
                    assertEquals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", call.desc);
                    if (owner.endsWith("$1")) assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
                    else assertEquals("interactAt", method.name);
                }
            }
            assertEquals(1, calls, owner);
        }
    }
}
