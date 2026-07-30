package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailDebugCommandTest {
    @Test
    void namespacedMailIdsAreAcceptedAsOneArgument() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        MailDebugCommand.register(dispatcher);

        var mail = dispatcher.getRoot().getChild("stardew").getChild("mail");
        var mailIdNode = assertInstanceOf(
                ArgumentCommandNode.class,
                mail.getChild("send").getChild("mailAndTargets"));
        var argument = assertInstanceOf(StringArgumentType.class, mailIdNode.getType());
        StringReader reader = new StringReader("example_stardew_addon:apple_club @a");

        assertEquals("example_stardew_addon:apple_club @a", argument.parse(reader));
        assertFalse(reader.canRead());
        assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, argument.getType());

        var targeted = MailDebugCommand.parseSend("example_stardew_addon:apple_club @a");
        assertEquals("example_stardew_addon:apple_club", targeted.mailId());
        assertNotNull(targeted.targetSelector());

        var legacy = MailDebugCommand.parseSend("RobinCooking");
        assertEquals("RobinCooking", legacy.mailId());
        assertNull(legacy.targetSelector());
        assertNotNull(mail.getChild("diagnostics"));
    }

    @Test
    void everyMailCommandArgumentCanBeSerializedToTheClient() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        MailDebugCommand.register(dispatcher);

        assertSerializable(dispatcher.getRoot().getChild("stardew").getChild("mail"));
    }

    private static void assertSerializable(CommandNode<?> node) {
        if (node instanceof ArgumentCommandNode<?, ?> argumentNode) {
            assertTrue(
                    ArgumentTypeInfos.isClassRecognized(argumentNode.getType().getClass()),
                    () -> "Unrecognized synchronized argument type: "
                            + argumentNode.getType().getClass().getName());
            assertNotNull(ArgumentTypeInfos.unpack(argumentNode.getType()));
        }
        node.getChildren().forEach(MailDebugCommandTest::assertSerializable);
    }
}
