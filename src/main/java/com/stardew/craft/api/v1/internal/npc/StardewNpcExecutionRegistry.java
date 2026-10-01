package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcExecution;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Ordered and failure-isolated addon dispatch; empty means pass to the next resolver. */
public final class StardewNpcExecutionRegistry {
    private static final OrderedExtensionRegistry<StardewNpcExecution.Condition> CONDITIONS=
            new OrderedExtensionRegistry<>(new ResourceLocation("stardewcraft:npc/conditions"));
    private static final OrderedExtensionRegistry<StardewNpcExecution.SupportResolver> SUPPORTS=
            new OrderedExtensionRegistry<>(new ResourceLocation("stardewcraft:npc/supports"));
    private StardewNpcExecutionRegistry() {}
    public static void registerCondition(ResourceLocation id,int priority,StardewNpcExecution.Condition condition) { CONDITIONS.register(id,priority,condition); }
    public static void registerSupport(ResourceLocation id,int priority,StardewNpcExecution.SupportResolver support) { SUPPORTS.register(id,priority,support); }
    public static Optional<Boolean> condition(StardewNpcExecution.ConditionContext context) {
        for (var entry:CONDITIONS.entries()) try {
            var answer=CONDITIONS.invoke(entry,handler->handler.evaluate(context));
            if (answer.isPresent()) return answer;
        } catch (RuntimeException error) { com.mojang.logging.LogUtils.getLogger().error("NPC condition provider {} failed",entry.id(),error); }
        return Optional.empty();
    }
    public static Optional<StardewNpcExecution.Support> support(StardewNpcExecution.SupportContext context) {
        for (var entry:SUPPORTS.entries()) try {
            var answer=SUPPORTS.invoke(entry,handler->handler.resolve(context));
            if (answer.isPresent()) return answer;
        } catch (RuntimeException error) { com.mojang.logging.LogUtils.getLogger().error("NPC support provider {} failed",entry.id(),error); }
        return Optional.empty();
    }
}
