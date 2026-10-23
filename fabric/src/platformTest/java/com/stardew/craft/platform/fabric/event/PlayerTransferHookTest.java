package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.mixin.data.mixin.ServerPlayerPersistentDataMixin;
import com.stardew.craft.platform.mixin.event.mixin.ServerPlayerDimensionChangeMixin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

class PlayerTransferHookTest {
    private static final String PLAYER = "net/minecraft/server/level/ServerPlayer";
    private static final String TRANSFER_DESCRIPTOR = "(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;";

    @Test
    void playerPersistedCopyStaysAtEntryBeforeTheFabricCopyFromCallback() {
        Inject copy = injection(ServerPlayerPersistentDataMixin.class);

        assertArrayEquals(new String[]{"restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V"}, copy.method());
        assertEquals("HEAD", copy.at()[0].value());
        assertEquals(1, copy.require());
    }

    @Test
    void transferHookMatchesTheUniqueCompletedCrossDimensionBranchInTheNativeBytecode() throws IOException {
        Inject injection = injection(ServerPlayerDimensionChangeMixin.class);
        assertArrayEquals(new String[]{"changeDimension" + TRANSFER_DESCRIPTOR}, injection.method());
        assertEquals("FIELD", injection.at()[0].value());
        assertEquals("L" + PLAYER + ";lastSentFood:I", injection.at()[0].target());
        assertEquals(Opcodes.PUTFIELD, injection.at()[0].opcode());
        assertEquals(At.Shift.AFTER, injection.at()[0].shift());
        assertEquals(LocalCapture.CAPTURE_FAILHARD, injection.locals());
        assertEquals(1, injection.require());

        List<String> operations = new ArrayList<>();
        Map<Integer, String> locals = new HashMap<>();
        try (var stream = ServerPlayer.class.getResourceAsStream("/" + PLAYER + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!name.equals("changeDimension") || !descriptor.equals(TRANSFER_DESCRIPTOR)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        private int returns;

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) operations.add("return-" + returns++);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (name.equals("sendActivePlayerEffects")) operations.add("effects-sync");
                            if (owner.endsWith("DimensionTransition$PostDimensionTransition") && name.equals("onTransition")) {
                                operations.add("post-transition");
                            }
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.PUTFIELD && owner.equals(PLAYER) && name.equals("lastSentFood")) {
                                operations.add("completed-transfer");
                            }
                        }

                        @Override
                        public void visitLocalVariable(String name, String descriptor, String signature,
                                Label start, Label end, int index) {
                            locals.put(index, descriptor);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(List.of("return-0", "post-transition", "return-1", "effects-sync",
                "post-transition", "completed-transfer", "return-2"), operations,
                "The hook must follow final sync/callbacks and skip removed/same-world returns");
        assertEquals(Map.of(2, "Lnet/minecraft/server/level/ServerLevel;",
                3, "Lnet/minecraft/server/level/ServerLevel;", 4, "Lnet/minecraft/resources/ResourceKey;",
                5, "Lnet/minecraft/world/level/storage/LevelData;", 6, "Lnet/minecraft/server/players/PlayerList;"),
                locals.entrySet().stream().filter(entry -> entry.getKey() >= 2)
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                "Captured invocation-local values must match the actual native method");
    }

    private static Inject injection(Class<?> mixin) {
        return Arrays.stream(mixin.getDeclaredMethods()).map(method -> method.getAnnotation(Inject.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
    }
}
