package com.stardew.craft.port.internal.network;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.network.CapabilityNegotiationTask;
import com.stardew.craft.pet.PetCatalogHandshake;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkRegistry;

@GameTestHolder("stardewcraft")
@PrefixGameTestTemplate(false)
public final class PortLoginTasksGameTests {
    private PortLoginTasksGameTests() {}

    private static NetworkRegistry.LoginPayload request(String taskId, byte[] body) {
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeByte(PortNetwork.INDEX_LOGIN_TASK);
        new PortLoginTasks.TaskMessage(taskId, body).write(data);
        return new NetworkRegistry.LoginPayload(data, PortNetwork.CHANNEL_ID, taskId);
    }

    private static PortLoginTasks.ReplyMessage reply(String taskId, int index) {
        var reply = new PortLoginTasks.ReplyMessage(taskId, new byte[]{99});
        reply.setLoginIndex(index);
        return reply;
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void configurationRepliesBindToServerIssuedTaskAndPendingIndex(GameTestHelper helper) {
        String pet = PetCatalogHandshake.TYPE.id();
        String capability = CapabilityNegotiationTask.TYPE.id();
        List<NetworkRegistry.LoginPayload> messages = List.of(request(pet, new byte[]{1}), request(capability, new byte[]{2}));
        List<Integer> pending = new ArrayList<>(List.of(0, 1));
        try {
            helper.assertTrue(PortLoginTasks.expectedReply(messages, pending, reply(pet, 0)) != null, "Normal pet acknowledgement rejected");
            helper.assertTrue(PortLoginTasks.expectedReply(messages, pending, reply(capability, 1)) != null, "Normal capability acknowledgement rejected");
            helper.assertTrue(PortLoginTasks.expectedReply(messages, pending, reply(capability, 0)) == null
                    && PortLoginTasks.expectedReply(messages, pending, reply(pet, 1)) == null, "A task could acknowledge another task's Forge login index");
            helper.assertTrue(PortLoginTasks.expectedReply(messages, pending, reply(pet, -1)) == null
                    && PortLoginTasks.expectedReply(messages, pending, reply(pet, 2)) == null, "Unissued login sequence accepted");
            pending.remove(Integer.valueOf(0));
            helper.assertTrue(PortLoginTasks.expectedReply(messages, pending, reply(pet, 0)) == null, "Completed login task accepted a replay");
            var authoritative = PortLoginTasks.expectedReply(messages, pending, reply(capability, 1));
            helper.assertTrue(Arrays.equals(authoritative.body, new byte[]{2}), "Server request metadata was replaced by the client's echo");
        } finally {
            messages.forEach(message -> message.getData().release());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void configurationFinishCannotCompleteAnotherCurrentTask(GameTestHelper helper) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        var finished = new HashSet<String>();
        var context = PortPayloadContext.login(connection, LogicalSide.SERVER, ignored -> {}, finished, PetCatalogHandshake.TYPE.id());
        try {
            context.finishCurrentTask(CapabilityNegotiationTask.TYPE);
            throw new AssertionError("Finishing a different configuration task was accepted");
        } catch (IllegalStateException expected) {
            helper.assertTrue(finished.isEmpty(), "Mismatched task mutated completion state");
        }
        context.finishCurrentTask(PetCatalogHandshake.TYPE);
        helper.assertTrue(finished.equals(java.util.Set.of(PetCatalogHandshake.TYPE.id())), "Current task completion lost its identity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty", timeoutTicks = 100)
    public static void configurationGatherRunsOnServerThreadWithoutJoiningItself(GameTestHelper helper) {
        Thread serverThread = Thread.currentThread();
        helper.assertTrue(PortLoginTasks.onServerThread(Thread::currentThread) == serverThread, "Already-main-thread gather did not run inline");
        CompletableFuture<Thread> queued = CompletableFuture.supplyAsync(() -> PortLoginTasks.onServerThread(Thread::currentThread));
        helper.succeedWhen(() -> helper.assertTrue(queued.isDone() && !queued.isCompletedExceptionally() && queued.join() == serverThread,
                "Off-thread gather did not execute on the server thread"));
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void configurationRepliesRequireLiveLoginNotFakeOrPlayConnection(GameTestHelper helper) {
        Connection disconnected = new Connection(PacketFlow.SERVERBOUND);
        helper.assertTrue(!PortLoginTasks.isLoginConnection(disconnected), "An unbound/fake connection accepted configuration replies");
        var fakePlayer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "port-login-boundary"));
        helper.assertTrue(!PortLoginTasks.isLoginConnection(fakePlayer.connection.connection), "A play-phase FakePlayer accepted login replies");
        Connection login = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(login);
        try {
            login.setProtocol(ConnectionProtocol.LOGIN);
            login.setListener(new ServerLoginPacketListenerImpl(helper.getLevel().getServer(), login));
            helper.assertTrue(PortLoginTasks.isLoginConnection(login), "A live server login connection failed the phase boundary");
            channel.close();
            helper.assertTrue(!PortLoginTasks.isLoginConnection(login), "Closed login connection remained eligible");
        } finally {
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }
}
