package com.stardew.craft.platform.fabric.client.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Guards the native branches and live locals used by the mixins without creating a client. */
class ClientExtensionHookContractTest {
    @Test
    void lateConstructorInitializationFollowsAllRendererFieldsAndPrecedesInitialReload() throws IOException {
        var ctor = method("net/minecraft/client/Minecraft", "<init>");
        int initialize = calls(ctor, "com/mojang/blaze3d/platform/Window", "updateVsync").getFirst();
        for (String fieldName : List.of("entityModels", "blockEntityRenderDispatcher", "itemRenderer", "particleEngine")) {
            int assignment = -1;
            for (int i = 0; i < ctor.instructions.size(); i++) if (ctor.instructions.get(i) instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD && field.owner.equals("net/minecraft/client/Minecraft") && field.name.equals(fieldName)) assignment = i;
            assertTrue(assignment >= 0 && assignment < initialize, fieldName + " is not ready at initialization");
        }
        assertEquals(1, calls(ctor, "com/mojang/blaze3d/platform/Window", "updateVsync").size());
        int reload = calls(ctor, "net/minecraft/server/packs/resources/ReloadableResourceManager", "createReload").getFirst();
        assertTrue(initialize < reload);
        var renderer = node("net/minecraft/client/renderer/entity/ItemRenderer");
        assertTrue(renderer.fields.stream().anyMatch(field -> field.name.equals("blockEntityRenderer")
                && field.desc.equals("Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;")));
    }

    @Test
    void customRendererRemainsInTheNativeCustomModelBranchAndRetainsThePoseLifecycle() throws IOException {
        var render = method("net/minecraft/client/renderer/entity/ItemRenderer", "render");
        int custom = calls(render, "net/minecraft/client/resources/model/BakedModel", "isCustomRenderer").getFirst();
        int actual = calls(render, "net/minecraft/client/renderer/BlockEntityWithoutLevelRenderer", "renderByItem").getFirst();
        assertEquals(1, calls(render, "net/minecraft/client/renderer/BlockEntityWithoutLevelRenderer", "renderByItem").size());
        assertTrue(calls(render, "com/mojang/blaze3d/vertex/PoseStack", "pushPose").getFirst() < custom);
        assertTrue(custom < actual);
        assertTrue(actual < calls(render, "com/mojang/blaze3d/vertex/PoseStack", "popPose").getFirst());
        var call = (MethodInsnNode) render.instructions.get(actual);
        assertEquals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", call.desc);
    }

    @Test
    void normalHandHookUsesTheSecondUsePredicateWithOneLiveArmAndSkipsNoSpecialBranch() throws IOException {
        var hand = method("net/minecraft/client/renderer/ItemInHandRenderer", "renderArmWithItem");
        var predicates = calls(hand, "net/minecraft/client/player/AbstractClientPlayer", "isUsingItem");
        assertEquals(2, predicates.size());
        int point = predicates.get(1);
        assertTrue(hand.localVariables.stream().anyMatch(local -> local.desc.equals("Lnet/minecraft/world/entity/HumanoidArm;")
                && hand.instructions.indexOf(local.start) <= point && hand.instructions.indexOf(local.end) > point));
        assertEquals(1, hand.localVariables.stream().filter(local -> local.desc.equals("Lnet/minecraft/world/entity/HumanoidArm;")
                && hand.instructions.indexOf(local.start) <= point && hand.instructions.indexOf(local.end) > point).count());
        assertTrue(calls(hand, "net/minecraft/client/renderer/ItemInHandRenderer", "renderOneHandedMap").getFirst() < point);
        assertTrue(calls(hand, "net/minecraft/client/renderer/ItemInHandRenderer", "renderTwoHandedMap").getFirst() < point);
        assertTrue(calls(hand, "net/minecraft/client/renderer/ItemInHandRenderer", "renderItem").getFirst() < point); // crossbow
        assertTrue(point < calls(hand, "net/minecraft/client/renderer/ItemInHandRenderer", "renderItem").getLast());
        assertEquals(1, calls(hand, "com/mojang/blaze3d/vertex/PoseStack", "popPose").size());
    }

    @Test
    void hitAndDestroyHooksHaveTheRealTargetAndRetainTheNativeAirGuard() throws IOException {
        var held = method("net/minecraft/client/Minecraft", "continueAttack");
        int hit = calls(held, "net/minecraft/client/particle/ParticleEngine", "crack").getFirst();
        assertTrue(held.localVariables.stream().anyMatch(local -> local.desc.equals("Lnet/minecraft/world/phys/BlockHitResult;")
                && held.instructions.indexOf(local.start) <= hit && held.instructions.indexOf(local.end) > hit));
        var destroy = method("net/minecraft/client/particle/ParticleEngine", "destroy");
        int air = calls(destroy, "net/minecraft/world/level/block/state/BlockState", "isAir").getFirst();
        int flag = calls(destroy, "net/minecraft/world/level/block/state/BlockState", "shouldSpawnTerrainParticles").getFirst();
        assertEquals(1, calls(destroy, "net/minecraft/world/level/block/state/BlockState", "shouldSpawnTerrainParticles").size());
        assertTrue(air < flag);
        assertTrue(flag < calls(destroy, "net/minecraft/world/level/block/state/BlockState", "getShape").getFirst());
    }

    private static List<Integer> calls(MethodNode method, String owner, String name) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) result.add(i);
        assertFalse(result.isEmpty(), owner + "." + name); return result;
    }
    private static MethodNode method(String owner, String name) throws IOException {
        return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }
    private static ClassNode node(String owner) throws IOException {
        try (var bytes = ClientExtensionHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
