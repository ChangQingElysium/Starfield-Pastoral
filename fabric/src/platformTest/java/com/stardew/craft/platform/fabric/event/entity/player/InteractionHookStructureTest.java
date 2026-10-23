package com.stardew.craft.platform.fabric.event.entity.player;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.mixin.event.entity.player.mixin.ItemUseInteractionMixin;
import com.stardew.craft.platform.mixin.event.entity.player.mixin.PlayerEntityInteractionMixin;
import com.stardew.craft.platform.mixin.event.entity.player.mixin.ServerPlayerInteractionMixin;
import com.stardew.craft.platform.fabric.event.level.BlockBreakDispatch;
import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import com.stardew.craft.platform.fabric.event.level.BlockSnapshot;
import com.stardew.craft.platform.fabric.event.level.PlacementTransactions;
import com.stardew.craft.platform.mixin.event.level.mixin.ItemPlacementMixin;
import com.stardew.craft.platform.mixin.event.level.mixin.LevelChunkPlacementMixin;
import com.stardew.craft.platform.mixin.event.level.mixin.LevelPlacementMixin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Checks the exact native 1.21.1 hooks without applying mixins or starting Minecraft. */
class InteractionHookStructureTest {
    @Test void levelCapturePointHasTheExpectedSingleMutationAndLocalVariables() throws IOException {
        var method = read("net/minecraft/world/level/Level", "setBlock",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z");
        assertEquals(1, method.calls.stream().filter(call -> call.owner.endsWith("/LevelChunk") && call.name.equals("setBlockState")).count());
        assertEquals("Lnet/minecraft/world/level/chunk/LevelChunk;", method.locals.get(5));
        assertEquals("Lnet/minecraft/world/level/block/Block;", method.locals.get(6));
        assertEquals("Lnet/minecraft/world/level/block/state/BlockState;", method.locals.get(7));
        int mutation = method.names().indexOf("setBlockState");
        assertTrue(mutation >= 0);
        assertTrue(mutation < method.names().indexOf("setBlocksDirty"));
        assertTrue(mutation < method.names().indexOf("sendBlockUpdated"));
    }

    @Test void itemPlacementWrapperRunsBeforeTheNativeStatAward() throws IOException {
        var method = read("net/minecraft/world/item/ItemStack", "useOn",
                "(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;");
        assertEquals(1, method.calls.stream().filter(call -> call.owner.equals("net/minecraft/world/item/Item") && call.name.equals("useOn")).count());
        assertTrue(method.names().indexOf("useOn") < method.names().indexOf("awardStat"));
    }

    @Test void clientPredictionTargetsDistinguishStartCreativeRepeatAndStop() throws IOException {
        String owner = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
        var start = read(owner, "startDestroyBlock", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z");
        var repeat = read(owner, "continueDestroyBlock", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z");
        assertEquals(2, start.names().stream().filter("startPrediction"::equals).count());
        assertEquals(2, repeat.names().stream().filter("startPrediction"::equals).count());
        assertEquals(2, repeat.names().stream().filter("onDestroyBlock"::equals).count());
        var survival = read(owner, "method_41930", "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;I)Lnet/minecraft/network/protocol/Packet;");
        assertEquals(1, survival.names().stream().filter("attack"::equals).count());
        assertTrue(survival.names().indexOf("attack") < survival.names().indexOf("getDestroyProgress"));
    }

    @Test void serverHooksKeepTheNativePermissionChecksAfterItemApproval() throws IOException {
        var method = read("net/minecraft/server/level/ServerPlayerGameMode", "destroyBlock", "(Lnet/minecraft/core/BlockPos;)Z");
        assertEquals(1, method.names().stream().filter("canAttackBlock"::equals).count());
        int item = method.names().indexOf("canAttackBlock");
        assertTrue(item < method.names().indexOf("canUseGameMasterBlocks"));
        assertTrue(item < method.names().indexOf("blockActionRestricted"));
        assertTrue(method.names().indexOf("blockActionRestricted") < method.names().indexOf("playerWillDestroy"));
    }

    @Test void levelChunkHasOneDeferredOnPlaceAndRetainsItsNativeOnRemove() throws IOException {
        var method = read("net/minecraft/world/level/chunk/LevelChunk", "setBlockState",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;");
        assertEquals(1, method.names().stream().filter("onPlace"::equals).count());
        assertEquals(1, method.names().stream().filter("onRemove"::equals).count());
        assertTrue(method.names().indexOf("onRemove") < method.names().indexOf("onPlace"));
    }

    @Test void publicInteractionAndPlacementClassesHaveNoClientClassReferences() throws IOException {
        for (Class<?> type : List.of(InteractionDispatch.class, PlayerInteractEvent.class,
                PlayerEntityInteractionMixin.class, ItemUseInteractionMixin.class, ServerPlayerInteractionMixin.class,
                BlockEvent.class, BlockSnapshot.class, BlockBreakDispatch.class, PlacementTransactions.class,
                ItemPlacementMixin.class, LevelPlacementMixin.class, LevelChunkPlacementMixin.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream);
                assertFalse(new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"),
                        type.getName());
            }
        }
    }

    private static NativeMethod read(String owner, String name, String descriptor) throws IOException {
        var method = new NativeMethod();
        try (var stream = InteractionHookStructureTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String nativeName, String nativeDescriptor,
                        String signature, String[] exceptions) {
                    if (!nativeName.equals(name) || !nativeDescriptor.equals(descriptor)) return null;
                    method.found = true;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String calledOwner, String calledName,
                                String calledDescriptor, boolean isInterface) {
                            method.calls.add(new Call(calledOwner, calledName));
                        }
                        @Override public void visitLocalVariable(String localName, String localDescriptor, String signature,
                                Label start, Label end, int index) { method.locals.put(index, localDescriptor); }
                    };
                }
            }, 0);
        }
        assertTrue(method.found, owner + "." + name + descriptor);
        return method;
    }

    private record Call(String owner, String name) {}
    private static final class NativeMethod {
        boolean found;
        final List<Call> calls = new ArrayList<>();
        final Map<Integer, String> locals = new HashMap<>();
        List<String> names() { return calls.stream().map(Call::name).toList(); }
    }
}
