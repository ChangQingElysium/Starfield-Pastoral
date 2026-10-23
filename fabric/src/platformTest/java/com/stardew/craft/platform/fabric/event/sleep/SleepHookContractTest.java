package com.stardew.craft.platform.fabric.event.sleep;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class SleepHookContractTest {
    @Test
    void vanillaValidationStillContainsAllChecksAndExactlyTheFourDeferredBusinessSites() throws IOException {
        var vanilla = method(node("net/minecraft/server/level/ServerPlayer"), "startSleepInBed");
        for (String check : List.of("isSleeping", "isAlive", "bedInRange", "bedBlocked", "setRespawnPosition", "isCreative"))
            assertTrue(calls(vanilla, "net/minecraft/server/level/ServerPlayer", check) > 0, check);
        assertEquals(1, calls(vanilla, "net/minecraft/world/entity/player/Player", "startSleepInBed"));
        assertEquals(1, calls(vanilla, "com/mojang/datafixers/util/Either", "ifRight"));
        assertEquals(1, calls(vanilla, "net/minecraft/server/level/ServerPlayer", "displayClientMessage"));
        assertEquals(1, calls(vanilla, "net/minecraft/server/level/ServerLevel", "updateSleepingPlayerList"));
        assertEquals(7, java.util.stream.StreamSupport.stream(vanilla.instructions.spliterator(), false).filter(value -> value.getOpcode() == Opcodes.ARETURN).count());
        var mixin = node("com/stardew/craft/platform/mixin/event/sleep/mixin/ServerPlayerSleepMixin");
        var attempt = method(mixin, "stardewcraft$attempt");
        assertTrue(position(attempt, "net/minecraft/world/level/block/state/BlockState", "hasProperty") < position(attempt, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call"));
        assertTrue(position(attempt, "com/stardew/craft/platform/fabric/event/sleep/SleepValidation$Scope", "finishValidation")
                < position(attempt, "com/stardew/craft/platform/fabric/event/sleep/FabricSleepEvents", "attempt"));
        assertFalse(attempt.tryCatchBlocks.isEmpty()); assertTrue(calls(attempt, "com/stardew/craft/platform/fabric/event/sleep/SleepValidation$Scope", "close") >= 2);
        for (String deferred : List.of("stardewcraft$deferStart", "stardewcraft$deferAwards", "stardewcraft$deferMessage", "stardewcraft$deferSleepingList"))
            assertEquals(1, calls(method(mixin, deferred), "com/stardew/craft/platform/fabric/event/sleep/SleepValidation", "probing"));
        var finish = method(mixin, "stardewcraft$finish");
        int realStart = position(finish, "net/minecraft/world/entity/player/Player", "startSleepInBed");
        assertEquals(Opcodes.INVOKESPECIAL, finish.instructions.get(realStart).getOpcode());
        assertEquals(1, calls(finish, "net/minecraft/world/entity/player/Player", "startSleepInBed"));
        assertEquals(1, calls(finish, "com/mojang/datafixers/util/Either", "ifRight"));
        assertEquals(1, calls(finish, "net/minecraft/server/level/ServerPlayer", "displayClientMessage"));
        assertEquals(1, calls(finish, "net/minecraft/server/level/ServerLevel", "updateSleepingPlayerList"));
    }
    @Test
    void wakeAndFinishedTimeRemainAtTheActualPreClearAndAllPlayersSleepingSites() throws IOException {
        var player = method(node("net/minecraft/world/entity/player/Player"), "stopSleepInBed");
        assertEquals(1, calls(player, "net/minecraft/world/entity/LivingEntity", "stopSleeping"));
        assertEquals(1, calls(player, "net/minecraft/server/level/ServerLevel", "updateSleepingPlayerList"));
        var tick = method(node("net/minecraft/server/level/ServerLevel"), "tick");
        assertEquals(1, calls(tick, "net/minecraft/server/level/ServerLevel", "setDayTime"));
        assertTrue(position(tick, "net/minecraft/server/players/SleepStatus", "areEnoughDeepSleeping")
                < position(tick, "net/minecraft/server/level/ServerLevel", "setDayTime"));
        assertTrue(position(tick, "net/minecraft/server/level/ServerLevel", "setDayTime")
                < position(tick, "net/minecraft/server/level/ServerLevel", "wakeUpAllPlayers"));
        var bed = method(node("net/minecraft/world/entity/LivingEntity"), "checkBedExists");
        assertEquals(1, calls(bed, "java/util/Optional", "map"));
        assertEquals(1, java.util.stream.StreamSupport.stream(bed.instructions.spliterator(), false).filter(value -> value.getOpcode() == Opcodes.IRETURN).count());
    }
    @Test
    void sleepProducerUsesNoClientTypesAndTheFapiTimeCallbackGatesInitialAttemptsOut() throws IOException {
        var producer = node("com/stardew/craft/platform/fabric/event/sleep/FabricSleepEvents");
        var callback = producer.methods.stream().filter(method -> calls(method, "net/minecraft/world/entity/player/Player", "isSleeping") > 0).findFirst().orElseThrow();
        assertTrue(position(callback, "net/minecraft/world/entity/player/Player", "isSleeping")
                < position(callback, "com/stardew/craft/platform/fabric/event/sleep/FabricSleepEvents", "canContinue"));
        for (var method : producer.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call)
            assertFalse(call.owner.startsWith("net/minecraft/client/"));
    }
    private static int calls(MethodNode method, String owner, String name) {
        int count = 0; for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++; return count;
    }
    private static int position(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = SleepHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes, name); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
