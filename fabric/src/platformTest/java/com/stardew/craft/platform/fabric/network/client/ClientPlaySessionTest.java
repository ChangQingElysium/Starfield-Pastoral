package com.stardew.craft.platform.fabric.network.client;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ClientPlaySessionTest {
    private static final CustomPacketPayload PAYLOAD = () -> new CustomPacketPayload.Type<>(ResourceLocation.parse("fixture:session"));
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void queuedRealPlayHandlerAfterLogoutDoesNotReadTheNowAbsentPlayer() throws Exception {
        var fixture = new Fixture();
        PacketSender old = sender(); fixture.current.set(old);
        var context = fixture.context(old);
        var queue = new ArrayList<Runnable>();
        queue.add(() -> fixture.receiver.receive(PAYLOAD, context));
        fixture.current.set(null); fixture.player.set(null);
        assertDoesNotThrow(queue.removeFirst()::run);
        assertEquals(0, fixture.playerReads.get());
        assertTrue(fixture.applied.isEmpty());
    }

    @Test void oldQueuedPayloadCannotBindToTheNewPlayerAndTheNewSendersHandlerStillDispatches() throws Exception {
        var fixture = new Fixture();
        PacketSender old = sender(), next = sender();
        fixture.current.set(old);
        var oldContext = fixture.context(old);
        var queue = new ArrayList<Runnable>();
        queue.add(() -> fixture.receiver.receive(PAYLOAD, oldContext));
        LocalPlayer nextPlayer = player();
        fixture.current.set(next); fixture.player.set(nextPlayer);
        var nextContext = fixture.context(next);
        queue.add(() -> fixture.receiver.receive(PAYLOAD, nextContext));
        queue.forEach(Runnable::run);
        assertEquals(List.of(nextPlayer), fixture.applied);
        assertEquals(List.of(next), fixture.usedSenders);
        assertEquals(1, fixture.playerReads.get());
    }

    @Test void activeRealPlayCallbacksForwardPayloadAndContextInTheirOriginalOrder() throws Exception {
        var fixture = new Fixture();
        PacketSender sender = sender(); fixture.current.set(sender);
        var context = fixture.context(sender);
        var seen = new ArrayList<CustomPacketPayload>();
        ClientPlayNetworking.PlayPayloadHandler<CustomPacketPayload> receiver = FabricClientNetworking.playReceiver(
                ignored -> fixture.current.get(), (payload, actual) -> {
                    assertSame(context, actual); assertSame(fixture.player.get(), actual.player()); seen.add(payload);
                });
        CustomPacketPayload other = () -> new CustomPacketPayload.Type<>(ResourceLocation.parse("fixture:other"));
        for (var payload : List.of(PAYLOAD, other, PAYLOAD)) receiver.receive(payload, context);
        assertEquals(List.of(PAYLOAD, other, PAYLOAD), seen);
        assertEquals(3, fixture.playerReads.get());
    }

    @Test void currentReceiverFailuresStillReachTheExistingDispatchBoundary() throws Exception {
        var fixture = new Fixture();
        PacketSender sender = sender(); fixture.current.set(sender);
        var failure = new IllegalArgumentException("handler fixture");
        ClientPlayNetworking.PlayPayloadHandler<CustomPacketPayload> receiver = FabricClientNetworking.playReceiver(
                ignored -> fixture.current.get(), (payload, context) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> receiver.receive(PAYLOAD, fixture.context(sender))));
    }

    @Test void globalPlayRegistrationActuallyUsesTheGuardedFactoryAndItsWorkRemainsLazy() throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(FabricClientNetworking.class.getName().replace('.', '/') + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(node, 0);
        }
        var register = node.methods.stream().filter(method -> method.name.equals("register")).findFirst().orElseThrow();
        int factoryIndex = -1, registerIndex = -1, index = 0;
        for (var instruction : register.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(node.name) && call.name.equals("playReceiver")) factoryIndex = index;
                if (call.owner.endsWith("/ClientPlayNetworking") && call.name.equals("registerGlobalReceiver")) registerIndex = index;
            }
            index++;
        }
        assertTrue(factoryIndex >= 0 && registerIndex > factoryIndex, "Real registration must use the same guarded FAPI handler tested above");
        var receiver = node.methods.stream().filter(method -> method.name.startsWith("lambda$playReceiver$")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(receiver.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.name.equals("receive") && call.owner.endsWith("ClientPlayNetworking$PlayPayloadHandler")));
        assertFalse(Arrays.stream(receiver.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.name.equals("player")), "Only the accepted connection's deferred dispatcher may read the player");
    }

    private static final class Fixture {
        final AtomicReference<PacketSender> current = new AtomicReference<>();
        final AtomicReference<LocalPlayer> player = new AtomicReference<>(player());
        final AtomicInteger playerReads = new AtomicInteger();
        final List<LocalPlayer> applied = new ArrayList<>();
        final List<PacketSender> usedSenders = new ArrayList<>();
        final ClientPlayNetworking.PlayPayloadHandler<CustomPacketPayload> receiver = FabricClientNetworking.playReceiver(
                ignored -> current.get(), (payload, context) -> { assertSame(PAYLOAD, payload); applied.add(context.player()); usedSenders.add(context.responseSender()); });
        Fixture() throws ReflectiveOperationException {}
        ClientPlayNetworking.Context context(PacketSender sender) {
            return (ClientPlayNetworking.Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ClientPlayNetworking.Context.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "responseSender" -> sender;
                        case "player" -> { playerReads.incrementAndGet(); yield Objects.requireNonNull(player.get(), "player"); }
                        default -> throw new AssertionError("Unexpected client access: " + method);
                    });
        }
    }
    private static PacketSender sender() {
        return (PacketSender) Proxy.newProxyInstance(ClientPlaySessionTest.class.getClassLoader(), new Class<?>[]{PacketSender.class},
                (proxy, method, arguments) -> { throw new AssertionError("Use sender identity, not equals or a recreated sender: " + method); });
    }
    private static LocalPlayer player() throws ReflectiveOperationException {
        var type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (LocalPlayer) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), LocalPlayer.class);
    }
}
