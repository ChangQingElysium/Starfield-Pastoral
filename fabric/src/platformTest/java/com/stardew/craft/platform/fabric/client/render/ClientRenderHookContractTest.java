package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Check the actual 1.21.1 bytecode targeted by the mixins without creating a GL window. */
class ClientRenderHookContractTest {
    @Test
    void bothGraphicsBranchesExposeParticlesAndWeatherBeforeTheirTargetsAreCleared() throws IOException {
        var trace = trace("net/minecraft/client/renderer/LevelRenderer", "renderLevel", null);
        var particles = indexes(trace, "call net/minecraft/client/particle/ParticleEngine.render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V");
        var weather = indexes(trace, "call net/minecraft/client/renderer/LevelRenderer.renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V");
        assertEquals(2, particles.size());
        assertEquals(2, weather.size());
        int firstTargetClear = trace.subList(particles.getFirst() + 1, trace.size()).indexOf(
                "call net/minecraft/client/renderer/RenderStateShard$OutputStateShard.clearRenderState()V") + particles.getFirst() + 1;
        assertTrue(firstTargetClear > particles.getFirst());
        assertTrue(firstTargetClear < particles.getLast());
        assertTrue(particles.getLast() < weather.getFirst());
        assertTrue(trace.contains("constant blockentities"));
        assertTrue(trace.contains("constant destroyProgress"));
        assertTrue(trace.indexOf("constant blockentities") < trace.indexOf("constant destroyProgress"));
        var section = trace("net/minecraft/client/renderer/LevelRenderer", "renderSectionLayer", null);
        assertEquals(1, indexes(section, "call net/minecraft/client/renderer/RenderType.clearRenderState()V").size());
    }

    @Test
    void logoutAndAtlasCompletionTargetsKeepStateAvailableUntilTheHook() throws IOException {
        var logout = trace("net/minecraft/client/Minecraft", "disconnect", "(Lnet/minecraft/client/gui/screens/Screen;Z)V");
        String gameMode = "put net/minecraft/client/Minecraft.gameMode:Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;";
        String player = "put net/minecraft/client/Minecraft.player:Lnet/minecraft/client/player/LocalPlayer;";
        assertEquals(1, indexes(logout, gameMode).size());
        assertTrue(logout.indexOf(gameMode) < logout.indexOf(player));
        var atlas = trace("net/minecraft/client/renderer/texture/TextureAtlas", "upload", "(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V");
        assertTrue(atlas.contains("put net/minecraft/client/renderer/texture/TextureAtlas.animatedTextures:Ljava/util/List;"));
        assertTrue(atlas.contains("put net/minecraft/client/renderer/texture/TextureAtlas.sprites:Ljava/util/List;"));
        assertTrue(atlas.indexOf("put net/minecraft/client/renderer/texture/TextureAtlas.animatedTextures:Ljava/util/List;") < atlas.lastIndexOf("return"));
        assertTrue(atlas.indexOf("put net/minecraft/client/renderer/texture/TextureAtlas.sprites:Ljava/util/List;") < atlas.lastIndexOf("return"));
        assertFalse(trace("net/minecraft/client/multiplayer/ClientPacketListener", "handleRespawn",
                "(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V").isEmpty());
    }

    private static List<Integer> indexes(List<String> trace, String text) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) if (trace.get(i).equals(text)) indexes.add(i);
        return indexes;
    }

    private static List<String> trace(String owner, String method, String descriptor) throws IOException {
        List<String> result = new ArrayList<>();
        try (var bytes = ClientRenderHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!method.equals(name) || (descriptor != null && !descriptor.equals(desc))) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { result.add("call " + owner + "." + name + desc); }
                        @Override public void visitLdcInsn(Object value) { result.add("constant " + value); }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            if (opcode == Opcodes.PUTFIELD) result.add("put " + owner + "." + name + ":" + desc);
                        }
                        @Override public void visitInsn(int opcode) { if (opcode == Opcodes.RETURN) result.add("return"); }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertFalse(result.isEmpty(), owner + "." + method + descriptor);
        return result;
    }
}
