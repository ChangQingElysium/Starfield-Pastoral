package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeInstance;
import com.stardew.craft.tree.prefab.PrefabTreeManager;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import com.stardew.craft.tree.prefab.PrefabTrees;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 预制树调试命令（schem 接入后用于测试放置/砍伐）：
 * <pre>
 *   /stardew prefabtree place &lt;species&gt; &lt;variant&gt;   在脚下放置指定变体（不校验占地）
 *   /stardew prefabtree random &lt;species&gt;             在脚下随机放置一个变体（校验占地）
 *   /stardew prefabtree info                          查看脚下是否属于某棵预制树
 * </pre>
 */
public final class PrefabTreeDebugCommand {
	private PrefabTreeDebugCommand() {
	}

	private static final SuggestionProvider<CommandSourceStack> SPECIES_SUGGESTIONS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(
					new String[] { "oak", "maple", "pine", "mahogany", "mystic_tree" }, builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("stardew")
						.then(Commands.literal("prefabtree")
								.requires(src -> src.hasPermission(2))
								.then(Commands.literal("place")
										.then(Commands.argument("species", StringArgumentType.word())
												.suggests(SPECIES_SUGGESTIONS)
												.then(Commands.argument("variant", IntegerArgumentType.integer(1, PrefabTrees.VARIANTS_PER_SPECIES))
														.executes(PrefabTreeDebugCommand::runPlace))))
								.then(Commands.literal("random")
										.then(Commands.argument("species", StringArgumentType.word())
												.suggests(SPECIES_SUGGESTIONS)
												.executes(PrefabTreeDebugCommand::runRandom)))
								.then(Commands.literal("info")
										.executes(PrefabTreeDebugCommand::runInfo))));
	}

	private static int runPlace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		WildTrees.Def def = resolve(ctx);
		if (def == null) {
			return 0;
		}
		int variant = IntegerArgumentType.getInteger(ctx, "variant");
		ServerLevel level = player.serverLevel();
		BlockPos root = player.blockPosition();
		boolean ok = PrefabTreeManager.place(level, root, def, variant, false);
		ctx.getSource().sendSystemMessage(ok
				? Component.translatable("stardewcraft.command.prefab_tree.placed",
						def.id(), variant, root.toShortString())
				: Component.translatable("stardewcraft.command.prefab_tree.place_failed",
						PrefabTrees.structurePath(PrefabTrees.speciesIndex(def), variant)));
		return ok ? 1 : 0;
	}

	private static int runRandom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		WildTrees.Def def = resolve(ctx);
		if (def == null) {
			return 0;
		}
		ServerLevel level = player.serverLevel();
		BlockPos root = player.blockPosition();
		boolean ok = PrefabTreeManager.tryPlaceRandomVariant(level, root, def);
		ctx.getSource().sendSystemMessage(ok
				? Component.translatable("stardewcraft.command.prefab_tree.random_placed",
						def.id(), root.toShortString())
				: Component.translatable("stardewcraft.command.prefab_tree.random_failed"));
		return ok ? 1 : 0;
	}

	private static int runInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		BlockPos pos = player.blockPosition();
		PrefabTreeInstance inst = PrefabTreeRegistry.get(level).getByMember(pos);
		if (inst == null) {
			ctx.getSource().sendSystemMessage(Component.translatable(
					"stardewcraft.command.prefab_tree.not_found_at", pos.toShortString()));
			return 0;
		}
		ctx.getSource().sendSystemMessage(Component.translatable(
				"stardewcraft.command.prefab_tree.info",
				inst.species(), inst.variant(), inst.root().toShortString(), inst.members().size(),
				Component.translatable(inst.felled()
						? "stardewcraft.command.prefab_tree.status.felled"
						: "stardewcraft.command.prefab_tree.status.intact")));
		return 1;
	}

	private static WildTrees.Def resolve(CommandContext<CommandSourceStack> ctx) {
		String species = StringArgumentType.getString(ctx, "species");
		WildTrees.Def def = PrefabTrees.defById(species);
		if (def == null) {
			ctx.getSource().sendSystemMessage(Component.translatable(
					"stardewcraft.command.prefab_tree.unknown_species", species));
		}
		return def;
	}
}
