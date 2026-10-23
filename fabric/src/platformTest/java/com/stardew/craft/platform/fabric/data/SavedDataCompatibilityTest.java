package com.stardew.craft.platform.fabric.data;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.stardew.craft.platform.mixin.data.mixin.DimensionDataStorageMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class SavedDataCompatibilityTest {
    @Test
    void compressedModRecordSurvivesWithoutAVanillaSchemaOrDataLoss() throws Exception {
        CompoundTag data = new CompoundTag();
        data.putLong("balance", 9_000_000_000L);
        data.putIntArray("unknown_extension", new int[] {1, 2, -3});
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        root.putInt("DataVersion", 3000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, bytes);
        CompoundTag reloaded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),
                NbtAccounter.unlimitedHeap());

        CompoundTag result = SavedDataFixes.update(null, new RecordingFixer(), reloaded, 3000, 3955);
        assertSame(reloaded, result);
        assertEquals(root, result);
    }

    @Test
    void vanillaSchemasStillReachTheRealDataFixerWithBothVersions() {
        RecordingFixer fixer = new RecordingFixer();
        CompoundTag input = new CompoundTag();
        input.putInt("existing", 7);
        CompoundTag result = SavedDataFixes.update(DataFixTypes.SAVED_DATA_MAP_DATA, fixer, input, 100, 3955);
        assertEquals(1, fixer.calls);
        assertEquals(100, fixer.from);
        assertEquals(3955, fixer.to);
        assertNotNull(fixer.schema);
        assertEquals(7, result.getInt("existing"));
        assertTrue(result.getBoolean("fixer_was_called"));
        assertFalse(input.contains("fixer_was_called"));
    }

    @Test
    void nullablePatchTargetsTheOnlyNativeDiskReadFixerInvocation() throws Exception {
        WrapOperation patch = Arrays.stream(DimensionDataStorageMixin.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(WrapOperation.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
        String descriptor = "(Ljava/lang/String;Lnet/minecraft/util/datafix/DataFixTypes;I)Lnet/minecraft/nbt/CompoundTag;";
        String update = "(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;";
        assertArrayEquals(new String[] {"readTagFromDisk" + descriptor}, patch.method());
        assertEquals(1, patch.require());
        assertEquals("Lnet/minecraft/util/datafix/DataFixTypes;update" + update, patch.at()[0].target());
        int[] matches = {0};
        try (var bytes = DimensionDataStorage.class.getResourceAsStream("DimensionDataStorage.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                        String signature, String[] exceptions) {
                    if (!name.equals("readTagFromDisk") || !methodDescriptor.equals(descriptor)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                String targetDescriptor, boolean isInterface) {
                            if (owner.equals("net/minecraft/util/datafix/DataFixTypes")
                                    && method.equals("update") && targetDescriptor.equals(update)) matches[0]++;
                        }
                    };
                }
            }, 0);
        }
        assertEquals(1, matches[0]);
    }

    private static final class RecordingFixer implements DataFixer {
        int calls;
        int from;
        int to;
        DSL.TypeReference schema;

        @Override
        public <T> Dynamic<T> update(DSL.TypeReference schema, Dynamic<T> input, int from, int to) {
            this.calls++;
            this.schema = schema;
            this.from = from;
            this.to = to;
            return input.set("fixer_was_called", input.createBoolean(true));
        }

        @Override
        public Schema getSchema(int version) {
            throw new AssertionError("The selected data-fix type should call update directly");
        }
    }
}
