package com.nakami.mcheadfunction.head;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Places requested heads on armor stands in a row in front of the player. */
public final class HeadDisplayCommand {
	private static final double SPACING = 1.5;
	private static final double DISTANCE = 3.0;

	private HeadDisplayCommand() { }

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
			literal("heads").requires(source -> source.hasPermissionLevel(2))
				.executes(c -> place(c, HeadType.values()))
				.then(argument("which", StringArgumentType.greedyString())
					.suggests(HeadDisplayCommand::suggest)
					.executes(HeadDisplayCommand::placeNamed))
		));
	}

	private static int placeNamed(CommandContext<ServerCommandSource> context) {
		String raw = StringArgumentType.getString(context, "which");
		var chosen = parse(raw);
		if (chosen == null) {
			context.getSource().sendError(Text.translatable("heads.mc_head_function.unknown", raw.trim()));
			return 0;
		}
		return place(context, chosen.toArray(HeadType[]::new));
	}

	static int place(CommandContext<ServerCommandSource> context, HeadType[] heads) {
		var source = context.getSource();
		var player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.translatable("heads.mc_head_function.no_player"));
			return 0;
		}
		var world = player.getServerWorld();
		var look = player.getRotationVector();
		var flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1.0E-6) flat = player.getRotationVector(0, player.getYaw());
		flat = flat.normalize();
		var right = new Vec3d(-flat.z, 0, flat.x);
		var center = player.getPos().add(flat.multiply(DISTANCE));
		double start = -(heads.length - 1) * SPACING / 2.0;
		float facing = player.getYaw() + 180.0F;
		for (int i = 0; i < heads.length; i++) {
			var at = center.add(right.multiply(start + i * SPACING));
			world.getChunk(BlockPos.ofFloored(at.x, player.getY(), at.z));
			var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
			stand.refreshPositionAndAngles(at.x, player.getY(), at.z, facing, 0);
			stand.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(heads[i])));
			world.spawnEntity(stand);
		}
		int count = heads.length;
		source.sendFeedback(() -> Text.translatable("heads.mc_head_function.placed", count), true);
		return count;
	}

	private static CompletableFuture<Suggestions> suggest(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining();
		int split = remaining.lastIndexOf(' ');
		String prefix = split < 0 ? "" : remaining.substring(0, split + 1);
		String current = (split < 0 ? remaining : remaining.substring(split + 1)).toLowerCase(Locale.ROOT);
		var next = builder.createOffset(builder.getStart() + prefix.length());
		for (HeadType type : HeadType.values()) {
			String name = type.name().toLowerCase(Locale.ROOT);
			if (name.startsWith(current)) next.suggest(name);
		}
		if ("all".startsWith(current)) next.suggest("all");
		return next.buildFuture();
	}

	private static HeadType head(String name) {
		try { return HeadType.valueOf(name.toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException e) { return null; }
	}

	/** Parses a display request. {@code all} or a blank list means every head. */
	public static List<HeadType> parse(String raw) {
		if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) return List.of(HeadType.values());
		var chosen = new ArrayList<HeadType>();
		for (String name : raw.trim().split("\\s+")) {
			var type = head(name);
			if (type == null) return null;
			chosen.add(type);
		}
		return chosen.isEmpty() ? null : chosen;
	}
}
