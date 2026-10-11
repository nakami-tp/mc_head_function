package com.nakami.mcheadfunction.haunt;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class HauntCommand {
	private HauntCommand() { }

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
			literal("haunt").requires(source -> source.hasPermissionLevel(2))
				.then(literal("start").executes(HauntCommand::start))
				.then(literal("stop").executes(HauntCommand::stop))
				.then(literal("off").executes(HauntCommand::off))
				.then(literal("on").executes(HauntCommand::on))
				.then(literal("count").then(argument("kind", StringArgumentType.word())
					.then(argument("count", IntegerArgumentType.integer(0, HauntRules.MAX_COUNT))
						.executes(HauntCommand::count))))
				.then(literal("town")
					.then(literal("build").executes(HauntCommand::build))
					.then(literal("reset").executes(HauntCommand::reset)))
				.then(literal("tree").then(literal("fill")
					.executes(c -> fill(c, null))
					.then(argument("except", StringArgumentType.word()).executes(c -> fill(c, head(StringArgumentType.getString(c, "except")))))))
		));
	}

	private static int start(CommandContext<ServerCommandSource> c) {
		var source = c.getSource();
		var world = source.getWorld();
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		var tree = HauntedTreeBlockEntity.nearest(world, source.getPosition());
		if (tree == null) {
			source.sendError(Text.translatable("haunt.mc_head_function.no_tree"));
			return 0;
		}
		tree.start(true);
		source.sendFeedback(() -> Text.translatable("haunt.mc_head_function.started"), true);
		return 1;
	}

	private static int stop(CommandContext<ServerCommandSource> c) {
		var tree = HauntedTreeBlockEntity.nearest(c.getSource().getWorld(), c.getSource().getPosition());
		if (tree == null) {
			c.getSource().sendError(Text.translatable("haunt.mc_head_function.no_tree"));
			return 0;
		}
		tree.stop();
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.stopped"), true);
		return 1;
	}

	private static int off(CommandContext<ServerCommandSource> c) {
		var world = c.getSource().getWorld();
		HauntWorldData.get(world).setOff(true);
		for (var pos : HauntIndex.trees(world)) {
			if (world.getBlockEntity(pos) instanceof HauntedTreeBlockEntity tree) tree.stop();
		}
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.off"), true);
		return 1;
	}

	private static int on(CommandContext<ServerCommandSource> c) {
		HauntWorldData.get(c.getSource().getWorld()).setOff(false);
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.on"), true);
		return 1;
	}

	private static int count(CommandContext<ServerCommandSource> c) {
		HostKind kind = kind(StringArgumentType.getString(c, "kind"));
		if (kind == null) {
			c.getSource().sendError(Text.translatable("haunt.mc_head_function.bad_kind"));
			return 0;
		}
		int value = IntegerArgumentType.getInteger(c, "count");
		HauntWorldData.get(c.getSource().getWorld()).setCount(kind, value);
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.count_set", kind.name().toLowerCase(), value), true);
		return 1;
	}

	private static int build(CommandContext<ServerCommandSource> c) {
		HauntTown.build(c.getSource().getWorld(), net.minecraft.util.math.BlockPos.ofFloored(c.getSource().getPosition()));
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.town_built"), true);
		return 1;
	}

	private static int reset(CommandContext<ServerCommandSource> c) {
		if (HauntWorldData.get(c.getSource().getWorld()).town() == null) {
			c.getSource().sendError(Text.translatable("haunt.mc_head_function.no_town"));
			return 0;
		}
		HauntTown.reset(c.getSource().getWorld());
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.town_reset"), true);
		return 1;
	}

	private static int fill(CommandContext<ServerCommandSource> c, HeadType except) {
		var tree = HauntedTreeBlockEntity.nearest(c.getSource().getWorld(), c.getSource().getPosition());
		if (tree == null) {
			c.getSource().sendError(Text.translatable("haunt.mc_head_function.no_tree"));
			return 0;
		}
		tree.fill(except);
		c.getSource().sendFeedback(() -> Text.translatable("haunt.mc_head_function.tree_filled"), true);
		return 1;
	}

	private static HeadType head(String name) {
		try { return HeadType.valueOf(name.toUpperCase()); }
		catch (IllegalArgumentException e) { return null; }
	}

	private static HostKind kind(String name) {
		try { return HostKind.valueOf(name.toUpperCase()); }
		catch (IllegalArgumentException e) { return null; }
	}
}
